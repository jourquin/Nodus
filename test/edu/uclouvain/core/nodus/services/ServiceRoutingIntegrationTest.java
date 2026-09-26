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

package edu.uclouvain.core.nodus.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AllOrNothingAssignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.AssignmentTestProject;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Service SQL tables through ordered virtual-network generation, costs, routing and saved paths.
 */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(30)
class ServiceRoutingIntegrationTest {
  @TempDir Path directory;

  @Test
  void orderedServiceMustVisitADeadEndStopBeforeContinuing() throws Exception {
    try (AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 4, new double[][] {{11, 1, 2, 2, 0}, {12, 2, 3, 3, 0}, {13, 2, 4, 4, 0}})) {
      createTables(project);
      addService(project, 1, 10, new int[] {11, 12, 12, 13}, new int[] {1, 3, 4});
      project.reloadServices();
      project.execute("INSERT INTO mini_od VALUES (1,1,4,100)");
      run(project, 1);
      // A-B-C-B-D must visit the dead-end stop C. A-B-D would skip occurrences 1 and 2.
      assertEquals(
          List.of(List.of("-12"), List.of("11"), List.of("12"), List.of("13")),
          project.rows("SELECT link FROM mini_paths1_detail ORDER BY link"));
      assertHeader(project, 1, 100, 10, 1, 12, 2, 0);
      assertEquals(
          200,
          project.number(
              "SELECT SUM(qty) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_MOVE
                  + " AND link1=12"));
      assertEquals(
          100,
          project.number(
              "SELECT SUM(qty) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_STOP
                  + " AND ABS(node1)=3"));
    }
  }

  @Test
  void intermediateNonstopAllowsThroughTravelButPreventsBoardingAndAlighting() throws Exception {
    try (AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 3, new double[][] {{11, 1, 2, 2, 0}, {12, 2, 3, 3, 0}})) {
      createTables(project);
      addService(project, 1, 10, new int[] {11, 12}, new int[] {1, 3});
      project.reloadServices();
      project.execute("INSERT INTO mini_od VALUES (1,1,3,100),(2,2,3,40),(3,1,2,30)");
      run(project, 3);
      assertEquals(1, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertHeader(project, 1, 100, 10, 1, 5, 0, 0);
      assertEquals(
          100,
          project.number(
              "SELECT SUM(qty) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_TRANSIT
                  + " AND ABS(node1)=2"));
      assertEquals(
          0,
          project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_STOP));

      project.execute("INSERT INTO mini_services_stops VALUES (1,2)");
      project.reloadServices();
      run(project, 3);
      assertEquals(3, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
      assertHeader(project, 1, 100, 10, 1, 5, 2, 0);
      assertHeader(project, 2, 40, 10, 1, 3, 0, 0);
      assertHeader(project, 3, 30, 10, 1, 2, 0, 0);
      assertEquals(170, project.number("SELECT SUM(qty) FROM mini_paths1_header"));
      assertEquals(170, project.number("SELECT SUM(qty) FROM mini_od"));
    }
  }

  @Test
  void permittedServiceTransferUsesDestinationFrequencyAndItsTransferCost() throws Exception {
    try (AssignmentTestProject project = transferProject()) {
      run(project, 1);
      assertTransfer(project, 12);
      project.execute("UPDATE mini_services_header SET frequency=40 WHERE id=2");
      project.reloadServices();
      run(project, 1);
      assertTransfer(project, 9.5);
      assertEquals(1, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
    }
  }

  @Test
  void transferRequiresBothServicesToStopAtTheInterchange() throws Exception {
    try (AssignmentTestProject project = transferProject()) {
      project.execute("DELETE FROM mini_services_stops WHERE id=1 AND stop=2");
      project.reloadServices();
      run(project, 1);
      assertNoAssignedDemand(project);
      assertEquals(
          0,
          project.number(
              "SELECT COUNT(*) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_SWITCH
                  + " AND ABS(node1)=2"));
    }
  }

  @Test
  void transferRequiresNodePermissionEvenWhenBothServicesStop() throws Exception {
    try (AssignmentTestProject project = transferProject()) {
      project.setNodeHandling(2, NodusC.HANDLING_LOAD_UNLOAD);
      run(project, 1);
      assertNoAssignedDemand(project);
      assertEquals(
          0,
          project.number(
              "SELECT COUNT(*) FROM mini_vnet1 WHERE vtype="
                  + VirtualLink.TYPE_SWITCH
                  + " AND ABS(node1)=2"));
    }
  }

  private AssignmentTestProject transferProject() throws Exception {
    AssignmentTestProject project =
        new AssignmentTestProject(
            directory, 4, new double[][] {{11, 1, 2, 2, 0}, {12, 2, 3, 3, 0}, {13, 2, 4, 4, 0}});
    project.setNodeHandling(2, NodusC.HANDLING_ALL);
    createTables(project);
    addService(project, 1, 10, new int[] {11, 12}, new int[] {1, 2, 3});
    addService(project, 2, 20, new int[] {13}, new int[] {2, 4});
    project.reloadServices();
    project.execute("INSERT INTO mini_od VALUES (1,1,4,100)");
    return project;
  }

  private static void createTables(AssignmentTestProject project) throws Exception {
    project.execute(
        "CREATE TABLE mini_services_header (id INTEGER,name VARCHAR(30),mode INTEGER,"
            + "means INTEGER,frequency INTEGER)");
    project.execute("CREATE TABLE mini_services_links (id INTEGER,pathidx INTEGER,link INTEGER)");
    project.execute("CREATE TABLE mini_services_stops (id INTEGER,stop INTEGER)");
  }

  private static void addService(
      AssignmentTestProject project, int id, int frequency, int[] links, int[] stops)
      throws Exception {
    project.execute(
        "INSERT INTO mini_services_header VALUES ("
            + id
            + ",'Service"
            + id
            + "',1,1,"
            + frequency
            + ")");
    // Insertion order is deliberately reversed; pathidx must determine route order when loaded.
    for (int index = links.length - 1; index >= 0; index--) {
      project.execute(
          "INSERT INTO mini_services_links VALUES (" + id + "," + index + "," + links[index] + ")");
    }
    for (int stop : stops) {
      project.execute("INSERT INTO mini_services_stops VALUES (" + id + "," + stop + ")");
    }
  }

  private static void run(AssignmentTestProject project, int groups) {
    AssignmentParameters parameters = project.parameters(4);
    parameters.getCostFunctions().setProperty("SERVICELINES.1,1", "true");
    parameters.getCostFunctions().setProperty("ld.1,1", "100/FREQUENCY");
    parameters.getCostFunctions().setProperty("ul.1,1", "1");
    parameters.getCostFunctions().setProperty("stp.1,1", "2");
    parameters.getCostFunctions().setProperty("sw.1,1-1,1", "7+100/FREQUENCY");
    project.run(new AllOrNothingAssignment(parameters), groups);
  }

  private static void assertHeader(
      AssignmentTestProject project,
      int group,
      double quantity,
      double loading,
      double unloading,
      double moving,
      double stop,
      double switching)
      throws Exception {
    String where = " FROM mini_paths1_header WHERE grp=" + group;
    assertEquals(quantity, project.number("SELECT qty" + where), 1e-9);
    assertEquals(loading, project.number("SELECT ldcost" + where), 1e-9);
    assertEquals(unloading, project.number("SELECT ulcost" + where), 1e-9);
    assertEquals(moving, project.number("SELECT mvcost" + where), 1e-9);
    assertEquals(stop, project.number("SELECT stcost" + where), 1e-9);
    assertEquals(switching, project.number("SELECT swcost" + where), 1e-9);
    assertEquals(0, project.number("SELECT trcost+tpcost" + where), 1e-9);
    assertEquals(
        quantity * (loading + unloading + moving + stop + switching),
        project.number("SELECT qty*(ldcost+ulcost+mvcost+stcost+swcost+trcost+tpcost)" + where),
        1e-9);
  }

  private static void assertTransfer(AssignmentTestProject project, double switching)
      throws Exception {
    assertHeader(project, 1, 100, 10, 1, 6, 0, switching);
    assertEquals(
        List.of(List.of("11"), List.of("13")),
        project.rows("SELECT link FROM mini_paths1_detail ORDER BY link"));
    assertEquals(
        100,
        project.number(
            "SELECT SUM(qty) FROM mini_vnet1 WHERE vtype="
                + VirtualLink.TYPE_SWITCH
                + " AND ABS(node1)=2 AND service1=1 AND service2=2"));
    assertEquals(
        100,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_LOAD));
    assertEquals(
        100,
        project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + VirtualLink.TYPE_UNLOAD));
  }

  private static void assertNoAssignedDemand(AssignmentTestProject project) throws Exception {
    assertEquals(0, project.number("SELECT COUNT(*) FROM mini_paths1_header"));
    assertEquals(0, project.number("SELECT COUNT(*) FROM mini_paths1_detail"));
    assertEquals(0, project.number("SELECT SUM(qty) FROM mini_vnet1"));
    assertEquals(100, project.number("SELECT SUM(qty) FROM mini_od"));
  }
}
