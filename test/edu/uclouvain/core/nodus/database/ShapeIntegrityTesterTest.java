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

package edu.uclouvain.core.nodus.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bbn.openmap.Environment;
import com.bbn.openmap.dataAccess.shape.EsriPoint;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.testing.NetworkTestProject;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Validates loaded network data through the actual EDT snapshot and validation paths. */
@ResourceLock("JDBCUtils")
class ShapeIntegrityTesterTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> invalidIdentifiersAreRejectedBeforeIntegerConversion() {
    return Stream.of(0L, -1L, 2147483648L, 4294967297L, 4294967298L)
        .flatMap(
            id ->
                Stream.of(
                    invalid(
                        "node " + id,
                        p -> p.nodes.getModel().setValueAt(id, 0, 0),
                        "NodeNum",
                        "Layer {0} : Node {1} has an invalid Num value",
                        "nodes",
                        id),
                    invalid(
                        "link " + id,
                        p -> p.links.getModel().setValueAt(id, 0, 0),
                        "LinkNum",
                        "Layer {0} : Link {1} has an invalid Num value",
                        "links",
                        id)));
  }

  @TestFactory
  Stream<DynamicTest> duplicatesMissingEndpointsAndUnbalancedLayersAreRejected() {
    return Stream.of(
        invalid(
            "duplicate nodes",
            p -> p.nodes.getModel().setValueAt(1, 1, 0),
            "Node",
            "Layer {0} : Node {1} already found in \"{2}\"",
            "nodes",
            1,
            "nodes"),
        invalid(
            "external node",
            p -> p.getOtherNodeNumbers().put(1, 1),
            "ExternalNode",
            "Layer {0} : Node {1} already found in external layer",
            "nodes",
            1),
        invalid(
            "external link",
            p -> p.getOtherLinkNumbers().put(11, 1),
            "ExternalLink",
            "Layer {0} : Link {1} already found in external layer",
            "links",
            11),
        invalid(
            "duplicate links",
            p -> {
              p.links.getModel().addRecord(new ArrayList<>(p.links.getModel().getRecord(0)));
              p.links.getEsriGraphicList().add(p.links.getEsriGraphicList().getOMGraphicAt(0));
            },
            "Link",
            "Layer {0}: Link {1} already found in \"{2}\"",
            "links",
            11,
            "links"),
        invalid(
            "missing origin",
            p -> p.links.getModel().setValueAt(99, 0, NodusC.DBF_IDX_NODE1),
            "makes_reference_to_inexistant_node",
            "Layer {0} Link {1} makes reference to inexistant node {2}",
            "links",
            11,
            99),
        invalid(
            "missing destination",
            p -> p.links.getModel().setValueAt(99, 0, NodusC.DBF_IDX_NODE2),
            "makes_reference_to_inexistant_node",
            "Layer {0} Link {1} makes reference to inexistant node {2}",
            "links",
            11,
            99),
        invalid(
            "missing node geometry",
            p -> p.nodes.getEsriGraphicList().remove(0),
            "is_unbalanced",
            "Layer '{0}' is unbalanced",
            "nodes"),
        invalid(
            "missing link geometry",
            p -> p.links.getEsriGraphicList().clear(),
            "is_unbalanced",
            "Layer \"{0}\" is unbalanced",
            "links"),
        invalid(
            "extra node geometry",
            p -> p.nodes.getEsriGraphicList().add(new EsriPoint(1, 2)),
            "is_unbalanced",
            "Layer '{0}' is unbalanced",
            "nodes"));
  }

  private DynamicTest invalid(
      String name,
      Consumer<NetworkTestProject> change,
      String key,
      String fallback,
      Object... arguments) {
    return DynamicTest.dynamicTest(
        name,
        () -> {
          try (NetworkTestProject project = project();
              Reporter tester = new Reporter(project)) {
            change.accept(project);
            tester.check();
            String expected =
                MessageFormat.format(
                    Environment.getI18n().get(ShapeIntegrityTester.class, key, fallback),
                    arguments);
            assertEquals(List.of(expected), tester.errors);
          }
        });
  }

  @Test
  void validNetworkRemainsUnchangedAndIdentifiersHaveSeparateNamespaces() throws Exception {
    try (NetworkTestProject project = project();
        Reporter tester = new Reporter(project)) {
      project.links.getModel().setValueAt(1, 0, 0);
      final List<Object> node = new ArrayList<>(project.nodes.getModel().getRecord(0));
      final List<Object> link = new ArrayList<>(project.links.getModel().getRecord(0));
      final Object graphic = project.links.getEsriGraphicList().getOMGraphicAt(0);
      tester.check();
      assertTrue(tester.errors.isEmpty());
      assertEquals(node, project.nodes.getModel().getRecord(0));
      assertEquals(link, project.links.getModel().getRecord(0));
      assertEquals(2, project.nodes.getEsriGraphicList().size());
      assertEquals(1, project.links.getEsriGraphicList().size());
      assertSame(graphic, project.links.getEsriGraphicList().getOMGraphicAt(0));
    }
  }

  @Test
  void maximumIntegerIdentifierIsValid() throws Exception {
    try (NetworkTestProject project = project();
        Reporter tester = new Reporter(project)) {
      project.nodes.getModel().setValueAt(Integer.MAX_VALUE, 0, 0);
      project.links.getModel().setValueAt(Integer.MAX_VALUE, 0, NodusC.DBF_IDX_NODE1);
      project.links.getModel().setValueAt(Integer.MAX_VALUE, 0, 0);
      tester.check();
      assertTrue(tester.errors.isEmpty());
    }
  }

  @Test
  void waitsForAllLayersAndExternalObjectsBeforeChecking() throws Exception {
    try (NetworkTestProject project = project();
        Reporter tester = new Reporter(project)) {
      project.nodes.getModel().setValueAt(0, 0, 0);
      project.nodes.ready = false;
      tester.check();
      assertTrue(tester.errors.isEmpty());
      project.nodes.ready = true;
      project.otherObjectsLoaded = false;
      tester.check();
      assertTrue(tester.errors.isEmpty());
      project.otherObjectsLoaded = true;
      tester.check();
      assertEquals(1, tester.errors.size());
    }
  }

  @Test
  void stoppingSuppressesAlreadyQueuedErrorPresentation() throws Exception {
    try (NetworkTestProject project = project();
        Reporter tester = new Reporter(project)) {
      project.nodes.getModel().setValueAt(0, 0, 0);
      SwingUtilities.invokeAndWait(
          () -> {
            tester.runIntegrityTest();
            tester.stop();
          });
      SwingUtilities.invokeAndWait(() -> {});
      assertTrue(tester.errors.isEmpty());
      tester.check();
      assertTrue(tester.errors.isEmpty());
    }
  }

  @TestFactory
  Stream<DynamicTest> identifiersMustBeUniqueAcrossLayers() {
    return Stream.of(false, true)
        .map(
            links ->
                DynamicTest.dynamicTest(
                    links ? "link layers" : "node layers",
                    () -> {
                      try (NetworkTestProject project = project()) {
                        NodusProject view =
                            new NodusProject(null) {
                              @Override
                              public NodusEsriLayer[] getNodeLayers() {
                                return links
                                    ? project.getNodeLayers()
                                    : new NodusEsriLayer[] {project.nodes, project.nodes};
                              }

                              @Override
                              public NodusEsriLayer[] getLinkLayers() {
                                return links
                                    ? new NodusEsriLayer[] {project.links, project.links}
                                    : project.getLinkLayers();
                              }

                              @Override
                              public boolean isOtherObjectsLoaded() {
                                return true;
                              }
                            };
                        try (Reporter tester = new Reporter(view)) {
                          tester.check();
                          String key = links ? "Link" : "Node";
                          String expected =
                              MessageFormat.format(
                                  Environment.getI18n()
                                      .get(ShapeIntegrityTester.class, key, "duplicate"),
                                  links ? "links" : "nodes",
                                  links ? 11 : 1,
                                  links ? "links" : "nodes");
                          assertEquals(List.of(expected), tester.errors);
                        }
                      }
                    }));
  }

  private NetworkTestProject project() throws Exception {
    return new NetworkTestProject(directory, new double[] {0, 0, 0, 10});
  }

  private static final class Reporter extends ShapeIntegrityTester implements AutoCloseable {
    final List<String> errors = new ArrayList<>();

    Reporter(NodusProject project) {
      super(project, false);
    }

    void check() throws Exception {
      runIntegrityTest();
      SwingUtilities.invokeAndWait(() -> {});
    }

    @Override
    protected void reportError(String message) {
      assertTrue(SwingUtilities.isEventDispatchThread());
      errors.add(message);
    }

    @Override
    public void close() {
      stop();
    }
  }
}
