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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.hsqldb.server.ServerConstants;

/**
 * Owns a project's built-in database listener, restricted to the IPv4 loopback interface.
 *
 * <p>Local R, Python, and JDBC clients can connect while the project is open. An explicit {@code
 * jdbc.url} denotes an independently managed database: this class neither starts nor stops its
 * server, regardless of its host or DBMS. Close the project's JDBC connections before closing this
 * owner. A failed start cleans up its own resources and never falls back to an occupied port.
 */
public final class LocalDatabaseServer implements AutoCloseable {
  /** Shared listener address and host used in the built-in JDBC URLs. */
  public static final String HOST = "127.0.0.1";

  private org.hsqldb.Server hsql;
  private org.h2.tools.Server h2;

  private LocalDatabaseServer() {}

  /**
   * Sets H2's process-wide listener binding before its settings class is initialized.
   *
   * <p>Call at application startup, before plugins or JDBC drivers can load H2. The start method
   * verifies H2's cached setting too, refusing to expose a server if H2 was initialized too early.
   * Outgoing JDBC connections to independently managed servers are unaffected.
   */
  public static void configureH2Binding() {
    System.setProperty("h2.bindAddress", HOST);
  }

  /**
   * Starts the selected built-in server, or returns null for an explicit JDBC URL.
   *
   * @param project properties from the .nodus file, excluding saved .local defaults
   * @param engine selected built-in DBMS identifier from JDBCUtils
   * @param directory project directory containing the database files
   * @param name project basename, also used as the HSQLDB database alias
   * @param port local TCP port, between 1 and 65535
   * @param user configured database username
   * @param password configured database password
   * @return the owned listener, or null when no local listener is needed
   * @throws Exception if binding, database initialization, or server startup fails
   */
  public static LocalDatabaseServer start(
      Properties project,
      int engine,
      Path directory,
      String name,
      int port,
      String user,
      String password)
      throws Exception {
    if (project.getProperty(NodusC.PROP_JDBC_URL) != null
        || (engine != JDBCUtils.DB_HSQLDB && engine != JDBCUtils.DB_H2)) {
      return null;
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("Invalid local database port: " + port);
    }
    LocalDatabaseServer owner = new LocalDatabaseServer();
    try {
      switch (engine) {
        case JDBCUtils.DB_HSQLDB:
          owner.hsql = new org.hsqldb.Server();
          // Configuration setters also log; silence them while retaining the error writer.
          owner.hsql.setLogWriter(null);
          owner.hsql.setAddress(HOST);
          owner.hsql.setPort(port);
          owner.hsql.setDatabaseName(0, name);
          owner.hsql.setDatabasePath(
              0, "file:" + directory.resolve(name + "_hsqldb") + ";shutdown=true");
          owner.hsql.setNoSystemExit(true);
          owner.hsql.start();
          if (owner.hsql.getState() != ServerConstants.SERVER_STATE_ONLINE) {
            throw new SQLException(
                "Could not start local HSQLDB server on port " + port, owner.hsql.getServerError());
          }
          break;
        case JDBCUtils.DB_H2:
          configureH2Binding();
          if (!HOST.equals(org.h2.engine.SysProperties.BIND_ADDRESS)) {
            throw new SQLException(
                "H2 was initialized before its loopback binding was configured. "
                    + "Restart Nodus before opening this project.");
          }
          String database = directory.resolve(name).toAbsolutePath().toString();
          owner.h2 =
              org.h2.tools.Server.createTcpServer(
                  "-tcpPort", Integer.toString(port), "-ifExists", "-key", database, database);
          owner.h2.start();
          // Only Nodus can create the project database; TCP clients may open only this database.
          try (Connection connection =
              DriverManager.getConnection("jdbc:h2:file:" + database, user, password)) {
            // Connecting locally creates a new database or validates access to the existing one.
            connection.getMetaData();
          }
          break;
        default:
          throw new IllegalArgumentException("Unsupported local server: " + engine);
      }
      return owner;
    } catch (Exception failure) {
      try {
        owner.close();
      } catch (Exception cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  /**
   * Stops only the listener owned by this instance. Safe to call again after successful closure.
   *
   * <p>H2 is stopped through its retained server instance, without a shared shutdown password or a
   * shutdown command sent to a possibly unrelated service at the same port. This also releases its
   * listener after SQL SHUTDOWN COMPACT has closed the database itself.
   *
   * @throws Exception if the owned server cannot be stopped
   */
  @Override
  public void close() throws Exception {
    if (hsql != null) {
      hsql.shutdown();
      hsql = null;
    }
    if (h2 != null) {
      h2.stop();
      h2 = null;
    }
  }
}
