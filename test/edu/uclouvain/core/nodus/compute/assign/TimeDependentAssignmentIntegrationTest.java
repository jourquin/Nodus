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

package edu.uclouvain.core.nodus.compute.assign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.real.RealLinkInitializer;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Complete timed coordinators: SQL demand through cost passes, workers and saved time slices. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@ResourceLock("NodusC.displayComputingTimes")
@ResourceLock(Resources.SYSTEM_OUT)
@Timeout(30)
class TimeDependentAssignmentIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> htmlComputingTimesIncludeEveryTimedPeriod() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean dynamic : new boolean[] {false, true}) {
      for (boolean hsql : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                "HTML/dynamic=" + dynamic + "/HSQLDB=" + hsql,
                () -> {
                  boolean previous = NodusC.displayComputingTimes;
                  try (AssignmentTestProject project = project(hsql)) {
                    NodusC.displayComputingTimes = true;
                    Assignment assignment = assignment(project, dynamic, 4);
                    String output =
                        ComputingTimesTestSupport.capture(() -> project.run(assignment, 2));
                    assertReference(project, dynamic);
                    String html =
                        ComputingTimesTestSupport.report(assignment.getAssignmentParameters());
                    assertFalse(output.contains("Computing times:"));
                    assertTrue(html.contains(assignment.getClass().getSimpleName()));
                    assertTrue(html.contains("Dijkstra searches:"));
                    assertTrue(html.endsWith("</body></html>"));
                  } finally {
                    NodusC.displayComputingTimes = previous;
                  }
                }));
      }
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> timedCoordinators() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean dynamic : new boolean[] {false, true}) {
        final String name = (hsql ? "HSQLDB" : "H2") + (dynamic ? " dynamic" : " static");
        tests.add(
            dynamicTest(
                name + " serial, parallel and repeated results",
                () -> {
                  try (AssignmentTestProject project = project(hsql)) {
                    project.run(assignment(project, dynamic, 1), 2);
                    assertReference(project, dynamic);
                    final List<List<String>> serial = snapshot(project);
                    project.run(assignment(project, dynamic, 4), 2);
                    assertReference(project, dynamic);
                    assertEquals(serial, snapshot(project));
                    project.run(assignment(project, dynamic, 4), 2);
                    assertReference(project, dynamic);
                    assertEquals(serial, snapshot(project));
                  }
                }));
      }
    }
    return tests;
  }

  private AssignmentTestProject project(boolean hsql) throws Exception {
    // 1 -> 2 takes 100s. From 2, the direct route costs 2 and takes 100s;
    // the route through 3 costs 3+3 and takes 20+20s. At period 1 the direct cost becomes 10.
    final AssignmentTestProject project =
        new AssignmentTestProject(
            directory,
            4,
            new double[][] {{11, 1, 2, 1, 0}, {12, 2, 4, 2, 8}, {13, 2, 3, 3, 0}, {14, 3, 4, 3, 0}},
            hsql);
    final double[] seconds = {100, 100, 20, 20};
    for (int i = 0; i < seconds.length; i++) {
      final float length =
          RealLinkInitializer.computeLength(project.links.getEsriGraphicList().getOMGraphicAt(i));
      project.links.getModel().setValueAt(length * 3600.0 / seconds[i], i, NodusC.DBF_IDX_SPEED);
    }
    project.execute(
        "ALTER TABLE mini_od ADD COLUMN "
            + edu.uclouvain.core.nodus.database.JDBCUtils.getQuotedCompliantIdentifier(
                NodusC.DBF_TIME)
            + " INTEGER");
    project.execute("INSERT INTO mini_od VALUES(1,1,4,100,10),(2,1,4,40,11)");
    return project;
  }

  private Assignment assignment(AssignmentTestProject project, boolean dynamic, int threads) {
    final AssignmentParameters parameters = project.parameters(threads);
    parameters.setTimeDependent(true);
    parameters.getCostFunctions().setProperty("tr.1,1", "1");
    parameters.getCostFunctions().setProperty(NodusC.VARNAME_STARTTIME, "10");
    parameters.getCostFunctions().setProperty(NodusC.VARNAME_ENDTIME, "16");
    parameters.getCostFunctions().setProperty(NodusC.VARNAME_TIMESLICE, "2");
    parameters.getCostFunctions().setProperty("t1.mv.1,1", "BASECOST + SLOPE");
    parameters.getCostFunctions().setProperty("t2.mv.1,1", "BASECOST + SLOPE");
    return dynamic
        ? new DynamicTimeDependentAssignment(parameters)
        : new StaticAoNTimeDependentAssignment(parameters);
  }

  private static void assertReference(AssignmentTestProject project, boolean dynamic)
      throws Exception {
    assertFlows(project, 11, new double[][] {{100, 0}, {0, 40}, {0, 0}});
    if (dynamic) {
      assertFlows(project, 12, new double[][] {{0, 0}, {0, 0}, {0, 0}});
      assertFlows(project, 13, new double[][] {{0, 0}, {100, 40}, {0, 0}});
      assertFlows(project, 14, new double[][] {{0, 0}, {100, 40}, {0, 0}});
      assertEquals(980, project.number("SELECT SUM(qty*mvcost) FROM mini_paths1_header"), 1e-9);
      assertEquals(280, project.number("SELECT SUM(qty*trcost) FROM mini_paths1_header"), 1e-9);
      assertEquals(6, project.number("SELECT COUNT(*) FROM mini_paths1_detail"));
      assertEquals(
          2,
          project.number(
              "SELECT ucost1 FROM mini_vnet1 WHERE link1=12 "
                  + "AND vtype=0 AND ABS(node1)<ABS(node2) AND time=10"));
      assertEquals(
          10,
          project.number(
              "SELECT ucost1 FROM mini_vnet1 WHERE link1=12 "
                  + "AND vtype=0 AND ABS(node1)<ABS(node2) AND time=12"));
    } else {
      assertFlows(project, 12, new double[][] {{0, 0}, {100, 0}, {0, 40}});
      assertFlows(project, 13, new double[][] {{0, 0}, {0, 0}, {0, 0}});
      assertFlows(project, 14, new double[][] {{0, 0}, {0, 0}, {0, 0}});
      assertEquals(420, project.number("SELECT SUM(qty*mvcost) FROM mini_paths1_header"), 1e-9);
      assertEquals(140, project.number("SELECT SUM(qty*trcost) FROM mini_paths1_header"), 1e-9);
      assertEquals(4, project.number("SELECT COUNT(*) FROM mini_paths1_detail"));
    }
    for (byte type : new byte[] {VirtualLink.TYPE_LOAD, VirtualLink.TYPE_UNLOAD}) {
      assertEquals(140, project.number("SELECT SUM(qty) FROM mini_vnet1 WHERE vtype=" + type));
    }
    assertEquals(2, project.number("SELECT COUNT(DISTINCT pathidx) FROM mini_paths1_header"));
    assertEquals(140, project.number("SELECT SUM(qty) FROM mini_od"));
    assertEquals(
        140,
        project.number(
            "SELECT SUM(qty) FROM (SELECT pathidx,MAX(qty) AS qty "
                + "FROM mini_paths1_header GROUP BY pathidx) paths"));
    assertEquals(
        List.of(List.of("10"), List.of("12"), List.of("14")),
        project.rows("SELECT DISTINCT time FROM mini_vnet1 ORDER BY time"));
  }

  private static void assertFlows(AssignmentTestProject project, int link, double[][] quantities)
      throws Exception {
    for (int slice = 0; slice < quantities.length; slice++) {
      final String where =
          " FROM mini_vnet1 WHERE vtype="
              + VirtualLink.TYPE_MOVE
              + " AND link1="
              + link
              + " AND time="
              + (10 + 2 * slice);
      final double first = quantities[slice][0];
      final double second = quantities[slice][1];
      assertEquals(
          first, project.number("SELECT SUM(qty1)" + where + " AND ABS(node1)<ABS(node2)"));
      assertEquals(
          second, project.number("SELECT SUM(qty2)" + where + " AND ABS(node1)<ABS(node2)"));
      assertEquals(0, project.number("SELECT SUM(qty)" + where + " AND ABS(node1)>ABS(node2)"));
      assertEquals(
          Math.ceil(first / 10) + Math.ceil(second / 10),
          project.number("SELECT SUM(veh)" + where + " AND ABS(node1)<ABS(node2)"));
    }
  }

  private static List<List<String>> snapshot(AssignmentTestProject project) throws Exception {
    final List<List<String>> rows =
        new ArrayList<>(
            project.rows("SELECT * FROM mini_vnet1 ORDER BY time,node1,node2,link1,link2,vtype"));
    rows.addAll(
        project.rows(
            "SELECT grp,org,dst,time,qty,mvcost FROM mini_paths1_header "
                + "ORDER BY grp,org,dst,time,mvcost"));
    rows.addAll(project.pathDetails());
    return rows;
  }
}
