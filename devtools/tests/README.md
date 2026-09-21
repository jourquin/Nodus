# Regression checks

## Assignment timing checks

Run `sh devtools/tests/run-computing-times-test.sh` from the project root with a JDK 11 or later.
The standalone checks use a controlled clock and two threads to verify overlapping worker times,
database-time attribution, repeated cost passes, resetting between assignments, disabled auditing
and partial-run reporting. Detailed-stage checks also cover overlapping workers, exclusion of
path-output time (including writer waits), nested automatic flushes counted only once, repeated
alternatives and displaying only applicable stages. Compilation uses a temporary directory and
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
header-only and detailed path output, automatic/final flushes and failed modal splits. It uses a
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
