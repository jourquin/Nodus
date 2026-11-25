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

package edu.uclouvain.core.nodus;

import com.bbn.openmap.Environment;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Renames the project-owned tables that follow the naming settings in Project preferences. */
public final class ProjectTableNames {
  private static final String SCENARIO = "(?:0|[1-9][0-9]*)";

  private enum Family {
    RULES(NodusC.PROP_EXC_TABLE, NodusC.SUFFIX_EXC, ""),
    VIRTUAL(NodusC.PROP_VNET_TABLE, NodusC.SUFFIX_VNET, SCENARIO),
    PATHS(
        NodusC.PROP_PATH_TABLE_PREFIX,
        NodusC.SUFFIX_PATH,
        SCENARIO + alternatives(NodusC.SUFFIX_HEADER, NodusC.SUFFIX_DETAIL)),
    SERVICES(
        NodusC.PROP_SERVICES_TABLE_PREFIX,
        NodusC.SUFFIX_SERVICES,
        alternatives(
            NodusC.SUFFIX_HEADER, NodusC.SUFFIX_SERVICES_LINKS, NodusC.SUFFIX_SERVICES_STOPS));

    final String property;
    final String defaultSuffix;
    final Pattern tail;

    Family(String property, String defaultSuffix, String tail) {
      this.property = property;
      this.defaultSuffix = defaultSuffix;
      this.tail = Pattern.compile(tail, Pattern.CASE_INSENSITIVE);
    }
  }

  private ProjectTableNames() {}

  private static String alternatives(String... suffixes) {
    return "(?:" + String.join("|", suffixes) + ")";
  }

  /**
   * Prepares a rename plan without changing the database or any preferences.
   *
   * @param project open project whose database and settings will be updated
   * @param requested new names keyed by their Nodus project property
   * @return the table renames and settings changes available for confirmation
   * @throws SQLException if metadata is unavailable or a table name is invalid or occupied
   */
  public static Plan inspect(NodusProject project, Map<String, String> requested)
      throws SQLException {
    Map<Family, String> before = new LinkedHashMap<>();
    Map<String, String> after = new LinkedHashMap<>();
    String projectName = project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME);
    for (Family family : Family.values()) {
      String current =
          project.getLocalProperty(family.property, projectName + family.defaultSuffix);
      String value = requested.getOrDefault(family.property, "").trim();
      if (!value.isEmpty() && !value.equals(current)) {
        before.put(family, current);
        after.put(family.property, value);
      }
    }
    Map<String, String> tables =
        before.isEmpty() ? Map.of() : tableRenames(requireConnection(project), before, after);
    return new Plan(project, before, after, tables);
  }

  /** The changes shown in the confirmation dialog and checked again before use. */
  public static final class Plan {
    private final NodusProject project;
    private final Map<Family, String> before;
    private final Map<String, String> after;
    private final Map<String, String> tables;

    private Plan(
        NodusProject project,
        Map<Family, String> before,
        Map<String, String> after,
        Map<String, String> tables) {
      this.project = project;
      this.before = before;
      this.after = after;
      this.tables = Collections.unmodifiableMap(tables);
    }

    /**
     * Reports whether the user changed a naming preference.
     *
     * @return true if any name or prefix will change
     */
    public boolean hasChanges() {
      return !after.isEmpty();
    }

    /**
     * Provides the table names to show in the confirmation dialog.
     *
     * @return current table names mapped to their proposed names
     */
    public Map<String, String> getRenames() {
      return tables;
    }

    /**
     * Applies the confirmed renames, then saves the corresponding preferences.
     *
     * @throws SQLException if the database changed or a rename could not be completed
     */
    public void apply() throws SQLException {
      if (!hasChanges()) {
        return;
      }
      for (Map.Entry<Family, String> old : before.entrySet()) {
        Family family = old.getKey();
        String defaultName =
            project.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + family.defaultSuffix;
        if (!old.getValue().equals(project.getLocalProperty(family.property, defaultName))) {
          throw changed();
        }
      }
      try (Connection connection = openRenameConnection(project)) {
        if (!tables.equals(tableRenames(connection, before, after))) {
          throw changed();
        }
        List<Map.Entry<String, String>> attempted = new ArrayList<>();
        try {
          for (Map.Entry<String, String> rename : tables.entrySet()) {
            attempted.add(rename);
            rename(connection, rename.getKey(), rename.getValue());
          }
          project.saveTableNamePreferences(after);
        } catch (SQLException | RuntimeException failure) {
          restore(connection, attempted, failure);
          throw failure;
        }
      }
    }
  }

  private static Map<String, String> tableRenames(
      Connection connection, Map<Family, String> before, Map<String, String> after)
      throws SQLException {
    DatabaseMetaData metadata = connection.getMetaData();
    String quote = metadata.getIdentifierQuoteString();
    int maximum = metadata.getMaxTableNameLength();
    boolean preserveCase = metadata.supportsMixedCaseQuotedIdentifiers();
    Map<String, Boolean> names = tableNames(connection);
    Set<String> occupied = new HashSet<>();
    for (String name : names.keySet()) {
      occupied.add(comparisonName(name, preserveCase));
    }
    Set<String> destinations = new HashSet<>();
    Map<String, String> changes = new TreeMap<>();
    for (Map.Entry<Family, String> change : before.entrySet()) {
      Family family = change.getKey();
      String oldPrefix = formatName(change.getValue());
      String newPrefix = after.get(family.property);
      if (newPrefix.indexOf(0) >= 0
          || (quote != null && !quote.isBlank() && newPrefix.startsWith(quote))) {
        throw new SQLException(
            text("Unquoted_table_name", "Enter an unquoted table name or prefix.")
                + " "
                + newPrefix);
      }
      if (maximum > 0 && formatName(newPrefix).length() > maximum) {
        throw new SQLException(
            text("Invalid_table_name", "The database cannot use this table name.")
                + " "
                + newPrefix);
      }
      for (Map.Entry<String, Boolean> table : names.entrySet()) {
        String name = table.getKey();
        if (!table.getValue()
            || !name.regionMatches(!preserveCase, 0, oldPrefix, 0, oldPrefix.length())) {
          continue;
        }
        String suffix = name.substring(oldPrefix.length());
        if (!family.tail.matcher(suffix).matches()) {
          continue;
        }
        String target = formatName(newPrefix + suffix);
        String key = comparisonName(target, preserveCase);
        if (key.equals(comparisonName(name, preserveCase))) {
          continue;
        }
        if ((maximum > 0 && target.length() > maximum) || quote == null || quote.isBlank()) {
          throw new SQLException(
              text("Invalid_table_name", "The database cannot use this table name.")
                  + " "
                  + target);
        }
        if (occupied.contains(key) || !destinations.add(key) || changes.containsKey(name)) {
          throw new SQLException(
              text("Table_name_conflict", "A table name is already in use.")
                  + " "
                  + name
                  + " → "
                  + target);
        }
        changes.put(name, target);
      }
    }
    return changes;
  }

  /** Returns exact names in the active namespace and marks the entries that can be renamed. */
  private static Map<String, Boolean> tableNames(Connection connection) throws SQLException {
    String catalog = connection.getCatalog();
    String schema = connection.getSchema();
    Map<String, Boolean> names = new LinkedHashMap<>();
    try (ResultSet rows = connection.getMetaData().getTables(catalog, schema, null, null)) {
      while (rows.next()) {
        if (schema != null && !schema.equals(rows.getString("TABLE_SCHEM"))) {
          continue;
        }
        if (catalog != null && !catalog.equals(rows.getString("TABLE_CAT"))) {
          continue;
        }
        String type = rows.getString("TABLE_TYPE");
        names.put(rows.getString("TABLE_NAME"), "TABLE".equals(type) || "BASE TABLE".equals(type));
      }
    }
    return names;
  }

  private static String formatName(String name) throws SQLException {
    String formatted = JDBCUtils.getCompliantIdentifier(name);
    if (formatted == null) {
      throw new SQLException(
          text("Invalid_table_name", "The database cannot use this table name.") + " " + name);
    }
    return formatted;
  }

  private static String comparisonName(String name, boolean preserveCase) {
    return preserveCase ? name : name.toLowerCase(Locale.ROOT);
  }

  /** Uses the same catalog and schema as the project without committing pending project work. */
  private static Connection openRenameConnection(NodusProject project) throws SQLException {
    Connection main = requireConnection(project);
    Connection copy =
        DriverManager.getConnection(
            project.getLocalProperty(NodusC.PROP_JDBC_URL, main.getMetaData().getURL()),
            project.getLocalProperty(NodusC.PROP_JDBC_USERNAME, main.getMetaData().getUserName()),
            project.getLocalProperty(NodusC.PROP_JDBC_PASSWORD, ""));
    try {
      String catalog = main.getCatalog();
      if (catalog != null && !catalog.equals(copy.getCatalog())) {
        copy.setCatalog(catalog);
      }
      String schema = main.getSchema();
      if (schema != null && !schema.equals(copy.getSchema())) {
        copy.setSchema(schema);
      }
      return copy;
    } catch (SQLException failure) {
      try {
        copy.close();
      } catch (SQLException cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private static Connection requireConnection(NodusProject project) throws SQLException {
    Connection connection = project.getMainJDBCConnection();
    if (connection == null || connection.isClosed()) {
      throw new SQLException(text("No_project_database", "There is no active project database."));
    }
    return connection;
  }

  private static void rename(Connection connection, String oldName, String newName)
      throws SQLException {
    String quote = connection.getMetaData().getIdentifierQuoteString();
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(10);
      statement.executeUpdate(
          "ALTER TABLE " + quoted(oldName, quote) + " RENAME TO " + quoted(newName, quote));
    }
  }

  private static String quoted(String name, String quote) {
    return quote + name.replace(quote, quote + quote) + quote;
  }

  /** Reverses completed renames even when the database commits each DDL statement. */
  private static void restore(
      Connection connection, List<Map.Entry<String, String>> attempted, Exception failure)
      throws SQLException {
    List<String> unresolved = new ArrayList<>();
    for (int index = attempted.size() - 1; index >= 0; index--) {
      Map.Entry<String, String> rename = attempted.get(index);
      try {
        Map<String, Boolean> names = tableNames(connection);
        if (!names.containsKey(rename.getKey())) {
          if (!Boolean.TRUE.equals(names.get(rename.getValue()))) {
            throw new SQLException(rename.getValue());
          }
          rename(connection, rename.getValue(), rename.getKey());
        }
      } catch (SQLException recovery) {
        failure.addSuppressed(recovery);
        unresolved.add(rename.getValue() + " → " + rename.getKey());
      }
    }
    if (!unresolved.isEmpty()) {
      throw new SQLException(
          text("Table_name_recovery", "Restore these table names before retrying:")
              + "\n"
              + String.join("\n", unresolved),
          failure);
    }
  }

  private static SQLException changed() {
    return new SQLException(
        text(
            "Table_names_changed",
            "The table names changed during confirmation. Review the plan again."));
  }

  private static String text(String key, String fallback) {
    return Environment.getI18n().get(ProjectTableNames.class, key, fallback);
  }
}
