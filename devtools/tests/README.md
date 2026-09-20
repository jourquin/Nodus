# Regression checks

## Assignment timing checks

Run `sh devtools/tests/run-computing-times-test.sh` from the project root with a JDK 11 or later.
The standalone checks use a controlled clock and two threads to verify overlapping worker times,
database-time attribution, repeated cost passes, resetting between assignments, disabled auditing
and partial-run reporting. Compilation uses a temporary directory and requires no external libraries.

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
