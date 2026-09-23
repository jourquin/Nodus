package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.NodusProject;
import edu.uclouvain.core.nodus.compute.assign.Assignment;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes;
import edu.uclouvain.core.nodus.compute.assign.AssignmentParameters;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.ModalSplitMethod;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.PathsForMode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.ReachabilityDijkstra;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.PathWriter;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import edu.uclouvain.core.nodus.database.JDBCUtils;
import edu.uclouvain.core.nodus.utils.ModalSplitMethodsLoader;
import edu.uclouvain.core.nodus.utils.WorkQueue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Checks actual assignment stage instrumentation and identical results with auditing on and off.
 */
public final class AssignmentAuditWorkerTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class Project extends NodusProject {
    final Connection connection;

    Project(Connection connection) {
      super(null);
      this.connection = connection;
    }

    @Override
    public Connection getMainJDBCConnection() {
      return connection;
    }

    @Override
    public String getLocalProperty(String key) {
      return "mfaudit";
    }

    @Override
    public String getLocalProperty(String key, String fallback) {
      return "mfaudit";
    }

    @Override
    public int getLocalProperty(String key, int fallback) {
      return key.equals(NodusC.PROP_MAX_SQL_BATCH_SIZE) ? 3 : fallback;
    }
  }

  private static final class Parameters extends AssignmentParameters {
    final AssignmentComputingTimes audit;

    Parameters(Connection connection) throws Exception {
      super(new Project(connection));
      // Every read advances one millisecond so even tiny fixture stages have visible output.
      Constructor<AssignmentComputingTimes> constructor =
          AssignmentComputingTimes.class.getDeclaredConstructor(LongSupplier.class);
      constructor.setAccessible(true);
      AtomicLong clock = new AtomicLong();
      audit = constructor.newInstance((LongSupplier) () -> clock.addAndGet(1_000_000));
    }

    @Override
    public AssignmentComputingTimes getComputingTimes() {
      return audit;
    }
  }

  private static final class Network extends VirtualNetwork {
    VirtualNode[] nodes;
    VirtualNodeList[] origins;
    AdjacencyNode[] graph;
    List<VirtualLink> links;
    int generations;
    boolean changeCostsBetweenJobs;
    Runnable beforeGeneration;

    private Network() {
      super(null);
    }

    @Override
    public AdjacencyNode[] generateAdjacencyList(byte group) {
      generations++;
      if (beforeGeneration != null) beforeGeneration.run();
      if (changeCostsBetweenJobs) {
        // Simulate costs computed by the coordinator before another iteration/time slice.
        links.get(2).setCost(group, generations == 2 ? -1 : 1);
        links.get(4).setCost(group, generations == 2 ? 3 : 1.1);
      }
      // Like VirtualNetwork, each job receives a fresh graph omitting excluded links.
      graph = new AdjacencyNode[nodes.length];
      for (int node = 1; node < nodes.length; node++) {
        AdjacencyNode tail = new AdjacencyNode(nodes[node]);
        graph[node] = tail;
        for (VirtualLink link : nodes[node].getVirtualLinkList()) {
          if (link.getCost(group) < 0) continue;
          tail.setNext(new AdjacencyNode(link.getEndVirtualNode()), group, link);
          tail = tail.nextNode;
        }
      }
      return graph;
    }

    @Override
    public VirtualNodeList[] getVirtualNodeLists() {
      return origins;
    }

    @Override
    public byte[] getGroups() {
      return new byte[] {1};
    }

    @Override
    public byte getNbODClasses() {
      return 1;
    }

    @Override
    public int getNodeIndexInVirtualNodeList(int id, boolean loading) {
      return id - 1;
    }

    @Override
    public int[] getAvailableModeMeans(byte group) {
      return new int[] {NodusC.MAXMM + 1};
    }

    void edge(int from, int to, byte type, double cost) {
      VirtualLink link =
          new VirtualLink(links.size() + 1, 0, 0, nodes[from], nodes[to], type) {
            @Override
            public double getLength() {
              return 1.25;
            }

            @Override
            public double getSpeed() {
              return 60;
            }

            @Override
            public double getDefaultDuration() {
              return 3.5;
            }
          };
      link.setNbGroups(1, 3);
      link.setCost((byte) 0, cost);
      link.setDuration((byte) 0, .25);
      links.add(link);
      nodes[from].add(link);
      AdjacencyNode tail = graph[from];
      while (tail.nextNode != null) tail = tail.nextNode;
      tail.setNext(new AdjacencyNode(nodes[to]), (byte) 0, link);
    }
  }

  private static Network network() throws Exception {
    // Bypass the network constructor's project/layer/GUI setup, as in other synthetic worker tests.
    Class<?> allocator = Class.forName("sun.misc.Unsafe");
    Field field = allocator.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Network n =
        (Network)
            allocator
                .getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), Network.class);
    n.nodes = new VirtualNode[8];
    n.graph = new AdjacencyNode[8];
    n.origins = new VirtualNodeList[7];
    n.links = new ArrayList<>();
    for (int i = 1; i <= 7; i++) {
      n.nodes[i] = new VirtualNode(i, i, 100 + i, (byte) 1, (byte) 1, (short) 0, 0, 0);
      n.graph[i] = new AdjacencyNode(n.nodes[i]);
      VirtualNodeList origin = new VirtualNodeList(i, NodusC.HANDLING_LOAD_UNLOAD, null);
      origin.addVirtualNode(n.nodes[i]);
      origin.setLoadingVirtualNodeNum(i);
      origin.setUnloadingVirtualNodeId(i);
      n.origins[i - 1] = origin;
    }
    n.edge(1, 2, VirtualLink.TYPE_LOAD, .1);
    n.edge(6, 2, VirtualLink.TYPE_LOAD, .2);
    n.edge(2, 3, VirtualLink.TYPE_MOVE, 1);
    n.edge(3, 4, VirtualLink.TYPE_UNLOAD, .1);
    n.edge(2, 5, VirtualLink.TYPE_MOVE, 1.1);
    n.edge(5, 4, VirtualLink.TYPE_UNLOAD, .1);
    for (int origin : new int[] {1, 6}) {
      n.origins[origin - 1].addDemand(new ODCell(1, origin, 4, 123.456789, 0));
      n.origins[origin - 1].addDemand(new ODCell(1, origin, 7, 2, 0)); // Unreachable destination.
    }
    return n;
  }

  private static final class Split extends ModalSplitMethod {
    final boolean fail;
    final List<String> shares = new ArrayList<>();

    Split(boolean fail) {
      super(null);
      this.fail = fail;
    }

    @Override
    public String getName() {
      return "audit-test";
    }

    @Override
    public String getPrettyName() {
      return getName();
    }

    @Override
    public boolean split(ODCell demand, List<PathsForMode> modes) {
      if (fail) return false;
      double total = 0;
      for (PathsForMode mode : modes)
        for (Path path : mode.pathList) total += 1 / path.weights.getCost();
      for (PathsForMode mode : modes)
        for (Path path : mode.pathList) {
          path.marketShare = (1 / path.weights.getCost()) / total;
          shares.add(
              demand.getOriginNodeId()
                  + ":"
                  + demand.getDestinationNodeId()
                  + ":"
                  + path.detailedPathKey
                  + ":"
                  + path.weights.getCost()
                  + ":"
                  + path.marketShare);
        }
      return true;
    }
  }

  private static final class Job extends Assignment {
    final PathWriter writer;
    final Network network;
    AssignmentWorker[] workers;

    Job(Parameters parameters, Network network) {
      super(parameters);
      this.network = network;
      writer = new PathWriter(parameters);
    }

    @Override
    public boolean assign() {
      throw new AssertionError("The fixture runs the real workers, not a project assignment");
    }

    @Override
    public PathWriter getPathWriter() {
      return writer;
    }

    @Override
    public VirtualNetwork getVNet() {
      return network;
    }

    @Override
    public AssignmentWorker[] getAssignmentWorkers() {
      return workers;
    }
  }

  /** Uses real workers, replacing only GUI progress updates. */
  private static AssignmentWorker worker(String algorithm, WorkQueue queue) {
    switch (algorithm) {
      case "AllOrNothing":
        return new AllOrNothingAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      case "Incremental":
        return new IncrementalAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      case "MSA":
        return new MSAAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      case "FrankWolfe":
        return new FrankWolfeAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      case "FastMF":
        return new FastMFAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      case "ExactMF":
        return new ExactMFAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      case "StaticAoNTimeDependent":
        return new StaticAoNTimeDependentAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      case "DynamicTimeDependent":
        return new DynamicTimeDependentAssignmentWorker(queue) {
          @Override
          boolean updateProgress(String message) {
            return true;
          }
        };
      default:
        throw new AssertionError("Unknown algorithm " + algorithm);
    }
  }

  private static List<String> table(Connection connection, String suffix) throws Exception {
    List<String> rows = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT * FROM " + JDBCUtils.getQuotedCompliantIdentifier("mfaudit0" + suffix))) {
      while (result.next()) {
        StringBuilder row = new StringBuilder(suffix);
        for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
          row.append('|').append(result.getString(column));
        }
        rows.add(row.toString());
      }
    }
    Collections.sort(rows);
    return rows;
  }

  private static void measured(String report, String label, boolean positive) {
    for (String line : report.split("\\R")) {
      if (line.stripLeading().startsWith(label + ":")) {
        double seconds =
            Double.parseDouble(line.substring(line.indexOf(':') + 1).trim().split(" +")[0]);
        check(positive ? seconds > 0 : seconds == 0, "Wrong stage timing: " + line);
        return;
      }
    }
    throw new AssertionError("Missing stage: " + label);
  }

  private static void count(String report, String label, int expected) {
    for (String line : report.split("\\R")) {
      if (line.stripLeading().startsWith(label + ":")) {
        check(
            Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()) == expected,
            "Wrong diagnostic count: " + line);
        return;
      }
    }
    throw new AssertionError("Missing diagnostic count: " + label);
  }

  private static List<String> run(
      Connection connection,
      String algorithm,
      boolean enabled,
      int outputMode,
      boolean fail,
      int routes)
      throws Exception {
    return run(connection, algorithm, enabled, outputMode, fail, routes, 1);
  }

  private static List<String> run(
      Connection connection,
      String algorithm,
      boolean enabled,
      int outputMode,
      boolean fail,
      int routes,
      int jobs)
      throws Exception {
    NodusC.displayComputingTimes = enabled;
    Parameters parameters = new Parameters(connection);
    parameters.setScenario(0);
    parameters.setSavePaths(outputMode > 0);
    parameters.setDetailedPaths(outputMode > 1);
    parameters.setDurationFunctions(true);
    parameters.setNbIterations(routes);
    parameters.setCostMarkup(.5);
    parameters.setModalSplitMethodName("audit-test");
    parameters.audit.startAssignment();
    Network network = network();
    Split split = new Split(fail);
    split.initialize(parameters);
    ModalSplitMethodsLoader.getAvailableModalSplitMethods().add(split);
    Job job = new Job(parameters, network);
    PathWriter writer = job.writer;
    WorkQueue queue = new WorkQueue();
    AssignmentWorker worker = worker(algorithm, queue);
    if (worker instanceof StaticAoNTimeDependentAssignmentWorker) {
      ((StaticAoNTimeDependentAssignmentWorker) worker).setTimeParameters(0, 120, 60);
    }
    if (worker instanceof DynamicTimeDependentAssignmentWorker) {
      ((DynamicTimeDependentAssignmentWorker) worker).setTimeParameters(0, 0, 60);
    }
    job.workers = new AssignmentWorker[] {worker};
    network.changeCostsBetweenJobs = jobs > 1;
    if (jobs > 1 && worker instanceof DynamicTimeDependentAssignmentWorker) {
      network.beforeGeneration =
          () ->
              ((DynamicTimeDependentAssignmentWorker) worker)
                  .setTimeParameters(network.generations - 1, network.generations - 1, 1);
    }
    for (int work = 1; work <= jobs; work++) {
      queue.addWork(new AssignmentWorkerParameters(job, (byte) 0, (byte) 0, work, .4));
    }
    queue.addWork(WorkQueue.NO_MORE_WORK);
    List<String> result = new ArrayList<>();
    try {
      ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
      PrintStream previousError = System.err;
      try (PrintStream capturedError = new PrintStream(diagnostics, true, "UTF-8")) {
        System.setErr(capturedError);
        worker.start();
        worker.join(10000);
      } finally {
        System.setErr(previousError);
      }
      String unexpected =
          diagnostics.toString("UTF-8").replace("Goal not reachable from source.", "").trim();
      check(unexpected.isEmpty(), "Unexpected worker failure: " + unexpected);
      check(!worker.isAlive(), "Worker did not finish: " + algorithm);
      Field searchField = worker.getClass().getSuperclass().getDeclaredField("shortestPath");
      searchField.setAccessible(true);
      check(
          (searchField.get(worker) instanceof ReachabilityDijkstra)
              == (algorithm.equals("FastMF") && enabled),
          "Observer must be used only for Fast MF when auditing is enabled");
      Field compactField = BinaryHeapDijkstra.class.getDeclaredField("compactGraph");
      compactField.setAccessible(true);
      check(
          (compactField.get(searchField.get(worker)) != null) == NodusC.useCompactShortestPaths,
          algorithm + " did not select the requested graph/heap implementation");
      check(network.generations == jobs, "Not all worker jobs generated a fresh graph");
      boolean success = !worker.isCancelled();
      check(success != fail, "Unexpected assignment outcome: " + algorithm);
      check(writer.close(), "Writer close failed");
      if (outputMode > 0) result.addAll(table(connection, NodusC.SUFFIX_HEADER));
      if (outputMode > 1) result.addAll(table(connection, NodusC.SUFFIX_DETAIL));
      double totalVolume = 0;
      for (VirtualLink link : network.links) {
        totalVolume += link.getCurrentVolume((byte) 0) + link.getAuxiliaryVolume((byte) 0);
        for (int slice = 0; slice < 3; slice++) {
          result.add(
              Long.toString(Double.doubleToLongBits(link.getCurrentVolume((byte) 0, slice))));
        }
        result.add(Long.toString(Double.doubleToLongBits(link.getAuxiliaryVolume((byte) 0))));
      }
      check(fail || totalVolume > 0, "Fixture assigned no volumes: " + algorithm);
      result.addAll(split.shares);
      // Dynamic searches may move demand to an intermediate origin between time slices.
      boolean relocated = false;
      for (VirtualNodeList origin : network.origins) {
        for (int row = 0; row < origin.getNbDemandLists(); row++) {
          List<ODCell> demands = origin.getDemandForGroup(row, 1, (byte) 0);
          if (demands == null) continue;
          for (ODCell cell : demands) {
            relocated |= origin.getRealNodeId() == 2;
            result.add(
                "demand:"
                    + origin.getRealNodeId()
                    + ":"
                    + origin.getLoadingVirtualNodeId(row)
                    + ":"
                    + cell.getDestinationNodeId()
                    + ":"
                    + cell.getRelocatedOriginNodeId()
                    + ":"
                    + cell.getRelocatedStartingTime());
          }
        }
      }
      if (jobs > 1 && algorithm.equals("DynamicTimeDependent")) {
        check(relocated, "Time-slice fixture never relocated demand");
        check(
            network.links.get(4).getCurrentVolume((byte) 0, 1) > 0,
            "Later time slice did not use the remaining route after a link was excluded");
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      PrintStream previous = System.out;
      try (PrintStream capture = new PrintStream(output, true, "UTF-8")) {
        System.setOut(capture);
        parameters.audit.finishAndPrint(algorithm + "Assignment", 0, 1, success);
      } finally {
        System.setOut(previous);
      }
      String report = output.toString("UTF-8");
      if (enabled) {
        if (algorithm.equals("FastMF")) {
          count(report, "Completed Dijkstra searches", fail ? routes : 2 * routes * jobs);
          count(
              report,
              "Searches ending with unreachable destinations",
              fail ? routes : 2 * routes * jobs);
          count(
              report,
              "Searches with previously known unreachable destinations",
              fail ? routes - 1 : 2 * (routes - 1) * jobs);
          count(
              report,
              "Potentially shortenable searches",
              fail ? routes - 1 : 2 * (routes - 1) * jobs);
          count(report, "Potentially entirely skippable searches", 0);
          count(report, "Searches excluded from reuse estimates", 0);
          measured(report, "Observed Dijkstra time (worker sum)", true);
          measured(report, "Potentially avoidable Dijkstra time (worker sum)", routes > 1);
          count(
              report,
              "Searches with mixed reachable/unreachable destinations",
              fail ? routes : 2 * routes * jobs);
          count(report, "Searches with no reachable destination", 0);
          measured(report, "Time after last reachable destination (worker sum)", true);
          measured(report, "Time with no reachable destination (worker sum)", false);
          measured(report, "Upper-bound avoidable Dijkstra time (worker sum)", true);
        } else {
          check(!report.contains("unreachable-destination diagnostic"), "Unexpected diagnostic");
        }
        boolean multiFlow = algorithm.equals("FastMF") || algorithm.equals("ExactMF");
        measured(report, algorithm.equals("ExactMF") ? "A*" : "Dijkstra", true);
        if (multiFlow) {
          measured(report, "Path reconstruction", true);
          measured(report, "Header matching", outputMode > 0 && !fail);
          measured(report, "Modal splitting and path filtering", true);
          measured(report, "Volume distribution", !fail);
          check(!report.contains("Path reconstruction and volume loading:"), "Wrong MF stages");
        } else {
          measured(report, "Path reconstruction and volume loading", true);
          check(!report.contains("Header matching:"), "Irrelevant header stage");
          check(!report.contains("Modal splitting and path filtering:"), "Irrelevant modal stage");
          check(!report.contains("Volume distribution:"), "Duplicate volume stage");
        }
        if (algorithm.equals("DynamicTimeDependent")) measured(report, "Demand relocation", true);
        measured(
            report,
            "Path output (includes DB calls and writer waits)",
            outputMode > 1 || (outputMode > 0 && !fail));
        check(report.contains(fail ? "failed/cancelled" : "completed"), "Wrong report outcome");
      } else check(report.isEmpty(), "Disabled audit produced output");
      return result;
    } finally {
      writer.discard();
      ModalSplitMethodsLoader.getAvailableModalSplitMethods().remove(split);
    }
  }

  /** Runs the worker fixtures with and without auditing on a disposable database. */
  public static void main(String[] args) throws Exception {
    boolean previous = NodusC.displayComputingTimes;
    boolean previousCompact = NodusC.useCompactShortestPaths;
    try (Connection connection =
        DriverManager.getConnection("jdbc:hsqldb:mem:nodus_assignment_audit")) {
      connection.setAutoCommit(false);
      JDBCUtils.setConnection(connection);
      for (String algorithm :
          new String[] {
            "AllOrNothing",
            "Incremental",
            "MSA",
            "FrankWolfe",
            "StaticAoNTimeDependent",
            "DynamicTimeDependent",
            "ExactMF",
            "FastMF"
          }) {
        boolean multiFlow = algorithm.endsWith("MF");
        for (int routes : multiFlow ? new int[] {1, 3} : new int[] {1}) {
          for (int mode = 0; mode <= 2; mode++) {
            for (boolean fail : new boolean[] {false, true}) {
              if (fail && !multiFlow) continue;
              NodusC.useCompactShortestPaths = false;
              List<String> baseline = run(connection, algorithm, false, mode, fail, routes);
              for (boolean compact : new boolean[] {false, true}) {
                NodusC.useCompactShortestPaths = compact;
                for (boolean audit : new boolean[] {false, true}) {
                  check(
                      baseline.equals(run(connection, algorithm, audit, mode, fail, routes)),
                      "Compact search/auditing changed paths, shares or volumes: "
                          + algorithm
                          + ", routes="
                          + routes
                          + ", output="
                          + mode
                          + ", failure="
                          + fail);
                }
              }
            }
          }
        }
        NodusC.useCompactShortestPaths = false;
        List<String> repeated = run(connection, algorithm, false, 2, false, 1, 3);
        NodusC.useCompactShortestPaths = true;
        check(
            repeated.equals(run(connection, algorithm, true, 2, false, 1, 3)),
            "Repeated jobs changed output after cost/topology or time-slice changes: " + algorithm);
      }
    } finally {
      NodusC.useCompactShortestPaths = previousCompact;
      NodusC.displayComputingTimes = previous;
      JDBCUtils.setConnection(null);
    }
    System.out.println("Assignment worker audit checks passed.");
  }
}
