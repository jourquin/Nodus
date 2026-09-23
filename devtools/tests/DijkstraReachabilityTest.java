package edu.uclouvain.core.nodus.compute.assign.shortestpath;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes;
import edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimes.WorkerTimes;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Checks observed avoidable work and unchanged searches, using synthetic graphs and a fake clock.
 */
public final class DijkstraReachabilityTest {
  private static boolean compact;

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static String value(String report, String label) {
    for (String line : report.split("\\R")) {
      if (line.stripLeading().startsWith(label + ":")) {
        return line.substring(line.indexOf(':') + 1).trim();
      }
    }
    throw new AssertionError("Missing diagnostic row: " + label + "\n" + report);
  }

  private static void count(String report, String label, long expected) {
    check(value(report, label).equals(Long.toString(expected)), "Wrong " + label + "\n" + report);
  }

  private static void seconds(String report, String label, String expected) {
    check(value(report, label).equals(expected + " s"), "Wrong " + label + "\n" + report);
  }

  /** Supplies only the real-to-virtual destination mapping, without project or GUI setup. */
  private static final class DemandNetwork extends VirtualNetwork {
    VirtualNodeList[] lists;

    private DemandNetwork() {
      super(null);
    }

    @Override
    public VirtualNodeList[] getVirtualNodeLists() {
      return lists;
    }

    @Override
    public int getNodeIndexInVirtualNodeList(int id, boolean unused) {
      return id - 1;
    }
  }

  private static final class Graph {
    final VirtualNode[] nodes;
    final AdjacencyNode[] edges;
    final DemandNetwork network;
    int edgeId;

    Graph(int size) throws Exception {
      Class<?> allocator = Class.forName("sun.misc.Unsafe");
      Field field = allocator.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      network =
          (DemandNetwork)
              allocator
                  .getMethod("allocateInstance", Class.class)
                  .invoke(field.get(null), DemandNetwork.class);
      nodes = new VirtualNode[size + 1];
      edges = new AdjacencyNode[size + 1];
      network.lists = new VirtualNodeList[size];
      for (int i = 1; i <= size; i++) {
        nodes[i] = new VirtualNode(i, i, 0, (byte) 1, (byte) 1, (short) 0, 0, 0);
        edges[i] = new AdjacencyNode(nodes[i]);
        network.lists[i - 1] = new VirtualNodeList(i, NodusC.HANDLING_LOAD_UNLOAD, null);
        network.lists[i - 1].setUnloadingVirtualNodeId(i);
      }
    }

    void add(int from, int to, double cost) {
      VirtualLink link =
          new VirtualLink(++edgeId, 0, 0, nodes[from], nodes[to], VirtualLink.TYPE_MOVE);
      link.setNbGroups(1, 1);
      link.setCost((byte) 0, cost);
      AdjacencyNode tail = edges[from];
      while (tail.nextNode != null) {
        tail = tail.nextNode;
      }
      tail.setNext(new AdjacencyNode(nodes[to]), (byte) 0, link);
    }
  }

  private static Graph graph() throws Exception {
    Graph graph = new Graph(5);
    graph.add(1, 2, 1); // Requested, reached before the irrelevant branch.
    graph.add(1, 3, 2);
    graph.add(3, 4, 1);
    // Node 5 is requested but unreachable.
    return graph;
  }

  /** Independent trace of the reference search, used to check retrospective work counts. */
  private static final class ReferenceSearch extends BinaryHeapDijkstra {
    final List<int[]> settled = new ArrayList<>();
    int edgeCount;

    ReferenceSearch(Graph graph) {
      super(graph.edges, graph.network);
    }

    @Override
    public int extractMin() {
      int node = super.extractMin();
      if (node != -1 && minWeight != Double.MAX_VALUE) {
        settled.add(new int[] {node, edgeCount});
      }
      return node;
    }

    @Override
    public void relax(int from, int to, double weight) {
      edgeCount++;
      super.relax(from, to, weight);
    }
  }

  private static final class Run {
    final AssignmentComputingTimes audit;
    final WorkerTimes worker;
    final ReachabilityDijkstra observed;
    final ReferenceSearch reference;
    long expectedMixed;
    long expectedWhole;
    long expectedTailNodes;
    long expectedTailEdges;
    long expectedAvoidableNodes;
    long expectedAvoidableEdges;
    final CompactShortestPathGraph compactGraph;

    Run(Graph graph, double multiplier) throws Exception {
      Constructor<AssignmentComputingTimes> constructor =
          AssignmentComputingTimes.class.getDeclaredConstructor(LongSupplier.class);
      constructor.setAccessible(true);
      AtomicLong clock = new AtomicLong();
      audit = constructor.newInstance((LongSupplier) () -> clock.addAndGet(1_000_000_000));
      audit.startAssignment();
      worker = audit.newWorkerTimes();
      compactGraph = compact ? new CompactShortestPathGraph(graph.edges) : null;
      observed = new ReachabilityDijkstra(graph.edges, graph.network, worker, compactGraph);
      reference = new ReferenceSearch(graph);
      observed.startSequence(multiplier);
    }

    void search(int source, int... destinations) {
      LinkedList<ODCell> demand = new LinkedList<>();
      for (int destination : destinations) {
        demand.add(new ODCell(1, source, destination, 17.3));
      }
      // The fixture edits costs directly. Production uses the affected-edge updater tested
      // separately.
      if (compactGraph != null) {
        for (AdjacencyNode head : compactGraph.graph) {
          for (AdjacencyNode edge = head;
              edge != null && edge.nextNode != null;
              edge = edge.nextNode) {
            compactGraph.copyWeight(edge);
          }
        }
      }
      reference.settled.clear();
      reference.edgeCount = 0;
      reference.compute(source, demand);
      observed.compute(source, demand);
      Set<Integer> missing = new HashSet<>();
      for (int destination : destinations) missing.add(destination);
      int nodesAtLast = 0;
      int edgesAtLast = 0;
      for (int index = 0; index < reference.settled.size(); index++) {
        int[] event = reference.settled.get(index);
        if (missing.remove(event[0])) {
          nodesAtLast = index + 1;
          edgesAtLast = event[1];
        }
      }
      if (!missing.isEmpty()) {
        long avoidableNodes = reference.settled.size() - nodesAtLast;
        long avoidableEdges = reference.edgeCount - edgesAtLast;
        if (nodesAtLast == 0) {
          expectedWhole++;
        } else {
          expectedMixed++;
          expectedTailNodes += avoidableNodes;
          expectedTailEdges += avoidableEdges;
        }
        expectedAvoidableNodes += avoidableNodes;
        expectedAvoidableEdges += avoidableEdges;
      }
      check(
          Arrays.equals(reference.getPredecessors(), observed.getPredecessors()),
          "Observer changed predecessor tree or tie breaking");
      check(
          Arrays.equals(reference.getWeights(), observed.getWeights()),
          "Observer changed distances, initialization or stopping condition");
    }

    String report() throws Exception {
      worker.close();
      worker.close(); // Repeated close must not merge the search counters twice.
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      PrintStream previous = System.out;
      try (PrintStream capture = new PrintStream(output, true, "UTF-8")) {
        System.setOut(capture);
        audit.finishAndPrint("FastMFAssignment", 1, 1, true);
      } finally {
        System.setOut(previous);
      }
      String report = output.toString("UTF-8");
      count(report, "Searches with mixed reachable/unreachable destinations", expectedMixed);
      count(report, "Searches with no reachable destination", expectedWhole);
      count(report, "Nodes after last reachable destination", expectedTailNodes);
      count(report, "Edges after last reachable destination", expectedTailEdges);
      count(report, "Upper-bound avoidable nodes", expectedAvoidableNodes);
      count(report, "Upper-bound avoidable edges", expectedAvoidableEdges);
      double tail =
          Double.parseDouble(
              value(report, "Time after last reachable destination (worker sum)").split(" ")[0]);
      double whole =
          Double.parseDouble(
              value(report, "Time with no reachable destination (worker sum)").split(" ")[0]);
      double upper =
          Double.parseDouble(
              value(report, "Upper-bound avoidable Dijkstra time (worker sum)").split(" ")[0]);
      double total =
          Double.parseDouble(value(report, "Observed Dijkstra time (worker sum)").split(" ")[0]);
      check(
          upper == tail + whole && upper <= total,
          "Retrospective time must partition into tails and whole searches within observed time");
      return report;
    }
  }

  private static void firstRoutePotential() throws Exception {
    Run run = new Run(graph(), 1);
    run.search(1, 2, 5, 2, 5);
    String report = run.report();
    count(report, "Searches with previously known unreachable destinations", 0);
    count(report, "Potentially avoidable nodes", 0);
    count(report, "Upper-bound avoidable nodes", 2);
    count(report, "Upper-bound avoidable edges", 1);
    seconds(report, "Time after last reachable destination (worker sum)", "1.000");
    seconds(report, "Time with no reachable destination (worker sum)", "0.000");
    seconds(report, "Upper-bound avoidable Dijkstra time (worker sum)", "1.000");
    check(
        value(report, "Upper-bound share of observed Dijkstra time").equals("50.00 %"),
        "Single-route opportunity was not measured");
    check(
        value(report, "Upper-bound share of edge examinations").equals("33.33 %"),
        "Single-route edge share is incorrect");
  }

  private static void lastDestinationCases() throws Exception {
    Graph graph = graph();
    graph.add(3, 1, 0); // The final target's outgoing edges still belong to the tail.
    Run run = new Run(graph, 1);
    run.search(1, 1, 2, 3, 5, 3);
    String report = run.report();
    count(report, "Nodes after last reachable destination", 1);
    count(report, "Edges after last reachable destination", 2);
    seconds(report, "Time after last reachable destination (worker sum)", "1.000");

    run = new Run(graph(), 1);
    run.search(1, 4, 5); // Last reachable target is also the last finite node extracted.
    report = run.report();
    count(report, "Nodes after last reachable destination", 0);
    count(report, "Edges after last reachable destination", 0);
    // Only terminal search bookkeeping remains; fake clocks intentionally amplify that interval.
    seconds(report, "Time after last reachable destination (worker sum)", "1.000");

    run = new Run(graph(), 1);
    run.search(1, 1, 5); // Source is the only reachable requested target.
    report = run.report();
    count(report, "Nodes after last reachable destination", 3);
    count(report, "Edges after last reachable destination", 3);

    run = new Run(graph(), 1);
    run.search(1, 5, 5); // A first route can already be a whole-search opportunity.
    report = run.report();
    count(report, "Searches with no reachable destination", 1);
    count(report, "Upper-bound avoidable nodes", 4);
    count(report, "Upper-bound avoidable edges", 3);
    seconds(report, "Time after last reachable destination (worker sum)", "0.000");
    seconds(report, "Time with no reachable destination (worker sum)", "1.000");
    check(
        value(report, "Upper-bound share of observed Dijkstra time").equals("100.00 %"),
        "Whole-search opportunity omitted setup or heap initialization");

    run = new Run(new Graph(2), 1);
    run.search(1, 2); // No edges, nonempty unreachable destination set.
    report = run.report();
    count(report, "Upper-bound avoidable nodes", 1);
    check(
        value(report, "Upper-bound share of edge examinations").equals("n/a"),
        "Zero examined edges should have no percentage");
  }

  private static void partialTail() throws Exception {
    Run run = new Run(graph(), 1.5);
    run.search(1, 2, 5, 2, 5); // Duplicates must not postpone the hypothetical stop.
    run.search(1, 2, 5, 2, 5);
    String report = run.report();
    count(report, "Completed Dijkstra searches", 2);
    count(report, "Searches ending with unreachable destinations", 2);
    count(report, "Searches with previously known unreachable destinations", 1);
    count(report, "Potentially shortenable searches", 1);
    count(report, "Potentially entirely skippable searches", 0);
    count(report, "Nodes settled", 8);
    count(report, "Edges examined", 6);
    count(report, "Potentially avoidable nodes", 2);
    count(report, "Potentially avoidable edges", 1);
    seconds(report, "Observed Dijkstra time (worker sum)", "4.000");
    seconds(report, "Potentially avoidable Dijkstra time (worker sum)", "1.000");
    check(
        value(report, "Potentially avoidable edge examinations").equals("16.67 %"),
        "Wrong edge percentage");
    check(
        value(report, "Potentially avoidable share of observed Dijkstra time").equals("25.00 %"),
        "Wrong time percentage");
  }

  private static void wholeSearch() throws Exception {
    Run run = new Run(graph(), 1);
    run.search(1, 5);
    run.search(1, 5);
    String report = run.report();
    count(report, "Potentially shortenable searches", 0);
    count(report, "Potentially entirely skippable searches", 1);
    count(report, "Potentially avoidable nodes", 4);
    count(report, "Potentially avoidable edges", 3);
    seconds(report, "Observed Dijkstra time (worker sum)", "2.000");
    seconds(report, "Potentially avoidable Dijkstra time (worker sum)", "1.000");
  }

  private static void allReachableAndEmpty() throws Exception {
    Run run = new Run(graph(), 1);
    run.search(1, 2);
    run.search(1, 2);
    run.search(1); // The diagnostic must preserve the existing empty-row behavior too.
    run.search(1, 1); // Source is also a requested destination.
    String report = run.report();
    count(report, "Searches ending with unreachable destinations", 0);
    count(report, "Searches with previously known unreachable destinations", 0);
    count(report, "Potentially avoidable nodes", 0);
    seconds(report, "Potentially avoidable Dijkstra time (worker sum)", "0.000");
  }

  private static void newlyUnreachable() throws Exception {
    Graph graph = graph();
    Run run = new Run(graph, 1.5);
    run.search(1, 2, 5);
    graph.edges[1].edgeWeight = Double.POSITIVE_INFINITY;
    run.search(1, 2, 5); // Losing node 2 is not known in advance: this search remains necessary.
    run.search(1, 2, 5); // Both missing targets are now known.
    String report = run.report();
    count(report, "Searches with previously known unreachable destinations", 2);
    count(report, "Potentially shortenable searches", 0);
    count(report, "Potentially entirely skippable searches", 1);
    count(report, "Nodes settled", 10);
    count(report, "Edges examined", 9);
    count(report, "Potentially avoidable nodes", 3);
    count(report, "Potentially avoidable edges", 3);
  }

  private static void overflowAndFailure() throws Exception {
    Graph graph = new Graph(5);
    graph.add(1, 2, Double.MAX_VALUE / 4);
    graph.add(2, 3, Double.MAX_VALUE / 4);
    Run run = new Run(graph, 2);
    run.search(1, 3, 5);
    graph.edges[1].edgeWeight *= 2;
    graph.edges[2].edgeWeight *= 2;
    run.search(1, 3, 5); // Cost sum reaches the infinity sentinel, without a topology change.
    try {
      LinkedList<ODCell> invalid = new LinkedList<>();
      invalid.add(new ODCell(1, 1, 99, 1));
      run.observed.compute(1, invalid);
      throw new AssertionError("Expected invalid destination to fail");
    } catch (ArrayIndexOutOfBoundsException expected) {
      // Incomplete searches must not contribute hypothetical savings or completed-search counts.
    }
    run.search(1, 3, 5);
    String report = run.report();
    count(report, "Completed Dijkstra searches", 3);
    count(report, "Potentially shortenable searches", 0);
    count(report, "Potentially entirely skippable searches", 1);
    count(report, "Potentially avoidable nodes", 2);
    count(report, "Potentially avoidable edges", 2);
  }

  private static void contextResets() throws Exception {
    Graph graph = graph();
    graph.add(4, 5, Double.POSITIVE_INFINITY);
    Run run = new Run(graph, 1);
    run.search(1, 2, 5);
    run.search(5, 5); // A different source reaches the previously missing target immediately.
    run.search(1, 2, 5); // Changing the source back must also start cold.
    run.observed.startSequence(1); // Represents a new mode with a previously blocked link reopened.
    graph.edges[4].edgeWeight = 1;
    run.search(1, 2, 5);
    String report = run.report();
    count(report, "Searches with previously known unreachable destinations", 0);
    count(report, "Potentially avoidable nodes", 0);
    count(report, "Searches ending with unreachable destinations", 2);
  }

  private static void excludedCosts() throws Exception {
    for (double multiplier : new double[] {.99, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      Run run = new Run(graph(), multiplier);
      run.search(1, 2, 5);
      run.search(1, 2, 5);
      String report = run.report();
      count(report, "Searches excluded from reuse estimates", 2);
      count(report, "Searches ending with unreachable destinations", 2);
      count(report, "Searches with previously known unreachable destinations", 0);
      count(report, "Potentially avoidable edges", 0);
    }
    for (double cost : new double[] {-1, Double.NaN}) {
      Graph graph = graph();
      graph.edges[3].edgeWeight = cost;
      Run run = new Run(graph, 1);
      run.search(1, 2, 5);
      run.search(1, 2, 5);
      String report = run.report();
      count(report, "Searches excluded from reuse estimates", 2);
      count(report, "Potentially avoidable edges", 0);
    }
  }

  private static void invalidatedKnowledge() throws Exception {
    Graph graph = graph();
    graph.add(4, 5, Double.POSITIVE_INFINITY);
    Run run = new Run(graph, 1);
    run.search(1, 2, 5);
    graph.edges[4].edgeWeight = 1; // Deliberately reopen an edge without startSequence.
    run.search(1, 2, 5);
    String report = run.report();
    count(report, "Searches excluded from reuse estimates", 1);
    count(report, "Potentially avoidable nodes", 0);
    seconds(report, "Potentially avoidable Dijkstra time (worker sum)", "0.000");
  }

  private static void randomSearches() throws Exception {
    Random random = new Random(982341);
    for (int trial = 0; trial < 20; trial++) {
      Graph graph = new Graph(25);
      for (int from = 1; from < 25; from++) {
        for (int edge = 0; edge < 3; edge++) {
          graph.add(from, 1 + random.nextInt(24), random.nextInt(5));
        }
      }
      Run run = new Run(graph, 1.5);
      for (int sequence = 0; sequence < 10; sequence++) {
        run.observed.startSequence(1.5);
        int source = 1 + random.nextInt(24);
        int destination = 1 + random.nextInt(24);
        for (int alternative = 0; alternative < 3; alternative++) {
          run.search(source, source, destination, destination, 25);
          // Includes ties, zero weights, restrictions and cost overflow to infinity.
          for (AdjacencyNode head : graph.edges) {
            for (AdjacencyNode edge = head;
                edge != null && edge.nextNode != null;
                edge = edge.nextNode) {
              if (random.nextInt(10) == 0) {
                edge.edgeWeight = Double.POSITIVE_INFINITY;
              } else {
                edge.edgeWeight *= 1.5;
              }
            }
          }
        }
      }
      String report = run.report();
      count(report, "Completed Dijkstra searches", 30);
      check(
          Long.parseLong(value(report, "Potentially avoidable nodes"))
              <= Long.parseLong(value(report, "Nodes settled")),
          "Node savings exceed observed work");
      check(
          Long.parseLong(value(report, "Potentially avoidable edges"))
              <= Long.parseLong(value(report, "Edges examined")),
          "Edge savings exceed observed work");
    }
  }

  /** Runs deterministic diagnostics and differential searches without any project or database. */
  public static void main(String[] args) throws Exception {
    boolean previous = NodusC.displayComputingTimes;
    NodusC.displayComputingTimes = true;
    try {
      for (boolean useCompact : new boolean[] {false, true}) {
        compact = useCompact;
        firstRoutePotential();
        lastDestinationCases();
        partialTail();
        wholeSearch();
        allReachableAndEmpty();
        newlyUnreachable();
        overflowAndFailure();
        contextResets();
        Run noEdges = new Run(new Graph(1), 1);
        noEdges.search(1, 1);
        check(
            value(noEdges.report(), "Potentially avoidable edge examinations").equals("n/a"),
            "Zero examined edges should have no percentage");
        excludedCosts();
        invalidatedKnowledge();
        randomSearches();
      }
    } finally {
      NodusC.displayComputingTimes = previous;
    }
    System.out.println(
        "Dijkstra reachability diagnostic checks passed (1200 random searches, both representations).");
  }
}
