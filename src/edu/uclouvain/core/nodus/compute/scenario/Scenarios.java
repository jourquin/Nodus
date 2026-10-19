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

package edu.uclouvain.core.nodus.compute.scenario;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.virtual.AssignmentOutputTables;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Performs arithmetic and management operations on saved scenarios. */
public class Scenarios {
  private static final List<String> DESCRIPTORS =
      List.of(
          NodusC.DBF_NODE1,
          NodusC.DBF_LINK1,
          NodusC.DBF_MODE1,
          NodusC.DBF_MEANS1,
          NodusC.DBF_SERVICE1,
          NodusC.DBF_NODE2,
          NodusC.DBF_LINK2,
          NodusC.DBF_MODE2,
          NodusC.DBF_MEANS2,
          NodusC.DBF_SERVICE2,
          NodusC.DBF_TIME);
  private final NodusProject nodusProject;
  private final boolean hasBatchSupport;

  private static final class Key {
    final long[] values;

    Key(long[] descriptors) {
      values = descriptors;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key && Arrays.equals(values, ((Key) other).values);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(values);
    }
  }

  private static final class Amounts {
    static final Amounts ZERO = new Amounts(BigDecimal.ZERO, BigDecimal.ZERO, 0);
    final BigDecimal cost;
    final BigDecimal quantity;
    final long vehicles;

    Amounts(BigDecimal cost, BigDecimal quantity, long vehicles) {
      this.cost = cost;
      this.quantity = quantity;
      this.vehicles = vehicles;
    }
  }

  private static final class Record {
    final Key key;
    final BigDecimal length;
    final Map<Byte, Amounts> groups = new LinkedHashMap<>();
    BigDecimal consolidatedCost;

    Record(long[] descriptors, BigDecimal length) {
      key = new Key(descriptors);
      this.length = length;
    }

    BigDecimal weightedCost() {
      BigDecimal total = BigDecimal.ZERO;
      for (Amounts amounts : groups.values()) {
        total = total.add(amounts.cost.multiply(amounts.quantity));
      }
      return total;
    }

    BigDecimal quantity() {
      BigDecimal total = BigDecimal.ZERO;
      for (Amounts amounts : groups.values()) {
        total = total.add(amounts.quantity);
      }
      return total;
    }
  }

  @FunctionalInterface
  private interface RecordReader {
    void accept(Record record) throws SQLException;
  }

  /**
   * Creates arithmetic operations for a project.
   *
   * @param nodusProject project owning the source and destination scenario tables
   */
  public Scenarios(NodusProject nodusProject) {
    this.nodusProject = nodusProject;
    hasBatchSupport = JDBCUtils.hasBatchSupport();
  }

  /**
   * Subtracts the comparison scenario from the reference, including per-group unit costs.
   * Consolidated unit cost is the difference of the sources' quantity-weighted unit costs.
   *
   * @param referenceScenario first source ID
   * @param scenarioToCompare source ID to subtract
   * @param resultScenario destination ID, distinct from both sources
   * @param whereString SQL predicate applied independently to both sources; null means no filter
   * @throws IllegalArgumentException if scenario IDs overlap
   * @throws IllegalStateException if reading, writing or publishing fails
   */
  public void compare(
      int referenceScenario, int scenarioToCompare, int resultScenario, String whereString) {
    plusOrMinus(referenceScenario, scenarioToCompare, resultScenario, whereString, true);
  }

  /**
   * Adds quantities and vehicles, using quantity-weighted unit costs for the combined flows. A zero
   * total quantity has unit cost zero. Signed, nonzero quantities remain valid weights.
   *
   * @param scenario1 first source ID
   * @param scenario2 second source ID
   * @param resultScenario destination ID, distinct from both sources
   * @param whereString SQL predicate applied independently to both sources; null means no filter
   * @throws IllegalArgumentException if scenario IDs overlap
   * @throws IllegalStateException if reading, writing or publishing fails
   */
  public void sum(int scenario1, int scenario2, int resultScenario, String whereString) {
    plusOrMinus(scenario1, scenario2, resultScenario, whereString, false);
  }

  private void plusOrMinus(int first, int second, int result, String filter, boolean compare) {
    if (first == second || result == first || result == second) {
      throw new IllegalArgumentException("Source and result scenario IDs must be distinct");
    }
    Connection connection = nodusProject.getMainJDBCConnection();
    try {
      Map<Key, Record> records = new LinkedHashMap<>();
      Set<Byte> referenceGroups =
          read(
              connection,
              first,
              filter,
              record -> {
                if (records.putIfAbsent(record.key, record) != null) {
                  throw new SQLException("Duplicate virtual-link descriptor in scenario " + first);
                }
              });
      Set<Key> compared = new HashSet<>();
      Set<Byte> comparisonGroups =
          read(
              connection,
              second,
              filter,
              record -> {
                Record original = records.get(record.key);
                Key key = original == null ? record.key : original.key;
                if (!compared.add(key)) {
                  throw new SQLException("Duplicate virtual-link descriptor in scenario " + second);
                }
                Set<Byte> rowGroups = new TreeSet<>(record.groups.keySet());
                if (original != null) {
                  rowGroups.addAll(original.groups.keySet());
                }
                records.put(key, combine(original, record, rowGroups, compare));
              });
      for (Map.Entry<Key, Record> entry : records.entrySet()) {
        if (!compared.contains(entry.getKey())) {
          entry.setValue(combine(entry.getValue(), null, referenceGroups, compare));
        }
      }
      Set<Byte> groups = new TreeSet<>(referenceGroups);
      groups.addAll(comparisonGroups);
      byte[] outputGroups = new byte[groups.size()];
      int index = 0;
      for (byte group : groups) {
        outputGroups[index++] = group;
      }
      write(connection, result, outputGroups, records);
    } catch (SQLException | ArithmeticException failure) {
      throw new IllegalStateException(
          "Cannot "
              + (compare ? "compare" : "sum")
              + " scenarios "
              + first
              + " and "
              + second
              + ": "
              + failure.getMessage(),
          failure);
    }
  }

  private Set<Byte> read(Connection connection, int scenario, String filter, RecordReader reader)
      throws SQLException {
    String prefix = nodusProject.getLocalProperty(NodusC.PROP_PROJECT_DOTNAME) + NodusC.SUFFIX_VNET;
    String table = nodusProject.getLocalProperty(NodusC.PROP_VNET_TABLE, prefix) + scenario;
    String sql = "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier(table);
    if (filter != null && !filter.isBlank()) {
      sql += " WHERE " + filter;
    }
    Set<Byte> groups = new TreeSet<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      ResultSetMetaData metadata = rows.getMetaData();
      Map<String, Integer> columns = new LinkedHashMap<>();
      for (int column = 1; column <= metadata.getColumnCount(); column++) {
        columns.put(metadata.getColumnLabel(column).toLowerCase(Locale.ROOT), column);
      }
      for (String name : columns.keySet()) {
        for (String groupPrefix :
            List.of(NodusC.DBF_QUANTITY, NodusC.DBF_UNITCOST, NodusC.DBF_VEHICLES)) {
          if (name.startsWith(groupPrefix) && name.length() > groupPrefix.length()) {
            try {
              groups.add(Byte.valueOf(name.substring(groupPrefix.length())));
            } catch (NumberFormatException invalid) {
              throw new SQLException("Invalid commodity column: " + name, invalid);
            }
          }
        }
      }
      for (String name : DESCRIPTORS) {
        column(columns, name);
      }
      column(columns, NodusC.DBF_LENGTH);
      for (byte group : groups) {
        for (String name : List.of(NodusC.DBF_QUANTITY, NodusC.DBF_UNITCOST, NodusC.DBF_VEHICLES)) {
          column(columns, name + group);
        }
      }
      while (rows.next()) {
        long[] descriptors = new long[12];
        for (int i = 0; i < DESCRIPTORS.size(); i++) {
          descriptors[i] = decimal(rows, columns, DESCRIPTORS.get(i)).longValueExact();
        }
        Record record = new Record(descriptors, decimal(rows, columns, NodusC.DBF_LENGTH));
        descriptors[11] =
            columns.containsKey(NodusC.DBF_VIRTUAL_LINK_TYPE)
                ? decimal(rows, columns, NodusC.DBF_VIRTUAL_LINK_TYPE).longValueExact()
                : inferVirtualLinkType(record);
        for (byte group : groups) {
          record.groups.put(
              group,
              new Amounts(
                  decimal(rows, columns, NodusC.DBF_UNITCOST + group),
                  decimal(rows, columns, NodusC.DBF_QUANTITY + group),
                  decimal(rows, columns, NodusC.DBF_VEHICLES + group).longValueExact()));
        }
        if (groups.isEmpty()) {
          throw new SQLException("No commodity columns in " + table);
        }
        reader.accept(record);
      }
    }
    return groups;
  }

  private static int column(Map<String, Integer> columns, String name) throws SQLException {
    Integer index = columns.get(name);
    if (index == null) {
      throw new SQLException("Missing scenario column: " + name);
    }
    return index;
  }

  private static BigDecimal decimal(ResultSet rows, Map<String, Integer> columns, String name)
      throws SQLException {
    BigDecimal value = rows.getBigDecimal(column(columns, name));
    if (value == null) {
      throw new SQLException("NULL scenario value: " + name);
    }
    return value;
  }

  private int inferVirtualLinkType(Record record) throws SQLException {
    long[] d = record.key.values;
    if (d[2] == 0) {
      return VirtualLink.TYPE_LOAD;
    }
    if (d[7] == 0) {
      return VirtualLink.TYPE_UNLOAD;
    }
    if (Math.abs(d[0]) != Math.abs(d[5])) {
      return VirtualLink.TYPE_MOVE;
    }
    if (d[2] != d[7] || d[3] != d[8]) {
      return VirtualLink.TYPE_TRANSHIP;
    }
    if (d[4] != d[9]) {
      return VirtualLink.TYPE_SWITCH;
    }
    if (d[4] > 0) {
      if (nodusProject.getServiceHandler() == null) {
        throw new SQLException("Project services are needed to infer legacy stop operations");
      }
      if (nodusProject
          .getServiceHandler()
          .isNodeStopService(Math.toIntExact(Math.abs(d[0])), Math.toIntExact(d[4]))) {
        return VirtualLink.TYPE_STOP;
      }
    }
    return VirtualLink.TYPE_TRANSIT;
  }

  private static Record combine(Record first, Record second, Set<Byte> groups, boolean compare) {
    Record descriptor = first == null ? second : first;
    Record result = new Record(descriptor.key.values, descriptor.length);
    for (byte group : groups) {
      Amounts a = first == null ? Amounts.ZERO : first.groups.getOrDefault(group, Amounts.ZERO);
      Amounts b = second == null ? Amounts.ZERO : second.groups.getOrDefault(group, Amounts.ZERO);
      BigDecimal quantity = compare ? a.quantity.subtract(b.quantity) : a.quantity.add(b.quantity);
      BigDecimal cost =
          compare
              ? a.cost.subtract(b.cost)
              : average(a.cost.multiply(a.quantity).add(b.cost.multiply(b.quantity)), quantity);
      long vehicles =
          compare
              ? Math.subtractExact(a.vehicles, b.vehicles)
              : Math.addExact(a.vehicles, b.vehicles);
      result.groups.put(group, new Amounts(cost, quantity, vehicles));
    }
    BigDecimal cost1 = first == null ? BigDecimal.ZERO : first.weightedCost();
    BigDecimal cost2 = second == null ? BigDecimal.ZERO : second.weightedCost();
    BigDecimal quantity1 = first == null ? BigDecimal.ZERO : first.quantity();
    BigDecimal quantity2 = second == null ? BigDecimal.ZERO : second.quantity();
    result.consolidatedCost =
        compare
            ? average(cost1, quantity1).subtract(average(cost2, quantity2))
            : average(cost1.add(cost2), quantity1.add(quantity2));
    return result;
  }

  private static BigDecimal average(BigDecimal weightedCost, BigDecimal quantity) {
    return quantity.signum() == 0
        ? BigDecimal.ZERO
        : weightedCost.divide(quantity, MathContext.DECIMAL128);
  }

  private void write(Connection connection, int scenario, byte[] groups, Map<Key, Record> records)
      throws SQLException {
    try (AssignmentOutputTables outputs = new AssignmentOutputTables(nodusProject, scenario)) {
      if (!outputs.initializeVirtualNetwork(groups)) {
        throw new SQLException("Cannot create scenario result table");
      }
      Savepoint savepoint = connection.getAutoCommit() ? null : connection.setSavepoint();
      try {
        String sql =
            "INSERT INTO "
                + JDBCUtils.getQuotedCompliantIdentifier(outputs.virtualNetwork())
                + " VALUES ("
                + String.join(",", java.util.Collections.nCopies(16 + 3 * groups.length, "?"))
                + ")";
        int maxBatchSize = nodusProject.getSqlBatchSize();
        if (maxBatchSize > NodusC.MAXBATCHSIZE) {
          maxBatchSize /= 5;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
          int batchSize = 0;
          for (Record record : records.values()) {
            int index = 1;
            for (int i = 0; i < 10; i++) {
              statement.setLong(index++, record.key.values[i]);
            }
            statement.setLong(index++, record.key.values[10]);
            statement.setBigDecimal(index++, record.length);
            statement.setLong(index++, record.key.values[11]);
            long totalVehicles = 0;
            for (byte group : groups) {
              Amounts amounts = record.groups.getOrDefault(group, Amounts.ZERO);
              statement.setBigDecimal(index++, amounts.cost);
              statement.setBigDecimal(index++, amounts.quantity);
              statement.setLong(index++, amounts.vehicles);
              totalVehicles = Math.addExact(totalVehicles, amounts.vehicles);
            }
            statement.setBigDecimal(index++, record.consolidatedCost);
            statement.setBigDecimal(index++, record.quantity());
            statement.setLong(index, totalVehicles);
            if (hasBatchSupport) {
              statement.addBatch();
              if (++batchSize >= maxBatchSize) {
                executeBatch(statement);
                batchSize = 0;
              }
            } else {
              statement.executeUpdate();
            }
          }
          if (hasBatchSupport && batchSize > 0) {
            executeBatch(statement);
          }
        }
        if (!connection.getAutoCommit()) {
          connection.commit();
        }
        outputs.publish();
      } catch (SQLException | RuntimeException failure) {
        if (savepoint != null) {
          try {
            connection.rollback(savepoint);
          } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
          }
        }
        throw failure;
      }
    }
  }

  private static void executeBatch(PreparedStatement statement) throws SQLException {
    for (int count : statement.executeBatch()) {
      if (count == Statement.EXECUTE_FAILED) {
        throw new SQLException("A scenario output row could not be saved");
      }
    }
    statement.clearBatch();
  }

  /**
   * Removes a scenario and its stored settings.
   *
   * @param scenarioId scenario to delete
   */
  public void remove(int scenarioId) {
    nodusProject.removeScenario(scenarioId);
  }

  /**
   * Renames a scenario and its stored settings.
   *
   * @param currentId source scenario ID
   * @param newId destination scenario ID
   * @throws IllegalStateException if the destination is occupied or the operation fails
   */
  public void rename(int currentId, int newId) {
    nodusProject.renameScenario(currentId, newId);
  }
}
