package edu.uclouvain.core.nodus.compute.costs;

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
import java.util.Arrays;
import java.util.Properties;
import parsii.eval.Parser;
import parsii.eval.Scope;
import parsii.eval.Variable;
import parsii.tokenizer.ParseException;

/** Regression checks using in-memory layers, with no project files or database changes. */
public final class CostParserCacheTest {
  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void equal(double expected, double actual) {
    check(Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual),
        "Expected " + expected + ", got " + actual);
  }

  private static void equivalent(CostExpressionCache cache, Scope scope, String formula)
      throws ParseException {
    equal(Parser.parse(formula, scope).evaluate(), cache.evaluate(formula, scope));
  }

  private static String error(CostExpressionCache cache, Scope scope, String formula) {
    try {
      cache.evaluate(formula, scope);
      throw new AssertionError("Expected a parse error for " + formula);
    } catch (ParseException expected) {
      return expected.toString();
    }
  }

  private static final class CountingScope extends Scope {
    int resolutions;

    @Override
    public Variable getVariable(String name) {
      resolutions++;
      return super.getVariable(name);
    }
  }

  private static void checkCache() throws Exception {
    CountingScope scope = new CountingScope();
    scope.withStrictLookup(true);
    CostExpressionCache cache = new CostExpressionCache();
    scope.create("LENGTH").setValue(10);
    scope.create("SPEED").setValue(50);
    equal(0.2, cache.evaluate("LENGTH/SPEED", scope));
    int parsedResolutions = scope.resolutions;
    for (int i = 1; i <= 1000; i++) {
      scope.create("LENGTH").setValue(i);
      equal(i / 50.0, cache.evaluate("LENGTH/SPEED", scope));
      scope.remove("LENGTH");
      scope.create("LENGTH").setValue(i + 1);
      equal((i + 1) / 50.0, cache.evaluate("LENGTH/SPEED", scope));
    }
    check(scope.resolutions == parsedResolutions, "Formula was reparsed after variable updates");
    scope.remove("LENGTH");
    String cachedError = error(cache, scope, "LENGTH/SPEED");
    try {
      Parser.parse("LENGTH/SPEED", scope);
      throw new AssertionError("Expected uncached parse failure");
    } catch (ParseException expected) {
      check(cachedError.equals(expected.toString()), "Cached error differs from Parsii's error");
    }
    scope.create("LENGTH").setValue(100);
    equal(2, cache.evaluate("LENGTH/SPEED", scope));

    // A dependency must remain valid even when simplification removes it from the expression.
    equivalent(cache, scope, "if(0,LENGTH,1)");
    scope.remove("LENGTH");
    error(cache, scope, "if(0,LENGTH,1)");
    scope.create("LENGTH").setValue(10);
    equivalent(cache, scope, "pi*LENGTH+euler");
    scope.create("pi").setValue(3); // Shadow a folded parent constant with a mutable variable.
    equivalent(cache, scope, "pi*LENGTH+euler");
    scope.create("pi").setValue(4);
    equivalent(cache, scope, "pi*LENGTH+euler");
    scope.remove("pi");
    equivalent(cache, scope, "pi*LENGTH+euler");
    scope.create("RATE").makeConstant(2);
    equivalent(cache, scope, "RATE*LENGTH");
    scope.remove("RATE");
    scope.create("RATE").makeConstant(3);
    equivalent(cache, scope, "RATE*LENGTH");
    scope.create("SPEED").setValue(0);
    equivalent(cache, scope, "LENGTH/SPEED");
    error(cache, scope, "LENGTH+");
    error(cache, scope, "UNKNOWN+1");

    Scope other = new Scope().withStrictLookup(true);
    other.create("LENGTH").setValue(42);
    other.create("SPEED").setValue(2);
    equal(21, new CostExpressionCache().evaluate("LENGTH/SPEED", other));
  }

  static final class Layer extends NodusEsriLayer {
    final String variable;
    final DbfTableModel model;
    final EsriGraphicList graphics = new EsriPolylineList();

    Layer(String variable, String[] fields, Double... values) {
      this.variable = variable;
      model = new DbfTableModel(fields.length);
      for (int i = 0; i < fields.length; i++) {
        model.setColumnName(i, fields[i]);
        model.setType(i, DbfTableModel.TYPE_NUMERIC);
      }
      model.addRecord(Arrays.asList((Object[]) values));
    }

    @Override
    public String getLayerVariableName() {
      return variable;
    }

    @Override
    public DbfTableModel getModel() {
      return model;
    }

    @Override
    public EsriGraphicList getEsriGraphicList() {
      return graphics;
    }
  }

  static final class Fixture extends NodusProject {
    final Layer node = new Layer("NODE", new String[] {"NUM", "STYLE", "TRANSHIP"}, 1.0, 0.0, 3.0);
    final Layer road = new Layer("ROAD", new String[] {"SPEED", "CAPACITY"}, 40.0, 1000.0);
    final Layer rail = new Layer("RAIL", new String[] {"SPEED", "CAPACITY"}, 60.0, 2000.0);
    final RealLink firstReal = realLink(road, 10);
    final RealLink secondReal = realLink(rail, 30);
    final VirtualNode first = new VirtualNode(1, -1, 10, (byte) 1, (byte) 1, (short) 0, 0, 0);
    final VirtualNode last = new VirtualNode(2, 2, 10, (byte) 1, (byte) 1, (short) 0, 0, 1);
    final VirtualLink forward = new VirtualLink(1, 0, 0, first, last, firstReal);
    final VirtualLink reverse = new VirtualLink(2, 0, 0, last, first, firstReal);
    final VirtualLink second = new VirtualLink(3, 1, 0,
        new VirtualNode(3, -1, 20, (byte) 1, (byte) 1, (short) 0, 0, 0),
        new VirtualNode(4, 2, 20, (byte) 1, (byte) 1, (short) 0, 0, 1), secondReal);
    final VirtualLink transit = new VirtualLink(4, 0, 0, first, last, VirtualLink.TYPE_TRANSIT);

    Fixture() {
      super(null);
      firstReal.addPassengerCarUnits(forward, 100);
      firstReal.addPassengerCarUnits(reverse, 200);
      secondReal.addPassengerCarUnits(second, 300);
    }

    private static RealLink realLink(Layer layer, float length) {
      RealLink link = new RealLink();
      link.setLength(length);
      link.setOriginNodeId(1);
      EsriPolyline graphic = new EsriPolyline(new double[] {0, 0, 0, 1},
          OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
      graphic.putAttribute(0, link);
      layer.graphics.add(graphic);
      return link;
    }

    @Override
    public NodusEsriLayer[] getNodeLayers() {
      return new NodusEsriLayer[] {node};
    }

    @Override
    public NodusEsriLayer[] getLinkLayers() {
      return new NodusEsriLayer[] {road, rail};
    }
  }

  static Properties formulas() {
    Properties formulas = new Properties();
    formulas.setProperty("mv.1,1", "LENGTH+VOLUME+SPEED+UPSTREAM+ROAD+RAIL");
    formulas.setProperty("mv@1,1", "SECONDS(LENGTH,SPEED)");
    formulas.setProperty("tr.1,1", "TRANSHIP+NODE");
    return formulas;
  }

  private static CostParser parser(Properties formulas, Fixture fixture, int scenario, int time) {
    CostParser parser = new CostParser(formulas, fixture, scenario, (byte) 2, (byte) 3, time);
    check(parser.isInitialized(), "Parser initialization failed: " + parser.getErrorMessage());
    return parser;
  }

  private static void checkParser() throws Exception {
    Fixture fixture = new Fixture();
    Properties formulas = formulas();
    CostParser parser = parser(formulas, fixture, 1, -1);
    for (int i = 0; i < 20; i++) {
      equal(152, parser.compute(fixture.forward, false));
      equal(900, parser.compute(fixture.forward, true));
      equal(251, parser.compute(fixture.reverse, false));
      equal(4, parser.compute(fixture.transit, false));
      equal(CostParser.UNDEFINED_FUNCTION, parser.compute(fixture.transit, true));
      equal(392, parser.compute(fixture.second, false));
      equal(1800, parser.compute(fixture.second, true));
    }
    fixture.node.model.setValueAt(7.0, 0, 2);
    equal(8, parser.compute(fixture.transit, false));
    fixture.firstReal.setLength(20);
    equal(162, parser.compute(fixture.forward, false));
    equal(1800, parser.compute(fixture.forward, true));

    // Preserve the existing scenario/group/class and time-specific selection order.
    String[] keys = {"1.mv.1,1.2-3", "1.mv.1,1-3", "1.mv.1,1.2", "1.mv.1,1",
        "mv.1,1.2-3", "mv.1,1-3", "mv.1,1.2", "t5.mv.1,1", "mv.1,1"};
    for (int i = 0; i < keys.length; i++) {
      formulas.setProperty(keys[i], Integer.toString(i + 1));
    }
    parser = parser(formulas, fixture, 1, 5);
    for (int i = 0; i < keys.length; i++) {
      equal(i + 1, parser.compute(fixture.forward, false));
      equal(i + 1, parser.compute(fixture.reverse, false));
      formulas.remove(keys[i]);
    }
    equal(CostParser.UNDEFINED_FUNCTION, parser.compute(fixture.forward, false));
    formulas.setProperty("mv.1,1", "null");
    equal(CostParser.UNDEFINED_FUNCTION, parser.compute(fixture.forward, false));

    formulas = formulas();
    formulas.setProperty("mv.1,1", "HOURS(LENGTH,SPEED)*BPR(VOLUME,CAPACITY,4,0.15)");
    parser = parser(formulas, fixture, 1, -1);
    equal((20.0 / 40) * (1 + 0.15 * Math.pow(100.0 / 1000, 4)),
        parser.compute(fixture.forward, false));
    equal((20.0 / 40) * (1 + 0.15 * Math.pow(200.0 / 1000, 4)),
        parser.compute(fixture.reverse, false));
    formulas.setProperty("tr.1,1", "LENGTH/SPEED");
    formulas.setProperty("mv.1,1", "LENGTH/SPEED");
    equal(0.5, parser.compute(fixture.forward, false));
    equal(CostParser.PARSER_ERROR, parser.compute(fixture.transit, false));
    check(parser.getErrorMessage().contains("LENGTH"), "Missing unknown-variable error");
    equal(0.5, parser.compute(fixture.forward, false));
    formulas.setProperty("mv.1,1", "-LENGTH");
    equal(CostParser.PARSER_ERROR, parser.compute(fixture.forward, false));
    formulas.setProperty("mv.1,1", "LENGTH/0");
    equal(CostParser.PARSER_ERROR, parser.compute(fixture.forward, false));
    formulas.setProperty("mv.1,1", "HOURS(LENGTH,0)");
    equal(CostParser.PARSER_ERROR, parser.compute(fixture.forward, false));
  }

  public static void main(String[] args) throws Exception {
    checkCache();
    checkParser();
    System.out.println("Cost parser cache checks passed.");
  }
}
