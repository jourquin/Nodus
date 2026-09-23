package edu.uclouvain.core.nodus.compute.virtual;

import com.bbn.openmap.dataAccess.shape.EsriGraphicList;
import com.bbn.openmap.dataAccess.shape.EsriPolyline;
import com.bbn.openmap.dataAccess.shape.EsriPolylineList;
import com.bbn.openmap.layer.shape.NodusEsriLayer;
import com.bbn.openmap.omGraphics.OMGraphic;
import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes;
import edu.uclouvain.core.nodus.compute.costs.VehiclesParser;
import edu.uclouvain.core.nodus.compute.real.RealLink;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/** Checks vehicle characteristics and conversion against the previous group-first calculation. */
public final class VehiclesConversionTest {
  private static final byte[] GROUPS = {0, 3, 19, 99};
  private static final int SCENARIO = 26;
  private static final int SLICES = 3;

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void equal(double expected, double actual) {
    check(
        Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual),
        "Expected " + expected + ", got " + actual);
  }

  private static Object field(Object object, String name) throws Exception {
    Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  private static void set(Object object, String name, Object value) throws Exception {
    Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }

  /** Resolves overrides independently, using the documented precedence and default. */
  private static double characteristic(
      Properties properties, String name, int group, int mode, int means) {
    if (mode < 0 || mode >= NodusC.MAXMM || means < 0 || means >= NodusC.MAXMM) return 1;
    String core = name + "." + mode + "," + means;
    for (String key :
        new String[] {
          SCENARIO + "." + core + "." + group, SCENARIO + "." + core, core + "." + group, core
        }) {
      String value = properties.getProperty(key);
      if (value != null) return Double.parseDouble(value);
    }
    return 1;
  }

  private static Properties properties() {
    Properties defaults = new Properties();
    defaults.setProperty(NodusC.VARNAME_AVERAGELOAD + ".99,99", "17.25");
    Properties properties = new Properties(defaults);
    Random random = new Random(719);
    for (String name : new String[] {NodusC.VARNAME_AVERAGELOAD, NodusC.VARNAME_PCU}) {
      for (int mode = 0; mode < NodusC.MAXMM; mode++) {
        for (int means = 0; means < NodusC.MAXMM; means++) {
          String core = name + "." + mode + "," + means;
          if (random.nextBoolean()) properties.setProperty(core, "2.25");
          for (byte group : GROUPS) {
            if (random.nextBoolean()) properties.setProperty(core + "." + group, "3.5");
            if (random.nextBoolean()) properties.setProperty(SCENARIO + "." + core, "7.75");
            if (random.nextBoolean()) {
              properties.setProperty(SCENARIO + "." + core + "." + group, "11.125");
            }
          }
        }
      }
    }
    // Keep the existing division-by-zero, signed-zero and negative-characteristic behavior.
    properties.setProperty(SCENARIO + "." + NodusC.VARNAME_AVERAGELOAD + ".1,1.0", "0");
    properties.setProperty(SCENARIO + "." + NodusC.VARNAME_AVERAGELOAD + ".1,1.3", "-0.0");
    properties.setProperty(SCENARIO + "." + NodusC.VARNAME_PCU + ".1,1.19", "-2");
    return properties;
  }

  private static VehiclesParser parser(Properties properties) {
    VehiclesParser parser = new VehiclesParser(SCENARIO);
    for (byte group : GROUPS) {
      check(parser.loadVehicleCharacteristics(properties, group), "Failed to load characteristics");
      // Already loaded groups must retain their values if a caller supplies different properties.
      check(parser.loadVehicleCharacteristics(new Properties(), group), "Cache was not retained");
      for (int mode = -1; mode <= NodusC.MAXMM; mode++) {
        for (int means = -1; means <= NodusC.MAXMM; means++) {
          equal(
              characteristic(properties, NodusC.VARNAME_AVERAGELOAD, group, mode, means),
              parser.getAverageLoad(group, mode, means));
          equal(
              characteristic(properties, NodusC.VARNAME_PCU, group, mode, means),
              parser.getPassengerCarUnits(group, mode, means));
        }
      }
    }
    return parser;
  }

  private static final class Layer extends NodusEsriLayer {
    final EsriGraphicList graphics = new EsriPolylineList();

    @Override
    public EsriGraphicList getEsriGraphicList() {
      return graphics;
    }
  }

  private static final class Fixture {
    final List<VirtualLink> links = new ArrayList<>();
    final RealLink real = new RealLink();
    final VirtualNetwork network;

    Fixture() throws Exception {
      // Only the GUI/project constructor is bypassed; conversion uses the production methods.
      Class<?> allocator = Class.forName("sun.misc.Unsafe");
      Field unsafe = allocator.getDeclaredField("theUnsafe");
      unsafe.setAccessible(true);
      network =
          (VirtualNetwork)
              allocator
                  .getMethod("allocateInstance", Class.class)
                  .invoke(unsafe.get(null), VirtualNetwork.class);
      real.setOriginNodeId(1);
      Layer layer = new Layer();
      EsriPolyline graphic =
          new EsriPolyline(
              new double[] {0, 0, 0, 1}, OMGraphic.DECIMAL_DEGREES, OMGraphic.LINETYPE_STRAIGHT);
      graphic.putAttribute(0, real);
      layer.graphics.add(graphic);
      VirtualNodeList[] lists = new VirtualNodeList[12];
      Random random = new Random(331);
      for (int i = 0; i < lists.length; i++) {
        lists[i] = new VirtualNodeList(i + 1, NodusC.HANDLING_NONE, null);
        // Include shared real links, both directions, loading transitions and boundary modes.
        VirtualNode from =
            new VirtualNode(
                2 * i + 1, i % 2 + 1, 1, (byte) (i % 3 == 0 ? 99 : 1), (byte) 1, (short) 0, 0, 0);
        VirtualNode to =
            new VirtualNode(
                2 * i + 2, 2 - i % 2, 1, (byte) 2, (byte) (i % 3 == 0 ? 99 : 2), (short) 0, 0, 0);
        lists[i].addVirtualNode(from);
        for (byte type = VirtualLink.TYPE_MOVE; type <= VirtualLink.TYPE_STOP; type++) {
          VirtualLink link =
              type == VirtualLink.TYPE_MOVE
                  ? new VirtualLink(links.size(), 0, 0, from, to, real)
                  : new VirtualLink(links.size(), 0, 0, from, to, type);
          link.setNbGroups(GROUPS.length, SLICES);
          for (byte group = 0; group < GROUPS.length; group++) {
            link.addAuxiliaryVolume(group, random.nextDouble() * 1000);
            for (int slice = 0; slice < SLICES; slice++) {
              double volume = slice == 0 ? 0 : (random.nextDouble() - .1) * 10000;
              link.addVolume(group, slice, volume);
            }
          }
          from.add(link);
          links.add(link);
        }
      }
      set(network, "groups", GROUPS);
      set(network, "vnl", lists);
      set(network, "linksEsriLayer", new NodusEsriLayer[] {layer});
      set(network, "computingTimes", new AssignmentComputingTimes());
    }

    /** Replays the old group-first traversal, with independent property resolution. */
    void original(Properties properties, int slice, Double lambda) {
      real.resetPassengerCarUnits();
      for (byte index = 0; index < GROUPS.length; index++) {
        for (VirtualLink link : links) {
          byte type = link.getType();
          if (lambda != null ? type != VirtualLink.TYPE_MOVE : type > VirtualLink.TYPE_UNLOAD) {
            continue;
          }
          VirtualNode node =
              type == VirtualLink.TYPE_LOAD ? link.getEndVirtualNode() : link.getBeginVirtualNode();
          double load =
              characteristic(
                  properties,
                  NodusC.VARNAME_AVERAGELOAD,
                  GROUPS[index],
                  node.getMode(),
                  node.getMeans());
          double pcu =
              characteristic(
                  properties, NodusC.VARNAME_PCU, GROUPS[index], node.getMode(), node.getMeans());
          if (lambda == null) link.volumesToVehicles(index, slice, load, pcu);
          else link.projectedVolumesToVehicles(index, slice, load, pcu, lambda);
        }
      }
    }

    void compare(Fixture other) throws Exception {
      for (int i = 0; i < links.size(); i++) {
        VirtualLink expected = links.get(i);
        VirtualLink actual = other.links.get(i);
        for (byte group = 0; group < GROUPS.length; group++) {
          for (int slice = 0; slice < SLICES; slice++) {
            equal(
                expected.getCurrentVehicles(group, slice), actual.getCurrentVehicles(group, slice));
            equal(expected.getCurrentVolume(group, slice), actual.getCurrentVolume(group, slice));
          }
        }
        check(
            Arrays.equals(
                (int[]) field(expected, "auxiliaryVehicles"),
                (int[]) field(actual, "auxiliaryVehicles")),
            "Auxiliary vehicle counts changed");
        equal(
            real.getCurrentPassengerCarUnits(expected),
            other.real.getCurrentPassengerCarUnits(actual));
        equal(
            real.getAuxiliaryPassengerCarUnits(expected),
            other.real.getAuxiliaryPassengerCarUnits(actual));
      }
    }
  }

  /** Runs without project files, databases or a GUI. */
  public static void main(String[] args) throws Exception {
    Properties properties = properties();
    VehiclesParser parser = parser(properties);
    parser(new Properties()); // Unspecified characteristics default to one for every combination.
    Fixture expected = new Fixture();
    Fixture actual = new Fixture();
    for (int repeat = 0; repeat < 2; repeat++) {
      for (int slice = SLICES - 1; slice >= 0; slice--) {
        expected.original(properties, slice, null);
        check(actual.network.volumesToVehicles(parser, slice), "Conversion failed");
        expected.compare(actual);
        for (double lambda : new double[] {0, .125, .5, 1}) {
          expected.original(properties, slice, lambda);
          check(
              actual.network.projectedVolumesToVehicles(parser, slice, lambda),
              "Projection failed");
          expected.compare(actual);
        }
      }
    }
    System.out.println("Vehicle characteristic and conversion checks passed.");
  }
}
