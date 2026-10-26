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

package edu.uclouvain.core.nodus.compute.virtual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.testing.AssignmentOutputFixture;
import edu.uclouvain.core.nodus.testing.AssignmentOutputFixture.Fault;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock("JDBCUtils")
@ResourceLock(Resources.SYSTEM_ERR)
class VirtualNetworkWriterTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> failedWritesPreservePreviousOutputOnBothDatabases() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(
                        Fault.LATE_BATCH,
                        Fault.BATCH_STATUS,
                        Fault.NETWORK_COMMIT,
                        Fault.COMMIT_ACK_LOST,
                        Fault.PUBLISH_COMMIT)
                    .flatMap(
                        fault ->
                            Stream.of(false, true)
                                .filter(
                                    auto ->
                                        !auto
                                            || fault == Fault.LATE_BATCH
                                            || fault == Fault.BATCH_STATUS)
                                .map(
                                    auto ->
                                        DynamicTest.dynamicTest(
                                            "HSQLDB=" + hsql + ", " + fault + ", auto=" + auto,
                                            () -> checkFailure(hsql, fault, auto)))));
  }

  private void checkFailure(boolean hsql, Fault fault, boolean autoCommit) throws Exception {
    try (AssignmentOutputFixture project = new AssignmentOutputFixture(directory, hsql)) {
      final AssignmentParameters parameters = project.parameters(1);
      final VirtualNetwork network = network(parameters);
      try {
        project.execute("CREATE TABLE mini_vnet1 (marker INTEGER)");
        project.execute("INSERT INTO mini_vnet1 VALUES (123)");
        project.execute("CREATE TABLE unrelated (marker INTEGER)");
        project.getMainJDBCConnection().commit();
        project.getMainJDBCConnection().setAutoCommit(autoCommit);
        project.execute("INSERT INTO unrelated VALUES (456)");
        project.jdbc.reset(fault);
        assertFalse(new VirtualNetworkWriter(parameters, network).save());
        assertTrue(project.jdbc.triggered);
        if (fault != Fault.PUBLISH_COMMIT) {
          assertTrue(project.jdbc.insertedBeforeFailure >= 2);
        }
        if (!autoCommit && fault != Fault.COMMIT_ACK_LOST && fault != Fault.PUBLISH_COMMIT) {
          assertEquals(0, project.jdbc.rowsAfterRollback);
        }
        assertEquals(123, project.number("SELECT marker FROM mini_vnet1"));
        project.assertClean();
        // Table creation can commit pre-existing DML; a later savepoint cannot undo that work.
        if (!autoCommit) {
          project.getMainJDBCConnection().rollback();
        }
        assertEquals(456, project.number("SELECT marker FROM unrelated"));
      } finally {
        network.dispose();
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> cancellationAfterAnExecutedBatchNeverPublishesPartialRows() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(false, true)
                    .map(
                        auto ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + ", auto=" + auto,
                                () -> {
                                  try (AssignmentOutputFixture project =
                                      new AssignmentOutputFixture(directory, hsql)) {
                                    final AssignmentParameters parameters = project.parameters(1);
                                    final VirtualNetwork network = network(parameters);
                                    try {
                                      project.getMainJDBCConnection().setAutoCommit(auto);
                                      project.outputPanel.progress =
                                          message -> project.jdbc.networkBatches == 0;
                                      assertFalse(
                                          new VirtualNetworkWriter(parameters, network).save());
                                      assertTrue(project.jdbc.networkBatches >= 1);
                                      assertFalse(JDBCUtils.tableExists("mini_vnet1"));
                                      if (!auto) {
                                        assertEquals(0, project.jdbc.rowsAfterRollback);
                                      }
                                      assertTrue(project.outputPanel.messages.isEmpty());
                                      project.assertClean();
                                    } finally {
                                      network.dispose();
                                    }
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> nonBatchDriversSaveAllGroupsAndConsolidatedValues() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        final AssignmentParameters parameters = project.parameters(1);
                        final VirtualNetwork network = network(parameters);
                        try {
                          project.jdbc.reset(Fault.NO_BATCH);
                          assertTrue(new VirtualNetworkWriter(parameters, network).save());
                          assertEquals(0, project.jdbc.networkBatches);
                          int links =
                              java.util.Arrays.stream(network.getVirtualNodeLists())
                                  .flatMap(nodes -> nodes.getVirtualNodeList().stream())
                                  .mapToInt(node -> node.getVirtualLinkList().size())
                                  .sum();
                          assertEquals(links, project.jdbc.networkUpdates);
                          assertEquals(links, project.number("SELECT COUNT(*) FROM mini_vnet1"));
                          assertEquals(
                              0,
                              project.number(
                                  "SELECT COUNT(*) FROM mini_vnet1 WHERE qty1<>3 OR qty3<>1"
                                      + " OR ucost1<>2 OR ucost3<>6 OR qty<>4 OR ucost<>3"));
                          assertEquals(
                              0,
                              project.number(
                                  "SELECT COUNT(*) FROM mini_vnet1 WHERE vtype="
                                      + VirtualLink.TYPE_MOVE
                                      + " AND (veh1<>3 OR veh3<>1 OR veh<>4)"));
                          assertTrue(
                              VirtualNetworkWriter.getTableName().equalsIgnoreCase("mini_vnet1"));
                          project.assertClean();
                        } finally {
                          network.dispose();
                        }
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> consolidatedVehicleCountsCanExceedTheIntegerRange() {
    return Stream.of(false, true)
        .flatMap(
            hsql ->
                Stream.of(2, NodusC.MAXGROUPS)
                    .map(
                        count ->
                            DynamicTest.dynamicTest(
                                "HSQLDB=" + hsql + ", groups=" + count,
                                () -> {
                                  try (AssignmentOutputFixture project =
                                      new AssignmentOutputFixture(directory, hsql)) {
                                    final AssignmentParameters parameters = project.parameters(1);
                                    final VirtualNetwork network = new VirtualNetwork(parameters);
                                    try {
                                      assertTrue(network.generate());
                                      byte[] groups = new byte[count];
                                      for (int i = 0; i < count; i++) {
                                        groups[i] = (byte) i;
                                      }
                                      network.setGroups(groups);
                                      for (VirtualNodeList nodes : network.getVirtualNodeLists()) {
                                        for (VirtualNode node : nodes.getVirtualNodeList()) {
                                          for (VirtualLink link : node.getVirtualLinkList()) {
                                            for (byte group = 0; group < count; group++) {
                                              link.setCost(group, 2);
                                              link.addVolume(group, 2);
                                              link.volumesToVehicles(group, 0, 1e-9, 0);
                                            }
                                          }
                                        }
                                      }
                                      assertTrue(
                                          new VirtualNetworkWriter(parameters, network).save());
                                      try (Statement statement =
                                              project.getMainJDBCConnection().createStatement();
                                          ResultSet rows =
                                              statement.executeQuery(
                                                  "SELECT MIN(veh), MAX(veh), MIN(veh0), MAX(veh0)"
                                                      + " FROM mini_vnet1 WHERE vtype="
                                                      + VirtualLink.TYPE_MOVE)) {
                                        assertTrue(rows.next());
                                        assertEquals(count * 2_000_000_000L, rows.getLong(1));
                                        assertEquals(count * 2_000_000_000L, rows.getLong(2));
                                        assertEquals(2_000_000_000L, rows.getLong(3));
                                        assertEquals(2_000_000_000L, rows.getLong(4));
                                      }
                                      project.assertClean();
                                    } finally {
                                      network.dispose();
                                    }
                                  }
                                })));
  }

  @TestFactory
  Stream<DynamicTest> reportingFailureCannotPreventRollbackOrCleanup() {
    return Stream.of(false, true)
        .map(
            hsql ->
                DynamicTest.dynamicTest(
                    "HSQLDB=" + hsql,
                    () -> {
                      try (AssignmentOutputFixture project =
                          new AssignmentOutputFixture(directory, hsql)) {
                        final AssignmentParameters parameters = project.parameters(1);
                        final VirtualNetwork network = network(parameters);
                        try {
                          project.jdbc.reset(Fault.LATE_BATCH);
                          project.outputPanel.failReporting = true;
                          assertThrows(
                              IllegalStateException.class,
                              () -> new VirtualNetworkWriter(parameters, network).save());
                          assertEquals(0, project.jdbc.rowsAfterRollback);
                          assertFalse(JDBCUtils.tableExists("mini_vnet1"));
                          project.assertClean();
                        } finally {
                          network.dispose();
                        }
                      }
                    }));
  }

  private static VirtualNetwork network(AssignmentParameters parameters) {
    VirtualNetwork network = new VirtualNetwork(parameters);
    assertTrue(network.generate());
    network.setGroups(new byte[] {1, 3});
    for (VirtualNodeList nodes : network.getVirtualNodeLists()) {
      for (VirtualNode node : nodes.getVirtualNodeList()) {
        for (VirtualLink link : node.getVirtualLinkList()) {
          link.setCost((byte) 0, 2);
          link.setCost((byte) 1, 6);
          link.addVolume((byte) 0, 3);
          link.addVolume((byte) 1, 1);
          if (link.getType() == VirtualLink.TYPE_MOVE
              || link.getType() == VirtualLink.TYPE_LOAD
              || link.getType() == VirtualLink.TYPE_UNLOAD
              || link.getType() == VirtualLink.TYPE_TRANSIT) {
            link.volumesToVehicles((byte) 0, 0, 1, 1);
            link.volumesToVehicles((byte) 1, 0, 1, 1);
          }
        }
      }
    }
    return network;
  }
}
