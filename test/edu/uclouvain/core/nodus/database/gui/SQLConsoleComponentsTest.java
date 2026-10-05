/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package edu.uclouvain.core.nodus.database.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.swing.GridSwing;
import java.awt.event.ActionListener;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Checks the extracted result, metadata and history responsibilities with real JDBC/Swing data. */
@ResourceLock("JDBCUtils")
class SQLConsoleComponentsTest {
  @TempDir Path directory;

  @Test
  void resultFormattingKeepsLabelsNullValuesHeadersAndClosesTheResultSet() throws Exception {
    try (DatabaseFixture database = new DatabaseFixture(directory);
        Statement statement = database.connection.createStatement()) {
      statement.execute("SELECT 42 AS ID, CAST(NULL AS VARCHAR) AS NOTE");
      ResultSet jdbcResult = statement.getResultSet();
      SQLConsoleResults.FormattedResult result = SQLConsoleResults.read(statement, 1);
      assertTrue(jdbcResult.isClosed());
      assertArrayEquals(new String[] {"ID", "NOTE"}, result.header);
      assertEquals(1, result.rows.size());
      assertArrayEquals(new String[] {"42", "(null)"}, result.rows.get(0));
      assertTrue(result.maxRowsReached);
      GridSwing grid = new GridSwing();
      grid.setHead(result.header);
      result.rows.forEach(grid::addRow);
      String nl = System.lineSeparator();
      assertEquals(
          "ID NOTE " + nl + "-- ---- " + nl + "42 (null)", SQLConsoleResults.asText(grid, true));
      assertEquals("42 (null)", SQLConsoleResults.asText(grid, false));
      statement.execute("SELECT 1 AS ID WHERE FALSE");
      result = SQLConsoleResults.read(statement, 1);
      assertTrue(result.rows.isEmpty());
      assertFalse(result.maxRowsReached);
    }
  }

  @Test
  void metadataTreeIncludesTablesColumnsAndIndexes() throws Exception {
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      database.execute("CREATE TABLE INSPECTED (ID INTEGER NOT NULL, LABEL VARCHAR(30))");
      database.execute("CREATE UNIQUE INDEX INSPECTED_ID ON INSPECTED(ID)");
      DefaultMutableTreeNode root =
          SQLConsoleMetadata.build(database.connection, database.connection.getMetaData());
      assertEquals(database.connection.getMetaData().getURL(), root.getUserObject());
      DefaultMutableTreeNode table =
          Collections.list(root.children())
              .stream()
              .map(DefaultMutableTreeNode.class::cast)
              .filter(node -> "INSPECTED".equals(node.getUserObject()))
              .findFirst()
              .orElseThrow();
      List<Object> values = new ArrayList<>();
      Collections.list(table.depthFirstEnumeration())
          .stream()
          .map(DefaultMutableTreeNode.class::cast)
          .forEach(node -> values.add(node.getUserObject()));
      assertTrue(values.contains("ID"));
      assertTrue(values.contains("LABEL"));
      assertTrue(values.contains("INSPECTED_ID"));
    }
  }

  @Test
  void historyWrapsDeduplicatesPersistsAndRetainsTheOriginalListener() throws Exception {
    Properties properties = new Properties();
    NodusProject project =
        new NodusProject(null) {
          @Override
          public boolean isOpen() {
            return true;
          }

          @Override
          public String getLocalProperty(String key, String fallback) {
            return properties.getProperty(key, fallback);
          }

          @Override
          public void setLocalProperty(String key, String value) {
            properties.setProperty(key, value);
          }
        };
    SwingUtilities.invokeAndWait(
        () -> {
          JMenu menu = new JMenu();
          List<String> events = new ArrayList<>();
          ActionListener listener = event -> events.add(event.getActionCommand());
          SQLConsoleHistory history = new SQLConsoleHistory(project, menu, listener);
          for (int i = 0; i < 25; i++) {
            history.addToRecent("SELECT " + i);
          }
          history.addToRecent("SELECT 24");
          assertEquals(24, menu.getItemCount());
          assertEquals("SELECT 24", history.get(0));
          assertEquals("SELECT 1", history.get(1));
          assertSame(listener, menu.getItem(0).getActionListeners()[0]);
          menu.getItem(0).doClick();
          assertEquals(List.of("#0"), events);
          history.saveHistory();
          JMenu restoredMenu = new JMenu();
          SQLConsoleHistory restored = new SQLConsoleHistory(project, restoredMenu, listener);
          restored.loadHistory();
          assertEquals(24, restoredMenu.getItemCount());
          for (int i = 0; i < 24; i++) {
            assertEquals(history.get(i), restored.get(i));
          }
        });
  }
}
