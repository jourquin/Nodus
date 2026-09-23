# Regression checks

## Assignment timing checks

Run `sh devtools/tests/run-computing-times-test.sh` from the project root with a JDK 11 or later.
The standalone checks use a controlled clock and two threads to verify overlapping worker times,
database-time attribution, repeated cost passes, resetting between assignments, disabled auditing
and partial-run reporting. Detailed-stage checks also cover overlapping workers, exclusion of
path-output time (including writer waits), nested automatic flushes counted only once, repeated
alternatives, displaying only applicable stages, and merging/resetting reachability counters across
workers. Compilation uses a temporary directory and
requires no external libraries.

With `NodusC.displayComputingTimes` enabled, every assignment algorithm prints a worker breakdown
in addition to the existing overall timings. The rows depend on the algorithm:

| Algorithm | Computation stages |
| --- | --- |
| Fast Multi-flow | Dijkstra, path reconstruction, header matching, modal splitting and path filtering, volume distribution. |
| Exact Multi-flow | A*, path reconstruction, header matching, modal splitting and path filtering, volume distribution. |
| All-or-Nothing, Incremental, MSA, Frank-Wolfe, Incremental–Frank-Wolfe | Dijkstra, path reconstruction and volume loading. |
| Static time-dependent | Dijkstra, path reconstruction and volume loading. |
| Dynamic time-dependent | Dijkstra, path reconstruction and volume loading, demand relocation. |

All algorithms also report **Path output (includes DB calls and writer waits)**.

| Measurement | Included work |
| --- | --- |
| Dijkstra / A* | Each shortest-path computation, including destination setup and heap initialization. |
| Path reconstruction | Following predecessors, collecting path costs/durations, marking edges, attaching demand cells and preparing multi-flow header metadata. |
| Path reconstruction and volume loading | Reconstructing single paths and applying their demand to current or auxiliary link volumes, including time-slice handling. These workers load volumes during reconstruction, so the two operations are measured together without adding a timer on every edge. |
| Header matching | The existing header-list scans for each OD cell, validity checks and quantity calculation in multi-flow assignments. |
| Modal splitting and path filtering | Duplicate, detour and intermodal filtering, followed by the configured modal-split method. |
| Volume distribution | Applying the computed multi-flow path shares to virtual-link volumes. |
| Demand relocation | Moving dynamic demands between network demand lists after assigning a time slice. |
| Path output (includes DB calls and writer waits) | Header/detail row preparation and buffering, automatic flushes and the worker's final partial-buffer flush, including waiting for the shared writer. |

These are **elapsed times summed across workers, not CPU times or separate wall times**. Computation
stages exclude path-output time, so writer contention is not attributed to reconstruction or header
matching. Path output overlaps the existing database total; its scope also includes writer waits,
while the database total additionally covers coordinator output, writer finalization, indexes,
commits and virtual-network output. Do not add either breakdown to the overall timing rows.

Setup, demand retrieval outside reconstruction, cost-markup updates and progress reporting remain
in the existing overall worker timers. Coordinator operations, such as equilibrium volume blending,
are not included in the worker breakdown; the overall assignment and existing cost/database timers
retain their original scopes. Counters accumulate locally and merge once per job, across groups,
iterations and time slices. Applicable stages print zero if unused, for example header matching
when path saving is disabled. Disabled auditing reads no clocks. Assignment computations and the
header-matching algorithm have not been optimized here.

`AssignmentAuditWorkerTest.java` exercises all eight concrete worker classes through the real job
loop on a synthetic network with two origins and an unreachable destination. Multi-flow workers
compute three alternatives. The test compares exact current/auxiliary volumes, modal shares and
saved path rows with auditing disabled/enabled, checks the applicable stages, and covers disabled,
header-only and detailed path output, automatic/final flushes and failed modal splits. It also checks
Fast Multi-flow reachability counts and verifies that the observer is absent when auditing is off.
It uses a
disposable in-memory HSQLDB database and a clock that advances on each read; its reported times are
test values, not performance measurements. Run it from the project root:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/AssignmentAuditWorkerTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.workers.AssignmentAuditWorkerTest
rm -r "$nodus_test_dir"
```

These checks are developer regression tests. To collect baseline runtimes for your own project,
enable `NodusC.displayComputingTimes` and run the assignment normally in Nodus.

## Fast Multi-flow unreachable-destination diagnostic

To measure the potential benefit of knowing unreachable destinations in advance, set
`NodusC.displayComputingTimes` to `true` and run your normal **Fast Multi-flow** assignment. A section
headed `Fast multi-flow unreachable-destination diagnostic` is printed below the timing breakdown.
No separate test script or synthetic input is needed to measure your project.

The section **Reachability preprocessing potential (includes first routes)** measures the
opportunity even with **Nb routes = 1**. It observes the full search, then uses hindsight to identify
the last requested destination that was actually reached. If some requested destinations remained
unreachable, work after that final reached destination could potentially have been avoided with
perfect advance knowledge. The last destination itself remains necessary; its outgoing edges are
part of the tail because Dijkstra could stop before examining them.

| Preprocessing-potential row | Meaning |
| --- | --- |
| Searches with mixed reachable/unreachable destinations | Completed searches which reached at least one requested destination and exhausted their finite frontier with others still missing. |
| Searches with no reachable destination | Completed searches with a nonempty destination set and no reached target. These are whole-search opportunities and are separate from mixed searches. |
| Nodes / Edges after last reachable destination | Work in the tails of mixed searches only. The final infinite-cost heap extraction is excluded from the node count. |
| Time after last reachable destination (worker sum) | Elapsed time in those tails, including final search bookkeeping. |
| Time with no reachable destination (worker sum) | Complete compute-call time for whole-search opportunities, including destination setup and heap initialization. |
| Upper-bound avoidable nodes / edges | Mixed-search tails plus the entire work of searches with no reachable destination. |
| Upper-bound avoidable Dijkstra time (worker sum) | Sum of the two time rows above; do not add them again. |
| Upper-bound share of edge examinations / observed Dijkstra time | The upper-bound work divided by all completed searches' work, including searches with no opportunity. A zero denominator prints `n/a`. |

**Look first at the two Upper-bound share rows.** A large unreachable-destination count alone does
not imply large savings: the final reachable target may be settled near the end of the search.
Empty destination sets and fully reached destination sets contribute no opportunity. Duplicate
destinations are counted once; the source itself counts if requested. Interrupted searches do not
contribute completed-search observations.

These are **retrospective upper bounds, not measured savings or assignment wall-time reductions**.
No reachability index is built, no destination is filtered and no search is shortened. Index
construction, lookup and memory costs are not measured. A topology index might prove fewer targets
unreachable than this ideal estimate, for example when numerical overflow prevents a finite-cost
route in an otherwise connected graph. This observation concerns each search independently, so it
does not need earlier alternatives or a nondecreasing markup. It does not establish that unsuitable
cost conditions are safe for an optimization.

The earlier rows, above this new subsection, retain their different meaning: **reuse between
alternative routes**. They may still show zero with one route, even when the new preprocessing
estimate is large. The two estimates overlap and must not be added together.

The observer runs the existing Dijkstra algorithm to its usual stopping point. After a completed
search exhausts its finite frontier, any requested destinations still missing become known
unreachable for the remaining alternatives of that origin/group/mode/means sequence. A later
search is observed at the point when all destinations not already known unreachable have been
settled. Work performed after that point is recorded as potentially avoidable. If every requested
destination was already known unreachable, the entire call is potentially skippable, including
its destination setup and heap initialization.

| Report row | Meaning |
| --- | --- |
| Completed Dijkstra searches | Searches which finished normally; interrupted searches remain in the existing stage time but are excluded from these diagnostic totals. |
| Searches ending with unreachable destinations | Searches which ran out of finite-cost nodes before reaching every unique requested destination. |
| Searches with previously known unreachable destinations | Searches requesting at least one destination proven unreachable in an earlier alternative of the same sequence. |
| Potentially shortenable searches | Searches which reached the hypothetical stopping point after doing some necessary work. |
| Potentially entirely skippable searches | Searches whose complete requested destination set was already known unreachable; separate from the preceding count. |
| Searches excluded from reuse estimates | Searches with unsuitable cost conditions, or where observing a previously unreachable target become reachable invalidated the sequence assumptions. Total work is still counted. |
| Nodes settled / Edges examined | Finite-cost node extractions and edge-relaxation attempts in the completed searches. Blocked/non-improving edges count as examined; the final infinite-cost heap extraction does not count as a settled node. |
| Potentially avoidable nodes / edges | Work after the hypothetical stopping point, including the complete work of entirely skippable searches. Outgoing edges from the final required destination count here if examined after that point. |
| Observed Dijkstra time (worker sum) | Elapsed time inside the observed compute calls, including the observer's counting overhead, but excluding its final counter aggregation and learning of newly unreachable targets. |
| Potentially avoidable Dijkstra time (worker sum) | The portion after the hypothetical stop, including full-call time for entirely skippable searches. |
| Potentially avoidable edge examinations / share of observed Dijkstra time | Percentages of the corresponding observed totals; `n/a` means the denominator is zero. |

For the reuse-between-alternatives estimate only, first searches must establish reachability and
contribute no savings estimate. Newly unreachable targets, for example after cost overflow, must
also be discovered before subsequent searches can reuse that knowledge. Duplicate OD destinations
are counted once in each search's target set.
Knowledge resets at every origin/mode/means boundary and worker job, including changes of group;
a source change also clears it defensively. Reuse estimates require nonnegative, non-NaN initial
edge costs and a finite markup multiplier of at least one. Infinite edge costs are allowed because
they represent blocked links. Cost decreases and non-finite multipliers disable reuse estimates.

For reuse between alternatives, inspect **Potentially avoidable share of observed Dijkstra time**,
together with its edge percentage and entirely skippable search count. A high count of unreachable
destinations alone is insufficient: a necessary destination might already require exploring almost
the whole graph.
These are worker sums and work counts, **not promised reductions in assignment wall time**. No
search is shortened by this diagnostic. The observer adds overhead, and all its time is already
inside the existing Dijkstra/assignment rows; do not add the diagnostic times to those rows.
The observer reads the clock at search boundaries and once per reached requested destination,
never for every edge or unrelated node. This extra measurement work is included in Dijkstra time.
With the audit disabled, Fast Multi-flow uses ordinary Dijkstra with the selected graph/heap
representation and no diagnostic counters, reachability storage or extra clock reads. Other
algorithms retain their existing timing reports.

`DijkstraReachabilityTest.java` uses synthetic graphs and a controlled clock to check exact node,
edge and time attribution for partial and whole-search opportunities, first searches, duplicate
and empty destination sets, unreachable targets introduced by overflow, sequence/source resets,
invalid cost conditions and failed searches. It also checks first-route opportunities, searches
with no reachable target, a last target at the end of the finite frontier, and the final target's
outgoing edges. An independent trace of ordinary Dijkstra checks all retrospective node/edge
counts, including 1,200 random searches across both graph/heap representations with cost ties,
zero-cost edges and loading restrictions. Complete predecessor and distance arrays must match.
No project or database is opened. Run these developer checks after changing the diagnostic:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/DijkstraReachabilityTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.shortestpath.DijkstraReachabilityTest
rm -r "$nodus_test_dir"
```

## Cost parser cache checks

`CostParserCacheTest.java` checks formula reuse, changing variable values, movement/node
transitions, formula overrides, custom functions and parser errors. Its inputs are in-memory test
layers and formulas; it does not open or modify a project or database. Run it after changing the
cost parser or its expression cache. Run the following commands from the project root:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/CostParserCacheTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.costs.CostParserCacheTest
rm -r "$nodus_test_dir"
```

## Service index checks

`ServiceIndexTest.java` checks indexed ID and stop lookups against linear searches, including
missing services, frequency/means edits, renaming, replacement, removal and reload. It also checks
edits through the live stop list (including iterator and sublist edits), cloning, serialization and
concurrent assignment readers. Inputs are synthetic services held in memory; no project, database
or GUI is opened. Run it after changing service storage or stop indexing, from the project root:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/ServiceIndexTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.services.ServiceIndexTest
rm -r "$nodus_test_dir"
```

These are developer regression checks, not runtime measurements. To measure an actual assignment,
use `NodusC.displayComputingTimes` and run the assignment normally in Nodus.

## Service database checks

`ServiceDatabaseTest.java` checks bulk loading and batched saving using disposable in-memory HSQLDB,
H2, SQLite and Derby databases and synthetic services. It compares loading with the previous
per-service queries and checks route order, repeated links, missing links, duplicate service IDs,
empty services, stop deduplication and migration from tables without a path index. Saving checks
include registry names, bounded and partial batches, individual-insert fallback, JDBC batch status
codes, resource closure and rollback of failed inserts to the caller's savepoint.

Run it after changing service persistence, from the project root. The bundled JDBC drivers are used;
no external database, project files or GUI are needed:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/ServiceDatabaseTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.services.ServiceDatabaseTest
rm -r "$nodus_test_dir"
```

The test also counts JDBC calls for 200 services containing 2,000 links and 1,000 stops: loading uses
3 data queries instead of 401, and saving with a batch size of 1,000 uses 4 batch executions instead
of 3,200 individual inserts. These are call counts, not elapsed-time measurements or guaranteed
network round trips. In Nodus, the batch limit uses `maxSqlBatchSize` (default 1,000), with individual
inserts when the driver does not support batches. Empty service tables need only one load query.
This optimization affects service loading and saving in the project/editor lifecycle; those
operations are not necessarily included in the assignment timing audit.

## Multi-flow edge update checks

`MultiFlowEdgeUpdatesTest.java` compares the affected-edge updates with the original full-graph
passes, using the real Dijkstra and A* implementations on synthetic networks. It checks alternative
routes, edge weights, path marks and assigned volumes for both Fast and Exact multi-flow schedules.
Cases include shared edges, multiple origins and commodity groups, unavailable loading modes,
unreachable destinations, discarded paths, successive OD rows, zero/infinite weights and cost
overflow. It also verifies that unused links are skipped during volume distribution.

Run it after changing multi-flow edge updates, from the project root. No project files, database
or GUI are used:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/MultiFlowEdgeUpdatesTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.workers.MultiFlowEdgeUpdatesTest
rm -r "$nodus_test_dir"
```

For performance measurements, run a Fast or Exact multi-flow assignment in Nodus with
`NodusC.displayComputingTimes` enabled and compare the path-computation and total times.

## Compact assignment shortest paths

All assignment algorithms now default to compact graph and heap arrays, controlled by
`NodusC.useCompactShortestPaths` (renamed from the earlier `useCompactFastMFDijkstra` switch).
Set it to `false` to compare with the original implementations:

| Assignment algorithms | Compact search |
| --- | --- |
| All-or-Nothing, Incremental, MSA, Frank-Wolfe, Incremental–Frank-Wolfe | Dijkstra |
| Static and dynamic time-dependent | Dijkstra |
| Fast Multi-flow | Dijkstra |
| Exact Multi-flow | A* |

```java
public static boolean displayComputingTimes = true;
public static boolean useCompactShortestPaths = true; // false selects the previous implementation
```

To benchmark your project, rebuild/relaunch Nodus after changing the switch and run the usual
assignment. Keep the same scenario inputs, thread count, iterations/routes, path-saving options
and other settings for both implementations. One route is sufficient for multi-flow algorithms.
Allow a warm-up assignment for each setting, then compare several measured runs (preferably their
medians). Compare **Total elapsed**, **Paths and flow assignment (wall)** and **Dijkstra** (or **A***
for Exact Multi-flow); the last is a worker elapsed sum, not assignment wall time. Both settings
retain the same timing breakdown and Fast Multi-flow's unreachable-destination diagnostic when
auditing is enabled. No separate test script is needed to measure your project. The gain will
depend on the algorithm and its workload; the Fast Multi-flow improvement does not establish a
speedup for the other algorithms.

The compact implementation stores outgoing destinations/costs in contiguous arrays and replaces
heap-node objects with primitive node IDs and tentative costs. It preserves adjacency order,
source-first heap ordering, strict comparisons for equal costs, sparse initialization and existing
stopping conditions. The linked adjacency list remains for reconstruction and volume loading.
Loading restrictions, alternative-route markups and weight restoration update both representations
in the existing affected-edge passes; there is no full cost-array copy between searches. The compact
graph is built once per worker job, and its setup is included in assignment/worker totals, outside
the Dijkstra/A* stage. Each new job rebuilds its compact arrays from current costs and topology,
including changed costs and excluded links between iterations, OD classes, groups or time slices.
Incremental–Frank-Wolfe uses the compact searches in both its Incremental and Frank-Wolfe phases.
Within-job topology is fixed. A* additionally uses primitive coordinate, heuristic and combined-key
arrays, preserving the original distance formula, zero-estimate sentinel, tie comparisons,
non-finite-value behavior and downward heap repair. Ordinary Dijkstra does not allocate these
additional arrays. Service-route searches retain their previous graph and heap.

`CompactDijkstraTest.java` compares 8,000 single-goal and OD-row searches against the previous
implementation. It checks full predecessor/distance arrays, exact extraction order and edge counts,
heap positions, sparse resets, direct heap operations, changing sources/costs, ties, parallel edges,
self loops, duplicates, empty rows, disconnected nodes and infinite/NaN/overflowed costs. Its optional
benchmark uses synthetic graphs, warms up both implementations, alternates their execution order,
and reports the median of five search batches plus separate compact-construction time. These
figures are not promises of end-to-end assignment gains.

`CompactAStarTest.java` adds 4,800 differential searches with changing sources/goals/costs, zero
and nonzero heuristics, non-finite estimates, equal-cost paths and disconnected targets. It checks
predecessors, distances, extraction order, edge counts, heap positions, sparse resets and direct
heap operations against the original A* implementation.

`MultiFlowEdgeUpdatesTest.java` additionally checks compact cost synchronization for both Dijkstra
and A* across loading restrictions, alternatives, origins and groups. `AssignmentAuditWorkerTest.java`
compares actual saved headers/details, current/auxiliary/time-slice volumes, modal shares and
relocated demand for all eight concrete worker types, with compact storage and auditing independently
on/off. Both multi-flow methods run with one and three routes and cover failed modal splits. Repeated
jobs exclude/reopen an edge and change its alternative's cost; dynamic jobs also advance through three
time slices and relocate demand. These fixtures exercise worker behavior, not the full GUI/coordinator
convergence loop. The reachability diagnostic checks also run against both representations. Run from
the project root:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/CompactDijkstraTest.java devtools/tests/CompactAStarTest.java devtools/tests/MultiFlowEdgeUpdatesTest.java devtools/tests/AssignmentAuditWorkerTest.java devtools/tests/DijkstraReachabilityTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.shortestpath.CompactDijkstraTest --benchmark
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.shortestpath.CompactAStarTest
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.workers.MultiFlowEdgeUpdatesTest
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.workers.AssignmentAuditWorkerTest
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.shortestpath.DijkstraReachabilityTest
rm -r "$nodus_test_dir"
```

## Shortest-path initialization checks

`ShortestPathInitializationTest.java` compares Dijkstra and A* with snapshots of their previous
implementations. It checks predecessor trees (including equal-cost route choices) and path weights
across 8,000 single-destination searches and 1,000 OD rows. Cases include changing sources and costs,
zero-cost edges, duplicate destinations, empty OD rows, disconnected networks, independent searches
sharing a graph, and infinite/overflowed costs. It also checks that small searches allocate only a
small part of the heap and stop before traversing unreachable nodes.

Run it after changing the shortest-path algorithms, from the project root. All inputs are synthetic
in-memory graphs; no project, GUI or database is opened:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/ShortestPathInitializationTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.shortestpath.ShortestPathInitializationTest
rm -r "$nodus_test_dir"
```

To also run the optional synthetic benchmark, append `--benchmark` to the `java` command before
removing the temporary directory. It compares the previous and current Dijkstra implementations for
nearby goals, unreachable goals in a small connected component, and searches over most of a graph.
Reported times are medians of five batches after three warm-up batches and exclude graph/search-object
construction. These are shortest-path measurements; use `NodusC.displayComputingTimes` for complete
assignment measurements on your projects.

## Assignment path buffering checks

`PathWriterBufferTest.java` checks path output using synthetic demands and links in disposable
in-memory HSQLDB, H2, SQLite and Derby databases. It compares 20,000 header conversions with the
previous numeric bindings, including three-decimal rounding, float lengths, legacy durations and
different locales. It also checks stored headers and repeated/directional links, concurrent workers,
stable header/detail associations, mutable input snapshots, bounded and partial buffers, JDBC batch
fallback, failed writes, cancellation, finalization, header-only output and disabled output.

The equilibrium checks verify that completed worker buffers reach JDBC before quantities are split.
These SQL checks run on HSQLDB, H2 and SQLite; Derby lacks the `ROUND` function used by the existing
split SQL, so its partial rows are checked through finalization instead.

`AssignmentPathBufferWorkerTest.java` exercises the actual assignment-worker job loop on HSQLDB.
It checks successive successful jobs, failed jobs, explicit cancellation and exceptions, and verifies
that preparation and flushing remain included in the assignment's database timing.

Run after changing path saving or worker output handling, from the project root:

```sh
ant build-project
nodus_test_dir=$(mktemp -d)
javac --release 11 -cp 'classes:lib/*:lib/groovy/*:jdbcDrivers/*' -d "$nodus_test_dir" devtools/tests/PathWriterBufferTest.java devtools/tests/AssignmentPathBufferWorkerTest.java
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.virtual.PathWriterBufferTest
java -Djava.awt.headless=true -cp "$nodus_test_dir:classes:lib/*:lib/groovy/*:jdbcDrivers/*" edu.uclouvain.core.nodus.compute.assign.workers.AssignmentPathBufferWorkerTest
rm -r "$nodus_test_dir"
```

The concurrent fixture submits 8,402 rows through 13 shared-writer block handoffs. It also verifies
that workers can prepare rows while another thread holds the writer lock, and that JDBC statements
are never used concurrently. These are concurrency and call-count checks, not runtime benchmarks.

Worker buffers hold at most `min(maxSqlBatchSize, 1000)` rows, with a minimum of one. Formatting and
link resolution occur on each worker; only binding and writing the prepared rows share the writer
lock. Successful jobs flush their final partial buffer before an iteration or time slice ends.
The existing JDBC batch limit still controls actual database batch execution.

To measure real gains, run assignments with path saving enabled and use
`NodusC.displayComputingTimes`. Compare total elapsed and path wall time; the database total includes
row preparation summed across workers, so concurrent preparation can overlap in that total.
