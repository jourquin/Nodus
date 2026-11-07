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

package edu.uclouvain.core.nodus.database.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.database.DatabaseFixture;
import edu.uclouvain.core.nodus.swing.GridSwing;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
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
      database.execute("CREATE INDEX COMPOSITE_INDEX ON INSPECTED(ID, LABEL)");
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
      DefaultMutableTreeNode composite =
          Collections.list(table.depthFirstEnumeration())
              .stream()
              .map(DefaultMutableTreeNode.class::cast)
              .filter(node -> "COMPOSITE_INDEX".equals(node.getUserObject()))
              .findFirst()
              .orElseThrow();
      List<Object> indexColumns = new ArrayList<>();
      Collections.list(composite.children())
          .stream()
          .map(DefaultMutableTreeNode.class::cast)
          .forEach(node -> indexColumns.add(node.getUserObject()));
      assertTrue(indexColumns.contains("ID"));
      assertTrue(indexColumns.contains("LABEL"));
    }
  }

  @Test
  void historyShowsNewestFirstAndPreservesTheOrderWhenReopened() throws Exception {
    Properties properties = new Properties();
    properties.setProperty("sql.history0", "SELECT 'older'");
    properties.setProperty("sql.history2", "SELECT 'newer'");
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

          @Override
          public void removeLocalProperty(String key) {
            properties.remove(key);
          }
        };
    SwingUtilities.invokeAndWait(
        () -> {
          final List<String> events = new ArrayList<>();
          SQLConsoleHistory history = new SQLConsoleHistory(project);
          history.loadHistory();
          assertEquals("SELECT 'newer'", history.get(0));
          assertEquals("SELECT 'older'", history.get(1));
          history.saveHistory();
          assertFalse(properties.containsKey("sql.history2"));

          for (int i = 0; i < 25; i++) {
            history.addToRecent("SELECT " + i);
          }
          assertEquals("SELECT 24", history.get(0));
          assertEquals("SELECT 23", history.get(1));
          assertEquals("SELECT 1", history.get(23));
          history.addToRecent("SELECT 1");
          assertEquals("SELECT 1", history.get(0));
          assertEquals("SELECT 24", history.get(1));
          JPopupMenu popup = history.createPopup(events::add);
          assertEquals(24, popup.getComponentCount());
          ((JMenuItem) popup.getComponent(0)).doClick();
          assertEquals(List.of("SELECT 1"), events);
          history.saveHistory();
          SQLConsoleHistory restored = new SQLConsoleHistory(project);
          restored.loadHistory();
          JPopupMenu restoredPopup = restored.createPopup(events::add);
          assertEquals(24, restoredPopup.getComponentCount());
          for (int i = 0; i < 24; i++) {
            assertEquals(history.get(i), restored.get(i));
            assertEquals(history.get(i), ((JMenuItem) restoredPopup.getComponent(i)).getText());
          }
        });
  }

  @Test
  void historyPopupRecallsAnUnterminatedCommandAndUndoRestoresTheDraft() throws Exception {
    try (DatabaseFixture database = new DatabaseFixture(directory)) {
      SwingUtilities.invokeAndWait(
          () -> {
            SQLConsole console = new SQLConsole(database.project, false);
            try {
              RSyntaxTextArea editor = (RSyntaxTextArea) console.getSqlCommandArea();
              String draft = "SELECT draft";
              editor.setText(draft);

              SQLConsoleHistory history = new SQLConsoleHistory(database.project);
              String command = "SELECT ID, DESCRIPTION FROM A_TABLE_WITH_A_LONG_NAME";
              history.addToRecent(command);
              history.addToRecent("SELECT 42");
              JPopupMenu popup = history.createPopup(console::recallRecentCommand);
              assertEquals(draft, editor.getText());
              assertEquals(2, popup.getComponentCount());
              assertEquals("SELECT 42", ((JMenuItem) popup.getComponent(0)).getText());
              JMenuItem older = (JMenuItem) popup.getComponent(1);
              assertEquals(command.substring(0, 40) + "...", older.getText());

              older.doClick();
              assertEquals(command, editor.getText());
              assertEquals(command.length(), editor.getCaretPosition());
              editor.undoLastAction();
              assertEquals(draft, editor.getText());

              String script = "SELECT 1;\nSELECT 2;";
              editor.setText(script);
              editor.setCaretPosition(3);
              ((JMenuItem) popup.getComponent(0)).doClick();
              assertEquals("SELECT 1;\nSELECT 42;\nSELECT 2;", editor.getText());
              editor.undoLastAction();
              assertEquals(script, editor.getText());
            } finally {
              console.windowClosing(null);
            }
          });
    }
  }
}
