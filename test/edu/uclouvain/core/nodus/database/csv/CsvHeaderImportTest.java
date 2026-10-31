/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * <p>Center for Operations Research and Econometrics (CORE)
 *
 * <p>http://www.uclouvain.be
 *
 * <p>This file is part of Nodus.
 *
 * <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program. If
 * not, see http://www.gnu.org/licenses/.
 */

package edu.uclouvain.core.nodus.database.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real CSV schema inference, field mapping and transaction recovery on both embedded engines. */
@ResourceLock("JDBCUtils")
class CsvHeaderImportTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> inferenceAndValidation() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (String scenario :
          List.of(
              "infer",
              "headerOnly",
              "allNull",
              "bom",
              "identifiers",
              "largeIntegers",
              "hugeExponent",
              "tinyExponent",
              "mixed",
              "quotedNames",
              "reorder",
              "dates",
              "missing",
              "duplicate",
              "blankHeader",
              "tooMany",
              "tooFew",
              "badNumber",
              "longText",
              "fraction",
              "precision",
              "notNull",
              "constraint",
              "malformed",
              "empty",
              "changed",
              "renamed",
              "unsupportedBatch",
              "ambiguous",
              "extremeNumber")) {
        tests.add(
            dynamicTest(
                hsql + "/" + scenario,
                () -> {
                  try (DatabaseFixture db = new DatabaseFixture(directory, hsql)) {
                    check(db, scenario);
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> transactionBoundaries() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean success : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                "transaction/" + hsql + "/" + success,
                () -> {
                  try (DatabaseFixture db = new DatabaseFixture(directory, hsql)) {
                    db.execute("CREATE TABLE data(id INTEGER PRIMARY KEY, label VARCHAR(20))");
                    db.execute("INSERT INTO data VALUES (99, 'original')");
                    db.execute("CREATE TABLE earlier(id INTEGER)");
                    List<String> errors = new ArrayList<>();
                    if (success) {
                      db.connection.setAutoCommit(false);
                      db.execute("INSERT INTO earlier VALUES (7)");
                      write("label,id\nnew,42\n");
                      assertTrue(importFile(db.project, errors), errors.toString());
                      assertFalse(db.connection.getAutoCommit());
                      assertEquals("new", scalar(db, "SELECT label FROM data WHERE id=42"));
                      assertEquals("1", scalar(db, "SELECT COUNT(*) FROM earlier"));
                      db.connection.rollback();
                      assertEquals("0", scalar(db, "SELECT COUNT(*) FROM earlier"));
                    } else {
                      write("id,label\n1,first\n2,second\n1,duplicate\n");
                      assertFalse(importFile(db.project, errors));
                      assertTrue(db.connection.getAutoCommit());
                      assertFalse(errors.isEmpty());
                    }
                    assertEquals("original", scalar(db, "SELECT label FROM data WHERE id=99"));
                    assertEquals("1", scalar(db, "SELECT COUNT(*) FROM data"));
                    assertFalse(db.connection.isClosed());
                  }
                }));
      }
    }
    return tests;
  }

  private void check(DatabaseFixture db, String scenario) throws Exception {
    List<String> errors = new ArrayList<>();
    if (scenario.equals("infer")) {
      write(
          "code,quantity,weight,flag,mixed,shipdate,empty\n"
              + "00123,10,12.50,TRUE,1,2026-10-31,\n"
              + "00124,20,8.75,false,rail,01/02/2026,\n"
              + "a longer code,30,123.456,,\"padded, 東京\",not a date,\n");
      assertTrue(importFile(db.project, errors), errors.toString());
      try (Statement statement = db.connection.createStatement();
          ResultSet rows = statement.executeQuery("SELECT * FROM data ORDER BY quantity")) {
        assertEquals(7, rows.getMetaData().getColumnCount());
        assertEquals(Types.BIGINT, rows.getMetaData().getColumnType(2));
        assertEquals(Types.DECIMAL, rows.getMetaData().getColumnType(3));
        assertEquals(6, rows.getMetaData().getPrecision(3));
        assertEquals(3, rows.getMetaData().getScale(3));
        assertEquals(Types.VARCHAR, rows.getMetaData().getColumnType(6));
        assertTrue(rows.next());
        assertEquals("00123", rows.getString(1));
        assertEquals(new BigDecimal("12.500"), rows.getBigDecimal(3));
        assertTrue(rows.getBoolean(4));
        assertEquals("1", rows.getString(5));
        assertEquals("2026-10-31", rows.getString(6));
        assertNull(rows.getObject(7));
        assertTrue(rows.next());
        assertFalse(rows.getBoolean(4));
        assertTrue(rows.next());
        assertEquals("a longer code", rows.getString(1));
        assertEquals("padded, 東京", rows.getString(5));
        assertNull(rows.getObject(4));
      }
      return;
    }
    if (List.of(
            "headerOnly",
            "allNull",
            "bom",
            "identifiers",
            "largeIntegers",
            "hugeExponent",
            "tinyExponent",
            "mixed",
            "quotedNames")
        .contains(scenario)) {
      String sql;
      String expected;
      switch (scenario) {
        case "headerOnly":
          write("id,label\n");
          sql = "SELECT COUNT(*) FROM data";
          expected = "0";
          break;
        case "allNull":
          write("id,label\n,\n");
          sql = "SELECT label FROM data";
          expected = null;
          break;
        case "bom":
          write("\uFEFFid,label\n42,Tokyo\n");
          sql = "SELECT label FROM data WHERE id=42";
          expected = "Tokyo";
          break;
        case "identifiers":
          write("code\n12\n00123\n-0012\n0.25\n");
          sql = "SELECT code FROM data WHERE code='00123'";
          expected = "00123";
          break;
        case "largeIntegers":
          write("id\n9223372036854775808\n");
          sql = "SELECT id FROM data";
          expected = "9223372036854775808";
          break;
        case "hugeExponent":
          write("id\n1e2147483647\n");
          sql = "SELECT id FROM data";
          expected = "1e2147483647";
          break;
        case "tinyExponent":
          write("id\n1e-2147483647\n");
          sql = "SELECT id FROM data";
          expected = "1e-2147483647";
          break;
        case "quotedNames":
          write("\"a,b\",\"a\"\"b\"\nrail,sea\n");
          sql = "SELECT * FROM data";
          expected = "rail";
          break;
        default:
          write("mixed_value\ntrue\n1\ntext\n");
          sql = "SELECT mixed_value FROM data WHERE mixed_value='1'";
          expected = "1";
          break;
      }
      assertTrue(importFile(db.project, errors), errors.toString());
      assertEquals(expected, scalar(db, sql));
      return;
    }
    if (scenario.equals("reorder")) {
      db.execute(
          "CREATE TABLE data (id INTEGER PRIMARY KEY, label VARCHAR(20), amount DECIMAL(8,2))");
      db.execute("INSERT INTO data VALUES (99,'original',1)");
      write("amount,LABEL,id\n12.50,\"quoted, 東京\",42\n,,43\n");
      assertTrue(importFile(db.project, errors), errors.toString());
      assertEquals("quoted, 東京", scalar(db, "SELECT label FROM data WHERE id=42"));
      assertNull(scalar(db, "SELECT amount FROM data WHERE id=43"));
      assertNull(scalar(db, "SELECT label FROM data WHERE id=43"));
      assertThrows(
          java.sql.SQLException.class, () -> db.execute("INSERT INTO data VALUES (42,'x',0)"));
      return;
    }
    if (scenario.equals("dates")) {
      db.execute("CREATE TABLE data (d DATE, t TIME, stamp TIMESTAMP)");
      write("stamp,t,d\n2026-10-31T12:30:00,12:30:00,2026-10-31\n");
      assertTrue(importFile(db.project, errors), errors.toString());
      assertEquals("2026-10-31", scalar(db, "SELECT d FROM data"));
      return;
    }
    if (scenario.equals("unsupportedBatch")) {
      db.execute("CREATE TABLE data(id INTEGER, label VARCHAR(20))");
      write("label,id\nfirst,1\nsecond,2\nthird,3\n");
      Connection tracked = proxy(db.connection, null, true);
      assertTrue(importFile(project(db, tracked), errors), errors.toString());
      assertEquals("3", scalar(db, "SELECT COUNT(*) FROM data"));
      return;
    }
    if (List.of("duplicate", "blankHeader", "malformed", "empty").contains(scenario)) {
      write(
          scenario.equals("duplicate")
              ? "id,ID\n42,43\n"
              : scenario.equals("blankHeader")
                  ? "id,\n42,43\n"
                  : scenario.equals("malformed") ? "id,label\n1,\"unterminated\n" : "");
      assertFalse(importFile(db.project, errors));
      assertFalse(errors.isEmpty());
      assertFalse(JDBCUtils.tableExists(db.connection, "data"));
      assertNoStaging(db);
      return;
    }
    if (scenario.equals("ambiguous")) {
      db.execute("CREATE TABLE data (\"ID\" INTEGER, \"id\" INTEGER)");
      db.execute("INSERT INTO data VALUES (7,8)");
      write("id,other\n42,43\n");
      assertFalse(importFile(db.project, errors));
      assertTrue(errors.get(0).contains("Ambiguous"), errors.toString());
      assertEquals("7", scalar(db, "SELECT * FROM data"));
      return;
    }
    db.execute("CREATE TABLE data (id INTEGER PRIMARY KEY, label VARCHAR(8), amount DECIMAL(4,2))");
    db.execute("INSERT INTO data VALUES (99,'original',1)");
    String row = "3,third,3\n";
    switch (scenario) {
      case "missing":
        write("other,label,amount\n42,new,1\n");
        break;
      case "tooMany":
        row = "3,third,3,extra\n";
        break;
      case "tooFew":
        row = "3,third\n";
        break;
      case "badNumber":
        row = "3,third,not-a-number\n";
        break;
      case "longText":
        row = "3,much too long,3\n";
        break;
      case "fraction":
        row = "3.1,third,3\n";
        break;
      case "precision":
        row = "3,third,100\n";
        break;
      case "notNull":
        row = ",third,3\n";
        break;
      case "constraint":
        row = "1,third,3\n";
        break;
      case "extremeNumber":
        row = "3,third,1e-2147483647\n";
        break;
      default:
        break;
    }
    if (!scenario.equals("missing")) {
      write("id,label,amount\n1,first,1\n2,second,2\n" + row);
    }
    db.execute("CREATE TABLE earlier (id INTEGER)");
    db.connection.setAutoCommit(false);
    db.execute("INSERT INTO earlier VALUES (7)");
    NodusProject project = db.project;
    AtomicBoolean changed = new AtomicBoolean();
    if (scenario.equals("changed") || scenario.equals("renamed")) {
      project =
          project(
              db,
              proxy(
                  db.connection,
                  () -> {
                    if (changed.compareAndSet(false, true)) {
                      try {
                        write(
                            scenario.equals("renamed")
                                ? "label,id,amount\nfirst,1,1\n"
                                : "id,label,amount\n1,first,1\n2,second,2\n3,altered,3\n");
                      } catch (Exception error) {
                        throw new RuntimeException(error);
                      }
                    }
                  },
                  false));
    }
    assertFalse(importFile(project, errors));
    assertFalse(errors.isEmpty());
    if (scenario.equals("changed")) {
      assertTrue(errors.get(0).contains("changed"), errors.toString());
    }
    if (List.of("badNumber", "longText", "fraction", "precision", "notNull", "extremeNumber")
        .contains(scenario)) {
      assertTrue(errors.get(0).contains("CSV record 4, column"), errors.toString());
    }
    assertEquals("original", scalar(db, "SELECT label FROM data WHERE id=99"));
    assertEquals("1", scalar(db, "SELECT COUNT(*) FROM data"));
    assertEquals("1", scalar(db, "SELECT COUNT(*) FROM earlier"));
    assertFalse(db.connection.getAutoCommit());
    assertFalse(db.connection.isClosed());
    db.connection.rollback();
    assertEquals("0", scalar(db, "SELECT COUNT(*) FROM earlier"));
    assertNoStaging(db);
  }

  private void write(String csv) throws Exception {
    Files.writeString(directory.resolve("data.csv"), csv, Charset.defaultCharset());
  }

  private static boolean importFile(NodusProject project, List<String> errors) {
    return ImportCSV.importTable(project, "data", true, errors::add);
  }

  private static String scalar(DatabaseFixture db, String sql) throws Exception {
    try (Statement s = db.connection.createStatement();
        ResultSet r = s.executeQuery(sql)) {
      assertTrue(r.next());
      return r.getString(1);
    }
  }

  private static void assertNoStaging(DatabaseFixture db) throws Exception {
    try (ResultSet tables =
        db.connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
      while (tables.next()) {
        String name = tables.getString("TABLE_NAME");
        assertFalse(name.startsWith("NDI_") || name.startsWith("NDB_"), name);
      }
    }
  }

  private NodusProject project(DatabaseFixture db, Connection connection) {
    return new NodusProject(null) {
      @Override
      public Connection getMainJDBCConnection() {
        return connection;
      }

      @Override
      public String getLocalProperty(String key) {
        return NodusC.PROP_PROJECT_DOTPATH.equals(key) ? directory + java.io.File.separator : null;
      }

      @Override
      public int getSqlBatchSize() {
        return 2;
      }
    };
  }

  private static Connection proxy(Connection connection, Runnable onDelete, boolean noBatch) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (p, method, args) -> {
              try {
                Object value = method.invoke(connection, args);
                if (method.getName().equals("getMetaData") && noBatch) {
                  return Proxy.newProxyInstance(
                      java.sql.DatabaseMetaData.class.getClassLoader(),
                      new Class<?>[] {java.sql.DatabaseMetaData.class},
                      (proxy, operation, parameters) -> {
                        if (operation.getName().equals("supportsBatchUpdates")) {
                          return false;
                        }
                        try {
                          return operation.invoke(value, parameters);
                        } catch (InvocationTargetException error) {
                          throw error.getCause();
                        }
                      });
                }
                if (method.getName().equals("createStatement") && onDelete != null) {
                  return Proxy.newProxyInstance(
                      Statement.class.getClassLoader(),
                      new Class<?>[] {Statement.class},
                      (proxy, operation, parameters) -> {
                        try {
                          Object result = operation.invoke(value, parameters);
                          if (operation.getName().equals("executeUpdate")
                              && parameters[0].toString().startsWith("DELETE")) {
                            onDelete.run();
                          }
                          return result;
                        } catch (InvocationTargetException error) {
                          throw error.getCause();
                        }
                      });
                }
                return value;
              } catch (InvocationTargetException error) {
                throw error.getCause();
              }
            });
  }
}
