package edu.uclouvain.core.nodus.compute.assign.shortestpath;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.od.ODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNetwork;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNodeList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/** Differential checks against snapshots of the original fully initialized heap algorithms. */
public final class ShortestPathInitializationTest {
  private static volatile double benchmarkResult;

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void compare(FullInitializationDijkstra expected, BinaryHeapDijkstra actual) {
    check(
        Arrays.equals(expected.getPredecessors(), actual.getPredecessors()),
        "Predecessor tree differs, including equal-cost route choices");
    check(
        Arrays.equals(expected.getWeights(), actual.getWeights()), "Shortest-path weights differ");
  }

  private static final class Network {
    final VirtualNode[] nodes;
    final AdjacencyNode[] graph;
    final List<AdjacencyNode> edges = new ArrayList<>();

    Network(int size, boolean coordinates) {
      nodes = new VirtualNode[size + 1];
      graph = new AdjacencyNode[size + 1];
      for (int i = 1; i <= size; i++) {
        nodes[i] =
            new VirtualNode(
                i,
                i,
                0,
                (byte) 1,
                (byte) 1,
                (short) 0,
                coordinates ? i * 0.1 : 0,
                coordinates ? (i % 5) * 0.1 : 0);
        graph[i] = new AdjacencyNode(nodes[i]);
      }
    }

    void add(int from, int to, double cost) {
      VirtualLink link =
          new VirtualLink(edges.size() + 1, 0, 0, nodes[from], nodes[to], VirtualLink.TYPE_TRANSIT);
      link.setNbGroups(1, 1);
      link.setCost((byte) 0, cost);
      AdjacencyNode edge = graph[from];
      while (edge.nextNode != null) {
        edge = edge.nextNode;
      }
      edge.setNext(new AdjacencyNode(nodes[to]), (byte) 0, link);
      edges.add(edge);
    }
  }

  /** Only supplies OD destination mappings; the GUI/database-backed constructor is not used. */
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
    // Test-only allocation bypasses VirtualNetwork's project/database/UI initialization.
    Class<?> allocatorClass = Class.forName("sun.misc.Unsafe");
    Field allocatorField = allocatorClass.getDeclaredField("theUnsafe");
    allocatorField.setAccessible(true);
    Object allocator = allocatorField.get(null);
    DemandNetwork network =
        (DemandNetwork)
            allocatorClass
                .getMethod("allocateInstance", Class.class)
                .invoke(allocator, DemandNetwork.class);
    network.lists = new VirtualNodeList[size];
    for (int i = 0; i < size; i++) {
      network.lists[i] = new VirtualNodeList(i + 1, NodusC.HANDLING_LOAD_UNLOAD, null);
      network.lists[i].setUnloadingVirtualNodeId(i + 1);
    }
    return network;
  }

  private static Network randomNetwork(Random random, int size, boolean coordinates) {
    Network n = new Network(size, coordinates);
    for (int from = 1; from <= size; from++) {
      for (int edge = 0; edge < 4; edge++) {
        int to = 1 + random.nextInt(size);
        if (to == from) {
          continue;
        }
        double cost = random.nextInt(4); // Numerous ties and zero-weight edges.
        if (coordinates) {
          cost += n.graph[from].goalEst(n.graph[to]);
        }
        if (random.nextInt(6) == 0) {
          cost = Double.POSITIVE_INFINITY;
        }
        n.add(from, to, cost);
      }
    }
    return n;
  }

  private static void checkSingleGoals() {
    Random random = new Random(946105);
    for (boolean aStar : new boolean[] {false, true}) {
      for (int run = 0; run < 50; run++) {
        Network n = randomNetwork(random, 5 + random.nextInt(65), run % 2 == 0);
        FullInitializationDijkstra reference =
            aStar ? new FullInitializationAStar(n.graph) : new FullInitializationDijkstra(n.graph);
        BinaryHeapDijkstra actual =
            aStar ? new BinaryHeapAStar(n.graph) : new BinaryHeapDijkstra(n.graph);
        for (int search = 0; search < 80; search++) {
          int source = 1 + random.nextInt(n.nodes.length - 1);
          int target = 1 + random.nextInt(n.nodes.length - 1);
          if (search % 5 == 0) {
            // Restrictions and changing assignment costs must not reuse old labels or heuristics.
            AdjacencyNode edge = n.edges.get(random.nextInt(n.edges.size()));
            edge.edgeWeight = search % 10 == 0 ? Double.POSITIVE_INFINITY : edge.originalEdgeWeight;
          }
          reference.compute(source, target);
          actual.compute(source, target);
          compare(reference, actual);
        }
      }
    }
  }

  private static void checkDemandRows() throws Exception {
    Random random = new Random(78023);
    for (int run = 0; run < 20; run++) {
      Network n = randomNetwork(random, 60, false);
      DemandNetwork virtualNet = demands(60);
      Network copy = new Network(60, false);
      for (AdjacencyNode edge : n.edges) {
        copy.add(
            edge.virtualLink.getBeginVirtualNode().getId(), edge.endVirtualNode, edge.edgeWeight);
      }
      FullInitializationDijkstra reference = new FullInitializationDijkstra(n.graph, virtualNet);
      BinaryHeapDijkstra actual = new BinaryHeapDijkstra(copy.graph, virtualNet);
      for (int row = 0; row < 50; row++) {
        int source = 1 + random.nextInt(60);
        LinkedList<ODCell> demand = new LinkedList<>();
        for (int i = 0; i < row % 7; i++) {
          int destination = 1 + random.nextInt(60);
          demand.add(new ODCell(1, source, destination, 100));
          demand.add(new ODCell(1, source, destination, 50)); // Duplicate destination.
        }
        reference.compute(source, demand);
        actual.compute(source, demand);
        compare(reference, actual);
        for (int node = 1; node < n.graph.length; node++) {
          final int destination = node;
          boolean requested =
              demand.stream().anyMatch(cell -> cell.getDestinationNodeId() == destination);
          check(copy.graph[node].isNodeToReach == requested, "Stale destination flag for " + node);
        }
      }
    }
  }

  private static final class CountingDijkstra extends BinaryHeapDijkstra {
    int extractions;
    int relaxations;

    CountingDijkstra(AdjacencyNode[] graph) {
      super(graph);
    }

    CountingDijkstra(AdjacencyNode[] graph, VirtualNetwork network) {
      super(graph, network);
    }

    @Override
    public int extractMin() {
      extractions++;
      return super.extractMin();
    }

    @Override
    public void relax(int u, int v, double cost) {
      relaxations++;
      super.relax(u, v, cost);
    }
  }

  private static int allocatedNodes(BinaryHeapDijkstra search) {
    int allocated = 0;
    for (BinaryHeapNode node : search.stock) {
      if (node != null) {
        allocated++;
      }
    }
    return allocated;
  }

  private static void checkSparseAndUnreachable() throws Exception {
    Network n = new Network(20000, false);
    n.add(1, 2, 1);
    n.add(2, 3, 1);
    n.add(3, 4, Double.POSITIVE_INFINITY);
    for (int i = 100; i < 19999; i++) {
      n.add(i, i + 1, 1);
    }
    CountingDijkstra search = new CountingDijkstra(n.graph, demands(20000));
    check(allocatedNodes(search) == 0, "Constructor eagerly allocated all heap nodes");
    search.compute(1, 10000);
    check(
        search.extractions <= 4 && search.relaxations == 3,
        "Dijkstra processed unreachable vertices or their outgoing edges");
    check(
        search.getPredecessors()[10000] == 0 && search.getWeights()[10000] == Double.MAX_VALUE,
        "Unreachable destination acquired a path");
    check(allocatedNodes(search) < 100, "Sparse search materialized the full heap");
    search.compute(19990, 19995);
    check(
        search.getPredecessors()[3] == 0 && search.getWeights()[3] == Double.MAX_VALUE,
        "Previous search results leaked into a different component");
    search.compute(1, 1);
    check(
        search.getWeights()[1] == 0 && search.getPredecessors()[19995] == 0,
        "Source-equals-goal search did not reset the previous state");
    search.extractions = 0;
    search.relaxations = 0;
    search.compute(
        1,
        new LinkedList<>(
            Arrays.asList(
                new ODCell(1, 1, 3, 10),
                new ODCell(1, 1, 10000, 10),
                new ODCell(1, 1, 10000, 20))));
    check(
        search.extractions <= 4 && search.relaxations == 3,
        "OD-row search processed unreachable vertices");
    search.compute(1, new LinkedList<>(Arrays.asList(new ODCell(1, 1, 2, 10))));
    check(
        !n.graph[3].isNodeToReach && !n.graph[10000].isNodeToReach && n.graph[2].isNodeToReach,
        "Destinations from the preceding OD row remained marked");
  }

  private static void checkSharedGraphDestinations() throws Exception {
    Network n = new Network(5, false);
    n.add(1, 2, 1);
    n.add(2, 3, 1);
    n.add(3, 4, 1);
    DemandNetwork network = demands(5);
    BinaryHeapDijkstra first = new BinaryHeapDijkstra(n.graph, network);
    BinaryHeapDijkstra second = new BinaryHeapDijkstra(n.graph, network);
    first.compute(1, new LinkedList<>(Arrays.asList(new ODCell(1, 1, 2, 10))));
    second.compute(1, new LinkedList<>(Arrays.asList(new ODCell(1, 1, 4, 10))));
    check(second.getWeights()[4] == 3, "Another search's destination stopped the search early");
    first.compute(1, new LinkedList<>(Arrays.asList(new ODCell(1, 1, 3, 10))));
    check(first.getWeights()[3] == 2, "Interleaved OD rows retained another search's targets");
  }

  private static void checkBoundaryCases() {
    Network single = new Network(1, false);
    single.add(1, 1, 0);
    BinaryHeapDijkstra search = new BinaryHeapDijkstra(single.graph);
    search.compute(1, 1);
    check(search.nodePos[1] == -1, "Last extracted node remains marked as in the heap");
    search.relax(1, 1, 0); // Must not access a removed heap node.
    check(search.getWeights()[1] == 0 && search.getPredecessors()[1] == 0, "Self route differs");
    check(search.extractMin() == -1, "Empty heap should return -1");
    BinaryHeapAStar aStar = new BinaryHeapAStar(single.graph);
    aStar.compute(1, 1);
    check(aStar.getWeights()[1] == 0, "A* source-equals-goal route differs");

    Network overflow = new Network(5, false);
    overflow.add(1, 2, Double.MAX_VALUE / 2);
    overflow.add(2, 3, Double.MAX_VALUE);
    overflow.add(2, 4, Double.NaN);
    overflow.add(1, 5, Double.POSITIVE_INFINITY);
    for (boolean heuristic : new boolean[] {false, true}) {
      FullInitializationDijkstra old =
          heuristic
              ? new FullInitializationAStar(overflow.graph)
              : new FullInitializationDijkstra(overflow.graph);
      BinaryHeapDijkstra current =
          heuristic ? new BinaryHeapAStar(overflow.graph) : new BinaryHeapDijkstra(overflow.graph);
      old.compute(1, 3);
      current.compute(1, 3);
      compare(old, current);
    }
  }

  private static void benchmarkCase(Network n, int goal, int searches, String label) {
    FullInitializationDijkstra old = new FullInitializationDijkstra(n.graph);
    BinaryHeapDijkstra current = new BinaryHeapDijkstra(n.graph);
    double[] before = new double[5];
    double[] after = new double[5];
    for (int pass = -3; pass < 5; pass++) {
      long start = System.nanoTime();
      for (int i = 0; i < searches; i++) {
        old.compute(1 + i % 10, goal);
        benchmarkResult = old.getWeights()[goal];
      }
      double oldTime = (System.nanoTime() - start) / 1e6;
      start = System.nanoTime();
      for (int i = 0; i < searches; i++) {
        current.compute(1 + i % 10, goal);
        benchmarkResult = current.getWeights()[goal];
      }
      double newTime = (System.nanoTime() - start) / 1e6;
      if (pass >= 0) {
        before[pass] = oldTime;
        after[pass] = newTime;
      }
    }
    Arrays.sort(before);
    Arrays.sort(after);
    System.out.printf(
        "%s (%d searches): before %.2f ms; after %.2f ms%n", label, searches, before[2], after[2]);
  }

  private static void benchmark() {
    System.out.println(
        "Synthetic Dijkstra benchmarks: median of 5 passes after 3 warm-up passes; "
            + "construction excluded.");
    Network smallComponent = new Network(100000, false);
    for (int i = 1; i < 32; i++) {
      smallComponent.add(i, i + 1, 1);
    }
    benchmarkCase(smallComponent, 20, 200, "100000 nodes, nearby goal");
    benchmarkCase(smallComponent, 50000, 200, "100000 nodes, unreachable goal, 32 reachable nodes");
    Network broad = new Network(20001, false);
    for (int i = 1; i < 20000; i++) {
      for (int step : new int[] {1, 3, 7, 11}) {
        if (i + step <= 20000) {
          broad.add(i, i + step, 1 + (i % 17) * 0.1 + step * 0.3);
        }
      }
    }
    benchmarkCase(broad, 20001, 20, "20000 reachable nodes, broad search");
  }

  public static void main(String[] args) throws Exception {
    // A* deliberately reports unreachable goals; keep expected messages out of the check output.
    PrintStream originalError = System.err;
    try (PrintStream expectedDiagnostics = new PrintStream(new ByteArrayOutputStream())) {
      System.setErr(expectedDiagnostics);
      checkSingleGoals();
      checkDemandRows();
      checkSparseAndUnreachable();
      checkBoundaryCases();
      checkSharedGraphDestinations();
    } finally {
      System.setErr(originalError);
    }
    System.out.println("Shortest-path initialization checks passed.");
    if (Arrays.asList(args).contains("--benchmark")) {
      benchmark();
    }
  }
}

// Reference snapshots of the original heap algorithms, kept independent of the optimized classes.
class FullInitializationDijkstra {
  BinaryHeapNode[] upperBoundCosts;
  AdjacencyNode[] graph;
  int heapSize;
  double minWeight;
  int nbNodesToReach;
  int[] nodePos;
  int[] pi;
  double[] weights;
  BinaryHeapNode[] stock;
  private VirtualNetwork virtualNet;

  public FullInitializationDijkstra(AdjacencyNode[] graph) {
    this.graph = graph;
    weights = new double[graph.length];
    for (int i = 0; i < graph.length; i++) {
      weights[i] = Double.MAX_VALUE;
    }
    pi = new int[graph.length];
    stock = new BinaryHeapNode[graph.length];
    upperBoundCosts = new BinaryHeapNode[graph.length];
    // Initialization of binary heap specific implementation
    for (int i = 0; i < graph.length; i++) {
      stock[i] = new BinaryHeapNode();
    }
    nodePos = new int[graph.length];
  }

  public FullInitializationDijkstra(AdjacencyNode[] graph, VirtualNetwork virtualNet) {
    this(graph);
    this.virtualNet = virtualNet;
  }

  public void compute(int source, LinkedList<ODCell> demandList) {
    setNodesToReach(demandList);
    initializeSingleSource(source);
    int min = extractMin();
    while (min != -1) {
      weights[min] = minWeight;
      // Speed-Up test
      if (graph[min].isNodeToReach) {
        nbNodesToReach--;
        if (nbNodesToReach == 0) {
          break;
        }
      }
      for (AdjacencyNode cursor = graph[min]; cursor.nextNode != null; cursor = cursor.nextNode) {
        relax(graph[min].virtualNodeNum, cursor.nextNode.virtualNodeNum, cursor.edgeWeight);
      }
      min = extractMin();
    }
  }

  public void compute(int source, int goal) {
    initializeSingleSource(source);
    int min = extractMin();
    while (min != -1) {
      weights[min] = minWeight;
      if (min == goal) {
        break;
      }
      for (AdjacencyNode cursor = graph[min]; cursor.nextNode != null; cursor = cursor.nextNode) {
        relax(graph[min].virtualNodeNum, cursor.nextNode.virtualNodeNum, cursor.edgeWeight);
      }
      min = extractMin();
    }
  }

  public void decreaseKey(int nodeNum, double newVal) {
    int y = nodePos[nodeNum];
    upperBoundCosts[y].weight = newVal;
    int parent = y >> 1;
    while (parent != 0 && upperBoundCosts[y].weight < upperBoundCosts[parent].weight) {
      swap(y, parent);
      y = parent;
      parent = y >> 1;
    }
  }

  public int extractMin() {
    BinaryHeapNode min;
    if (heapSize < 1) {
      return -1;
    }
    min = upperBoundCosts[1];
    nodePos[upperBoundCosts[1].id] = -1; // node removed from node positions
    upperBoundCosts[1] = upperBoundCosts[heapSize];
    nodePos[upperBoundCosts[1].id] = 1; // largest value node now in position
    // one
    upperBoundCosts[heapSize] = null; // remove node
    heapSize--;
    heapify(1);
    minWeight = min.weight;
    return min.id;
  }

  public int[] getPredecessors() {
    return pi;
  }

  public double[] getWeights() {
    return weights;
  }

  public void heapify(int i) {
    int l = i << 1;
    int r = l + 1;
    int smallest;
    if (l <= heapSize && upperBoundCosts[l].weight < upperBoundCosts[i].weight) {
      smallest = l;
    } else {
      smallest = i;
    }
    if (r <= heapSize && upperBoundCosts[r].weight < upperBoundCosts[smallest].weight) {
      smallest = r;
    }
    if (smallest != i) {
      swap(i, smallest);
      heapify(smallest);
    }
  }

  public void initializeSingleSource(int source) {
    Arrays.fill(pi, 0);
    Arrays.fill(weights, Double.MAX_VALUE);
    for (int i = 0; i < upperBoundCosts.length; i++) {
      upperBoundCosts[i] = stock[i];
    }
    upperBoundCosts[1].init(source, 0);
    nodePos[source] = 1;
    // BHNodes greater than source
    int i = 2;
    for (int nextNodeNum = source + 1; nextNodeNum < graph.length; i++, nextNodeNum++) {
      upperBoundCosts[i].init(nextNodeNum, Double.MAX_VALUE);
      nodePos[nextNodeNum] = i;
    }
    // BHNodes less than source
    for (int nextNodeNum = 1; nextNodeNum < source; i++, nextNodeNum++) {
      upperBoundCosts[i].init(nextNodeNum, Double.MAX_VALUE);
      nodePos[nextNodeNum] = i;
    }
    heapSize = upperBoundCosts.length - 1;
  }

  public void relax(int u, int v, double w) {
    if (nodePos[v] == -1) {
      return;
    }
    if (upperBoundCosts[nodePos[v]].weight > weights[u] + w) {
      decreaseKey(v, weights[u] + w);
      pi[v] = u;
      heapify(nodePos[v]);
    }
  }

  private void setNodesToReach(LinkedList<ODCell> demandList) {
    for (int i = 1; i < graph.length; i++) {
      graph[i].isNodeToReach = false;
    }
    Iterator<ODCell> it = demandList.iterator();
    nbNodesToReach = 0;
    while (it.hasNext()) {
      ODCell demand = it.next();
      int index =
          virtualNet
              .getVirtualNodeLists()[
              virtualNet.getNodeIndexInVirtualNodeList(demand.getDestinationNodeId(), true)]
              .getUnloadingVirtualNodeId();
      if (!graph[index].isNodeToReach) {
        graph[index].isNodeToReach = true;
        nbNodesToReach++;
      }
    }
  }

  public void swap(int a, int b) {
    BinaryHeapNode temp = upperBoundCosts[a];
    upperBoundCosts[a] = upperBoundCosts[b];
    upperBoundCosts[b] = temp;
    int tempInt = nodePos[upperBoundCosts[a].id];
    nodePos[upperBoundCosts[a].id] = nodePos[upperBoundCosts[b].id];
    nodePos[upperBoundCosts[b].id] = tempInt;
  }
}

class FullInitializationAStar extends FullInitializationDijkstra {
  public FullInitializationAStar(AdjacencyNode[] graph) {
    super(graph);
  }

  public void compute(int source, int goal) {
    this.initializeSingleSource(graph, source);
    int min = extractMin();
    while (min != goal) {
      if (min == -1 || minWeight == Double.MAX_VALUE) {
        System.err.println("Goal not reachable from source.");
        return;
      }
      weights[min] = minWeight;
      for (AdjacencyNode curNode = graph[min];
          curNode.nextNode != null;
          curNode = curNode.nextNode) {
        int nextNodeNum = curNode.nextNode.virtualNodeNum;
        if (nodePos[nextNodeNum] != -1
            && upperBoundCosts[nodePos[nextNodeNum]].goalEstWeight == 0) {
          upperBoundCosts[nodePos[nextNodeNum]].updateGoalEstWeight(
              curNode.nextNode.goalEst(graph[goal]));
        }
        relax(graph[min].virtualNodeNum, nextNodeNum, curNode.edgeWeight);
      }
      min = extractMin();
    }
    if (min != -1) {
      weights[min] = minWeight;
    }
  }

  @Override
  public void decreaseKey(int nodeNum, double newVal) {
    int y = nodePos[nodeNum];
    upperBoundCosts[y].updateWeight(newVal);
    int parent = y >> 1;
    while (parent != 0 && upperBoundCosts[y].keyWeight < upperBoundCosts[parent].keyWeight) {
      swap(y, parent);
      y = parent;
      parent = y >> 1;
    }
  }

  @Override
  public void heapify(int i) {
    int l = i << 1;
    int r = l + 1;
    int smallest;
    if (l <= heapSize && upperBoundCosts[l].keyWeight < upperBoundCosts[i].keyWeight) {
      smallest = l;
    } else {
      smallest = i;
    }
    if (r <= heapSize && upperBoundCosts[r].keyWeight < upperBoundCosts[smallest].keyWeight) {
      smallest = r;
    }
    if (smallest != i) {
      swap(i, smallest);
      heapify(smallest);
    }
  }

  public void initializeSingleSource(AdjacencyNode[] graph, int source) {
    super.initializeSingleSource(source);
  }
}
