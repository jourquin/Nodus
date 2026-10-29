/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Inspects actual MySQL/MariaDB table engines without changing tables or connection settings. */
public final class MySqlStorageEngines {
  private MySqlStorageEngines() {}

  /** Transaction support of the existing tables and the current session's default engine. */
  public static final class Inspection {
    private final boolean applicable;
    private final String defaultEngine;
    private final String defaultTransactions;
    private final Map<String, String> nonTransactionalTables;
    private final List<String> unverifiedTables;

    private Inspection(
        boolean applicable,
        String defaultEngine,
        String defaultTransactions,
        Map<String, String> nonTransactionalTables,
        List<String> unverifiedTables) {
      this.applicable = applicable;
      this.defaultEngine = defaultEngine;
      this.defaultTransactions = defaultTransactions;
      this.nonTransactionalTables =
          Collections.unmodifiableMap(new TreeMap<>(nonTransactionalTables));
      this.unverifiedTables = Collections.unmodifiableList(new ArrayList<>(unverifiedTables));
    }

    /**
     * Returns the nontransactional base tables, keyed by name with their actual engine.
     *
     * @return unmodifiable table-name-to-engine map; empty if none were found
     */
    public Map<String, String> getNonTransactionalTables() {
      return nonTransactionalTables;
    }

    /**
     * Returns the distinct engines used by nontransactional tables.
     *
     * @return unmodifiable set of engine names; empty if no nontransactional tables were found
     */
    public Set<String> getNonTransactionalEngines() {
      return Collections.unmodifiableSet(new TreeSet<>(nonTransactionalTables.values()));
    }

    /**
     * Returns the selected engine for newly created tables.
     *
     * @return session default engine name, or null if it is unavailable
     */
    public String getDefaultEngine() {
      return defaultEngine;
    }

    /**
     * Indicates whether new tables would use a known nontransactional engine.
     *
     * @return true if the default engine explicitly reports no transaction support
     */
    public boolean isDefaultNonTransactional() {
      return "NO".equalsIgnoreCase(defaultTransactions);
    }

    /**
     * Returns tables whose engine's transaction support could not be established.
     *
     * @return unmodifiable list of unverified table names; empty if all tables were verified
     */
    public List<String> getUnverifiedTables() {
      return unverifiedTables;
    }

    /**
     * Indicates whether any existing table or the default engine could not be checked.
     *
     * @return true for MySQL/MariaDB if any inspected engine's transaction support is unknown
     */
    public boolean hasUnverifiedEngines() {
      return applicable
          && (!unverifiedTables.isEmpty()
              || !("YES".equalsIgnoreCase(defaultTransactions) || isDefaultNonTransactional()));
    }

    /**
     * Indicates whether opening the project should report limited or unverified recovery.
     *
     * @return true for MySQL/MariaDB if existing tables or the default engine are nontransactional
     *     or have unverified transaction support
     */
    public boolean requiresWarning() {
      return applicable
          && (!nonTransactionalTables.isEmpty()
              || isDefaultNonTransactional()
              || hasUnverifiedEngines());
    }
  }

  /**
   * Reads engine capabilities for the selected database and session default. JDBC's database-wide
   * transaction flag cannot establish whether individual tables support rollback.
   *
   * <p>The caller owns the connection. This method owns only its statements and results; it never
   * commits, rolls back, changes session settings, creates tables or converts existing tables.
   *
   * @param connection project connection with its database already selected
   * @return immutable inspection, with no warning for other database products
   * @throws SQLException if the metadata cannot be read completely
   */
  public static Inspection inspect(Connection connection) throws SQLException {
    String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (!product.contains("mysql") && !product.contains("mariadb")) {
      return new Inspection(false, null, null, Collections.emptyMap(), Collections.emptyList());
    }

    String defaultEngine = null;
    String defaultTransactions = null;
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(5);
      try (ResultSet rows =
          statement.executeQuery(
              "SELECT @@session.default_storage_engine, TRANSACTIONS"
                  + " FROM information_schema.ENGINES"
                  + " WHERE ENGINE=@@session.default_storage_engine")) {
        if (rows.next()) {
          defaultEngine = rows.getString(1);
          defaultTransactions = rows.getString(2);
        }
      }
    }

    Map<String, String> nonTransactional = new TreeMap<>();
    List<String> unverified = new ArrayList<>();
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(5);
      try (ResultSet rows =
          statement.executeQuery(
              "SELECT t.TABLE_NAME, t.ENGINE, e.TRANSACTIONS"
                  + " FROM information_schema.TABLES t LEFT JOIN information_schema.ENGINES e"
                  + " ON t.ENGINE=e.ENGINE WHERE t.TABLE_SCHEMA=DATABASE()"
                  + " AND t.TABLE_TYPE IN ('BASE TABLE','SYSTEM VERSIONED')"
                  + " ORDER BY t.TABLE_NAME")) {
        while (rows.next()) {
          String name = rows.getString(1);
          String engine = rows.getString(2);
          String transactions = rows.getString(3);
          if ("NO".equalsIgnoreCase(transactions) && engine != null) {
            nonTransactional.put(name, engine);
          } else if (!"YES".equalsIgnoreCase(transactions)) {
            unverified.add(name);
          }
        }
      }
    }
    return new Inspection(true, defaultEngine, defaultTransactions, nonTransactional, unverified);
  }
}
