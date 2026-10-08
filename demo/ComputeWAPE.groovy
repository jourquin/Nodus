/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 *
 * This file is part of Nodus and is distributed under the GNU General Public License,
 * version 3 or (at your option) any later version.
 */

import edu.uclouvain.core.nodus.database.JDBCUtils

import java.math.RoundingMode
import java.sql.Connection
import java.sql.ResultSet

// Edit these three values, then run this script from the open Demo project.
// WAPE = 100 * sum(|assigned - observed|) / sum(|observed|), after summing
// quantities by mode, group, origin and destination. A zero denominator is undefined.
String modeIds = "1,2,3"
String modalTables = "od_road,od_iww,od_rail" // Same order as modeIds
String pathHeaderTable = "demo_path6_header"

class ComputeWAPE_ {

  static void run(Connection connection, String modeIds, String modalTables,
      String pathHeaderTable) {
    List<String> ids = modeIds.split(',', -1).collect { it.trim() }
    List<String> tables = modalTables.split(',', -1).collect { it.trim() }
    if (ids.size() != tables.size() || ids.any { !it } || tables.any { !it }) {
      throw new IllegalArgumentException("Provide one modal table for each mode ID.")
    }

    List<Integer> modes = ids.collect {
      try {
        return Integer.parseInt(it)
      } catch (NumberFormatException ignored) {
        throw new IllegalArgumentException("Invalid mode ID: ${it}")
      }
    }
    if (modes.size() != modes.toSet().size()) {
      throw new IllegalArgumentException("Mode IDs must be unique.")
    }

    checkTable(connection, pathHeaderTable, ['grp', 'org', 'dst', 'ldmode', 'qty'])
    tables.each { checkTable(connection, it, ['grp', 'org', 'dst', 'qty']) }

    BigDecimal allError = BigDecimal.ZERO
    BigDecimal allObserved = BigDecimal.ZERO
    Map<Object, Map> allGroups = [:]
    Map<Integer, Map<Object, Map>> modeGroups = [:]
    println "WAPE by mode for ${pathHeaderTable}:"

    modes.eachWithIndex { int mode, int i ->
      String od = JDBCUtils.getQuotedCompliantIdentifier(tables[i])
      String paths = JDBCUtils.getQuotedCompliantIdentifier(pathHeaderTable)
      // UNION ALL retains OD cells present on only one side. Grouping first also
      // handles multiple matrix rows or multiple assigned paths for one OD cell.
      String sql = """
          SELECT grp, org, dst, SUM(observed_qty), SUM(assigned_qty)
          FROM (
            SELECT grp, org, dst, COALESCE(SUM(qty), 0) AS observed_qty,
                   0 AS assigned_qty
            FROM ${od} GROUP BY grp, org, dst
            UNION ALL
            SELECT grp, org, dst, 0 AS observed_qty,
                   COALESCE(SUM(qty), 0) AS assigned_qty
            FROM ${paths} WHERE ldmode = ? GROUP BY grp, org, dst
          ) AS quantities
          GROUP BY grp, org, dst
          """

      BigDecimal error = BigDecimal.ZERO
      BigDecimal observed = BigDecimal.ZERO
      int cells = 0
      Map<Object, Map> groups = [:]
      connection.prepareStatement(sql).withCloseable { statement ->
        statement.setInt(1, mode)
        statement.executeQuery().withCloseable { ResultSet rows ->
          while (rows.next()) {
            BigDecimal actual = rows.getBigDecimal(4)
            BigDecimal assigned = rows.getBigDecimal(5)
            error = error.add(actual.subtract(assigned).abs())
            observed = observed.add(actual.abs())
            Object group = rows.getObject(1)
            addGroupCell(groups, group, actual, assigned)
            addGroupCell(allGroups, group, actual, assigned)
            cells++
          }
        }
      }

      allError = allError.add(error)
      allObserved = allObserved.add(observed)
      modeGroups[mode] = groups
      println "  Mode ${mode} (${tables[i]}): ${formatWAPE(error, observed)} " +
          "[OD cells=${cells}; absolute error=${error}; observed weight=${observed}]"
    }

    // The combined result preserves mode in the comparison: errors in different
    // modes cannot cancel one another out for the same grp/org/dst.
    println "  All specified modes: ${formatWAPE(allError, allObserved)} " +
        "[absolute error=${allError}; observed weight=${allObserved}]"

    println "WAPE by mode and commodity group for ${pathHeaderTable}:"
    allGroups.keySet().toList().sort().each { group ->
      println "  Group ${group}:"
      modes.eachWithIndex { int mode, int i ->
        Map totals = modeGroups[mode][group]
        if (totals != null) {
          printGroupTotals("    Mode ${mode} (${tables[i]})", totals)
        }
      }
      printGroupTotals("    All specified modes", allGroups[group])
    }
  }

  static void addGroupCell(Map<Object, Map> groups, Object group,
      BigDecimal actual, BigDecimal assigned) {
    if (!groups.containsKey(group)) {
      groups[group] = [error: BigDecimal.ZERO, observed: BigDecimal.ZERO, cells: 0]
    }
    Map totals = groups[group]
    totals.error = totals.error.add(actual.subtract(assigned).abs())
    totals.observed = totals.observed.add(actual.abs())
    totals.cells++
  }

  static void printGroupTotals(String label, Map totals) {
    println "${label}: ${formatWAPE(totals.error, totals.observed)} " +
        "[OD cells=${totals.cells}; absolute error=${totals.error}; " +
        "observed weight=${totals.observed}]"
  }

  static String formatWAPE(BigDecimal error, BigDecimal observed) {
    if (observed.signum() == 0) {
      return "undefined (observed weight is zero)"
    }
    return error.multiply(100G).divide(observed, 2, RoundingMode.HALF_UP) + "%"
  }

  static void checkTable(Connection connection, String name, List<String> required) {
    // Table names are SQL identifiers, not JDBC parameters. Reject anything that
    // could change the query before interpolating a user-supplied name.
    if (name == null || !(name ==~ /[A-Za-z][A-Za-z0-9_]*/)) {
      throw new IllegalArgumentException("Invalid table name: ${name}")
    }
    String quoted = JDBCUtils.getQuotedCompliantIdentifier(name)
    connection.createStatement().withCloseable { statement ->
      statement.executeQuery("SELECT * FROM ${quoted} WHERE 1 = 0").withCloseable { rows ->
        def metadata = rows.metaData
        Set<String> columns = (1..metadata.columnCount).collect {
          metadata.getColumnName(it).toLowerCase(Locale.ROOT)
        }.toSet()
        List<String> missing = required.findAll { !columns.contains(it) }
        if (missing) {
          throw new IllegalArgumentException("${name} is missing columns: ${missing.join(', ')}")
        }
      }
    }
  }
}

def project = nodusMapPanel.getNodusProject()
if (!project.isOpen()) {
  throw new IllegalStateException("Open the Demo project before running ComputeWAPE.groovy.")
}
ComputeWAPE_.run(project.getMainJDBCConnection(), modeIds, modalTables, pathHeaderTable)
