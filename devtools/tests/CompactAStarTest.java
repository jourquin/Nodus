package edu.uclouvain.core.nodus.compute.assign.shortestpath;

import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Checks compact A* against the original implementation, including heuristic and tie ordering. */
public final class CompactAStarTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class Network {
    final VirtualNode[] nodes;
    final AdjacencyNode[] graph;
    final List<AdjacencyNode> edges = new ArrayList<>();

    Network(int size, int coordinates) {
      nodes = new VirtualNode[size + 1];
      graph = new AdjacencyNode[size + 1];
      for (int node = 1; node <= size; node++) {
        double longitude = coordinates == 0 ? 0 : node * .1;
        double latitude = coordinates == 0 ? 0 : (node % 5) * .1;
        if (coordinates == 2 && node % 3 == 0) longitude = Double.MAX_VALUE;
        if (coordinates == 3 && node % 3 == 0) latitude = Double.NaN;
        if (coordinates == 4 && node % 3 == 0) longitude = Double.POSITIVE_INFINITY;
        nodes[node] =
            new VirtualNode(node, node, 0, (byte) 1, (byte) 1, (short) 0, longitude, latitude);
        graph[node] = new AdjacencyNode(nodes[node]);
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

  private static final class Search extends BinaryHeapAStar {
    final List<Integer> extracted = new ArrayList<>();
    int edges;

    Search(Network network, CompactShortestPathGraph compact) {
      super(network.graph, compact);
    }

    @Override
    public void initializeSingleSource(AdjacencyNode[] graph, int source) {
      extracted.clear();
      edges = 0;
      super.initializeSingleSource(graph, source);
    }

    @Override
    public int extractMin() {
      int node = super.extractMin();
      extracted.add(node);
      return node;
    }

    @Override
    public void relax(int from, int to, double weight) {
      edges++;
      super.relax(from, to, weight);
    }
  }

  private static void compare(Search expected, Search actual) {
    check(
        Arrays.equals(expected.getPredecessors(), actual.getPredecessors()),
        "A* predecessor tree changed");
    check(Arrays.equals(expected.getWeights(), actual.getWeights()), "A* distances changed");
    check(expected.extracted.equals(actual.extracted), "A* extraction/heuristic tie order changed");
    check(expected.edges == actual.edges, "A* edge examination count changed");
    check(Arrays.equals(expected.nodePos, actual.nodePos), "A* logical heap positions changed");
    check(expected.heapSize == actual.heapSize, "A* stopping condition changed");
  }

  private static void randomSearches() {
    Random random = new Random(87425691);
    double[] costs = {
      0,
      -0.0,
      1,
      1,
      2.5,
      Double.MAX_VALUE / 2,
      Double.MAX_VALUE,
      Double.POSITIVE_INFINITY,
      Double.NaN
    };
    for (int trial = 0; trial < 60; trial++) {
      int size = 2 + random.nextInt(98);
      Network network = new Network(size, trial % 5);
      for (int from = 1; from < size; from++) {
        for (int edge = 0; edge < 4; edge++) {
          // Parallel edges, self loops, ties and an isolated destination are deliberate.
          network.add(from, 1 + random.nextInt(size - 1), costs[random.nextInt(costs.length)]);
        }
      }
      CompactShortestPathGraph compact = new CompactShortestPathGraph(network.graph);
      Search expected = new Search(network, null);
      Search actual = new Search(network, compact);
      check(
          actual.stock == null && actual.upperBoundCosts == null,
          "Compact A* allocated heap objects");
      for (int search = 0; search < 80; search++) {
        for (int edit = 0; edit < 3; edit++) {
          AdjacencyNode edge = network.edges.get(random.nextInt(network.edges.size()));
          edge.edgeWeight = costs[random.nextInt(costs.length)];
          compact.copyWeight(edge);
        }
        int source = 1 + random.nextInt(size);
        int goal = search % 10 == 0 ? source : 1 + random.nextInt(size);
        expected.compute(source, goal);
        actual.compute(source, goal);
        compare(expected, actual);
      }
    }
  }

  private static void sparseAndHeapOperations() throws Exception {
    Network network = new Network(100000, 1);
    network.add(1, 2, 1);
    network.add(2, 3, 1);
    network.add(3, 4, Double.POSITIVE_INFINITY);
    Search expected = new Search(network, null);
    Search actual = new Search(network, new CompactShortestPathGraph(network.graph));
    for (int source : new int[] {1, 100000, 2, 1}) {
      expected.compute(source, 50000);
      actual.compute(source, 50000);
      compare(expected, actual);
      check(actual.extracted.size() <= 4, "A* traversed unreachable vertices");
    }
    java.lang.reflect.Field count = CompactShortestPathHeap.class.getDeclaredField("touchedCount");
    count.setAccessible(true);
    check(count.getInt(actual.compactHeap) < 100, "A* materialized the entire heap");

    Network small = new Network(20, 1);
    expected = new Search(small, null);
    actual = new Search(small, new CompactShortestPathGraph(small.graph));
    for (int source = 1; source <= 20; source++) {
      expected.initializeSingleSource(small.graph, source);
      actual.initializeSingleSource(small.graph, source);
      for (int node = 1; node <= 20; node++) {
        expected.decreaseKey(node, node % 4);
        actual.decreaseKey(node, node % 4);
      }
      expected.swap(2, 3);
      actual.swap(2, 3);
      expected.heapify(1);
      actual.heapify(1);
      for (int node = 0; node <= 20; node++) {
        check(expected.extractMin() == actual.extractMin(), "A* direct heap operation changed");
        check(expected.minWeight == actual.minWeight, "A* direct heap operation changed cost");
      }
      compare(expected, actual);
    }
    Network single = new Network(1, 0);
    expected = new Search(single, null);
    actual = new Search(single, new CompactShortestPathGraph(single.graph));
    expected.compute(1, 1);
    actual.compute(1, 1);
    compare(expected, actual);
    check(actual.getWeights()[1] == 0, "A* source-equals-goal path changed");
    check(actual.extractMin() == -1, "A* empty heap did not terminate");
  }

  /** Runs synthetic checks without a project or database. */
  public static void main(String[] args) throws Exception {
    PrintStream previous = System.err;
    try (PrintStream diagnostics = new PrintStream(new ByteArrayOutputStream())) {
      System.setErr(diagnostics); // A* reports expected unreachable destinations.
      randomSearches();
      sparseAndHeapOperations();
    } finally {
      System.setErr(previous);
    }
    System.out.println("Compact A* checks passed (4800 differential searches).");
  }
}
