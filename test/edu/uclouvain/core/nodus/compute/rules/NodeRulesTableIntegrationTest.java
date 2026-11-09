/*
 * Copyright (c) 1991-2026 Université catholique de Louvain
 * This file is part of Nodus, distributed under the GNU GPL, version 3 or later.
 */

package edu.uclouvain.core.nodus.compute.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.AssignmentTestProject;
import edu.uclouvain.core.nodus.compute.costs.CostParser;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Checks the deliverable centroid rules against independently enumerated allowed movements. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(60)
class NodeRulesTableIntegrationTest {
  private static final int FIRST = 1020201;
  private static final int SECOND = 1020303;
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> generatedTableEnforcesEveryExpectedDecision() {
    List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      for (boolean reverse : new boolean[] {false, true}) {
        tests.add(
            dynamicTest(
                (hsql ? "HSQLDB" : "H2") + (reverse ? ", reversed rows" : ", original rows"),
                () -> verify(hsql, reverse)));
      }
    }
    return tests;
  }

  private void verify(boolean hsql, boolean reverse) throws Exception {
    try (AssignmentTestProject project =
        new AssignmentTestProject(
            directory,
            2,
            new double[][] {
              {11, FIRST, SECOND, 10, 0},
              {12, FIRST, SECOND, 10, 0},
              {13, FIRST, SECOND, 10, 0}
            },
            hsql)) {
      for (int row = 0; row < 2; row++) {
        project.setNodeHandling(row + 1, 8);
        project
            .getNodeLayers()[0]
            .getModel()
            .setValueAt((double) (row == 0 ? FIRST : SECOND), row, NodusC.DBF_IDX_NUM);
      }
      int[] meansCounts = {3, 7, 2};
      AssignmentParameters parameters = project.parameters(1);
      for (int mode = 1; mode <= 3; mode++) {
        project
            .getLinkLayers()[0]
            .getModel()
            .setValueAt((double) mode, mode - 1, NodusC.DBF_IDX_MODE);
        project
            .getLinkLayers()[0]
            .getModel()
            .setValueAt((double) meansCounts[mode - 1], mode - 1, NodusC.DBF_IDX_MEANS);
        for (int means = 1; means <= meansCounts[mode - 1]; means++) {
          parameters.getCostFunctions().setProperty("mv." + mode + "," + means, "10");
          parameters.getCostFunctions().setProperty("ld." + mode + "," + means, "1");
          parameters.getCostFunctions().setProperty("ul." + mode + "," + means, "1");
        }
      }
      project.properties.setProperty(NodusC.PROP_EXC_TABLE, "nodus_rule_tests");
      List<String> rules = new ArrayList<>();
      // The generated fixture writes exactly one complete statement per line.
      for (String line :
          Files.readAllLines(Path.of("test/fixtures/node-rules/node_rules_test.sql"))) {
        if (line.startsWith("--") || line.trim().isEmpty()) {
          continue;
        }
        String sql = line.trim();
        assertTrue(sql.endsWith(";"));
        sql = sql.substring(0, sql.length() - 1);
        if (sql.startsWith("INSERT INTO nodus_rule_tests ")) {
          rules.add(sql);
        } else if (!sql.isEmpty()) {
          project.execute(sql);
        }
      }
      if (reverse) {
        Collections.reverse(rules);
      }
      for (String sql : rules) {
        project.execute(sql);
      }
      assertEquals(67, rules.size());

      List<Integer> scenarios = new ArrayList<>();
      try (Statement statement = project.getMainJDBCConnection().createStatement();
          ResultSet result =
              statement.executeQuery(
                  "SELECT scenario FROM nodus_rule_test_cases ORDER BY scenario")) {
        while (result.next()) {
          scenarios.add(result.getInt(1));
        }
      }
      assertEquals(25, scenarios.size());
      int decisions = 0;
      for (int scenario : scenarios) {
        VirtualNetwork network = new VirtualNetwork(parameters);
        try {
          assertTrue(network.generate());
          network.setGroups(new byte[] {0, 1});
          network.setMaxClass((byte) 0);
          NodeRulesReader reader = new NodeRulesReader(network, scenario);
          assertTrue(reader.hasExclusions());
          assertTrue(reader.loadExclusions());
          assertTrue(network.computeCosts(0, scenario, (byte) 0, 1));
          try (Statement statement = project.getMainJDBCConnection().createStatement();
              ResultSet result =
                  statement.executeQuery(
                      "SELECT * FROM nodus_rule_test_expected WHERE scenario=" + scenario)) {
            while (result.next()) {
              int node = result.getInt("num");
              final byte group = result.getByte("grp");
              int mode1 = result.getInt("mode1");
              int means1 = result.getInt("means1");
              int mode2 = result.getInt("mode2");
              int means2 = result.getInt("means2");
              VirtualNodeList list =
                  network.getVirtualNodeLists()[network.getNodeIndexInVirtualNodeList(node, true)];
              assertTrue(list.isLoadingUnloadingNode());
              assertFalse(list.isTranshipmentNode());
              List<VirtualLink> links =
                  list.getVirtualNodeList()
                      .stream()
                      .flatMap(virtualNode -> virtualNode.getVirtualLinkList().stream())
                      .filter(
                          link ->
                              link.getType() == VirtualLink.TYPE_LOAD
                                  || link.getType() == VirtualLink.TYPE_UNLOAD)
                      .filter(
                          link ->
                              link.getBeginVirtualNode().getMode() == mode1
                                  && link.getBeginVirtualNode().getMeans() == means1
                                  && link.getEndVirtualNode().getMode() == mode2
                                  && link.getEndVirtualNode().getMeans() == means2)
                      .collect(Collectors.toList());
              assertFalse(links.isEmpty(), "Missing expected centroid movement");
              for (VirtualLink link : links) {
                assertEquals(
                    result.getInt("excluded") == 1 ? CostParser.UNDEFINED_FUNCTION : 1,
                    link.getCost(group),
                    "node="
                        + node
                        + " scenario="
                        + scenario
                        + " group="
                        + group
                        + " movement="
                        + mode1
                        + ","
                        + means1
                        + " -> "
                        + mode2
                        + ","
                        + means2);
              }
              decisions++;
            }
          }
        } finally {
          network.dispose();
        }
      }
      assertEquals(2400, decisions);
    }
  }
}
