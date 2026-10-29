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

package edu.uclouvain.core.nodus.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusMapPanel;
import edu.uclouvain.core.nodus.compute.assign.AssignmentTestProject;
import edu.uclouvain.core.nodus.compute.assign.workers.AssignmentWorker;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.utils.SoundPlayer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Real assignment network and SQL, with faults restricted to the output JDBC boundary. */
public final class AssignmentOutputFixture extends AssignmentTestProject {
  /** Controlled failures, including successful writes whose JDBC acknowledgement is lost. */
  public enum Fault {
    NONE,
    NO_BATCH,
    LATE_BATCH,
    BATCH_STATUS,
    NETWORK_COMMIT,
    COMMIT_ACK_LOST,
    PATH_FINAL_BATCH,
    PATH_COMMIT,
    STATEMENT_CLOSE,
    PUBLISH_METADATA,
    PUBLISH_RENAME,
    PUBLISH_RENAME_AFTER,
    PUBLISH_BACKUP_AFTER,
    PUBLISH_COMMIT
  }

  public final ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
  private final PrintStream previousError = System.err;
  private PrintStream capturedError;
  public final RecordingPanel outputPanel;
  public final OutputJdbc jdbc;

  /** Creates a private H2 or HSQLDB network with two commodities and a batch size of two. */
  public AssignmentOutputFixture(Path directory, boolean hsql) throws Exception {
    super(
        directory,
        4,
        new double[][] {{11, 1, 2, 2, 0}, {12, 2, 4, 3, 0}, {13, 1, 3, 4, 0}, {14, 3, 4, 4, 0}},
        hsql);
    execute("INSERT INTO mini_od VALUES (1,1,4,100), (2,2,4,40)");
    super.getMainJDBCConnection().commit();
    outputPanel = new RecordingPanel(this);
    jdbc = new OutputJdbc(super.getMainJDBCConnection());
    JDBCUtils.setConnection(jdbc.connection);
    capturedError = new PrintStream(diagnostics, true, StandardCharsets.UTF_8);
    System.setErr(capturedError);
  }

  @Override
  public Connection getMainJDBCConnection() {
    return jdbc == null ? super.getMainJDBCConnection() : jdbc.connection;
  }

  @Override
  public NodusMapPanel getNodusMapPanel() {
    return outputPanel == null ? super.getNodusMapPanel() : outputPanel;
  }

  @Override
  public void close() {
    try {
      super.close();
    } finally {
      System.setErr(previousError);
      capturedError.close();
    }
  }

  /** Checks resources through the real JDBC driver, independently of proxy event counters. */
  public void assertClean() throws Exception {
    assertFalse(outputPanel.progressRunning);
    for (PreparedStatement statement : jdbc.statements) {
      assertTrue(statement.isClosed());
    }
    try (ResultSet tables =
        getMainJDBCConnection().getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
      while (tables.next()) {
        String name = tables.getString("TABLE_NAME").toUpperCase(Locale.ROOT);
        assertFalse(
            name.startsWith("NO_") || name.startsWith("NB_"), "Leaked output table " + name);
      }
    }
  }

  /** Records presentation without throwing when an expected SQL error is reported. */
  public static final class RecordingPanel extends NodusMapPanel {
    private static final long serialVersionUID = 1L;
    final AssignmentOutputFixture project;
    public final List<Integer> messages = new CopyOnWriteArrayList<>();
    public final List<Integer> sounds = new CopyOnWriteArrayList<>();
    public final Set<Thread> workers = ConcurrentHashMap.newKeySet();
    public volatile boolean progressRunning;
    public volatile int textResets;
    public volatile int scenarioRefreshes;
    public volatile boolean lastRefreshWasIdle;
    public Predicate<String> progress = message -> true;
    public boolean failReporting;
    private final SoundPlayer player =
        new SoundPlayer(false) {
          @Override
          public void play(int sound) {
            sounds.add(sound);
          }
        };

    RecordingPanel(AssignmentOutputFixture project) {
      this.project = project;
    }

    @Override
    public AssignmentOutputFixture getNodusProject() {
      return project;
    }

    @Override
    public SoundPlayer getSoundPlayer() {
      return player;
    }

    @Override
    public void showAssignmentMessage(String text, int type) {
      messages.add(type);
      if (failReporting) {
        throw new IllegalStateException("Injected presentation failure");
      }
    }

    @Override
    public void setBusy(boolean value) {}

    @Override
    public void setText(String text) {}

    @Override
    public void resetText() {
      textResets++;
    }

    @Override
    public void updateScenarioComboBox(boolean reset) {
      scenarioRefreshes++;
      lastRefreshWasIdle = getAssignmentMenuItem().isEnabled();
    }

    @Override
    public void startProgress(int length) {
      progressRunning = true;
    }

    @Override
    public void stopProgress() {
      progressRunning = false;
    }

    @Override
    public boolean updateProgress(String message) {
      return updateProgress(message, 1);
    }

    @Override
    public boolean updateProgress(String message, int interval) {
      if (Thread.currentThread() instanceof AssignmentWorker) {
        workers.add(Thread.currentThread());
      }
      return progress.test(message) && !Thread.currentThread().isInterrupted();
    }

    /** Clears callbacks from fixture setup or a previous successful run. */
    public void reset() {
      messages.clear();
      sounds.clear();
      workers.clear();
      textResets = 0;
      scenarioRefreshes = 0;
      progress = message -> true;
    }
  }

  /** Delegates all SQL to the real engine while injecting one selected output fault. */
  public static final class OutputJdbc {
    private final Connection delegate;
    public final Connection connection;
    public volatile Fault fault = Fault.NONE;
    public volatile boolean triggered;
    public volatile boolean networkCommitted;
    public volatile int networkBatches;
    public volatile int networkUpdates;
    public volatile int insertedBeforeFailure;
    public volatile int rowsAfterRollback = -1;
    private volatile String networkTable;
    private volatile int publicationRenames;
    public final List<PreparedStatement> statements = new CopyOnWriteArrayList<>();

    OutputJdbc(Connection delegate) {
      this.delegate = delegate;
      connection =
          (Connection)
              Proxy.newProxyInstance(
                  Connection.class.getClassLoader(),
                  new Class<?>[] {Connection.class},
                  (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getMetaData")) {
                      DatabaseMetaData metadata = delegate.getMetaData();
                      return Proxy.newProxyInstance(
                          DatabaseMetaData.class.getClassLoader(),
                          new Class<?>[] {DatabaseMetaData.class},
                          (object, member, values) -> {
                            if (member.getName().equals("getTables")
                                && networkCommitted
                                && "MINI_VNET1"
                                    .equalsIgnoreCase(((String) values[2]).replace("\\_", "_"))
                                && trip(Fault.PUBLISH_METADATA)) {
                              throw new SQLNonTransientConnectionException(
                                  "connection exception: closed", "08003");
                            }
                            return member.getName().equals("supportsBatchUpdates")
                                    && fault == Fault.NO_BATCH
                                ? false
                                : invoke(metadata, member, values);
                          });
                    }
                    if (name.equals("prepareStatement")) {
                      PreparedStatement statement =
                          (PreparedStatement) invoke(delegate, method, args);
                      String sql = ((String) args[0]).toUpperCase(Locale.ROOT);
                      boolean network = sql.startsWith("INSERT INTO \"NO_") && sql.contains("_V\"");
                      boolean header = sql.startsWith("INSERT INTO \"NO_") && sql.contains("_H\"");
                      boolean detail = sql.startsWith("INSERT INTO \"NO_") && sql.contains("_D\"");
                      if (!network && !header && !detail) {
                        return statement;
                      }
                      statements.add(statement);
                      if (network) {
                        networkTable =
                            sql.substring("INSERT INTO ".length(), sql.indexOf(" VALUES"));
                      }
                      return wrap(statement, network, header);
                    }
                    if (name.equals("createStatement")) {
                      Statement statement = (Statement) invoke(delegate, method, args);
                      return Proxy.newProxyInstance(
                          Statement.class.getClassLoader(),
                          new Class<?>[] {Statement.class},
                          (object, member, values) -> {
                            if (member.getName().equals("execute") && values[0] instanceof String) {
                              String sql = ((String) values[0]).toUpperCase(Locale.ROOT);
                              if (sql.startsWith("ALTER TABLE") && sql.contains("RENAME TO")) {
                                publicationRenames++;
                                if ((sql.contains("RENAME TO \"NB_")
                                        && trip(Fault.PUBLISH_BACKUP_AFTER))
                                    || (sql.contains("\"NO_")
                                        && sql.contains("_H\"")
                                        && trip(Fault.PUBLISH_RENAME_AFTER))) {
                                  invoke(statement, member, values);
                                  throw failure();
                                }
                                if (sql.contains("\"NO_")
                                    && sql.contains("_H\"")
                                    && trip(Fault.PUBLISH_RENAME)) {
                                  throw failure();
                                }
                              }
                            }
                            return invoke(statement, member, values);
                          });
                    }
                    if (name.equals("commit") && networkTable != null) {
                      if (!networkCommitted) {
                        if (trip(Fault.NETWORK_COMMIT)) {
                          throw failure();
                        }
                        if (trip(Fault.COMMIT_ACK_LOST)) {
                          delegate.commit();
                          throw failure();
                        }
                        Object value = invoke(delegate, method, args);
                        networkCommitted = true;
                        return value;
                      }
                      if (publicationRenames > 0
                          ? trip(Fault.PUBLISH_COMMIT)
                          : trip(Fault.PATH_COMMIT)) {
                        throw failure();
                      }
                    }
                    Object value = invoke(delegate, method, args);
                    if (name.equals("rollback") && networkTable != null) {
                      rowsAfterRollback = countNetworkRows();
                    }
                    return value;
                  });
    }

    /** Arms a fresh failure attempt, retaining the same real project connection. */
    public void reset(Fault mode) {
      fault = mode;
      triggered = false;
      networkCommitted = false;
      networkBatches = 0;
      networkUpdates = 0;
      insertedBeforeFailure = 0;
      rowsAfterRollback = -1;
      networkTable = null;
      publicationRenames = 0;
      statements.clear();
    }

    private PreparedStatement wrap(PreparedStatement statement, boolean network, boolean header) {
      return (PreparedStatement)
          Proxy.newProxyInstance(
              PreparedStatement.class.getClassLoader(),
              new Class<?>[] {PreparedStatement.class},
              (proxy, method, args) -> {
                String name = method.getName();
                if (network && name.equals("executeUpdate")) {
                  networkUpdates++;
                }
                if (name.equals("executeBatch")) {
                  if (network) {
                    networkBatches++;
                    if (networkBatches == 2 && trip(Fault.LATE_BATCH)) {
                      throw failure();
                    }
                    if (networkBatches == 2 && trip(Fault.BATCH_STATUS)) {
                      int[] counts = statement.executeBatch();
                      counts[0] = Statement.EXECUTE_FAILED;
                      return counts;
                    }
                  } else if (header && networkCommitted && trip(Fault.PATH_FINAL_BATCH)) {
                    throw failure();
                  }
                }
                Object value = invoke(statement, method, args);
                if (name.equals("close")
                    && header
                    && networkCommitted
                    && trip(Fault.STATEMENT_CLOSE)) {
                  throw failure();
                }
                return value;
              });
    }

    private synchronized boolean trip(Fault expected) {
      if (!triggered && fault == expected) {
        triggered = true;
        insertedBeforeFailure = countNetworkRows();
        return true;
      }
      return false;
    }

    private int countNetworkRows() {
      if (networkTable == null) {
        return 0;
      }
      try (Statement statement = delegate.createStatement();
          ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + networkTable)) {
        rows.next();
        return rows.getInt(1);
      } catch (SQLException unavailableAfterRename) {
        return -1;
      }
    }

    private SQLException failure() {
      return new SQLException("Injected output failure: " + fault);
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
      try {
        return method.invoke(target, args);
      } catch (InvocationTargetException failure) {
        throw failure.getCause();
      }
    }
  }
}
