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

import edu.uclouvain.core.nodus.NodusProject;
import java.awt.event.ActionListener;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Maintains recent commands and persists them in the project properties. */
final class SQLConsoleHistory {

  private static final int MAX_HISTORY = 24;
  private final String[] recentQueries = new String[MAX_HISTORY];
  private final NodusProject nodusProject;
  private final JMenu menuRecent;
  private final ActionListener listener;
  private int history;

  SQLConsoleHistory(NodusProject project, JMenu menu, ActionListener listener) {
    this.nodusProject = project;
    this.menuRecent = menu;
    this.listener = listener;
  }

  String get(int index) {
    return recentQueries[index];
  }

  /**
   * Adds a new SQL statement in the history.
   *
   * @param s The SQL statement to recall later
   */
  void addToRecent(String s) {
    for (int i = 0; i < MAX_HISTORY; i++) {
      if (s.equals(recentQueries[i])) {
        return;
      }
    }

    if (recentQueries[history] != null) {
      menuRecent.remove(history);
    }

    recentQueries[history] = s;

    if (s.length() > 43) {
      s = s.substring(0, 40) + "...";
    }

    JMenuItem item = new JMenuItem(s);

    item.setActionCommand("#" + history);
    item.addActionListener(listener);
    menuRecent.insert(item, history);

    history = (history + 1) % MAX_HISTORY;
  }

  /** Loads history (recent SQL statements) from property file. */
  void loadHistory() {
    for (int i = 0; i < MAX_HISTORY; i++) {
      String key = "sql.history" + i;
      String value = nodusProject.getLocalProperty(key, null);

      if (value != null) {
        addToRecent(value);
      }
    }
  }

  /** Saves history (recent SQL statements) in property file. */
  void saveHistory() {
    if (nodusProject.isOpen()) {
      for (int i = 0; i < MAX_HISTORY; i++) {
        if (recentQueries[i] != null) {
          String key = "sql.history" + i;
          nodusProject.setLocalProperty(key, recentQueries[i]);
        }
      }
    }
  }
}
