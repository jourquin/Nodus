# Nodus tests

## Running with Ant

Use the dedicated `build-tests.xml` from the repository root, with JDK 11 or later
and a full Apache Ant 1.10.6+ installation (including `ant-junitlauncher.jar` in
Ant's `lib` directory):

```sh
ant -f build-tests.xml
```

The default target is `Test`; `ant -f build-tests.xml Test` is equivalent. It builds
the application using the normal build, recompiles the tests, then runs JUnit
Jupiter in a separate JVM with `java.awt.headless=true`.

The suite includes unit tests, database tests and complete assignment integration tests.
No project data, external database, network access, or graphical desktop is needed. Database tests
create a fresh H2 database in memory for each test and close it afterwards.
File import/export tests use JUnit temporary directories, which are removed after
the tests. They never read or overwrite files in an existing Nodus project.
Dependencies are included in the repository: JUnit 5 and OpenTest4J under
`lib/groovy/`, API Guardian under `devtools/junit/`, and H2 under `lib/`.

For a clean application rebuild followed by the tests:

```sh
ant -f build-tests.xml clean Test
```

To run one class (quote the pattern so the shell does not expand it):

```sh
ant -f build-tests.xml '-Dtest.includes=**/CostExpressionCacheTest.class' Test
```

The same filter can select an integration test, for example
`**/PathWriterIntegrationTest.class`. The default `**/*Test.class` runs both kinds.
The build fails on test failures, execution errors, or a class pattern that matches
nothing. Each run replaces the previous text/XML reports in `test-build/reports/`.
Compiled tests are in `test-build/classes/`; neither is included in the application
jar. To remove both:

```sh
ant -f build-tests.xml CleanTests
```

## Running in Eclipse

In Eclipse, refresh the project, then right-click `build-tests.xml` and choose
**Run As > Ant Build**. Its default target is `Test`, so this runs the full suite.
To select targets explicitly, use **Run As > Ant Build...** and the **Targets** tab.
Do not launch `build.xml` with its default target: that only builds the application.

Eclipse's Ant launcher can require a newer Java runtime than Nodus itself. If an Ant
launch exits with no console output, open **Run As > Ant Build... > JRE** and select
a compatible installed JDK under **Separate JRE**. The Eclipse launcher used here
requires Java 17 or later; GraalVM 25 has been verified. Launching it with Java 11
causes a startup failure and an `Accept timed out` entry in Eclipse's Error Log.
The project compiler setting and the Ant launch runtime are separate settings.
The application and tests still compile for Java 11, while the forked tests use
Ant's runtime. Standalone `ant -f build-tests.xml` can still run under JDK 11.

Eclipse also recognizes `test/` as a test source folder with a separate output
directory. To use Eclipse's test results view, select the folder or a test class
and choose **Run As > JUnit Test** with JUnit 5. This does not use the Ant targets
or produce Ant's reports.

## Build-file arrangement

`build-tests.xml` imports the application build so Eclipse can resolve its `build`
dependency. `build-user.xml` contains forwarding targets, preserving `ant Test` and
`ant CleanTests` even after Eclipse regenerates `build.xml`. The forwarding targets
also work when launched directly from `build-user.xml`. Keep the test implementation
in `build-tests.xml`; do not add it to Eclipse's automatic buildfile imports, as it
already imports `build.xml`.

## Continuous integration on GitHub

[The Tests workflow](../.github/workflows/tests.yml) runs the complete suite on
pushes and pull requests, with separate jobs for Temurin JDK 11 and 25 on Ubuntu.
Both jobs compile for Java 11 and execute the tests on their selected runtime.
The workflow downloads the full Apache Ant 1.10.17 distribution, verifies its
SHA-512 checksum, and checks that `ant-junitlauncher.jar` is present. It runs:

```sh
ant -noinput -f build-tests.xml clean Test
```

To activate it, commit and push the workflow together with the test sources,
`build-tests.xml`, the other build changes, and `devtools/junit/`. If Actions is
disabled for the repository, enable it under **Settings > Actions > General**.
No custom secrets or database setup are required.

Open the repository's **Actions** tab and select **Tests** to inspect runs.
Compilation errors or failing tests make the corresponding check fail. The run's
**Artifacts** section contains `test-reports-java-11` and `test-reports-java-25`,
with the text and JUnit XML reports retained for 14 days. Reports are uploaded
even after test failures; a failure before test execution may leave no reports.
After the workflow is on the default branch, **Actions > Tests > Run workflow**
also allows a manual run.

For CI to prevent merging a failing pull request, add **Tests (Java 11)** and
**Tests (Java 25)** as required status checks in the default branch's ruleset or
branch protection rule after the first run. Running CI alone does not require
those checks to pass before merging.

The same Ant command works with other CI providers: install a JDK and a full Ant
distribution, then collect `test-build/reports/TEST-*.xml` as JUnit test results.
See [GitHub's Ant CI guide](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-ant)
for the workflow mechanism.

## Coverage

- `AllOrNothingAssignmentIntegrationTest`: complete all-or-nothing assignments on a four-node
  network with independently known results, including real demand loading, virtual-network
  generation, cost evaluation, worker jobs, vehicle conversion and saved H2 outputs. Checks
  paths, directional flows by commodity group, conservation and total costs; compares one
  worker with four; closes links to exercise rerouting and unreachable demand; and repeats
  an assignment to detect accumulated flows or duplicate outputs. Only project inputs and
  presentation callbacks are supplied by the fixture. See the reference case below.
- `SQLConsoleExportTest`: real CSV/CSVH, DBF, XLS and XLSX exports; cancelling or
  accepting direct overwrites; new outputs; loaded scripts, pasted batches, variable
  definitions and `runBatch()`; restoring confirmation after a script; and execution
  without a desktop window. Only the dialog response is substituted. Exported files
  are read back to check their contents and the project connection remains open.
- `CsvImportExportIntegrationTest`: quoted headers and fields, commas, quotes, Unicode,
  embedded line breaks, empty fields, exact decimal values, header/no-header round trips,
  empty files, batching, missing files, and malformed records. Failed imports must restore
  existing rows, preserve earlier uncommitted work, and restore the connection's transaction
  mode. Extra fields must be rejected rather than silently discarded. Expected errors are
  captured through the importer's error reporter instead of opening dialogs.
- `TabularFileIntegrationTest`: DBF rows, dates and decimal scale; XLS/XLSX text and numeric
  cells, Unicode and line breaks, schema recreation on import, and blank numeric cells for
  SQL NULLs. Checks exported files independently before importing them back. CSV NULLs
  export as empty fields; these tests do not imply lossless NULL round trips or arbitrary
  decimal precision in Excel's numeric cells.
- `ShortestPathTest`: Dijkstra and A*, each with linked and compact storage. Covers known
  routes, equal-cost choices, parallel edges, zero-cost cycles, disconnected nodes, changing
  sources/goals, updated costs, non-finite costs, and admissible geometric heuristics.
  Seeded small networks are also checked against an independent Floyd-Warshall reference,
  including the cost of reconstructed predecessor paths. The random cases use zero
  coordinates (a zero heuristic); the geometric case exercises a nonzero A* heuristic.
- `CostExpressionCacheTest`: changing values, recreated or missing variables, expressions
  simplified by the parser, changed constants, different scopes/formulas, and syntax errors.
- `CostParameterTest`: precedence and isolation of numeric parameters by scenario, commodity
  group and OD class, zero/negative overrides, inherited defaults, and NaN fallbacks.
- `ModalSplitTest`: proportional and multinomial-logit shares, normalization across modes
  and paths, large costs, and valid alternatives alongside non-finite path costs.
- `AbrahamTest`: inverse-power shares between modes and their alternatives, default and
  group-specific exponents, independent worker clones, invariance to cost units, and
  numerical stability for steep exponents and extreme positive cost ranges.
- `AssignmentCancellationTest`: end-of-work markers, joining all workers, coordinator
  interruption, cancellation while waiting for a first job, and malformed queued work.
  Uses real assignment-worker threads with latches and bounded waits, without loading a project.
  Verifies that normal cancellation is quiet and captures/asserts the expected error from
  deliberately malformed work, so a passing run does not print misleading stack traces.
- `PathWeightsTest`: all seven cost and duration components contribute to totals.
- `VirtualLinkFlowTest`: exact and fast multi-flow demand distribution, rejected paths,
  demand consumed only once, independent commodity groups and dynamic time slices,
  equilibrium blending, vehicle rounding, directional passenger-car units, and projections
  that leave stored flows unchanged.
- `MultiFlowEdgeUpdatesTest`: shared-edge penalties, loading restrictions on intermediate
  nodes, separate lifetimes of path marks and pending demand, and restoration between
  searches. Each case runs against linked and compact storage and checks resulting routes
  where applicable.
- `PathWriterIntegrationTest`: real H2 inserts and commits, numeric snapshots and all header
  columns, repeated and reverse links, automatic flushes in the middle of a path, explicit
  and implicit path IDs, equilibrium quantity updates, legacy duration rounding, discarded
  output, invalid input and SQL failures, and concurrent buffers sharing one writer.
- `VirtualNodeListDemandTest`: duplicate demand aggregation, independent destinations,
  commodity groups, OD classes and departure times, relocation to separate loading nodes
  or transit nodes, removal, and preservation of stored demand when a returned list changes.
  Includes regressions for static and timed demands incorrectly merged across OD classes.
- `NodeRuleTest`: exact and wildcard matching, direction, transit exceptions, and precedence
  of selected-scenario and commodity-group rules over generic rules.
- `VehiclesParserTest`: capacity and passenger-car-unit property precedence, isolation
  between scenarios, groups and mode/means, default values, fractional values, and repeated
  initialization.
- `TransportServiceTest`: ordered stops and route links, duplicate/null handling, live edits
  through lists and iterators, bulk replacements/removals, and independent cloned stop indexes.
- `ServiceRegistryTest`: service replacement and renaming, duplicate IDs, removal through
  iterators, visibility of stop edits, protected collection views, and clearing cached lookups
  before another project is loaded.

The suite does not yet cover loading complete projects from disk, other database engines,
or GUI workflows. The assignment fixture supplies in-memory Esri layers and DBF table models;
it does not test shapefile import or project-property file loading. Concurrency coverage
includes complete assignments with two commodity jobs, path output, and worker cancellation.
Modal-split calibration and invalid vehicle properties that display dialogs remain outside
this headless suite. Complete equilibrium convergence and line-search behavior still need
reference-project integration tests.

## Four-node assignment reference case

`AllOrNothingAssignmentIntegrationTest` builds four nodes, A=1, B=2, C=3 and D=4,
with the following links. Nodus generates both travel directions for each link.

| Link ID | Endpoints | Cost per unit, either direction |
| --- | --- | ---: |
| 11 | A–B | 2 |
| 12 | B–D | 3 |
| 13 | A–C | 4 |
| 14 | C–D | 4 |

There is one mode and means (1,1), zero loading/unloading/transit costs, and a vehicle
load of 10 units. Demand is 100 units from A to D in commodity group 1 and 40 from B
to D in group 2. Separate groups provide two real jobs for the concurrent-worker test.

| Case | Expected routes | Assigned quantity | Quantity × path cost |
| --- | --- | ---: | ---: |
| Baseline | A–B–D: 100 at cost 5; B–D: 40 at cost 3 | 140 | 620 |
| B–D closed | A–C–D: 100 at cost 8; B–A–C–D: 40 at cost 10 | 140 | 1,200 |
| A–B and B–D closed | A–C–D: 100 at cost 8; B's 40 units unassigned | 100 | 800 |

In the baseline, A–B carries 100 units (10 vehicles) and B–D carries 140 (14 vehicles);
A–C, C–D and reverse directions carry none. The tests verify these values in
`mini_vnet1` and cross-check the saved `mini_paths1_header` and `mini_paths1_detail`
tables. The concurrent case uses four workers and a bounded latch to ensure the two
commodity jobs overlap. Comparisons ignore generated path IDs, whose order can vary
with scheduling. Repeating the same scenario must replace the outputs with identical
results. Every test owns and closes a private in-memory database.

These five tests run automatically with the full Ant suite and the existing GitHub
workflow. To run just this reference case:

```sh
ant -f build-tests.xml '-Dtest.includes=**/AllOrNothingAssignmentIntegrationTest.class' Test
```

## Adding a test

Mirror the production package under `test/` and name the class `SomethingTest`. Use
`org.junit.jupiter.api.Test` (or `TestFactory` for a shared set of behavioral examples)
and JUnit assertions. Package-private tests can exercise package-private helpers without
changing the production API.

Prefer small examples with independently known results. Include a numerical tolerance
where appropriate, keep generated inputs deterministic, and check behavior rather than
private implementation details. When fixing a calculation bug, first add a test that
reproduces it, then verify the fix with `ant -f build-tests.xml`.

Tests that temporarily set the global `JDBCUtils` connection must use
`@ResourceLock("JDBCUtils")`, restore it to `null`, and close their private database
in `@AfterEach` or a try-with-resources fixture. Concurrency tests must join their workers
and shut down executors, using bounded waits rather than sleeps. Integration tests use `@Tag("integration")`
for selection in JUnit-aware tools; they remain part of the default Ant suite.
