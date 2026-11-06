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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Maintains recent commands and persists them in the project properties. */
final class SQLConsoleHistory {

  private static final int MAX_HISTORY = 24;
  private static final String HISTORY_KEY = "sql.history";
  private final List<String> recentQueries = new ArrayList<>(MAX_HISTORY);
  private final NodusProject nodusProject;

  SQLConsoleHistory(NodusProject project) {
    this.nodusProject = project;
  }

  String get(int index) {
    return recentQueries.get(index);
  }

  /** Moves a SQL statement to the top of the history. */
  void addToRecent(String command) {
    recentQueries.remove(command);
    recentQueries.add(0, command);
    if (recentQueries.size() > MAX_HISTORY) {
      recentQueries.remove(MAX_HISTORY);
    }
  }

  /** Builds the newest-first history popup. */
  JPopupMenu createPopup(Consumer<String> recall) {
    JPopupMenu popup = new JPopupMenu();
    for (String command : recentQueries) {
      String label = command.length() > 43 ? command.substring(0, 40) + "..." : command;
      JMenuItem item = new JMenuItem(label);
      item.addActionListener(event -> recall.accept(command));
      popup.add(item);
    }
    return popup;
  }

  /** Loads history (recent SQL statements) from property file. */
  void loadHistory() {
    recentQueries.clear();
    // Read oldest-first storage in reverse. Legacy ring buffers did not save their write position.
    for (int i = MAX_HISTORY - 1; i >= 0; i--) {
      String command = nodusProject.getLocalProperty(HISTORY_KEY + i, null);
      if (command != null && !recentQueries.contains(command)) {
        recentQueries.add(command);
      }
    }
  }

  /** Saves history (recent SQL statements) in property file. */
  void saveHistory() {
    if (nodusProject.isOpen()) {
      for (int i = 0; i < MAX_HISTORY; i++) {
        String key = HISTORY_KEY + i;
        if (i < recentQueries.size()) {
          nodusProject.setLocalProperty(key, recentQueries.get(recentQueries.size() - 1 - i));
        } else {
          nodusProject.removeLocalProperty(key);
        }
      }
    }
  }
}
