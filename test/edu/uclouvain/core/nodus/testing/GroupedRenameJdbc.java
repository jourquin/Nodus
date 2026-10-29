/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.testing;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translates grouped SQL to an embedded driver; faults occur before or after the whole group. */
public final class GroupedRenameJdbc {
  public final Connection connection;
  public final List<String> renames = new ArrayList<>();
  public String fault = "";
  public boolean triggered;
  public int failCommit;
  private int commits;
  private int groups;

  /** Wraps the embedded connection with MySQL/MariaDB publication SQL and fault injection. */
  public GroupedRenameJdbc(Connection delegate) {
    connection =
        (Connection)
            Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getMetaData")) {
                    DatabaseMetaData metadata = delegate.getMetaData();
                    return Proxy.newProxyInstance(
                        DatabaseMetaData.class.getClassLoader(),
                        new Class<?>[] {DatabaseMetaData.class},
                        (object, member, values) -> {
                          if (member.getName().equals("getDatabaseProductName")) {
                            return "MariaDB";
                          }
                          return invoke(metadata, member, values);
                        });
                  }
                  if (method.getName().equals("commit") && ++commits == failCommit) {
                    triggered = true;
                    throw new SQLException("Injected publication commit failure");
                  }
                  if (method.getName().equals("createStatement")) {
                    Statement statement = delegate.createStatement();
                    return Proxy.newProxyInstance(
                        Statement.class.getClassLoader(),
                        new Class<?>[] {Statement.class},
                        (object, member, values) -> {
                          if ((member.getName().equals("executeUpdate")
                                  || member.getName().equals("execute"))
                              && fault.equals("cleanup")
                              && ((String) values[0]).startsWith("DROP TABLE")
                              && (((String) values[0]).contains("NB_")
                                  || ((String) values[0]).contains("NDB_"))) {
                            triggered = true;
                            throw new SQLException("Injected backup cleanup failure");
                          }
                          if (member.getName().equals("executeUpdate")
                              && values[0] instanceof String
                              && ((String) values[0]).startsWith("RENAME TABLE ")) {
                            String sql = (String) values[0];
                            renames.add(sql);
                            groups++;
                            if ((fault.equals("before") && groups == 1)
                                || (fault.equals("recovery") && groups == 2)) {
                              triggered = true;
                              throw new SQLException("Injected grouped rename failure");
                            }
                            Matcher names =
                                Pattern.compile("(\"(?:[^\"]|\"\")+\") TO (\"(?:[^\"]|\"\")+\")")
                                    .matcher(sql);
                            while (names.find()) {
                              try (Statement rename = delegate.createStatement()) {
                                rename.executeUpdate(
                                    "ALTER TABLE "
                                        + names.group(1)
                                        + " RENAME TO "
                                        + names.group(2));
                              }
                            }
                            if ((fault.equals("after") || fault.equals("recovery"))
                                && groups == 1) {
                              triggered = true;
                              throw new SQLException("Injected lost rename acknowledgement");
                            }
                            return 0;
                          }
                          return invoke(statement, member, values);
                        });
                  }
                  return invoke(delegate, method, args);
                });
  }

  private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException failure) {
      throw failure.getCause();
    }
  }
}
