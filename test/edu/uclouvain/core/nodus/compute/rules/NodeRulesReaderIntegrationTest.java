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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** SQL rows must become effective rules, including at nodes which cannot load or unload. */
@Tag("integration")
@ResourceLock("JDBCUtils")
@Timeout(20)
class NodeRulesReaderIntegrationTest {
  @TempDir Path directory;

  @TestFactory
  List<DynamicTest> loadedRules() {
    final List<DynamicTest> tests = new ArrayList<>();
    for (boolean hsql : new boolean[] {false, true}) {
      final String prefix = hsql ? "HSQLDB: " : "H2: ";
      tests.add(
          dynamicTest(
              prefix + "symmetric exclusions reach transhipment-only nodes",
              () -> {
                try (Fixture fixture = new Fixture(hsql)) {
                  fixture.rows("(-2,7,3,1,1,2,1,1)");
                  fixture.load(7);
                  fixture.assertCosts(7, -1, -1, 4, 6);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "asymmetry and unrelated scenarios stay isolated",
              () -> {
                try (Fixture fixture = new Fixture(hsql)) {
                  fixture.rows("(-2,7,3,1,1,2,1,0),(-2,8,4,2,1,1,1,1)");
                  fixture.load(7);
                  fixture.assertCosts(7, -1, 6, 4, 6);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "selected scenario replaces generic rules",
              () -> {
                try (Fixture fixture = new Fixture(hsql)) {
                  fixture.rows("(-2,-1,-1,1,1,2,1,1),(2,7,3,1,1,2,1,0)");
                  fixture.load(7);
                  fixture.assertCosts(7, 4, -1, 4, 6);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "generic defaults apply when the scenario has no rules",
              () -> {
                try (Fixture fixture = new Fixture(hsql)) {
                  fixture.rows("(-2,-1,-1,1,1,2,1,1),(2,7,3,1,1,2,1,0)");
                  fixture.load(8);
                  fixture.assertCosts(8, -1, -1, -1, -1);
                }
              }));
      tests.add(
          dynamicTest(
              prefix + "missing table and unknown nodes leave movements available",
              () -> {
                try (Fixture fixture = new Fixture(hsql)) {
                  final NodeRulesReader absent = new NodeRulesReader(fixture.network, 7);
                  assertFalse(absent.hasExclusions());
                  assertFalse(absent.loadExclusions());
                  fixture.rows("(-999,7,-1,-1,-1,-1,-1,1)");
                  fixture.load(7);
                  fixture.assertCosts(7, 4, 6, 4, 6);
                }
              }));
    }
    return tests;
  }

  private final class Fixture implements AutoCloseable {
    final AssignmentTestProject project;
    final VirtualNetwork network;
    final VirtualNodeList transfer;

    Fixture(boolean hsql) throws Exception {
      project =
          new AssignmentTestProject(
              directory, 3, new double[][] {{11, 1, 2, 2, 0}, {12, 2, 3, 3, 0}}, hsql);
      project.setNodeHandling(2, NodusC.HANDLING_TRANSHIP);
      project.getLinkLayers()[0].getModel().setValueAt(2.0, 1, NodusC.DBF_IDX_MODE);
      final AssignmentParameters parameters = project.parameters(2);
      parameters.getCostFunctions().setProperty("mv.2,1", "BASECOST");
      parameters.getCostFunctions().setProperty("ld.2,1", "0");
      parameters.getCostFunctions().setProperty("ul.2,1", "0");
      parameters.getCostFunctions().setProperty("tr.2,1", "0");
      parameters.getCostFunctions().setProperty("tp.1,1-2,1", "4");
      parameters.getCostFunctions().setProperty("tp.2,1-1,1", "6");
      network = new VirtualNetwork(parameters);
      assertTrue(network.generate());
      network.setGroups(new byte[] {3, 4});
      network.setMaxClass((byte) 0);
      transfer = network.getVirtualNodeLists()[network.getNodeIndexInVirtualNodeList(2, false)];
      assertTrue(transfer.isTranshipmentNode());
      assertFalse(transfer.isLoadingUnloadingNode());
    }

    void rows(String rows) throws Exception {
      assertTrue(NodeRulesReader.createExclusionsTable("mini_exc"));
      project.execute("INSERT INTO mini_exc VALUES " + rows);
    }

    void load(int scenario) {
      final NodeRulesReader reader = new NodeRulesReader(network, scenario);
      assertTrue(reader.hasExclusions());
      assertTrue(reader.loadExclusions());
    }

    void assertCosts(
        int scenario, double forward3, double reverse3, double forward4, double reverse4) {
      assertTrue(network.computeCosts(0, scenario, (byte) 0, 2));
      final VirtualLink forward = movement(1, 2);
      final VirtualLink reverse = movement(2, 1);
      assertEquals(
          forward3 < 0 ? CostParser.UNDEFINED_FUNCTION : forward3, forward.getCost((byte) 0));
      assertEquals(
          reverse3 < 0 ? CostParser.UNDEFINED_FUNCTION : reverse3, reverse.getCost((byte) 0));
      assertEquals(
          forward4 < 0 ? CostParser.UNDEFINED_FUNCTION : forward4, forward.getCost((byte) 1));
      assertEquals(
          reverse4 < 0 ? CostParser.UNDEFINED_FUNCTION : reverse4, reverse.getCost((byte) 1));
    }

    VirtualLink movement(int from, int to) {
      return transfer
          .getVirtualNodeList()
          .stream()
          .flatMap(node -> node.getVirtualLinkList().stream())
          .filter(
              link ->
                  link.getType() == VirtualLink.TYPE_TRANSHIP
                      && link.getBeginVirtualNode().getMode() == from
                      && link.getEndVirtualNode().getMode() == to)
          .findFirst()
          .orElseThrow();
    }

    @Override
    public void close() {
      network.dispose();
      project.close();
    }
  }
}
