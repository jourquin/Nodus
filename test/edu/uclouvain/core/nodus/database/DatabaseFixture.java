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

/** Private in-memory database and temporary project directory; callers must lock JDBCUtils. */
public final class DatabaseFixture implements AutoCloseable {
  public final Connection connection;
  public final NodusProject project;

  /** Creates a database with a small batch size to exercise intermediate and final flushes. */
  public DatabaseFixture(Path directory) throws SQLException {
    connection =
        DriverManager.getConnection("jdbc:h2:mem:nodus_files_" + UUID.randomUUID(), "sa", "");
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

  /** Executes setup SQL without retaining a statement. */
  public void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  @Override
  public void close() throws SQLException {
    JDBCUtils.setConnection(null);
    connection.close();
  }
}
