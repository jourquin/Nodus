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

package edu.uclouvain.core.nodus.compute.costs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.bbn.openmap.dataAccess.shape.DbfTableModel;
import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Evaluates production formulas against small, explicit layer records; no database or GUI. */
class CostParserTest {
  @Test
  void consecutiveNodesOfTheSameTypeUseTheirOwnRecord() {
    Fixture fixture = new Fixture();
    fixture.costs.setProperty("ld.1,1", "TOLL + TRANSHIP");
    CostParser parser = fixture.parser();
    assertEquals(9, parser.compute(fixture.handling(0, 0), false), 1e-12);
    assertEquals(22, parser.compute(fixture.handling(0, 1), false), 1e-12);
    assertEquals(9, parser.compute(fixture.handling(0, 0), false), 1e-12);
  }

  @Test
  void firstNodeEvaluationActivatesItsLayerVariable() {
    Fixture fixture = new Fixture();
    fixture.costs.setProperty("ld.1,1", "10 * nodes + 20 * depots");
    assertEquals(10, fixture.parser().compute(fixture.handling(0, 0), false), 1e-12);
  }

  @Test
  void changingLayersRefreshesAttributesAndOnlyActivatesTheCurrentLayer() {
    Fixture fixture = new Fixture();
    fixture.costs.setProperty("ld.1,1", "TOLL + 100 * nodes + 200 * depots");
    fixture.costs.setProperty("mv.1,1", "TOLL + 100 * roads + 200 * rails");
    CostParser parser = fixture.parser();
    assertEquals(107, parser.compute(fixture.handling(0, 0), false), 1e-12);
    assertEquals(231, parser.compute(fixture.handling(1, 0), false), 1e-12);
    assertEquals(105, parser.compute(fixture.forward, false), 1e-12);
    assertEquals(217, parser.compute(fixture.otherMove, false), 1e-12);
    assertEquals(107, parser.compute(fixture.handling(0, 0), false), 1e-12);
  }

  @Test
  void movementAndHandlingVariablesAreReboundAndUnavailableVariablesAreRejected() {
    Fixture fixture = new Fixture();
    fixture.costs.setProperty("mv.1,1", "LENGTH + TOLL");
    fixture.costs.setProperty("ld.1,1", "TOLL + TRANSHIP");
    CostParser parser = fixture.parser();
    assertEquals(17, parser.compute(fixture.forward, false), 1e-12);
    assertEquals(9, parser.compute(fixture.handling(0, 0), false), 1e-12);
    assertEquals(17, parser.compute(fixture.forward, false), 1e-12);
    fixture.costs.setProperty("ld.1,1", "LENGTH");
    assertEquals(CostParser.PARSER_ERROR, parser.compute(fixture.handling(0, 0), false));
    fixture.costs.setProperty("mv.1,1", "TRANSHIP");
    assertEquals(CostParser.PARSER_ERROR, parser.compute(fixture.forward, false));
  }

  @Test
  void directionalVolumesAndUpdatedVolumesAreReadOnEveryEvaluation() {
    Fixture fixture = new Fixture();
    fixture.costs.setProperty("mv.1,1", "VOLUME + 100 * UPSTREAM");
    fixture.real.addPassengerCarUnits(fixture.forward, 7);
    fixture.real.addPassengerCarUnits(fixture.reverse, 23);
    CostParser parser = fixture.parser();
    assertEquals(107, parser.compute(fixture.forward, false), 1e-12);
    assertEquals(23, parser.compute(fixture.reverse, false), 1e-12);
    fixture.real.addPassengerCarUnits(fixture.reverse, 5);
    assertEquals(28, parser.compute(fixture.reverse, false), 1e-12);
    assertEquals(107, parser.compute(fixture.forward, false), 1e-12);
  }

  @Test
  void costAndDurationFunctionsRemainIndependent() {
    Fixture fixture = new Fixture();
    fixture.costs.setProperty("mv.1,1", "TOLL");
    fixture.costs.setProperty("mv@1,1", "MINUTES(LENGTH, SPEED)");
    CostParser parser = fixture.parser();
    assertEquals(5, parser.compute(fixture.forward, false), 1e-12);
    assertEquals(14.4, parser.compute(fixture.forward, true), 1e-12);
    assertEquals(5, parser.compute(fixture.forward, false), 1e-12);
    fixture.costs.remove("mv@1,1");
    assertEquals(CostParser.UNDEFINED_FUNCTION, parser.compute(fixture.forward, true));
    fixture.costs.setProperty("mv.1,1", "null");
    assertEquals(CostParser.UNDEFINED_FUNCTION, parser.compute(fixture.forward, false));
  }

  @TestFactory
  List<DynamicTest> handlingOperationsSelectTheirOwnCostAndDurationFunctions() {
    byte[] types = {
      VirtualLink.TYPE_LOAD,
      VirtualLink.TYPE_UNLOAD,
      VirtualLink.TYPE_TRANSIT,
      VirtualLink.TYPE_TRANSHIP,
      VirtualLink.TYPE_STOP,
      VirtualLink.TYPE_SWITCH
    };
    String[] keys = {"ld.3,4", "ul.1,2", "tr.1,2", "tp.1,2-3,4", "stp.1,2", "sw.1,2-3,4"};
    List<DynamicTest> tests = new ArrayList<>();
    for (int i = 0; i < types.length; i++) {
      byte type = types[i];
      String key = keys[i];
      tests.add(
          dynamicTest(
              key,
              () -> {
                Fixture fixture = new Fixture();
                fixture.costs.setProperty(key, "TOLL + 10");
                fixture.costs.setProperty(key.replace('.', '@'), "TOLL + 20");
                VirtualNode from = new VirtualNode(1, 1, 11, (byte) 1, (byte) 2, (short) 0, 0, 0);
                VirtualNode to = new VirtualNode(2, 1, 12, (byte) 3, (byte) 4, (short) 0, 0, 0);
                VirtualLink link = new VirtualLink(1, 0, 0, from, to, type);
                CostParser parser = fixture.parser();
                assertEquals(17, parser.compute(link, false), 1e-12);
                assertEquals(27, parser.compute(link, true), 1e-12);
              }));
    }
    return tests;
  }

  @Test
  void timeSliceOverridesFallBackWithoutLeakingBetweenParsers() {
    Fixture fixture = new Fixture();
    fixture.costs.setProperty("mv.1,1", "5");
    fixture.costs.setProperty("t200.mv.1,1", "17");
    CostParser timed = new CostParser(fixture.costs, fixture, 1, (byte) 1, (byte) 0, 200);
    CostParser untimed = fixture.parser();
    CostParser otherSlice = new CostParser(fixture.costs, fixture, 1, (byte) 1, (byte) 0, 201);
    assertEquals(17, timed.compute(fixture.forward, false));
    assertEquals(5, untimed.compute(fixture.forward, false));
    assertEquals(5, otherSlice.compute(fixture.forward, false));
    assertEquals(17, timed.compute(fixture.forward, false));
  }

  @TestFactory
  List<DynamicTest> customFunctionsMatchIndependentReferenceValues() {
    String[] formulas = {
      "BPR(0,100,4,0.15)", "BPR(100,100,4,0.15)", "BPR(200,100,4,0.15)",
      "CONICAL(0,100,2)", "CONICAL(50,100,2)", "CONICAL(100,100,2)",
      "CONICAL(200,100,2)", "HOURS(12.5,50)", "MINUTES(12.5,50)",
      "SECONDS(12.5,50)", "SECONDS(0,50)", "AVG(1,3,8)"
    };
    double[] expected = {1, 1.15, 3.4, 1, 1.3027756377319946, 2, 5, 0.25, 15, 900, 0, 4};
    List<DynamicTest> tests = new ArrayList<>();
    for (int i = 0; i < formulas.length; i++) {
      String formula = formulas[i];
      double value = expected[i];
      tests.add(
          dynamicTest(
              formula,
              () -> {
                Fixture fixture = new Fixture();
                fixture.costs.setProperty("mv.1,1", formula);
                assertEquals(value, fixture.parser().compute(fixture.forward, false), 1e-12);
              }));
    }
    return tests;
  }

  @TestFactory
  List<DynamicTest> invalidFunctionResultsReportAnError() {
    List<DynamicTest> tests = new ArrayList<>();
    for (String formula :
        new String[] {
          "BPR(1,0,4,0.15)",
          "BPR(0,0,4,0.15)",
          "CONICAL(1,0,2)",
          "CONICAL(1,100,1)",
          "HOURS(1,0)",
          "MINUTES(1,0)",
          "SECONDS(0,0)",
          "sqrt(-1)",
          "1e200 * 1e200",
          "-1",
          "missing + 1",
          "1 + ("
        }) {
      tests.add(
          dynamicTest(
              formula,
              () -> {
                Fixture fixture = new Fixture();
                fixture.costs.setProperty("mv.1,1", formula);
                CostParser parser = fixture.parser();
                assertEquals(CostParser.PARSER_ERROR, parser.compute(fixture.forward, false));
                assertNotNull(parser.getErrorMessage());
                assertTrue(parser.getErrorMessage().contains(formula));
              }));
    }
    return tests;
  }

  private static VirtualNode node(int id, int realNode, int realLink) {
    return new VirtualNode(id, realNode, realLink, (byte) 1, (byte) 1, (short) 0, 0, 0);
  }

  private static final class Fixture extends NodusProject {
    final Properties costs = new Properties();
    final Layer[] nodes = {
      new Layer(
          "nodes",
          new String[] {"NUM", "STYLE", "TRANSHIP", "TOLL"},
          new double[][] {{1, 0, 2, 7}, {2, 0, 3, 19}}),
      new Layer(
          "depots",
          new String[] {"NUM", "STYLE", "TRANSHIP", "TOLL"},
          new double[][] {{3, 0, 4, 31}})
    };
    final Layer[] links = {
      new Layer(
          "roads",
          new String[] {"NUM", "CAPACITY", "SPEED", "TOLL"},
          new double[][] {{11, 100, 50, 5}}),
      new Layer(
          "rails",
          new String[] {"NUM", "CAPACITY", "SPEED", "TOLL"},
          new double[][] {{12, 200, 80, 17}})
    };
    final RealLink real = new RealLink();
    final VirtualNode first = node(1, 1, 11);
    final VirtualNode second = node(2, 2, 11);
    final VirtualLink forward = new VirtualLink(1, 0, 0, first, second, real);
    final VirtualLink reverse = new VirtualLink(2, 0, 0, second, first, real);
    final VirtualLink otherMove;

    Fixture() {
      super(null);
      real.setLength(12);
      real.setSpeed(50);
      real.setOriginNodeId(1);
      RealLink other = new RealLink();
      other.setLength(20);
      other.setSpeed(80);
      other.setOriginNodeId(3);
      otherMove = new VirtualLink(3, 1, 0, node(3, 3, 12), node(4, 4, 12), other);
      links[0].addLink(real);
      links[1].addLink(other);
    }

    CostParser parser() {
      CostParser parser = new CostParser(costs, this, 1, (byte) 1, (byte) 0, -1);
      assertTrue(parser.isInitialized(), parser.getErrorMessage());
      return parser;
    }

    VirtualLink handling(int layer, int row) {
      int realNode = layer == 0 ? row + 1 : 3;
      return new VirtualLink(
          10 + realNode,
          layer,
          row,
          node(10, realNode, 0),
          node(11, realNode, 11),
          VirtualLink.TYPE_LOAD);
    }

    @Override
    public NodusEsriLayer[] getNodeLayers() {
      return nodes;
    }

    @Override
    public NodusEsriLayer[] getLinkLayers() {
      return links;
    }
  }

  private static final class Layer extends NodusEsriLayer {
    private static final long serialVersionUID = 1L;
    final DbfTableModel model;
    final EsriPolylineList graphics = new EsriPolylineList();
    final String variable;

    Layer(String variable, String[] columns, double[][] rows) {
      this.variable = variable;
      model = new DbfTableModel(columns.length);
      for (int i = 0; i < columns.length; i++) {
        model.setColumnName(i, columns[i]);
        model.setType(i, DbfTableModel.TYPE_NUMERIC);
      }
      for (double[] values : rows) {
        List<Object> row = new ArrayList<>();
        for (double value : values) {
          row.add(value);
        }
        model.addRecord(row);
      }
    }

    void addLink(RealLink real) {
      EsriPolyline line =
          new EsriPolyline(
              new double[] {0, 0, 0, 1}, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
      line.putAttribute(0, real);
      graphics.add(line);
    }

    @Override
    public DbfTableModel getModel() {
      return model;
    }

    @Override
    public EsriGraphicList getEsriGraphicList() {
      return graphics;
    }

    @Override
    public String getLayerVariableName() {
      return variable;
    }
  }
}
