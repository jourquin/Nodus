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

package edu.uclouvain.core.nodus.database;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * An isolated in-memory database and minimal project stub for database and file-import tests.
 *
 * <p>Each instance uses a unique database name, with H2 by default or HSQLDB when requested. The
 * project exposes the fixture connection, reports itself as open, and resolves project files
 * beneath the supplied directory. Its SQL batch size is two, allowing small test inputs to exercise
 * both intermediate batch flushes and the final partial batch. Other properties return the caller's
 * default; this is not a fully initialized GUI project.
 *
 * <p>Construction installs the connection in the process-wide {@link JDBCUtils} state. Tests must
 * hold {@code @ResourceLock("JDBCUtils")} for the fixture's entire lifetime and must not nest
 * fixtures or use them concurrently. Closing clears that state rather than restoring a previous
 * connection.
 *
 * <p>Use try-with-resources to close the owned connection. The caller owns the supplied directory
 * (normally a JUnit {@code @TempDir}); this fixture neither creates nor deletes it or its files.
 */
public final class DatabaseFixture implements AutoCloseable {
  /** Owned JDBC connection; tests may change its transaction mode and commit or roll back work. */
  public final Connection connection;

  /**
   * Project stub sharing {@link #connection}, the supplied file directory, and batch-size settings.
   */
  public final NodusProject project;

  /**
   * Creates an H2 fixture with a SQL batch size of two.
   *
   * @param directory caller-managed project directory for imported and exported files
   * @throws SQLException if the connection or JDBCUtils metadata cannot be initialized
   */
  public DatabaseFixture(Path directory) throws SQLException {
    this(directory, false);
  }

  /**
   * Creates a fixture for either of the embedded engines used by persistence tests.
   *
   * <p>The connection initially uses the driver's default auto-commit mode. Tests that need an
   * existing transaction must explicitly disable auto-commit. The database name is unique to this
   * fixture, but additional connections can access it while testing imports that use independent
   * connections for schema changes.
   *
   * @param directory caller-managed project directory for imported and exported files
   * @param hsql {@code true} for HSQLDB, {@code false} for H2
   * @throws SQLException if the connection or JDBCUtils metadata cannot be initialized; a
   *     connection opened before metadata initialization fails is closed before the exception is
   *     thrown
   */
  public DatabaseFixture(Path directory, boolean hsql) throws SQLException {
    connection =
        DriverManager.getConnection(
            (hsql ? "jdbc:hsqldb:mem:" : "jdbc:h2:mem:") + "nodus_files_" + UUID.randomUUID(),
            "sa",
            "");
    project =
        new NodusProject(null) {
          @Override
          public Connection getMainJDBCConnection() {
            return connection;
          }

          @Override
          public boolean isOpen() {
            return true;
          }

          @Override
          public String getLocalProperty(String key) {
            return getLocalProperty(key, (String) null);
          }

          @Override
          public String getLocalProperty(String key, String defaultValue) {
            return NodusC.PROP_PROJECT_DOTPATH.equals(key)
                ? directory + File.separator
                : defaultValue;
          }

          @Override
          public int getLocalProperty(String key, int defaultValue) {
            return NodusC.PROP_MAX_SQL_BATCH_SIZE.equals(key) ? 2 : defaultValue;
          }
        };
    if (!JDBCUtils.setConnection(connection)) {
      connection.close();
      throw new SQLException("Could not initialize the test database metadata");
    }
  }

  /**
   * Executes SQL on the fixture connection and closes the temporary statement.
   *
   * <p>This method does not change auto-commit or explicitly commit the transaction.
   * Database-specific implicit commits, such as those caused by DDL on H2 and HSQLDB, still apply.
   * Query results are not exposed; use {@link #connection} directly for assertions that read rows.
   *
   * @param sql SQL command to execute, typically test setup DDL or DML
   * @throws SQLException if statement creation, execution, or closure fails
   */
  public void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /**
   * Clears the shared JDBCUtils connection and closes this fixture's connection.
   *
   * <p>There is no explicit commit, rollback, database shutdown, or file cleanup here. Tests should
   * finish transactions explicitly when their outcome matters; connection-close behavior is
   * determined by the driver.
   *
   * @throws SQLException if closing the connection fails
   */
  @Override
  public void close() throws SQLException {
    JDBCUtils.setConnection(null);
    connection.close();
  }
}
