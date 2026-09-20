package edu.uclouvain.core.nodus.compute.assign.workers;

import edu.uclouvain.core.nodus.NodusC;
import edu.uclouvain.core.nodus.compute.assign.modalsplit.Path;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.AdjacencyNode;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapAStar;
import edu.uclouvain.core.nodus.compute.assign.shortestpath.BinaryHeapDijkstra;
import edu.uclouvain.core.nodus.compute.virtual.PathODCell;
import edu.uclouvain.core.nodus.compute.virtual.VirtualLink;
import edu.uclouvain.core.nodus.compute.virtual.VirtualNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Compares sparse updates with the original full-graph passes using in-memory networks. */
public final class MultiFlowEdgeUpdatesTest {
  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static void equal(double expected, double actual, String message) {
    check(
        Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual),
        message + ": " + expected + " vs " + actual);
  }

  private static final class CountingLink extends VirtualLink {
    int spreads;
    int typeReads;

    CountingLink(int id, VirtualNode begin, VirtualNode end, byte type) {
      super(id, 0, 0, begin, end, type);
      setNbGroups(2, 1);
    }

    @Override
    public byte getType() {
      typeReads++;
      return super.getType();
    }

    @Override
    public void spreadFlowOverPaths(byte group, Path[] paths) {
      spreads++;
      super.spreadFlowOverPaths(group, paths);
    }

    @Override
    public void spreadVolumeOverPaths(byte group, Path[][] paths) {
      spreads++;
      super.spreadVolumeOverPaths(group, paths);
    }
  }

  private static final class Network {
    final VirtualNode[] nodes;
    final AdjacencyNode[] graph;
    final List<AdjacencyNode> edges = new ArrayList<>();
    final List<CountingLink> links = new ArrayList<>();

    Network(int size) {
      nodes = new VirtualNode[size + 1];
      graph = new AdjacencyNode[size + 1];
      for (int i = 1; i <= size; i++) {
        byte mode = (byte) (i == 3 || i == 8 || i == 9 ? 2 : 1);
        nodes[i] = new VirtualNode(i, i, 0, mode, (byte) 1, (short) 0, 0, 0);
        graph[i] = new AdjacencyNode(nodes[i]);
      }
    }

    AdjacencyNode add(int from, int to, byte type, double cost) {
      CountingLink link = new CountingLink(links.size() + 1, nodes[from], nodes[to], type);
      link.setCost((byte) 0, cost);
      link.setCost((byte) 1, cost);
      links.add(link);
      AdjacencyNode tail = graph[from];
      while (tail.nextNode != null) {
        tail = tail.nextNode;
      }
      tail.setNext(new AdjacencyNode(nodes[to]), (byte) 0, link);
      edges.add(tail);
      return tail;
    }

    boolean canLoad(int from, int modeMeans) {
      for (AdjacencyNode edge = graph[from]; edge.virtualLink != null; edge = edge.nextNode) {
        if (edge.virtualLink.getType() == VirtualLink.TYPE_LOAD
            && edge.virtualLink.getEndVirtualNode().getModeMeansKey() == modeMeans) {
          return true;
        }
      }
      return false;
    }
  }

  private static Network routes() {
    Network n = new Network(150);
    n.add(1, 2, VirtualLink.TYPE_LOAD, 0);
    n.add(1, 3, VirtualLink.TYPE_LOAD, 0.2);
    n.add(2, 4, VirtualLink.TYPE_MOVE, 1);
    n.add(4, 6, VirtualLink.TYPE_TRANSIT, 1);
    n.add(4, 7, VirtualLink.TYPE_STOP, 2);
    n.add(2, 5, VirtualLink.TYPE_SWITCH, 2);
    n.add(5, 6, VirtualLink.TYPE_TRANSIT, 1.1);
    n.add(5, 7, VirtualLink.TYPE_TRANSIT, 1.5);
    n.add(6, 10, VirtualLink.TYPE_UNLOAD, 0.1);
    n.add(7, 11, VirtualLink.TYPE_UNLOAD, 0.1);
    n.add(3, 8, VirtualLink.TYPE_MOVE, 2);
    n.add(3, 9, VirtualLink.TYPE_MOVE, 2.5);
    n.add(8, 10, VirtualLink.TYPE_UNLOAD, 0.1);
    n.add(9, 11, VirtualLink.TYPE_UNLOAD, 0.1);
    n.add(4, 8, VirtualLink.TYPE_TRANSHIP, 3);
    n.add(12, 4, VirtualLink.TYPE_LOAD, 0.2);
    n.add(12, 3, VirtualLink.TYPE_LOAD, 0.3);
    n.add(13, 2, VirtualLink.TYPE_LOAD, 0.1); // Second mode unavailable at this origin.
    n.add(2, 8, VirtualLink.TYPE_MOVE, Double.POSITIVE_INFINITY);
    for (int i = 15; i < 150; i++) {
      n.add(i, i + 1, VirtualLink.TYPE_MOVE, i); // Unused part of the network.
    }
    return n;
  }

  /** Reference operations copied from the original workers' full-graph loops. */
  private static final class FullScan {
    final AdjacencyNode[] graph;

    FullScan(AdjacencyNode[] graph) {
      this.graph = graph;
    }

    void restrictLoading(int modeMeans) {
      for (int i = 1; i < graph.length; i++) {
        for (AdjacencyNode edge = graph[i];
            edge != null && edge.virtualLink != null;
            edge = edge.nextNode) {
          if (edge.virtualLink.getType() == VirtualLink.TYPE_LOAD
              && edge.virtualLink.getEndVirtualNode().getModeMeansKey() != modeMeans) {
            edge.edgeWeight = Double.POSITIVE_INFINITY;
          }
        }
      }
    }

    void increaseCosts(double multiplier, boolean fast) {
      for (int i = 1; i < graph.length; i++) {
        for (AdjacencyNode edge = graph[i]; edge != null; edge = edge.nextNode) {
          if (edge.inCurrentTree && (!fast || !edge.isIncreased)) {
            edge.edgeWeight *= multiplier;
            if (fast) {
              edge.isIncreased = true;
            }
          }
        }
      }
      if (fast) {
        for (int i = 1; i < graph.length; i++) {
          for (AdjacencyNode edge = graph[i]; edge != null; edge = edge.nextNode) {
            edge.isIncreased = false;
            edge.inCurrentTree = false;
          }
        }
      }
    }

    void restoreWeights(boolean fast) {
      for (int i = 1; i < graph.length; i++) {
        for (AdjacencyNode edge = graph[i]; edge != null; edge = edge.nextNode) {
          edge.edgeWeight = edge.originalEdgeWeight;
          if (!fast) {
            edge.inCurrentTree = false;
          }
        }
      }
    }

    void spread(byte group, Path[][] paths, boolean fast) {
      for (int i = 1; i < graph.length; i++) {
        for (AdjacencyNode edge = graph[i]; edge != null; edge = edge.nextNode) {
          if (edge.virtualLink != null) {
            if (fast) {
              edge.virtualLink.spreadVolumeOverPaths(group, paths);
            } else {
              edge.virtualLink.spreadFlowOverPaths(group, column(paths));
            }
          }
        }
      }
    }
  }

  private static Path[] column(Path[][] paths) {
    Path[] result = new Path[paths.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = paths[i][0];
    }
    return result;
  }

  private static void compare(Network expected, Network actual) {
    for (int i = 0; i < expected.edges.size(); i++) {
      AdjacencyNode a = expected.edges.get(i);
      AdjacencyNode b = actual.edges.get(i);
      equal(a.edgeWeight, b.edgeWeight, "Edge weight " + i);
      check(a.inCurrentTree == b.inCurrentTree, "Path mark differs on edge " + i);
      check(a.isIncreased == b.isIncreased, "Increase flag differs on edge " + i);
      for (byte group = 0; group < 2; group++) {
        equal(
            a.virtualLink.getCurrentVolume(group),
            b.virtualLink.getCurrentVolume(group),
            "Volume on edge " + i + ", group " + group);
      }
    }
  }

  private static List<Integer> mark(
      Network network,
      MultiFlowEdgeUpdates updates,
      int[] pi,
      int source,
      int target,
      byte group,
      int alternative,
      int destination,
      Path path) {
    List<Integer> route = new ArrayList<>();
    PathODCell cell = new PathODCell(alternative, destination, 100.25 + target);
    for (int node = target; node != source; node = pi[node]) {
      if (pi[node] == 0) {
        path.isValid = false;
        break;
      }
      AdjacencyNode edge = network.graph[pi[node]];
      while (edge.endVirtualNode != node) {
        edge = edge.nextNode;
      }
      if (updates == null) {
        edge.inCurrentTree = true;
      } else {
        updates.markPathEdge(edge);
      }
      edge.virtualLink.addCell(group, cell);
      route.add(edge.virtualLink.getId());
    }
    return route;
  }

  /** Drives real shortest-path algorithms through the two workers' update/reset schedules. */
  private static void checkRoutes(boolean fast, int iterations, double markup) {
    Network expected = routes();
    Network actual = routes();
    FullScan full = new FullScan(expected.graph);
    MultiFlowEdgeUpdates sparse = new MultiFlowEdgeUpdates(actual.graph);
    BinaryHeapDijkstra oldSearch =
        fast ? new BinaryHeapDijkstra(expected.graph) : new BinaryHeapAStar(expected.graph);
    BinaryHeapDijkstra newSearch =
        fast ? new BinaryHeapDijkstra(actual.graph) : new BinaryHeapAStar(actual.graph);
    int[] modes = {NodusC.MAXMM + 1, 2 * NodusC.MAXMM + 1, 3 * NodusC.MAXMM + 1};
    for (byte group = 0; group < 2; group++) {
      // Repeated origins exercise marks left by the final Fast MF alternative.
      for (int source : new int[] {1, 12, 13, 1}) {
        int[][] batches = fast ? new int[][] {{10, 11, 14}} : new int[][] {{10}, {11}};
        for (int[] targets : batches) {
          Path[][] paths = new Path[modes.length * iterations][targets.length];
          int alternative = 0;
          for (int mode : modes) {
            boolean canLoad = expected.canLoad(source, mode);
            if (canLoad) {
              full.restrictLoading(mode);
              sparse.restrictLoading(mode);
            }
            compare(expected, actual);
            for (int iteration = 0; iteration < iterations; iteration++, alternative++) {
              if (fast && canLoad) {
                // Unreachable goal 14 makes Dijkstra exhaust the reachable tree for all targets.
                oldSearch.compute(source, 14);
                newSearch.compute(source, 14);
              }
              for (int destination = 0; destination < targets.length; destination++) {
                Path path = new Path();
                paths[alternative][destination] = path;
                if (fast && !canLoad) {
                  path.isValid = false;
                  continue;
                }
                if (!fast) {
                  oldSearch.compute(source, targets[destination]);
                  newSearch.compute(source, targets[destination]);
                }
                check(
                    Arrays.equals(oldSearch.getPredecessors(), newSearch.getPredecessors()),
                    "Predecessor tree differs");
                Path referencePath = new Path();
                List<Integer> oldRoute =
                    mark(
                        expected,
                        null,
                        oldSearch.getPredecessors(),
                        source,
                        targets[destination],
                        group,
                        alternative,
                        destination,
                        referencePath);
                List<Integer> newRoute =
                    mark(
                        actual,
                        sparse,
                        newSearch.getPredecessors(),
                        source,
                        targets[destination],
                        group,
                        alternative,
                        destination,
                        path);
                check(
                    oldRoute.equals(newRoute) && referencePath.isValid == path.isValid,
                    "Alternative route differs");
                path.marketShare = 1.0 / (alternative + destination + 2);
                // Modal split may discard a route after its edges have received demand cells.
                if (alternative % 4 == 3) {
                  path.isValid = false;
                }
              }
              if ((!fast || canLoad) && iteration < iterations - 1) {
                full.increaseCosts(1 + markup, fast);
                sparse.increaseCosts(1 + markup);
                if (fast) {
                  sparse.clearPathMarks();
                }
              }
              compare(expected, actual);
            }
            full.restoreWeights(fast);
            sparse.restoreWeights();
            if (!fast) {
              sparse.clearPathMarks();
            }
            compare(expected, actual);
          }
          full.spread(group, paths, fast);
          if (fast) {
            sparse.spreadVolumes(group, paths);
          } else {
            sparse.spreadVolumes(group, column(paths));
          }
          compare(expected, actual);
          // Demand cells must have been consumed, including those belonging to discarded paths.
          full.spread(group, paths, fast);
          compare(expected, actual);
        }
      }
    }
    for (int i = 0; i < actual.links.size(); i++) {
      if (actual.links.get(i).getBeginVirtualNode().getId() >= 15) {
        check(
            actual.links.get(i).spreads == 0, "Untouched link was visited while spreading volumes");
        check(expected.links.get(i).spreads > 0, "Reference did not scan unused links");
      }
    }
  }

  private static void checkSpecialWeightsAndIsolation() {
    Network n = new Network(4);
    AdjacencyNode zero = n.add(1, 2, VirtualLink.TYPE_LOAD, 0.0);
    AdjacencyNode infinite = n.add(2, 3, VirtualLink.TYPE_MOVE, Double.POSITIVE_INFINITY);
    AdjacencyNode huge = n.add(3, 4, VirtualLink.TYPE_UNLOAD, Double.MAX_VALUE);
    MultiFlowEdgeUpdates first = new MultiFlowEdgeUpdates(n.graph);
    Network other = new Network(4);
    other.add(1, 2, VirtualLink.TYPE_LOAD, 7.0);
    MultiFlowEdgeUpdates second = new MultiFlowEdgeUpdates(other.graph);
    for (AdjacencyNode edge : n.edges) {
      first.markPathEdge(edge);
      first.markPathEdge(edge);
    }
    first.increaseCosts(2);
    equal(0, zero.edgeWeight, "Zero cost");
    equal(Double.POSITIVE_INFINITY, infinite.edgeWeight, "Infinite cost");
    equal(Double.POSITIVE_INFINITY, huge.edgeWeight, "Overflow");
    second.restoreWeights();
    equal(7, other.edges.get(0).edgeWeight, "Worker isolation");
    first.restoreWeights();
    equal(Double.MAX_VALUE, huge.edgeWeight, "Original weight after overflow");
    first.clearPathMarks();

    int[] reads = n.links.stream().mapToInt(link -> link.typeReads).toArray();
    first.restrictLoading(2 * NodusC.MAXMM + 1);
    equal(Double.POSITIVE_INFINITY, zero.edgeWeight, "Loading restriction");
    for (int i = 0; i < reads.length; i++) {
      check(reads[i] == n.links.get(i).typeReads, "Loading restriction rescanned link types");
    }
    first.restoreWeights();
    equal(0, zero.edgeWeight, "Loading weight reset");
  }

  public static void main(String[] args) {
    for (boolean fast : new boolean[] {true, false}) {
      for (int iterations : new int[] {1, 2, 4}) {
        for (double markup : new double[] {0, 0.2, 1}) {
          checkRoutes(fast, iterations, markup);
        }
      }
    }
    checkSpecialWeightsAndIsolation();
    System.out.println("Multi-flow edge update checks passed.");
  }
}
