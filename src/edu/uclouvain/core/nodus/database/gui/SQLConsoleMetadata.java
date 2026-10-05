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

import com.bbn.openmap.Environment;
import com.bbn.openmap.util.I18n;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Vector;
import javax.swing.tree.DefaultMutableTreeNode;

/** Builds a detached database metadata tree without touching the visible Swing model. */
final class SQLConsoleMetadata {

  private static final I18n i18n = Environment.getI18n();

  private SQLConsoleMetadata() {}

  static DefaultMutableTreeNode build(Connection jdbcConnection, DatabaseMetaData metaData) {
    DefaultMutableTreeNode rebuiltRoot = new DefaultMutableTreeNode("");

    // Now rebuild the tree below its root
    try {
      // Start by naming the root node from its URL:
      rebuiltRoot.setUserObject(metaData.getURL());

      String schema = null;

      // For H2 (version 2), specify that only the "PUBLIC" schema must be displayed
      if (JDBCUtils.getDbEngine() == JDBCUtils.DB_H2) {
        schema = "PUBLIC";
      }

      String catalog = null;
      if (JDBCUtils.getDbEngine() == JDBCUtils.DB_MYSQL) {
        catalog = "";
      }

      Vector<String> tables = new Vector<>();
      Vector<String> remarks = new Vector<>();

      try (ResultSet result = JDBCUtils.getTables()) {
        String s;
        while (result.next()) {
          s = result.getString(3);
          if (s.indexOf("$") == -1) {
            tables.addElement(s);
            remarks.addElement(result.getString(5));
          }
        }
      }

      for (int i = 0; i < tables.size(); i++) {
        String name = tables.elementAt(i);
        DefaultMutableTreeNode tableNode = makeDetachedNode(name, rebuiltRoot);
        String remark = remarks.elementAt(i);

        if (remark != null && !remark.trim().equals("")) {
          makeDetachedNode(remark, tableNode);
        }

        try (ResultSet col = metaData.getColumns(catalog, schema, name, null)) {
          while (col.next()) {
            String c = col.getString(4);
            DefaultMutableTreeNode columnNode = makeDetachedNode(c, tableNode);
            String type = col.getString(6);

            makeDetachedNode(
                MessageFormat.format(i18n.get(SQLConsole.class, "Type", "Type: {0}"), type),
                columnNode);

            boolean nullable = col.getInt(11) != DatabaseMetaData.columnNoNulls;

            makeDetachedNode(
                MessageFormat.format(
                    i18n.get(SQLConsole.class, "Nullable", "Nullable: {0}"), nullable),
                columnNode);
          }
        }

        DefaultMutableTreeNode indexesNode =
            makeDetachedNode(i18n.get(SQLConsole.class, "Indices", "Indices"), tableNode);

        try (ResultSet ind = metaData.getIndexInfo(catalog, schema, name, false, false)) {
          String oldiname = null;

          while (ind.next()) {
            DefaultMutableTreeNode indexNode = null;
            boolean nonunique = ind.getBoolean(4);
            String iname = ind.getString(6);

            if (oldiname == null || !oldiname.equals(iname)) {
              indexNode = makeDetachedNode(iname, indexesNode);
              makeDetachedNode(
                  MessageFormat.format(
                      i18n.get(SQLConsole.class, "Unique", "Unique: {0}"), !nonunique),
                  indexNode);
              oldiname = iname;
            }

            makeDetachedNode(ind.getString(9), indexNode);
          }
        }
      }

      DefaultMutableTreeNode propertiesNode =
          makeDetachedNode(i18n.get(SQLConsole.class, "Properties", "Properties"), rebuiltRoot);

      makeDetachedNode(
          MessageFormat.format(
              i18n.get(SQLConsole.class, "User", "User: {0}"), metaData.getUserName()),
          propertiesNode);
      makeDetachedNode(
          MessageFormat.format(
              i18n.get(SQLConsole.class, "ReadOnly", "ReadOnly: {0}"), jdbcConnection.isReadOnly()),
          propertiesNode);
      makeDetachedNode(
          MessageFormat.format(
              i18n.get(SQLConsole.class, "AutoCommit", "AutoCommit: {0}"),
              jdbcConnection.getAutoCommit()),
          propertiesNode);
      makeDetachedNode(
          MessageFormat.format(
              i18n.get(SQLConsole.class, "Driver", "Driver: {0}"), metaData.getDriverName()),
          propertiesNode);
      makeDetachedNode(
          MessageFormat.format(
              i18n.get(SQLConsole.class, "Product", "Product: {0}"),
              metaData.getDatabaseProductName()),
          propertiesNode);
      makeDetachedNode(
          MessageFormat.format(
              i18n.get(SQLConsole.class, "Version", "Version: {0}"),
              metaData.getDatabaseProductVersion()),
          propertiesNode);
    } catch (SQLException se) {
      DefaultMutableTreeNode propertiesNode =
          makeDetachedNode(
              i18n.get(SQLConsole.class, "Error_getting_metadata", "Error getting metadata") + ":",
              rebuiltRoot);
      makeDetachedNode(se.getMessage(), propertiesNode);
      makeDetachedNode(se.getSQLState(), propertiesNode);
    }

    return rebuiltRoot;
  }

  /** Builds a detached tree node hierarchy that can safely be prepared off the EDT. */
  private static DefaultMutableTreeNode makeDetachedNode(
      Object userObject, DefaultMutableTreeNode parent) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(userObject);

    if (parent != null) {
      parent.add(node);
    }

    return node;
  }
}
