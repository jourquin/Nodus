# Nodus tests

## Running with Ant

Use the dedicated `build-tests.xml` from the repository root, with JDK 11 or later
and a full Apache Ant 1.10.6+ installation (including `ant-junitlauncher.jar` in
Ant's `lib` directory):

```sh
ant -f build-tests.xml
```

On Fedora, install Ant's optional JUnit 5 task with `sudo dnf install ant-junit5`.
The test fork uses the repository's JUnit libraries rather than Ant's system JUnit
Platform libraries to avoid mixing incompatible versions.

The default target is `Test`; `ant -f build-tests.xml Test` is equivalent. It builds
the application using the normal build, recompiles the tests, then runs JUnit
Jupiter in a separate JVM with `java.awt.headless=true`.

The suite includes unit tests, database tests and complete assignment integration tests.
The modal-choice migration tests compile temporary old and current demo plugins, then exercise
approved binary upgrades, declined upgrades, exact backups, unchanged resources, modal shares,
reflection names, arrays and lambdas. They also verify refusal of existing backups, signed JARs,
removed Abraham references and missing dependencies. Conversion itself does not use a compiler.
Fixtures and confirmation callbacks keep these checks independent of real project JARs and Swing
dialogs, including when run directly from Eclipse's JUnit runner.
No project data, remote database, Internet access, or graphical desktop is needed. Database tests
create private temporary databases and close them afterwards. Most use H2; service
persistence failure/retry and legacy-schema checks also run against HSQLDB. Import
failure tests cover H2 and HSQLDB staging, malformed DBF/XLS/XLSX input, replacement
failures, and preservation of earlier uncommitted work. Layer-save tests inject failures at each
sidecar installation, check interrupted-save recovery and retained backups when recovery fails,
and verify that a failed save prevents project close callbacks from running.
File import/export tests use JUnit temporary directories, which are removed after
the tests. They never read or overwrite files in an existing Nodus project.
Dependencies are included in the repository: JUnit 5 and OpenTest4J under
`lib/groovy/`, API Guardian under `devtools/junit/`, and H2/HSQLDB under `lib/`.

The local-server tests launch isolated JVMs with temporary HSQLDB and H2
databases. They require permission to bind loopback TCP ports and check that the
listeners cannot be reached through the computer's non-loopback interfaces. They
also cover local script connections, port conflicts, H2 database creation and
authentication failures, listener cleanup after compaction, quiet HSQLDB startup
with errors still reported, and explicit JDBC configurations that must not start a
local server. No remote server or Internet connection is used.

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
The default target of `build.xml` is `build`: it compiles application and test sources
but does not execute tests. Select its `Test` target explicitly, or use
`build-tests.xml` with its default `Test` target.

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
or produce Ant's reports. Assignment tests replace the message presentation callback,
so completion dialogs do not appear even without `-Djava.awt.headless=true`.

## Build-file arrangement

`build-tests.xml` imports the application build for its classpath and resource-copying
`init` target. It compiles with `release="11"` independently of Eclipse's generated
`build-project` target. `build-user.xml` contains forwarding targets, preserving `ant Test` and
`ant CleanTests` even after Eclipse regenerates `build.xml`. The forwarding targets
also work when launched directly from `build-user.xml`, which has no default target.
After exporting `build.xml` from Eclipse again, verify that it still contains
`<import file="build-user.xml"/>`; the forwarding targets require that import.
Keep the test implementation in `build-tests.xml`; do not add it to Eclipse's
automatic buildfile imports, as it already imports `build.xml`.

To run the suite through these forwarding targets:

```sh
ant Test
# Or launch build-user.xml directly:
ant -f build-user.xml Test
```

`Installer` depends on `Test`, so `ant Installer` runs the tests before generating
documentation or packaging the application. A compilation error, failed test or test
execution error aborts the installer build. In Eclipse's Ant Targets tab, select `Test`
to run only the suite, or `Installer` to run the suite and then create the installer.
The JUnit execution and reports still use the dedicated `build-tests.xml` approach.

## Continuous integration on GitHub

[The Tests workflow](../.github/workflows/tests.yml) runs the complete suite on
pushes and pull requests, with separate jobs for Temurin JDK 11, 25 and 27 on Ubuntu.
All jobs compile for Java 11 and execute the tests on their selected runtime.
The workflow downloads the full Apache Ant 1.10.18 distribution from Apache's CDN,
falls back to the release archive, verifies its pinned SHA-512 checksum, and checks
that `ant-junitlauncher.jar` is present. Failed or corrupt downloads are discarded.
It runs:

```sh
ant -noinput -f build-tests.xml clean Test
```

To activate it, commit and push the workflow together with the test sources,
`build-tests.xml`, the other build changes, and `devtools/junit/`. If Actions is
disabled for the repository, enable it under **Settings > Actions > General**.
No custom secrets or database setup are required.

Open the repository's **Actions** tab and select **Tests** to inspect runs.
Compilation errors or failing tests make the corresponding check fail. The run's
**Artifacts** section contains `test-reports-java-11`, `test-reports-java-25` and
`test-reports-java-27`,
with the text and JUnit XML reports retained for 14 days. Reports are uploaded
even after test failures; a failure before test execution may leave no reports.
After the workflow is on the default branch, **Actions > Tests > Run workflow**
also allows a manual run.

For CI to prevent merging a failing pull request, add **Tests (Java 11)**,
**Tests (Java 25)** and **Tests (Java 27)** as required status checks in the default
branch's ruleset or branch protection rule after the first run. Running CI alone does not require
those checks to pass before merging.

The same Ant command works with other CI providers: install a JDK and a full Ant
distribution, then collect `test-build/reports/TEST-*.xml` as JUnit test results.
See [GitHub's Ant CI guide][ant-ci]
for the workflow mechanism.

[ant-ci]: https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-ant

## Assignment computing-time HTML reports

Set `NodusC.displayComputingTimes = true` before starting an assignment to save its performance
report as a standalone UTF-8 HTML file in the project directory. The switch is sampled at the start
of the run and is off by default. All assignment methods use this output: All-or-Nothing,
Incremental, equilibrium, Fast/Exact Multi-flow and static/dynamic time-dependent assignments.
Timings are no longer printed to the terminal.
Set the same switch to `false` before starting a run to disable new reports; existing HTML files
remain unchanged. `NodusC.displayMapComputingTimes` controls map preparation timings separately.

Reports use the same table layout, light header shading, timestamp and print styling as the
modal-choice parameter report. They include the scenario, assignment method, configured threads,
completion status, wall-time totals, outside-phase breakdown, worker timings and search-call counts.
Fast MF's optional reachability diagnostics are included in their own tables.

Filenames distinguish scenario, method and Exact MF selector. Examples:

```text
demo_scenario27_FastMFAssignment_computing_times.html
demo_scenario27_ExactMFAssignment_IMPROVED_ASTAR_computing_times.html
demo_scenario27_ExactMFAssignment_DESTINATION_ASTAR_computing_times.html
```

Re-running the same scenario/method/selector replaces that report; other algorithm reports remain
available for comparison. Failed/cancelled runs are marked explicitly. Reports are staged before
replacement; a write failure retains the previous file and reports a warning without invalidating
published assignment results. HTML rendering and saving are excluded from the measured times.
`parameters.getComputingTimes().getReportPath()` returns the saved file, or `null` when no report
was saved in the current run. Reports are not opened automatically.

### Comparing Exact Multi-flow search times

The assignment dialog enables Exact Multi-flow only with `Nb routes > 1`. Reducing the count to
one selects Fast Multi-flow; saved one-route Exact scenarios also open with Fast Multi-flow selected.

Before running an Exact Multi-flow assignment, choose its search algorithm in the Groovy console
or a startup script:

```groovy
import edu.uclouvain.core.nodus.NodusC
import edu.uclouvain.core.nodus.NodusC.ExactMFAlgorithm

NodusC.displayComputingTimes = true
NodusC.exactMFAlgorithm = ExactMFAlgorithm.DESTINATION_ASTAR
```

`NodusC.exactMFAlgorithm` accepts these choices:

| Selector value | Search for subsequent routes | Timing row |
| --- | --- | --- |
| `ExactMFAlgorithm.IMPROVED_ASTAR` | Safe zero heuristic, giving Dijkstra ordering | `Improved A*:` |
| `ExactMFAlgorithm.DESTINATION_ASTAR` | A* with reverse-Dijkstra bounds for the current destination (default) | `Destination A*:` |

Improved A* uses a shared forward Dijkstra first pass: one search per origin and
available loading mode/means, within the current commodity group and OD cost class. Its predecessor trees provide
the first route to every requested destination. Origins that cannot load a particular mode/means
skip that combination. Destinations unreachable in a first tree need no subsequent searches for
that combination. Penalties for later routes remain independent for every OD pair, and original
weights are restored between combinations. Intermodal routes retain their complete modal grouping
key, as in Fast Multi-flow.

With `Nb routes = 1`, all choices use only Dijkstra (forward or reverse), without constructing an
A* query search. Each worker job samples the selector when creating
its searches, so change it before starting the assignment. Keep assignment settings and
`NodusC.useCompactShortestPaths` identical between runs.
Both algorithms support compact arrays and original linked graph/heap storage. The unused
legacy geographic A* implementation and its selector have been removed.
Destination-based A* can choose different equal-cost routes; the subsequent penalties can
then generate different alternatives even though each search minimizes its current penalized cost.

The `Dijkstra:` row measures shared first-route searches; the selected algorithm's row measures
subsequent OD-specific searches. Search-call counts appear in a separate HTML table for each algorithm.
Every search row measures the complete `compute()` call, including per-search initialization.
Rows sum elapsed time across worker jobs and may exceed wall time with parallel workers. Graph
and heap construction remain worker setup for improved A*.
Use `Total elapsed:` to compare complete assignments, including destination-bound setup; comparing
query rows alone omits the reverse searches and other preparation costs. Repeat runs to account for JVM warm-up and timing variation.
Auditing is sampled at assignment start and does not read the timing clock when disabled.

First-route predecessor snapshots use approximately `4 * eligibleLoadingModes * virtualNodes`
bytes per worker, in addition to the Dijkstra search arrays. They are discarded at the next origin;
no trees are reused across origins, commodity groups, cost classes or assignment runs.

Destination-based A* groups the job's OD cells by destination. For each destination and eligible
loading mode/means, a reverse Dijkstra search computes the original-cost distances to that
unloading node. It stops as soon as all distinct required origins have final distances. Its next-hop
tree supplies first routes for those origins; its bounds guide later OD-specific A* searches after
markups.

Before each destination/loading-mode search, an iterative reverse reachability prepass traverses
only currently available directed edges. Loading restrictions, infinite/NaN costs and the infinity
sentinel are respected. Distinct unreachable origins are removed from the weighted search's
stopping condition; they still produce the normal invalid paths and optional lost-path records.
If no required origin is reachable, Dijkstra is skipped entirely. The prepass snapshot also lets
forward A* reject proven unreachable nodes while retaining unexplored, reachable detours.
Snapshots remain private to their destination, mode, group and cost class. They are rebuilt from
current baseline costs on every run. A topological path whose accumulated cost overflows can still
require the weighted search to exhaust its finite frontier.

If the final settled distance is R, unexplored nodes have baseline distance at least R. Their bound
is therefore R, rounded down for numerical safety. This caps the full distance heuristic at R.
Unexplored nodes remain eligible for forward queries, including penalized detours outside the
reverse tree; their tentative labels are never used as exact distances or unreachable flags.
Loading restrictions are included in these bounds. The reverse index references live costs and is
built once per job. Each mode's bounds remain separate and costs
are restored after every OD/mode, preserving independent penalties. Negative markups disable A*
bounds and retain zero-heuristic queries. Progress counts destinations per group instead of origins.

The report includes `Destination A* setup:` for reverse-index construction, `Reverse Dijkstra:` for
first routes and bounds (including distance-table preparation), `Reachability prepass:` for the
traversal and result preparation for entirely skipped trees, and `Destination A*:` for subsequent
OD searches. Both weighted search types have call counts; reverse counts exclude entirely skipped
combinations. A `Reverse reachability prepass` table reports completed prepasses, distinct required
origins checked, origins proven unreachable, weighted searches skipped, nodes visited and edges
scanned. Origin counts are summed over destination/loading-mode prepasses, not global unique IDs.
A separate `Reverse Dijkstra work` table reports completed searches, searches stopped after reaching their required origins, exhausted searches,
nodes settled and edges scanned. Compare `Total elapsed:` with improved A* to include
all setup costs. With one route there is no `Destination A*:` query row.

Only the current destination's tables are retained: approximately
`20.125 * eligibleLoadingModes * virtualNodes` bytes per worker for distances, rounded bounds,
next hops and reachability bits, plus the reverse index, origin request markers, one reusable
reachability stack, query arrays, one predecessor scratch array and the OD grouping index.
No tables survive a group/class job or assignment run. This option shares reverse searches per
destination to obtain cost-based, constant-time bounds on many OD queries. Early stopping reduces
reverse work but can weaken the bounds and increase forward-query work; compare complete runs.
A speedup depends on the network, demand pattern, number of modes and markups; benchmark complete assignments. Equal-cost
first routes may differ from forward Dijkstra and can produce different subsequent alternatives.

## Coverage

- `BinaryHeapDestinationAStarTest`: independent Floyd-Warshall comparisons across tiny/large costs,
  directed components, zero-cost cycles, parallel/nonfinite edges, penalties, restrictions, restoration,
  destination switching, tree isolation, zero-bound fallback and cancellation. Full and early-stopped
  trees are compared with independent reference costs; penalized routes can pass through unexplored
  nodes. Covers duplicate/empty origin sets, unreachable origins, safe capped bounds and reduced
  reverse work. Prepass tests cover mixed and entirely unreachable requests, live blocked edges,
  mode/goal snapshot isolation, duplicate origins, cost overflow, cancellation and heap-free skipped
  searches. Assignment tests compare
  saved routes/volumes with improved A*, including multiple origins, groups, classes and both storage modes.
- `CommentedPropertiesTest`, `PropertiesFileSaveTest`, `NodusProjectPropertiesTest`: standard Java
  property syntax, comments and entry order, new/removed settings, escaped keys, Unicode and multiline
  values. Injected write/flush/close/replacement failures preserve existing project and local settings
  files, remove staged output, retain edits and allow retries. Checks unreadable/malformed project
  settings, symlinks, supported access permissions and files created during serialization.
- `ResultCalculationTest`: H2/HSQLDB quantities, vehicles and repeated path links reach the correct
  objects across multiple layers. Checks unknown IDs, empty/repeated queries, zero/equal/signed
  values, relative stroke widths and node radii, SQL/layer/view visibility, recovery from zero
  results, query errors and actual DBF exports with two decimal places and retained layer row order.
- `ResultPlaybackTest`: real period queries with controlled EDT timer delivery, fixed scaling,
  exclusive end boundaries and complete slices, scenario-specific time settings, Enter/Escape,
  canceled prompts, missing/invalid settings, SQL failures and once-only completion. Verifies restored
  labels/listeners, stopped and stale timers, replacement playback, absent labels, large time values,
  quoted/nested/OR query predicates, CR/LF line comments, nested block comments on H2, and project
  cleanup before JDBC closure and reuse of the map.
- `ScenariosTest`: comparison and summation on H2/HSQLDB with matching/disjoint links,
  unequal/reordered commodity columns, every operation type, directions, services, time periods,
  large identifiers/vehicle counts, filters and empty inputs. Covers current and legacy schemas,
  signed quantities, unit/aggregate cost semantics, source/result ID collisions and malformed
  sources. Injects later batch/row failures, failed-row statuses, statement-close errors, commits
  before/after execution and publication renames; verifies preserved source and destination
  outputs, successful retries, non-batch drivers and staging/statement cleanup.
- `VirtualNetworkWriterTest`: H2/HSQLDB cancellation after an executed batch, later batch errors,
  explicit failed-row statuses, commit failure before and after the commit reaches the database,
  and publication-commit failure. Checks transactional and auto-commit writes, non-batch drivers,
  consolidated quantities/costs/vehicles, preservation of previous output, closed statements,
  progress cleanup and error-reporting failures that must not prevent rollback. Two-group and
  maximum-group cases verify consolidated vehicle counts beyond the integer and ten-digit ranges.
- `AssignmentOutputFailureTest`: real routing and output with failures in network writing,
  final path batches, path commits, statement closure, publication renames and publication commits.
  Checks complete previous-result preservation, first-attempt failure, successful retries,
  canceled/stopped workers, menu/progress restoration, cleared completion state and suppression
  of success notifications and post-assignment scripts after output failure. Successful output
  replacement is also tested without batch support and with path details disabled.
- `ProjectFilesToolsTest`: mandatory node/link DBF names, types, widths and precision;
  truncated schemas, missing/corrupt sidecars, wrong shape types and supported historical ID widths.
  Legacy link migration inserts `ENABLED` once, preserves row order, custom attributes and UTF-8/
  Windows-1252 text, rejects malformed schemas without rewriting them, and preserves the original
  DBF when installation fails. Consecutive validations cannot share an upgrade decision.
- `DbfSchemaConversionTest`: lossless numeric widening/narrowing and numeric-text conversion,
  including `speed N(4,0)` to `N(7,2)`. Checks one confirmation per affected layer, multiple fields
  in one prompt, declined repairs, deleted rows, legacy `ENABLED` insertion, UTF-8/Windows-1252
  attributes, unchanged geometry/CPG files and byte-for-byte original backups. Rejects rounding,
  overflow, ambiguous/non-numeric text, extreme exponents and truncated records before prompting.
  Existing backups, concurrent source/backup changes, failed installation and retries are covered.
- `NodusProjectOpenWorkflowTest`: real temporary project files and embedded H2/HSQLDB connections,
  `.nodus`/`.local` precedence, actual layer loading, modified-DBF reimport, Groovy open-hook completion,
  close/reopen, background and late-opening failure cleanup, and ID allocation across loaded and
  unloaded layers. Rejected or declined layer formats, later opening failures and normal closure
  restore one visible World layer and the initial map view, hide scenario controls and allow retry.
  Rescans remove stale IDs and reject an unavailable project directory.
- `NodusWMSLayerTest`: the production SAX configuration rejects external general/parameter and
  custom/recursive entity declarations. Private sentinel files and a loopback HTTP canary check
  resource isolation; external DTD/schema and XInclude references must never trigger canary requests.
  WMS 1.1 SYSTEM/PUBLIC DOCTYPE references remain usable without downloading their DTDs. The normal
  [capabilities fixture](fixtures/wms/capabilities-1.1.1.xml) covers service/format/URL metadata,
  nested layers, inherited bounds, styles and split character callbacks. Actual layer requests check
  successful caching, single-layer roots and rejection/retry after unsafe or late malformed XML.
  They also check capabilities query separators, service/version parameters, UTF-8 vendor values,
  missing GET bindings, invalid numeric bounds, server reconfiguration and map requests using the
  advertised GET endpoint with its existing query parameters.
  Security settings follow the
  [JAXP security guidance](https://docs.oracle.com/en/java/javase/11/security/java-api-xml-processing-jaxp-security-guide.html).
- `WmsCapabilitiesParsingTest`: offline WMS 1.1, legacy Map and
  [WMS 1.3](fixtures/wms/capabilities-1.3.0.xml) metadata; default and explicit namespaces, arbitrary
  XLink prefixes, nested bounds and overrides, CRS, GET versus POST/other operations, scoped
  attribution/style/vendor metadata and checked failures for missing or nonfinite coordinates.
- `NodusMapTileLayerTest`: headless EDT control actions apply and persist transparency for server
  and local tile factories, restore saved opacity and clear memory/disk caches while preserving a
  second layer's files. Missing-server preparation never calls the tile factory. No public map
  servers, rendered-pixel comparisons or dialog construction are involved.
- `BackgroundMapResponsivenessTest`: a local HTTP server pauses tile or WMS image responses while
  the EDT hides/shows the background and another layer, rebuilds mouse-mode listeners through
  OpenMap's actual mouse delegator, and repaints the buffered map through the real layer handler/panel.
  Both standard and server tile factories
  are covered. Requests must still be pending when UI work completes. Fixtures release all paused
  responses before disposing the map; the regular suite opens no windows.
- `ExtensionApiCompatibilityTest`: the frozen public/protected API of the four extension
  entry classes, a plugin and subclass compiled before their refactoring, plugin menu dispatch
  and project/global disposal, and Groovy bean properties, overloads and shared collections.
  The original bytecode and API baseline are documented in
  [fixtures/extension-api/README.md](fixtures/extension-api/README.md).
- `PluginDiscoveryTest`: temporary external JARs with broken constructors, initialization,
  panel binding, metadata and cleanup callbacks; malformed menu IDs; missing superclass,
  constructor-signature and execution dependencies; corrupt archives/classes; and duplicate
  plugins, including checked callback exceptions from dynamic JVM languages. Rejected plugins
  cannot hide healthy menu commands, leave empty menus or reset global
  command offsets. Discovery does not initialize classes, dependencies can live in sibling JARs,
  and plugin resources remain available for lazy execution. `NodusProjectOpenWorkflowTest` also
  opens and reopens real H2/HSQLDB projects with a failing plugin, replacing a closed plugin JAR
  and checking fresh class loaders and updated resources on reopening.
- `ProjectScenariosTest`: renaming and deleting scenario tables and properties together,
  retaining data and unrelated scenarios, and preserving overridden property/UI callbacks.
- `ProjectScenariosSafetyTest`: H2/HSQLDB scenario changes with missing optional settings,
  identical IDs, occupied destination tables/rules/settings, default/custom quoted prefixes and
  exact metadata names. Scenario-specific inclusion/exclusion rules follow a rename and are removed
  on deletion; generic rules, unrelated scenarios and legacy rules tables are preserved. Rule changes
  persist even without output tables. Injects failure at each rename/drop, rule write and commit;
  checks reverse-rename recovery in both transaction modes, retained rules/settings after failed
  deletion, successful retries and diagnostics when recovery itself fails. Rename refuses occupied
  IDs; DDL rollback alone is not assumed to restore schema changes. Earlier successful drops can
  persist when a later deletion fails.
- `ConsoleStreamsTest`: separate output/error routing, restoration of previous streams without
  closing them, and respecting a later stream owner when the console closes.
- `NodusEsriLayerDatabaseTest`: actual layer initialization from temporary shapefiles and H2
  synchronization by `NUM`, independent of SQL row order. Checks dates, decimals and repeated
  refreshes; rejects incompatible row counts, columns, widths and decimal scales. Unknown or
  duplicate identifiers must leave every DBF row unchanged. Expected rejection messages are
  captured and asserted, so passing tests do not print misleading errors.
- `NodusEsriLayerEditingTest`: point/link insertion, deletion and removal of the last record;
  consistency of SQL rows, geometry, attributes and identifier indexes; replacing and clearing
  SQL filters; and saving/reloading `.shp`, `.shx` and `.dbf` files after edits. Includes quoted
  text and decimal values, and a regression for the spurious identifier zero created by adding
  a blank record before assigning its actual identifier. Also checks real identifier zero,
  insertion of complete link records, and discarding edits before reimporting the saved layer.
  Bulk deletion checks survivor order and identity, duplicate/invalid positions, one label refresh,
  and rollback after a later SQL failure or invalid identifier, preserving earlier caller work.
- `NodusEsriLayerPersistenceTest`: extracts point/link subsets and independently reloads their
  geometry and DBF records; saves and reloads empty layers after deleting every feature,
  preserving their shape type and DBF schema. All files are confined to temporary directories.
- `NodusEsriLayerStyleTest`: result widths, colors and point radii, preservation of shared
  styles, zero-to-nonzero visibility, restoring normal styles, SQL exclusions while displaying
  results, and simplified styling at the zoom threshold or when styles are disabled.
  Includes simultaneous positive/negative link results and restoring matting when zooming back.
- `NodusDrawingAttributesTest`: scoped style properties, positive/negative/default colors,
  fallback colors, and replacing old settings when properties are reloaded.
- `NodusMetaDbfTableModelTest`: mandatory node/link fields, immutable existing field names/types,
  duplicate names including case variants, supported field names/type codes, rejection of
  unsupported types, fixed date width/precision, unique new fields, and editing restrictions
  while a field has pending changes. Tests capture validation messages without creating a window.
- `NodusLocationHandlerIntegrationTest`: real SQL label selection and point/link coordinates;
  updates after DBF refreshes and row deletion; case-sensitive SQL literals; result values,
  zero-sized results and hidden layers; empty/missing fields; font persistence including
  bold italic; and disposal of attached locations. Uses the shared layer fixture, with actual
  label-generation and refresh code and only the final repaint callback replaced.
- `InsertedPointTest`: independently known projections onto horizontal, vertical and diagonal
  segments, reversed endpoints, clicks beyond or exactly at endpoints, and zero-length segments.
- `NodusDrawingToolSelectionTest`: drawing-completion events select existing links despite missed
  Ctrl/Command key releases when a dialog takes focus. Checks explicit modifier-click bypass,
  node priority at link endpoints, empty-space insertion, hidden layers and highlighted service
  routes. Uses real layer preparation and selection, replacing action dialogs and launcher cleanup.
- `NetworkEditFailureTest`: rejected node, link and complete-record insertions, plus failures
  creating a split node, inserting the second fragment or updating the original link. Real H2
  and HSQLDB constraints trigger the errors. Checks SQL rows, geometry, DBF attributes, IDs,
  dirty flags, service routes and stops, successful retries, restored auto-commit mode and
  preservation of earlier uncommitted work. Expected error reports are captured without dialogs.
- `NodusLinkSplittingTest`: real drawing-tool splits using temporary shapefiles, H2 tables and
  the service handler. Checks the nearest segment on folded lines, vertical lines, repeated
  vertices, coordinate precision, fragment endpoints, node/link IDs, copied DBF attributes and
  service membership. Service routes must replace the old geometry and preserve repeated and
  reverse traversals without adding stops. Endpoint clicks, degenerate lines and rejected node
  creation must leave the network unchanged. Also saves split layers and services and reloads
  them into fresh objects, checking attributes, geometry, stops, service frequency and ordered
  occurrences, including a repeated dead-end branch. Splitting must mark the services for saving.
  The fixture replaces the attribute-editor dialog and repaint callbacks; mouse interaction
  is not simulated.
- `DisplaySpatialIndexTest`: tree and linear queries checked against explicit geographic cases
  and 100 seeded queries over 257 independently described bounding boxes. Covers strict viewport
  boundaries, lines crossing the viewport with both endpoints outside, source drawing order,
  wrapped date-line queries without duplicate results, multipart identity, empty/coincident
  features and rebuilt indexes after coordinate edits, including cached multipart extents.
- `FastEsriLayerCacheTest`: real synchronous preparation across pan, zoom, resize, style changes,
  geometry movement/replacement, membership changes and source replacement. Checks that an edit
  or disposal during index construction prevents publishing stale results. These deterministic
  interleavings do not simulate arbitrary concurrent Swing activity.
- `NodusEsriLayerSpatialIndexTest`: real point/link layer insertion, movement and deletion must
  refresh viewport selection through the normal editing/dirty-state methods, without the test
  calling spatial-index invalidation directly.
- `MapPolylineDetailTest`: screen error measured independently against full-detail projections
  in Mercator and Equal Earth, including samples between projected vertices. Checks the 0.75-pixel
  bound (with 0.0001 pixel allowed for float rounding), actual vertex reduction, unchanged source
  coordinates/styles/attributes, cache reuse when panning, new detail levels when zooming, and
  rebuilding after coordinate replacement or projection/line-type changes. Also covers multipart
  structure, selected lines, disabled detail, unsupported projections, Equal Earth rhumb lines
  and panning across the longitude seam. These inspect projected geometry, not rendered images.
- `EqualEarthTest`: independent spherical PROJ reference coordinates for the equator, mid/high
  latitudes, poles and the date line. Both forward and inverse operations are checked against
  those constants, plus screen centering, degree/radian inputs, longitude wrapping, plotability
  and pole clamping. PROJ was used to obtain the constants; running the suite does not require it.
- `ShapeIntegrityTesterTest`: invalid and overflowing identifiers, duplicates within and across
  layers, conflicts with external layers, missing origin/destination nodes, and mismatched geometry
  and DBF row counts. Valid networks, separate node/link identifier namespaces and the maximum
  integer ID are accepted unchanged. Checks loading readiness and stopping before a queued error
  is displayed. Uses the actual EDT snapshot and validation paths, with an explicit run instead
  of a timer and a captured error callback instead of a dialog. Also checks automatic self-loop
  removal at the start, middle and end of a layer, consecutive loops, removal of every link,
  unchanged neighbouring geometry/records and repeated cleanup on H2 and HSQLDB. Closed geometry
  with distinct endpoint IDs survives. A failed SQL deletion preserves the layer and can be retried.
- `NodusPreferencesTest`: first-use migration from `.nodus8.properties`, preservation of
  the old file, precedence of existing Nodus 9 preferences (including an empty file), and
  saving fresh-installation settings exclusively to `.nodus9.properties`. Uses a temporary
  home directory and never reads or writes the user's actual preferences.
- `GitHubReleaseTest`: numeric release ordering, including 9.0-to-9.1 upgrades, rejection of older
  9.0 patch tags while running 9.1, and equivalent tags for the running `NodusC.VERSION`.
  Historical version numbers are intentional test inputs. Nodus 9.0 and 9.1 share
  `.nodus9.properties`; those preference and bridge-credential tests retain their major-version names.
- `SplashResourceTest`: the splash resource selected by `NodusC.VERSION` must exist for startup
  and About, decode successfully, and retain the 640-by-320 canvas. No windows are opened.
- `ProjectCleanupRegistryTest`: exactly-once, reverse-order project resource shutdown;
  isolation of a failing action; and rejection of duplicate or late registrations.
- `ProjectLockerTest`: rejected duplicate acquisitions retain the actual OS lock, another
  project cannot replace its owner, and simultaneous acquisitions have exactly one owner.
  Also checks repeated release, reacquisition, failed-opening retry, contention with another
  JVM, and recovery after normal release or forced process termination despite a remaining
  lock file. Child JVMs probe real lock ownership using only temporary project directories;
  readiness and release signals have bounded waits and every child is reaped.
- `BridgeCredentialsTest`: generation, persistence and reuse of the Python and R bridge
  credentials, private POSIX permissions, replacement of invalid values, and failure
  without publishing credentials that were not saved. Two independent JVMs starting with
  empty preferences must adopt the same saved credentials. Uses a temporary home directory.
- `ScriptRunnerTest`: a Groovy assertion failure still reports failure and invokes the
  asynchronous lifecycle continuation on the event dispatch thread.
- `NodusRuntimeSettingsTest`: declared defaults, startup Groovy overrides, repeated resets and
  restoration of all public mutable NodusC settings, with final constants left intact.
  Project-open workflow tests switch from a project with a real Groovy hook to one without a
  script on H2 and HSQLDB, retaining startup settings and checking close-hook ordering. These
  tests run in isolation because project loading restores all mutable NodusC settings.
- `SetJVMArgsTest`: migration of legacy JVM argument files, preservation of heap and custom
  settings, backups, repeated runs, and leaving current or customized scripts intact. The
  previous generated shell format also gains the macOS fullscreen export. Shell execution
  checks the generated options using simulated macOS/Linux systems and Java 11, 16, 17, 25
  and 27 version output (including early-access syntax); Windows batch files are checked as
  text. These tests do not require those JDK versions to be installed or execute the Nodus application.
- `AllOrNothingAssignmentIntegrationTest`: complete all-or-nothing assignments on a four-node
  network with independently known results, including real demand loading, virtual-network
  generation, cost evaluation, worker jobs, vehicle conversion and saved H2 outputs. Checks
  paths, directional flows by commodity group, conservation and total costs; compares one
  worker with four; closes links to exercise rerouting and unreachable demand; and repeats
  an assignment to detect accumulated flows or duplicate outputs. Only project inputs and
  presentation callbacks are supplied by the fixture. See the reference case below.
- `EquilibriumAssignmentIntegrationTest`: complete Frank-Wolfe and successive-averages
  assignments on parallel routes with an independently calculated equilibrium. Checks
  convergence metadata, iteration limits, uncongested convergence, flow conservation,
  saved paths, and agreement between serial and concurrent Frank-Wolfe runs.
- `MultiFlowPathAdmissionIntegrationTest`: complete fast and exact multi-flow assignments
  with explicit `MAX_DETOUR` boundaries (just below, exactly at and just above the limit),
  a disabled limit and mode-specific length references. Repeated searches retain each route
  once and preserve serial/concurrent results and demand allocation. Distinct routes survive
  a deliberate collision in the former numeric checksum; unreachable modes cannot supply
  the detour benchmark. Cheapest-intermodal filtering rejects costlier solutions, retains
  ties and stays independent of the detour reference mode. Checks saved headers and details,
  commodity conservation, handling flows and agreement with the saved virtual network.
- `MultiFlowPathIdentityTest`: full ordered virtual-link identities distinguish different
  operations, ordering and direction, survive hash collisions and caller mutation, and reset
  between choice sets.
- `IncrementalAssignmentIntegrationTest`: independently calculated increments and route
  choices as congestion changes, conservation, serial/concurrent agreement, repeated runs,
  and equivalence to all-or-nothing assignment when using one increment. Also checks runtime
  Groovy toggling of informational completion dialogs without changing assignment results.
- `MultiClassEquilibriumAssignmentTest`: independent two-class equilibrium with opposing route
  preferences, two commodity groups, consecutive/sparse class IDs, both shortest-path graphs and
  H2/HSQLDB. Frank-Wolfe and its incremental hybrid reach the known 600/400 route totals with
  class conservation and optimality; serial, concurrent and repeated runs agree. Covers detailed
  and header-only path output, flow-weighted final class costs, successive averages, false
  convergence from cancelling class shifts, and cancellation/retry during final cost evaluation.
  `VirtualLinkFlowTest` also checks class-specific directions, lambda endpoints, blending,
  group isolation and resetting class history without changing single-class behavior.
- `IncFrankWolfeAssignmentIntegrationTest`: four triangular warm-up increments followed by
  real Frank-Wolfe routing and line search on analytical parallel routes. Checks conservation
  and nonnegative flows before and after blending, equilibrium within vehicle/path rounding
  tolerances, separate warm-up/FW completion counts, zero/one-iteration limits, and lambda
  endpoints. Serial, concurrent and repeated results agree with route details enabled or disabled;
  toggling details preserves mandatory headers and removes stale detail tables. Cancellation
  during warm-up, FW routing or FW line search stops workers, preserves previous results,
  removes temporary outputs and permits retry.
- `ServiceDatabaseTest`: actual SQL rows and reloads for ordered/repeated links, empty services,
  authoritative renamed keys, Unicode/quoted names, missing graphics, orphan details, duplicate
  legacy headers/stops and legacy tables without path indexes. Checks individual writes and
  batching with several limits, partial final batches, read failure/retry, caller-controlled
  commit/rollback, and preservation of work preceding a savepoint after failed inserts.
- `ServiceHandlerPersistenceTest`: the real service-saving workflow replaces names, stops and
  frequency, avoids duplicate rows on repeated saves, removes deleted services, and never
  publishes partially loaded services after a read error. It also checks service tables imported
  after project loading and preserves pending edits when the editor rechecks those tables.
  First saves, missing detail tables, explicit resets and automatic legacy migration on H2/HSQLDB
  preserve unrelated transactions and savepoints. Covers schema/connection/setup/insert/commit
  failures, retained rows and pending edits, retry, owned-connection cleanup, the selected schema,
  ordinary saves reusing the main connection, and deletion of invalid legacy services during loading.
  Failed replacement/retry tests run
  on H2 and HSQLDB with auto-commit both enabled and disabled; they assert that previously saved
  services and earlier pending work survive a failed save, and that connection mode is restored.
  Legacy path-index migration is checked on both engines. Error callbacks are captured; unexpected
  persistence errors or invalid-service dialogs fail the fixture immediately.
- `ServiceShortestPathTest`: physical shortest routes with independently known lengths, disabled
  links, mode/means restrictions and `ALL_MEANS`, parallel links, sparse node IDs, reverse travel,
  ordered waypoints and disconnected segments. Geometry and speed edits must affect the next
  graph correctly. Generated services round-trip through H2/HSQLDB with ordered and repeated
  links and wildcard means preserved. Failed construction leaves existing services intact;
  explicit replacement, deferred saving, SQL failure, retained pending edits and retry are
  checked separately, including both auto-commit modes.
- `NetworkSimplifierTest`: compiles the shipped Groovy script and exercises its service guard
  on H2 and HSQLDB. Checks cancellation, dry runs, unsaved services, custom table prefixes,
  incomplete table sets and failed deletion. Accepted deletion clears loaded services without
  recreating tables on a subsequent save and preserves unrelated tables and network layers.
  Headless dialog tests run the actual warning code for saved and unsaved services, capturing
  only the Swing window call. They check the warning text, default Cancel choice, and handling
  of Continue, Cancel and window closure before any deletion or network editing.
- `ConsoleOutputBufferTest`: checks large output bursts without a display consumer, bounded
  display batches, stdout/stderr colours and ordering, split Unicode characters, concurrent
  writers, clear/save backlogs and stream shutdown. These tests do not open desktop windows.
- `NetworkSimplifierMergeTest`: runs topology passes on real layers with shuffled input rows and
  all four retained-ID policies. Checks final geometry, conflict boundaries, dry runs, save/reload,
  and one label refresh per layer per pass on a long corridor. These tests check behavior,
  not elapsed time.
- `ExcelIoTest`: real XLS/XLSX imports and exports on H2 and HSQLDB, bounded JDBC batch calls,
  drivers without batch support, rollback after earlier batches succeeded, Unicode and blank
  cells, streaming temporary-file cleanup on success/failure, and legacy row/column limits.
- `ServiceRoutingIntegrationTest`: real service SQL definitions through virtual-network
  generation and assignment. Checks ordered links including a repeated-link detour,
  boarding/alighting only at stops, through travel, transfer permissions, and waiting/transfer
  costs using the destination service's frequency. Edits to stops and frequencies must
  affect the next assignment without retaining previous outputs.
- `ODTableDiscoveryTest`: actual H2/HSQLDB metadata distinguishes OD schemas from missing
  columns, path headers, views, reserved result/service/rule tables and loaded layers. Checks
  custom prefixes under different locales, exact quoted identifiers and case-distinct names,
  active-schema scope (including wildcard characters), independent project/shared connections,
  missing connections and fresh discovery after schema changes. SQL/metadata/close failures close
  cursors and statements without hiding other candidates or changing caller-owned transactions,
  savepoints, connection settings or shared JDBC state; schema probes never read demand rows.
- `ODReaderIntegrationTest`: real SQL demand loading, duplicate aggregation, separate
  groups/destinations/classes/departure times, reordered columns, static versus timed loading,
  SQL selection, excluded invalid trips, empty selections, and changed demand on a fresh
  network. Source rows and the project connection are preserved. Includes a regression for
  an empty selection incorrectly marking class zero as having demand.
- `SQLConsoleImportTest`: real CSV/CSVH, DBF, XLS and XLSX imports, including Excel files with
  and without schema headers. Direct commands require approval before replacing existing tables,
  including empty ones; declining or closing the prompt preserves rows and schema. Checks new
  tables, silent script/batch execution, restored confirmation after a script, and EDT prompts.
  Missing source files and directories masquerading as files are reported before any prompt or
  database changes. Failed imports stop batches and preserve pending transactions; CSV commands
  explain when the destination schema must be created first.
- `SQLConsoleExportTest`: real CSV/CSVH, DBF, XLS and XLSX exports; cancelling or
  accepting direct overwrites; new outputs; loaded scripts, pasted batches, variable
  definitions and `runBatch()`; restoring confirmation after a script; and execution
  without a desktop window. Only the dialog response is substituted. Exported files
  are read back to check their contents and the project connection remains open.
- `SQLConsoleCompatibilityTest`: an external subclass compiled before the SQL console split,
  its overwrite callback on the EDT, and Groovy property access and batch scripts with comments,
  overlapping variable names and `STOP`. The frozen fixtures are documented in
  [sql-console-api/README.md](fixtures/sql-console-api/README.md).
- `SQLConsoleScriptTest`, `SQLConsoleScriptIntegrationTest`: quote-aware semicolon splitting,
  doubled quotes, quoted identifiers, backticks and dollar strings, nested block/inline comments,
  legacy full-line hash comments, literal line endings and Unicode. Checks same-line commands,
  final commands without terminators, empty statements, textual variables and `STOP`; separators
  supplied by `runBatch()` must follow trailing line comments. Real H2/HSQLDB consoles exercise
  direct commands, loaded files and arrays, including the demo OD-merging script. Unclosed quotes
  or block comments must execute no SQL, preserve pending transactions, close statements and permit
  retries. Existing import/export overwrite prompts and extension API fixtures remain covered.
- `TableSorterTest`: headless EDT checks for sorted reads, edits and editability, stable
  single/compound ordering and null values with comparable and lexical fallback types. Covers
  comparator replacement/removal and extreme comparator results, precise model-to-view cell events,
  lazy re-sorting, row insertion/deletion and data/structure/model replacement. Real JTable headers
  exercise reordered columns, modifier keys, clicks outside columns, and listener/renderer cleanup.
- `SQLConsoleComponentsTest`: JDBC result labels, NULLs, text headers and result-set closure;
  database metadata tables, exact-table columns and composite indexes; and history wrapping, deduplication,
  persistence and dispatch to the original listener.
- `TableExportTest`, `TableExportIntegrationTest`, `CsvExportPreservationTest`,
  `ExcelExportPreservationTest`: preserve previous CSV/CSVH/XLS/XLSX exports byte for byte after
  partial writes, flush/close errors, completed workbook serialization/closure failures and failed
  publication. H2/HSQLDB tests inject late row/cell reads and JDBC resource-close errors, check
  unchanged caller transactions and source rows, and retry to verify every exported row. Covers
  first exports, staging/spill-file cleanup, Unicode/null fields, existing permissions, symlinks,
  concurrent new destinations and unsupported atomic replacement. Publication requires atomic
  replacement for existing files; an unsupported filesystem returns failure without removing them.
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
  sources/goals, decreased/restored costs, non-finite costs and invalid/extreme coordinates.
  Small/large cost units and zero-cost movement between distinct points cannot change the cheapest
  route. Seeded networks with zero and nonzero coordinates are checked against an independent
  Floyd-Warshall reference, including reconstructed predecessor-path costs. A* uses a zero heuristic:
  geographic distance is not a guaranteed lower bound for user-defined transport costs.
- `ExactMultiFlowCostScaleTest`: H2/HSQLDB assignments with linked/compact storage, serial/concurrent
  group workers and one/two alternatives. Checks a cheaper route travelling away from its goal,
  zero-cost movement, changed costs on reruns, proportional route shares, stored path costs,
  per-link/group flows and preserved demand. Exact MF retains separate searches and cost penalties
  per OD pair for additional routes. These checks do not benchmark large-network search times.
  The comparison selector is exercised on successive runs with both engines, both graph
  representations and worker counts, checking the expected Dijkstra first routes.
- `ExactMultiFlowFirstRouteIntegrationTest`: both selectors on H2/HSQLDB with linked/compact
  storage and concurrent group jobs. One-route outputs match Fast MF for multiple destinations and
  origins, including a cheaper route travelling away from its goal and unavailable loading modes.
  A recording modal-choice method checks separate unimodal/intermodal choice sets. Independently
  known second routes distinguish Exact MF's OD-local penalties from Fast MF's shared penalties.
  Optional timing counts verify first-route trees per eligible origin/mode or destination/mode and the expected
  number of subsequent pair searches. Mixed and entirely disconnected destinations retain the same
  lost-path records, reachable outputs and OD demand with the prepass enabled. Single-route runs
  omit A* query searches.
- `RealLinkInitializerTest`: EsriPolyline and OMPoly lengths from declared degree/radian inputs,
  endpoint fallback near the equator/prime meridian and across the date line, reversed paths and
  intermediate vertices. Uses an independent haversine reference. Checks missing/degenerate and
  screen geometry, geographic offset points, and repeated initialization that preserves or resets
  current/auxiliary passenger-car units in both directions.
- `CostExpressionCacheTest`: changing values, recreated or missing variables, expressions
  simplified by the parser, changed constants, different scopes/formulas, and syntax errors.
- `CostParameterTest`: precedence and isolation of numeric parameters by scenario, commodity
  group and OD class, zero/negative overrides, inherited defaults, and NaN fallbacks.
- `CostParserTest`: production cost/duration evaluation across consecutive node records, layer
  changes and movement/handling transitions; directional and updated traffic volumes; operation
  selection and time-specific formulas beyond byte-indexed slices. Checks BPR, conical and time
  conversion functions against independent values, and rejects invalid divisions, non-finite or
  negative results, missing variables and malformed formulas. Uses explicit in-memory layer records
  without a database or GUI.
- `CostParserWorkerTest`: complete inclusion and exclusion sets at loading/unloading and
  transhipment nodes, multiple permitted movements and insertion order, wildcard patterns,
  loading/unloading direction, transit exceptions, node isolation, and scenario/group precedence
  and isolation when a worker is reused. Seeds only the worker's network input; matching, rule
  selection and enforcement use production code without a parser, database or GUI.
- `CostParserRulesIntegrationTest`: real cost jobs and generated three-mode networks verify that
  permitted links retain their costs and durations, excluded links skip formula evaluation and
  cannot form a shortest route, and repeated scenario/group jobs recompute availability. Uses
  the temporary H2 assignment fixture and checks both linked and compact shortest-path graphs.
- `CostParserWorkerFailureTest`: real worker threads and the cost coordinator handle invalid
  formulas, unchecked exceptions, fatal errors and progress cancellation. Checks prompt peer
  termination, retained diagnostics, quiet user cancellation and successful retries after failures.
  A delayed worker and repeated coordinator interrupts must not release the network before cleanup.
- `ModalParameterTableTest`: parameter persistence, method discovery and plugin tables without
  `@nodus.method` metadata. Missing declarations leave manual method selection available; empty
  declarations and unreadable tables still report errors, and embedded parameter validation stays
  strict. Covers H2 and HSQLDB, including saving and recovery failures.
- `ModalSplitTest`: proportional and multinomial-logit shares, normalization across modes
  and paths, large costs, invalid costs, legacy negative-cost defaults, and isolation between
  fitted/default groups and worker clones.
- `ModalSplitMethodsLoaderTest`: failing and reentrant plugin disposers, remaining method
  cleanup, delayed class-path closure, cleared registries and reported failures.
- `NodusProjectCleanupTest`: a broken modal-plugin disposer must not prevent layer disposal
  on normal close, or JDBC connection closure, project-lock release and busy-state reset
  after failed project opening. Registered project resources also stop after failed opening
  and when the project closes before opening completes.
- `NodusProjectCloseWorkflowTest`: real asynchronous close hooks, registered resource cleanup,
  JDBC closure and lock release must finish before the next project opens. Latches check the
  ordering, deferred closure while a computation owns the project, once-only EDT callbacks,
  and retained ownership on cancellation or failed saving;
  discarded open callbacks must not run on a later successful close.
- `NodusMapPanelStartupStateTest`: quitting after manually closing a project clears its saved
  startup name while preserving the chooser directory and reload preference. Quitting with a
  project open preserves its name. Intercepts the exit callback and reloads temporary preferences.
- `NodusProjectConnectionLossTest`: a lost JDBC session stops registered project resources
  once, warns once, and prevents further network edits or DBF saves.
- `MultinomialProbitTest`, `ProbitProbabilitiesTest`, `LogCostProbitEstimatorTest`: joint normal
  choice probabilities against independent SciPy references, binary and rare-choice limits,
  likelihood derivatives, coefficient recovery, availability, identification, cost/quantity scaling,
  default raw-cost utilities, underflowing alternatives, and route allocation after modal choice.
- `ProportionalEstimatorTest`: known cost factors, analytical uncertainty, reference normalization,
  equal costs, changing availability, quantity scaling, identification and cancellation.
- `LogitCalibrationIntegrationTest`: standalone logit/probit/proportional estimation and
  coefficient reuse in fast/exact assignment on H2/HSQLDB, skipped observations and diagnostics,
  cancellation, and preservation of prior coefficients/results on failure. Also checks saving
  all three models under another filename, using the source's costs and retaining both source
  and destination on cancellation or failed fitting. Missing MNL/probit parameters are checked
  through repeated fast/exact assignments with two groups and workers, one warning per run,
  preserved cost files, and shares matching the default models.
- `LogitCostFileTest`: comment/expression preservation, independent model sections, save-as
  contents, overwrite-confirmation rules and conflicts when source or output files change.
- `AssignmentCancellationTest`: end-of-work markers, joining all workers, coordinator
  interruption, slow cleanup exceeding one second, repeated interrupts, fatal worker errors,
  cancellation while waiting for a first job, and malformed queued work.
  Uses real assignment-worker threads with latches and bounded waits, without loading a project.
  Verifies that normal cancellation is quiet and captures/asserts the expected error from
  deliberately malformed work, so a passing run does not print misleading stack traces.
- `TimeDependentAssignmentWorkerTest`: direct static and dynamic worker execution with linked
  and compact graphs, real virtual-network generation and temporary H2 path output. Checks exact
  slice boundaries, fractional travel times retained across relocation, links longer than a slice,
  departures within a slice or in future slices, group/class isolation, unfinished and unreachable
  demand, the assignment horizon and slice indices beyond the byte range. Asserts per-slice and
  reverse-direction flows, demand conservation, once-only loading/unloading and saved path links.
  Covers final partial periods, windows shorter than one slice, excluded departures and bounded
  horizon diagnostics with counts and quantities for unfinished routes.
  Static flows use half-open arrival buckets; dynamic traversal may finish at a slice's end before
  continuing in the next slice. These tests call the workers directly, without worker threads or
  the full timed-assignment coordinator.
- `TimeDependentAssignmentIntegrationTest`: complete static and dynamic timed assignments on
  H2 and HSQLDB with SQL departure times and a route whose cost changes between periods.
  Checks rerouting after relocation, handling charged once, historical unit costs, per-period
  flows and vehicles, saved paths, demand conservation and worker termination. Serial,
  parallel and repeated runs must produce identical normalized outputs. Changing time slices,
  thread counts and detailed-path settings within the same project must retain its JDBC connection;
  restoring the original settings must reproduce the original complete output set.
  Final partial periods must be saved without extending the requested end time, in serial and
  parallel runs; departures outside the window are excluded and reported on both databases.
- `PathWeightsTest`: all seven cost and duration components contribute to totals.
- `VirtualLinkFlowTest`: exact and fast multi-flow demand distribution, rejected paths,
  demand consumed only once, independent commodity groups and dynamic time slices,
  equilibrium blending, vehicle rounding, directional passenger-car units, and projections
  that leave stored flows unchanged. Invalid characteristics, non-finite/negative volumes and
  oversized vehicle counts, PCU contributions or directional totals must fail before this link's
  counts/traffic change. Covers fractional capacity, PCU zero, the integer upper boundary and
  positive demand whose division underflows, for ordinary and projected conversions.
- `ReachabilityDijkstraTest`: compares all predecessor/distance arrays and graph state with
  ordinary Dijkstra for linked and compact storage. Checks exact search/work counters and
  controlled-clock estimates for mixed, unreachable, duplicate, empty and source destinations;
  new sources/restriction sequences, increased/unsafe/overflowing costs, unexpected improvements,
  failed calls and disabled auditing. Reuse estimates and retrospective preprocessing estimates
  are checked separately; the observer must leave each search running normally.
- `AssignmentComputingTimesTest`: deterministic clocks verify overlapping worker intervals,
  per-thread database subtraction, nested exclusive coordinator phases, nested path-output calls,
  partial failed stages, disabled audits, once-only merging/reporting and reset between runs.
  Counters from a worker belonging to an earlier assignment cannot enter a later report.
- `AssignmentComputingTimesReportTest`: standalone UTF-8 HTML tables, escaped names, timestamps,
  exact timing/count values, independent scenario/method/selector filenames, safe replacement and
  staging cleanup. Disabled auditing does not access files; failed runs retain their status, and
  the Exact MF selector is sampled at assignment start.
- `ComputingTimesAssignmentIntegrationTest`: fast and exact coordinators produce identical saved
  paths, costs and network flows with auditing disabled, enabled and disabled again, using both
  graph representations and serial/concurrent workers. Disconnected loading-mode alternatives
  exercise the real reachability-observer wiring and its per-mode resets. Exact MF covers
  zero-heuristic and destination-based searches. A controlled clock verifies positive search
  timings for both choices, separate reverse-Dijkstra/setup timings,
  early-stop/exhaustion counts, prepass counts/skipped searches and no timing-clock reads when
  auditing is disabled.
  HTML reports are also checked for All-or-Nothing, Incremental, MSA, Frank-Wolfe and incremental
  Frank-Wolfe assignments. An injected report-save failure warns without invalidating the saved
  assignment. `TimeDependentAssignmentIntegrationTest` verifies HTML reporting for both timed
  methods on H2 and HSQLDB while preserving all reference flows.
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
  of selected-scenario and commodity-group rules over generic rules. Multiple scenario lookups
  must keep their own rules and restore the generic fallback for an unspecified scenario.
- `NodeRulesReaderIntegrationTest`: actual exclusion-table rows on H2 and HSQLDB reach
  transhipment-only nodes and affect computed costs. Checks symmetry, direction, scenario/group
  isolation, selected-scenario precedence, generic defaults, missing tables and unknown nodes.
- `NodeRulesComparisonTest`, `NodeRulesPersistenceTest`: headless node-rule editor checks for
  exact dirty-state comparisons and duplicate detection, including concatenation/hash collisions,
  numeric equivalents, symmetric directions, signed inclusion/exclusion and scenario/group scope.
  Real H2/HSQLDB saves preserve earlier caller transactions and other rules; injected failures
  during deletion, insertion, statement closure and transaction completion preserve saved rows
  and pending edits for retry. Covers empty replacements, complete reads, malformed rows,
  first saves without implicit commits, connection cleanup, custom schemas and reordered columns.
- `NodeRulesSchemaTest`: reader/editor creation supports scenario 1000 on H2 and HSQLDB. Legacy
  two-digit scenario columns are widened through the real project-open upgrade hook, preserving
  generic/scenario rules, caller savepoints and unrelated pending edits; repeated upgrades are safe.
- `SoundPlayerTest`: a fake audio clip and real ULAW conversion exercise unavailable lines,
  initialization/listener/start failures, conversion-stream close failures and asynchronous STOP
  cleanup without an audio device. Successful playback retains its clip until the STOP event.
- `VehiclesParserTest`: capacity and passenger-car-unit property precedence, isolation
  between scenarios, groups and mode/means, default values, fractional values, and repeated
  initialization. Rejects malformed, non-finite, zero/negative capacities and negative PCU factors
  at all four precedence levels; checks complete retries after a failed load, preservation of other
  groups and valid overrides of invalid defaults. PCU zero remains valid. Error reporting is captured
  directly so rejection tests never open a dialog.
- `TransportServiceTest`: ordered stops and route links, duplicate/null handling, live edits
  through lists and iterators, bulk replacements/removals, and independent cloned stop indexes.
- `ServiceRegistryTest`: service replacement and renaming, duplicate IDs, removal through
  iterators, visibility of stop edits, protected collection views, and clearing cached lookups
  before another project is loaded.

The suite does not yet exercise complete project loading from disk or complete graphical
workflows. The assignment fixture supplies in-memory Esri layers and DBF table models.
The separate OpenMap layer fixture reads and writes actual temporary point/link shapefiles
and uses real SQL operations, replacing presentation callbacks. Style tests inspect graphic
attributes; they do not verify rendered pixels, mouse interaction or asynchronous repaint timing.
Schema-editor interaction and the actual save/cancel and modal-calibration dialogs remain
outside this headless suite. Calibration estimation, coefficient persistence and reuse in
assignments are covered by `LogitCalibrationIntegrationTest`; invalid vehicle properties
that display dialogs remain untested.

Most database integration tests use H2 and HSQLDB. The suite does not run against external MySQL,
MariaDB or PostgreSQL servers. Explicit JDBC configuration tests check that Nodus leaves those
servers independently managed, without connecting to them.

`JDBCUtilsTableExistsTest` checks literal underscores, percent signs and metadata escape characters
against actual H2/HSQLDB tables, rejecting names that would otherwise match metadata wildcards.
Column metadata must belong only to the named table and the connection's current literal schema,
including after a schema switch. This protects DBF/Excel exports and SQL-console metadata discovery.
`GroupedTablePublicationTest` exercises MySQL/MariaDB publication through an embedded-driver SQL
adapter. It checks imports and complete assignment output sets, failures before and after a grouped
rename, failed commits and restoration, first attempts, obsolete detail removal, and optional backup
cleanup failures. These tests verify the application recovery logic; they do not establish native
server statement atomicity. Separate validation on a disposable MariaDB instance covered native
grouped renames with both InnoDB and MyISAM, including a failure partway through a rename statement
and preservation of unrelated pending edits during imports. That server validation is not part of
the default suite and must never use production tables.

`MySqlStorageEnginesTest` supplies read-only JDBC metadata for transactional, nontransactional,
mixed and unknown table engines. It covers an independently selected default engine, empty
databases, other database products and query/read/close failures. Unexpected data changes,
connection-setting changes, commits, rollbacks or connection closure fail these tests.
`NodusProjectStorageEngineWarningTest` checks once-per-opening warnings, completion after an
unavailable metadata check, stale project callbacks, background queries and EDT presentation.
It also exercises the dialog checkbox, real local-settings persistence, suppression after reload,
isolation from other projects, and ignoring a checkbox choice after project replacement.
These tests do not connect to a production database or convert existing tables.

`NetworkEditFailureTest` covers SQL write failures partway through insertions and link splits,
including rollback and preservation of earlier uncommitted work. Integrity tests cover automatic
self-loop removal and failed-deletion retry, but not the timer's polling interval. Service-save
rollback covers row replacement in existing tables; first-time schema creation and legacy schema
changes can commit on some database engines and are not covered by that transaction guarantee.

Concurrency coverage includes complete assignments with two commodity jobs, path output,
worker cancellation and complete static/dynamic timed assignments. Equilibrium coverage uses
small parallel-route reference cases; large networks and nonlinear congestion functions remain
outside this coverage.

The OpenMap tests mirror their production packages under `test/com/bbn/openmap/` and run
automatically with the existing Ant target and GitHub workflow. To run just the layer tests:

```sh
ant -f build-tests.xml '-Dtest.includes=**/NodusEsriLayer*Test.class' Test
```

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

## Iterative assignment reference cases

`AssignmentTestProject` supplies shared in-memory layers, a private H2 database and
presentation callbacks for the assignment, service-routing and demand-loading tests.
The computation and database readers/writers are production implementations. The fixture
captures assignment messages and checks their number and severity; assignment error messages
fail the test immediately. This prevents completion dialogs under both Ant and Eclipse's
JUnit launcher, independently of whether a graphical desktop is available. Iterative
assignments retain their completion metadata for the convergence assertions.
`NodusC.displayAssignmentInformationDialogs` also controls informational completion dialogs
in the running application (default `true`). Tests exercise both values and restore the
original setting; all assignment fixtures share the `JDBCUtils` resource lock. The iteration
limit case verifies that convergence warnings are still reported with information disabled.

The equilibrium and incremental tests use two parallel links between A and B:

| Link ID | Cost function |
| --- | --- |
| 11 | `10 + 0.01 * VOLUME` |
| 12 | `12 + 0.01 * VOLUME` |

Vehicle load and PCU factors are one. Demand is divided equally between two commodity
groups, giving two independent worker jobs. For 1,000 units, equal route costs and
conservation give `q11 = 600`, `q12 = 400`, and a common cost of 16. Frank-Wolfe is
checked within 2.1 units because Nodus rounds vehicle counts upward per group.
Successive averages allows one update's flow change plus the two-group rounding bound.
Network conservation is checked to the saved output precision. Saved path quantities
have three decimal places and are rounded again when blended each iteration, so their
conservation checks allow at most `0.0005 * path row count * iteration count`, with a
minimum tolerance of 0.002. This distinguishes accumulated storage rounding from lost
network flow.

The incremental case uses 600 units and three triangular increments:

| Increment | Quantity | Selected link | Cost when selected |
| --- | ---: | ---: | ---: |
| 1 | 300 | 11 | 10 |
| 2 | 200 | 12 | 12 |
| 3 | 100 | 11 | 13 |

Final flows must be 400 on link 11 and 200 on link 12. The quantity-weighted sum of
the costs recorded when paths were selected must be 6,700. Tests check these values
directly in the saved tables, including each increment's quantity and selected route.

All these tests run with `ant -f build-tests.xml` and the existing GitHub workflow;
no additional target or CI configuration is needed.

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
and shut down executors, using bounded waits rather than sleeps. Integration tests use
`@Tag("integration")` for selection in JUnit-aware tools; they remain part of the default Ant suite.

## Scenario arithmetic

Comparison subtracts scenario 2 from scenario 1. A link's identity includes both endpoint
descriptors (node, link, mode, means and service), time period and operation type. Commodity
columns are matched by name; the output contains their sorted union, with missing groups/links
treated as zero. Matching links retain scenario 1's length; scenario-2-only links retain its length.
Legacy tables without `vtype` infer the operation, consulting current service stops when needed.

For each commodity, comparison subtracts unit costs, quantities and vehicles independently.
Its aggregate unit cost is the difference between the two sources' quantity-weighted unit costs,
even when the resulting quantity is zero. Summation adds quantities and vehicles and uses
`(cost1 * quantity1 + cost2 * quantity2) / (quantity1 + quantity2)` for each unit cost. Its aggregate
cost uses the same formula over all source commodity amounts, before storage rounding. A weighted
cost with a zero denominator is defined as zero; signed nonzero quantities remain valid weights.
Aggregate quantities/vehicles are always recomputed from the commodity values. Stored cost and
quantity precision remains three decimal places.

Both source IDs and the result ID must be distinct. Invalid source data and SQL failures propagate
to the caller. Arithmetic stages its result using the output replacement mechanism below. A
successful result replacement removes obsolete destination path tables; a failed attempt preserves
the complete previous result and both sources' output tables.

## Assignment output replacement

Assignments write the virtual network, path headers and optional path details to private staging
names. The scenario's existing result tables remain available until every writer has saved,
committed and closed successfully. Publication then replaces the whole output set, using backup
names to restore previous tables if a rename or publication commit fails. A canceled or failed
first attempt leaves no scenario output; a failed replacement retains the previous complete result.
A successful header-only assignment removes the previous detail table. Temporary names do not use
the scenario's public table prefixes.

MySQL/MariaDB publish the complete set with a single grouped `RENAME TABLE`, including removal of
obsolete details. Recovery inspects actual names before issuing one inverse rename, handling both
errors before execution and lost acknowledgements after execution. Other engines use the existing
sequential replacement path. Crash recovery depends on the server version and storage engine.

Post-assignment scripts and success notifications run after publication. If the script itself
fails, the completed network and path outputs remain together; the assignment reports failure.
If the database prevents restoration after a publication failure, the backup table is retained
rather than deleting the last complete data. Failure to remove backups after successful publication
is reported without undoing the completed result.

Output DDL and commits still use the shared project connection. They can commit earlier unrelated
SQL work, as demonstrated on H2 and HSQLDB. Savepoints cover inserts made after staging-table
creation; they do not restore DDL or undo work already committed by the database.

## Parquet runtime isolation

`ParquetRuntimeTest` launches a child JVM with application/test classes and only
the JARs under `lib/`, matching the installed library layout. The child asserts
that Hadoop and parquet-floor are absent, exports/imports typed/null/empty tables on both embedded engines, and checks
that failed exports/imports preserve existing data. It also reads Snappy and
uncompressed fixtures with unsigned and nanosecond timestamp columns. The parent uses the
standard Apache reader to verify every exported value. Hadoop development JARs
in `devtools/parquet/` remain on the parent test/compile classpath only.
