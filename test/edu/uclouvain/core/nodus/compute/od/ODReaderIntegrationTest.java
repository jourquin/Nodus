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

package edu.uclouvain.core.nodus.compute.od;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.AssignmentTestProject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Real H2 queries through ODReader into a generated virtual network. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(15)
class ODReaderIntegrationTest {
  @TempDir Path directory;

  @Test
  void duplicateRowsAggregateWithoutMixingGroupsDestinationsOrClasses() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      project.execute("DELETE FROM mini_od");
      project.execute("ALTER TABLE mini_od ADD class INTEGER DEFAULT 0");
      project.execute(
          "INSERT INTO mini_od VALUES (1,1,4,10.25,1),(1,1,4,5.5,1),"
              + "(1,1,4,7,2),(3,1,4,9,1),(1,1,2,4,1)");
      try (LoadedDemand loaded = new LoadedDemand(project.parameters(1))) {
        assertEquals(
            Map.of("1:1:4:1:-1", 15.75, "1:1:4:2:-1", 7.0, "3:1:4:1:-1", 9.0, "1:1:2:1:-1", 4.0),
            loaded.quantities());
        assertEquals(35.75, loaded.reader.getTotalQuantity(), 1e-12);
        assertArrayEquals(new byte[] {1, 3}, loaded.network.getGroups());
        assertFalse(loaded.network.odClassHasDemand((byte) 0));
        assertTrue(loaded.network.odClassHasDemand((byte) 1));
        assertTrue(loaded.network.odClassHasDemand((byte) 2));
        assertEquals(3, loaded.network.getNbODClasses());
        assertEquals(5, project.number("SELECT COUNT(*) FROM mini_od"));
        assertFalse(project.getMainJDBCConnection().isClosed());
      }
    }
  }

  @Test
  void timedRowsUseColumnNamesAndKeepDeparturesAndClassesSeparate() throws Exception {
    try (AssignmentTestProject project = timedProject()) {
      AssignmentParameters parameters = project.parameters(1);
      parameters.setTimeDependent(true);
      try (LoadedDemand loaded = new LoadedDemand(parameters)) {
        assertEquals(
            Map.of(
                "1:1:4:1:3600",
                15.0,
                "1:1:4:1:5400",
                20.0,
                "1:1:4:2:3600",
                7.0,
                "2:1:4:1:3600",
                8.0),
            loaded.quantities());
        assertEquals(50, loaded.reader.getTotalQuantity(), 1e-12);
        assertArrayEquals(new byte[] {1, 2}, loaded.network.getGroups());
      }
    }
  }

  @Test
  void staticLoadingIgnoresDepartureColumnButKeepsClassesSeparate() throws Exception {
    try (AssignmentTestProject project = timedProject();
        LoadedDemand loaded = new LoadedDemand(project.parameters(1))) {
      assertEquals(
          Map.of("1:1:4:1:-1", 35.0, "1:1:4:2:-1", 7.0, "2:1:4:1:-1", 8.0), loaded.quantities());
      assertEquals(50, loaded.reader.getTotalQuantity(), 1e-12);
    }
  }

  @Test
  void sqlSelectionControlsLoadedGroupsAndDemandWithoutChangingSourceRows() throws Exception {
    try (AssignmentTestProject project = timedProject()) {
      AssignmentParameters parameters = project.parameters(1);
      parameters.setTimeDependent(true);
      parameters.setWhereStmt("grp=1 AND class=1 AND time=60");
      try (LoadedDemand loaded = new LoadedDemand(parameters)) {
        assertEquals(Map.of("1:1:4:1:3600", 15.0), loaded.quantities());
        assertArrayEquals(new byte[] {1}, loaded.network.getGroups());
        assertEquals(15, loaded.reader.getTotalQuantity(), 1e-12);
        assertEquals(5, project.number("SELECT COUNT(*) FROM mini_od"));
        assertEquals(50, project.number("SELECT SUM(qty) FROM mini_od"));
      }
    }
  }

  @Test
  void absentNodesSelfTripsAndNonpositiveQuantitiesAreExcluded() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      project.execute("DELETE FROM mini_od");
      project.execute(
          "INSERT INTO mini_od VALUES (1,1,4,10),(1,99,4,20),(1,1,99,30),"
              + "(1,1,1,40),(1,1,4,0),(1,1,4,-5)");
      try (LoadedDemand loaded = new LoadedDemand(project.parameters(1))) {
        assertEquals(Map.of("1:1:4:0:-1", 10.0), loaded.quantities());
        assertEquals(10, loaded.reader.getTotalQuantity(), 1e-12);
        assertEquals(6, project.number("SELECT COUNT(*) FROM mini_od"));
      }
    }
  }

  @Test
  void emptySelectionHasNoDemandOrGroups() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      AssignmentParameters parameters = project.parameters(1);
      parameters.setWhereStmt("1=0");
      try (LoadedDemand loaded = new LoadedDemand(parameters)) {
        assertTrue(loaded.quantities().isEmpty());
        assertArrayEquals(new byte[0], loaded.network.getGroups());
        assertEquals(0, loaded.reader.getTotalQuantity(), 1e-12);
        assertFalse(loaded.network.odClassHasDemand((byte) 0));
      }
    }
  }

  @Test
  void freshNetworkReadsUpdatedDemandWithoutCarryingEarlierQuantities() throws Exception {
    try (AssignmentTestProject project = new AssignmentTestProject(directory)) {
      try (LoadedDemand first = new LoadedDemand(project.parameters(1))) {
        assertEquals(140, first.reader.getTotalQuantity(), 1e-12);
      }
      project.execute("UPDATE mini_od SET qty=25 WHERE grp=1");
      try (LoadedDemand second = new LoadedDemand(project.parameters(1))) {
        assertEquals(Map.of("1:1:4:0:-1", 25.0, "2:2:4:0:-1", 40.0), second.quantities());
        assertEquals(65, second.reader.getTotalQuantity(), 1e-12);
      }
    }
  }

  private AssignmentTestProject timedProject() throws Exception {
    AssignmentTestProject project = new AssignmentTestProject(directory);
    project.execute("DROP TABLE mini_od");
    // Deliberately reorder columns so a SELECT * implementation would fail these checks.
    project.execute(
        "CREATE TABLE mini_od (qty NUMERIC(12,3), dst INTEGER, time INTEGER,"
            + " grp INTEGER, class INTEGER, org INTEGER)");
    project.execute(
        "INSERT INTO mini_od VALUES (10,4,60,1,1,1),(5,4,60,1,1,1),"
            + "(20,4,90,1,1,1),(7,4,60,1,2,1),(8,4,60,2,1,1)");
    return project;
  }

  private static final class LoadedDemand implements AutoCloseable {
    final VirtualNetwork network;
    final ODReader reader;

    LoadedDemand(AssignmentParameters parameters) {
      network = new VirtualNetwork(parameters);
      assertTrue(network.generate());
      reader = new ODReader(parameters);
      assertTrue(reader.loadDemand(network));
    }

    Map<String, Double> quantities() {
      Map<String, Double> quantities = new TreeMap<>();
      for (VirtualNodeList node : network.getVirtualNodeLists()) {
        if (node.getInitialDemandList() == null) {
          continue;
        }
        for (ODCell cell : node.getInitialDemandList()) {
          String key =
              cell.getGroup()
                  + ":"
                  + cell.getOriginNodeId()
                  + ":"
                  + cell.getDestinationNodeId()
                  + ":"
                  + cell.getODClass()
                  + ":"
                  + cell.getStartingTime();
          assertNull(quantities.put(key, cell.getQuantity()), "Duplicate OD cell " + key);
        }
      }
      return quantities;
    }

    @Override
    public void close() {
      network.dispose();
    }
  }
}
