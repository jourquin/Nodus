package edu.uclouvain.core.nodus.compute.assign.shortestpath;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Differential checks and optional synthetic timings for compact Fast MF shortest paths. */
public final class CompactDijkstraTest {
  private static volatile double benchmarkResult;

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

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

  private static DemandNetwork demands(int size) throws Exception {
    // Only provide destination mapping, bypassing project, database and GUI initialization.
    Class<?> allocator = Class.forName("sun.misc.Unsafe");
    Field field = allocator.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    DemandNetwork network =
        (DemandNetwork)
            allocator
                .getMethod("allocateInstance", Class.class)
                .invoke(field.get(null), DemandNetwork.class);
    network.lists = new VirtualNodeList[size];
    for (int i = 0; i < size; i++) {
      network.lists[i] = new VirtualNodeList(i + 1, NodusC.HANDLING_LOAD_UNLOAD, null);
      network.lists[i].setUnloadingVirtualNodeId(i + 1);
    }
    return network;
  }

  private static final class Network {
    final VirtualNode[] nodes;
    final AdjacencyNode[] graph;
    final List<AdjacencyNode> edges = new ArrayList<>();

    Network(int size) {
      nodes = new VirtualNode[size + 1];
      graph = new AdjacencyNode[size + 1];
      for (int i = 1; i <= size; i++) {
        nodes[i] = new VirtualNode(i, i, 0, (byte) 1, (byte) 1, (short) 0, 0, 0);
        graph[i] = new AdjacencyNode(nodes[i]);
      }
    }

    void add(int from, int to, double cost) {
      VirtualLink link =
          new VirtualLink(edges.size() + 1, 0, 0, nodes[from], nodes[to], VirtualLink.TYPE_MOVE);
      link.setNbGroups(1, 1);
      link.setCost((byte) 0, cost);
      AdjacencyNode tail = graph[from];
      while (tail.nextNode != null) tail = tail.nextNode;
      tail.setNext(new AdjacencyNode(nodes[to]), (byte) 0, link);
      edges.add(tail);
    }
  }

  /** Checks extraction order and edge work as well as the final predecessor/distance arrays. */
  private static final class Search extends BinaryHeapDijkstra {
    final List<Integer> extracted = new ArrayList<>();
    int edges;

    Search(Network graph, VirtualNetwork network, CompactShortestPathGraph compact) {
      super(graph.graph, network, compact);
    }

    @Override
    public void initializeSingleSource(int source) {
      extracted.clear();
      edges = 0;
      super.initializeSingleSource(source);
    }

    @Override
    public int extractMin() {
      int node = super.extractMin();
      extracted.add(node);
      return node;
    }

    @Override
    public void relax(int from, int to, double cost) {
      edges++;
      super.relax(from, to, cost);
    }
  }

  private static void compare(Search expected, Search actual) {
    check(
        Arrays.equals(expected.getPredecessors(), actual.getPredecessors()),
        "Different predecessor tree, including equal-cost paths");
    check(Arrays.equals(expected.getWeights(), actual.getWeights()), "Different distances");
    check(expected.extracted.equals(actual.extracted), "Different heap extraction order");
    check(expected.edges == actual.edges, "Different edge examination count");
    check(Arrays.equals(expected.nodePos, actual.nodePos), "Different logical heap positions");
    check(expected.heapSize == actual.heapSize, "Different remaining heap size");
  }

  private static void randomSearches() throws Exception {
    Random random = new Random(78193632);
    double[] costs = {
      0,
      -0.0,
      1,
      1,
      2,
      3.25,
      Double.POSITIVE_INFINITY,
      Double.MAX_VALUE,
      Double.MAX_VALUE / 2,
      Double.NaN
    };
    for (int trial = 0; trial < 80; trial++) {
      int size = 1 + random.nextInt(100);
      Network graph = new Network(size);
      // Self loops and parallel edges are deliberate; leave the last node isolated in larger
      // graphs.
      for (int node = 1; node < size; node++) {
        for (int edge = 0; edge < 4; edge++) {
          graph.add(node, 1 + random.nextInt(size - 1), costs[random.nextInt(costs.length)]);
        }
      }
      CompactShortestPathGraph compact = new CompactShortestPathGraph(graph.graph);
      DemandNetwork network = demands(size);
      Search reference = new Search(graph, network, null);
      Search actual = new Search(graph, network, compact);
      check(
          actual.stock == null && actual.upperBoundCosts == null,
          "Compact search allocated the old heap-object arrays");
      for (int search = 0; search < 100; search++) {
        if (!graph.edges.isEmpty()) {
          for (int edit = 0; edit < 3; edit++) {
            AdjacencyNode edge = graph.edges.get(random.nextInt(graph.edges.size()));
            edge.edgeWeight = costs[random.nextInt(costs.length)];
            compact.copyWeight(edge);
          }
        }
        int source = 1 + random.nextInt(size);
        int goal = 1 + random.nextInt(size);
        if (search % 2 == 0) {
          reference.compute(source, goal);
          actual.compute(source, goal);
        } else {
          LinkedList<ODCell> demand = new LinkedList<>();
          if (search % 5 != 0) { // Empty rows still exhaust the reachable graph, as before.
            demand.add(new ODCell(1, source, goal, 10));
            demand.add(new ODCell(1, source, goal, 20));
            demand.add(new ODCell(1, source, source, 30));
            demand.add(new ODCell(1, source, size, 40));
          }
          reference.compute(source, demand);
          actual.compute(source, demand);
        }
        compare(reference, actual);
      }
    }
  }

  private static void sparseResetAndHeapOperations() throws Exception {
    Network graph = new Network(100000);
    graph.add(1, 2, 0);
    graph.add(2, 3, 1);
    graph.add(3, 4, Double.POSITIVE_INFINITY);
    CompactShortestPathGraph compact = new CompactShortestPathGraph(graph.graph);
    Search reference = new Search(graph, null, null);
    Search actual = new Search(graph, null, compact);
    for (int source : new int[] {1, 100000, 2, 1}) {
      reference.compute(source, 4);
      actual.compute(source, 4);
      compare(reference, actual);
      check(actual.extracted.size() <= 4, "Traversed unreachable nodes");
    }
    Field heapField = BinaryHeapDijkstra.class.getDeclaredField("compactHeap");
    heapField.setAccessible(true);
    Field count = CompactShortestPathHeap.class.getDeclaredField("touchedCount");
    count.setAccessible(true);
    check(count.getInt(heapField.get(actual)) < 100, "Sparse search materialized the entire heap");

    Network small = new Network(20);
    reference = new Search(small, null, null);
    actual = new Search(small, null, new CompactShortestPathGraph(small.graph));
    for (int source = 1; source <= 20; source++) {
      reference.initializeSingleSource(source);
      actual.initializeSingleSource(source);
      for (int node = 1; node <= 20; node++) {
        reference.decreaseKey(node, node % 4);
        actual.decreaseKey(node, node % 4);
      }
      reference.swap(2, 3);
      actual.swap(2, 3);
      reference.heapify(1);
      actual.heapify(1);
      for (int node = 0; node <= 20; node++) {
        check(reference.extractMin() == actual.extractMin(), "Heap operation changed extraction");
        check(reference.minWeight == actual.minWeight, "Heap operation changed cost");
      }
      compare(reference, actual);
    }
    try {
      new BinaryHeapDijkstra(small.graph, null, compact);
      throw new AssertionError("Accepted compact costs from a different graph");
    } catch (IllegalArgumentException expected) {
      // Pairing the worker's heap with another worker's costs must fail immediately.
    }
  }

  private static double timed(BinaryHeapDijkstra search, int goal, int repeats) {
    long started = System.nanoTime();
    for (int i = 0; i < repeats; i++) {
      search.compute(1 + i % 10, goal);
      benchmarkResult = search.getWeights()[goal];
    }
    return (System.nanoTime() - started) / 1e6;
  }

  private static void benchmarkCase(Network graph, int goal, int repeats, String label) {
    BinaryHeapDijkstra original = new BinaryHeapDijkstra(graph.graph);
    long started = System.nanoTime();
    CompactShortestPathGraph compactGraph = new CompactShortestPathGraph(graph.graph);
    BinaryHeapDijkstra compact = new BinaryHeapDijkstra(graph.graph, null, compactGraph);
    double construction = (System.nanoTime() - started) / 1e6;
    double[] before = new double[5];
    double[] after = new double[5];
    for (int pass = -3; pass < 5; pass++) {
      double oldTime;
      double newTime;
      if ((pass & 1) == 0) {
        oldTime = timed(original, goal, repeats);
        newTime = timed(compact, goal, repeats);
      } else {
        newTime = timed(compact, goal, repeats);
        oldTime = timed(original, goal, repeats);
      }
      if (pass >= 0) {
        before[pass] = oldTime;
        after[pass] = newTime;
      }
    }
    Arrays.sort(before);
    Arrays.sort(after);
    System.out.printf(
        Locale.ROOT,
        "%s (%d searches): original %.2f ms; compact %.2f ms; ratio %.2fx; compact setup %.2f ms%n",
        label,
        repeats,
        before[2],
        after[2],
        before[2] / after[2],
        construction);
  }

  private static void benchmark() {
    System.out.println(
        "Synthetic timings: 3 warm-up batches, then median of 5; alternating order; "
            + "audit disabled. Search timings exclude construction (reported separately).");
    Network sparse = new Network(100000);
    for (int i = 1; i < 32; i++) sparse.add(i, i + 1, 1);
    benchmarkCase(sparse, 20, 500, "Nearby goal");
    benchmarkCase(sparse, 100000, 500, "Unreachable goal, small component");
    Network broad = new Network(32001);
    Random random = new Random(18379);
    for (int node = 1; node <= 32000; node++) {
      broad.add(node, node == 32000 ? 1 : node + 1, 1);
      for (int edge = 0; edge < 3; edge++) {
        broad.add(node, 1 + random.nextInt(32000), 1 + random.nextInt(20));
      }
    }
    benchmarkCase(broad, 32001, 20, "Unreachable goal, 32000 reachable nodes");
    benchmarkCase(broad, 16000, 20, "Reachable goal in large component");
  }

  /** Runs deterministic regression checks; --benchmark adds optional synthetic measurements. */
  public static void main(String[] args) throws Exception {
    randomSearches();
    sparseResetAndHeapOperations();
    System.out.println("Compact Dijkstra checks passed (8000 differential searches).");
    if (Arrays.asList(args).contains("--benchmark")) benchmark();
  }
}
