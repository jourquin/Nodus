# Nodus source-tree test audit

Snapshot: commit `d8e56d4` (clean working tree), audited on 18 October 2026.

## Scope and evidence

The inventory covers all 237 production Java source files excluding package descriptors,
including Nodus's OpenMap extensions, and all 97 test classes. Source inspection covered
computation, persistence, project lifecycle, services, plugins, file handling, result processing,
and the logic embedded in the UI. Existing test assertions and indirect callers were checked
before classifying a gap; a missing class-named test was not treated as proof of missing coverage.

The complete suite passed under Java 11 with JaCoCo recording in the main JUnit fork:
948 tests, zero failures, zero errors, zero skips. Coverage was measured across the repository's
production class files, including inner classes. Helper JVMs spawned by individual tests were
not instrumented, so code executed only in those JVMs is not represented here.

| Measure | Covered / total | Percentage |
| --- | ---: | ---: |
| Executable source lines | 15,538 / 40,222 | 38.6% |
| Bytecode branches | 6,176 / 14,231 | 43.4% |

These whole-tree percentages include substantial GUI code and are not proposed coverage targets.
For comparison, cost computation has 93.7% line coverage, virtual-network code 85.9%, modal-split
code 84.9%, and assignment-worker code 77.0%. Recommendations below prioritize observable
correctness, data integrity and lifecycle behavior over increasing percentages.

No production or test source files were changed. Probes used private in-memory databases and
temporary files. In the list below, "confirmed" means a temporary probe reproduced the behavior;
"coverage gap" means the proposed behavior is not adequately protected, not that a bug was proved.

## Ranked must-have test groups

### 1. Scenario renaming/removal with sparse settings, collisions and SQL failures

Evidence: [ProjectScenarios.java](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/project/ProjectScenarios.java:101)
and [NodusProject.renameLocalProperty](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusProject.java:2310).
Existing `ProjectScenariosTest` covers one successful rename/remove with every property pre-seeded
and no destination collision. Its successful line coverage leaves half the helper's branches untested.

Confirmed: omitting an optional scenario property causes `NullPointerException` in
`Properties.setProperty(newKey, null)`. With a destination vnet table already present, the source
vnet remains under its old ID while source path tables move to the new ID and the destination's
cost-file property is replaced. This mixes outputs from different scenarios.

Add H2/HSQLDB cases for absent optional properties, identical old/new IDs, collision in each output
table, failure in the second/third rename, failed table deletion, and preservation of unrelated
scenarios. A failed/colliding operation must not silently migrate properties or select a partially
renamed scenario. Exercise both default and custom table prefixes. Do not assume that rollback
can undo DDL on both engines; validate the externally visible failure policy.

### 2. Real-link length and coordinate-unit handling

Evidence: [RealLinkInitializer.java](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/real/RealLinkInitializer.java:144).
The suite executes EsriPolyline length calculation, but neither plain OMPoly calculation nor the
endpoint fallback. `normalizeRadians` guesses units from coordinate magnitude.

Confirmed: endpoint fallback from geographic `(0°, 0°)` to `(0°, 1°)` returns **6,378.137 km**,
instead of approximately **111.3 km**, because a one-degree longitude is interpreted as one radian.

Add pure unit cases for EsriPolyline, OMPoly and endpoint fallback; coordinates close to the
equator/prime meridian and outside ±π degrees; reversed endpoints; intermediate polyline vertices;
coincident/missing endpoints; and repeated initialization with PCU preservation/reset. Derive
reference distances independently from the input's declared units, not from the helper under test.

### 3. Invalid vehicle characteristics and retry after a failed load

Evidence: [VehiclesParser.java](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/costs/VehiclesParser.java:109)
and [VirtualLink.volumesToVehicles](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/virtual/VirtualLink.java:566).
Existing tests cover valid precedence/defaults, not rejected values or partial initialization.

Confirmed: capacity `0` and PCU `NaN` are accepted together and loading returns true. Downstream
conversion divides by capacity and casts floating-point results to integers, so invalid values
can silently produce implausible vehicle/traffic counts.

Add zero/negative/non-finite capacity, negative/non-finite PCU, malformed numeric text, and
finite values outside the supported vehicle-count range. Explicitly define whether PCU zero
is allowed rather than assuming it is invalid. A failed load must not leave a group marked
initialized: fixing the property and retrying must perform a complete load. Check a valid
fractional capacity still produces the correct ceil-rounded count. Replace error presentation
in tests so rejection is checked independently of a desktop display.

### 4. WMS XML external-entity rejection

Evidence: [NodusWMSLayer.java](/Users/jourquinb/git/Nodus/src/com/bbn/openmap/layer/image/NodusWMSLayer.java:213)
and [WmsCapabilitiesXmlParser.java](/Users/jourquinb/git/Nodus/src/com/bbn/openmap/layer/image/capabilities/WmsCapabilitiesXmlParser.java:49).
These source files receive no execution in the suite.

Confirmed: with the production SAX factory configuration, an XML external entity referring to
a temporary local sentinel file is expanded into the WMS service title. The probe read only a
file created for this audit and contacted no server.

Add in-memory XML tests that reject or safely ignore external general/parameter entities,
external resource retrieval and recursive entity expansion. Keep a normal WMS 1.1 capabilities
fixture, nested layers, inherited settings and split character callbacks working. Test parser
configuration as used by the layer, not only the SAX handler; a correctly tested handler alone
would miss the unsafe factory settings. Account explicitly for legitimate WMS DTD compatibility.

### 5. Real persisted-project opening and legacy layer validation

Evidence: [ProjectFilesTools.java](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/database/ProjectFilesTools.java:372)
and [NodusProject.openProject](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusProject.java:1918).
Coverage records no execution of the real `openProject` method. The recent close/open tests replace
the next-project load. Most of `ProjectFilesTools` is also unexecuted.

Confirmed: a point DBF whose mandatory `STYLE` column is character rather than numeric is accepted;
a point DBF missing its handling column throws `ArrayIndexOutOfBoundsException` rather than
returning an invalid-layer result.

Add focused validation tests for every mandatory column's name/type/width/precision, truncated
schemas, wrong shape type, missing sidecars and the specifically supported old ID widths. Test
the old link schema without `ENABLED`: insert it once, preserve row order/custom fields/Unicode
and encoding, and never treat another malformed schema as upgradeable. Validate consecutive
files to expose leakage of the static upgrade flag.

Add one small headless load/close/reopen test using real temporary .nodus, shapefile/DBF files and
an explicit embedded JDBC configuration. Check .nodus/.local precedence, modified-DBF reimport,
real layer loading, open-hook completion and late failure cleanup. Include external unloaded
layers when checking new ID allocation: `ProjectLayerIds.getNewId` is not reached by current tests,
whose editing fixture supplies replacement ID generators.

### 6. Assignment output failure and coordinator finalization

Evidence: [VirtualNetworkWriter.saveNetwork](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/virtual/VirtualNetworkWriter.java:194)
and [Assignment.run](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/assign/Assignment.java:399).
Path-writer faults and successful assignments are tested separately; vnet output cancellation,
write errors and commit failures are not covered by those checks.

Inject cancellation after an earlier SQL batch, a later batch failure, commit failure, and a
driver without batch support on H2/HSQLDB. Verify no partially inserted result rows are presented
as a successful output, resources close, progress/menu state restores, and no success notification
or post-assignment script runs. Inject final path-writer close failure after vnet saving to check
the policy for a vnet table with absent/incomplete path output. Keep vnet and path results consistent.

Define preservation/replacement behavior for previous outputs explicitly. Current initialization
drops/recreates tables before taking a savepoint, and DDL can commit, so a test must not assume
that the existing savepoint protects the old table or earlier unrelated transactions.

### 7. Scenario comparison and summation

Evidence: [Scenarios.java](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/scenario/Scenarios.java:221).
Coverage is zero for all scenario arithmetic. The rename/remove helper test does not exercise it.

Add real SQL examples with matching and disjoint links; unequal/reordered commodity sets;
multiple service IDs, directions, virtual operation types and time periods; filters and empty
inputs. Assert the complete result descriptors, signed differences, summed quantities/vehicles
and the documented unit-cost/aggregate-cost semantics. Include current and legacy tables lacking
`vtype` so inference cannot merge distinct operations. Check input preservation, batch fallback,
later write failure and source/result ID collisions on both databases.

### 8. Result-to-map values, scaling and playback lifecycle

Evidence: [LinkResults.displayPath](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/results/LinkResults.java:480),
[NodeResults.readOD](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/results/NodeResults.java:145)
and [LinkResults playback](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/results/LinkResults.java:849).
Both result processors have zero coverage; performance-indicator and style tests exercise different code.

Add SQL-to-model checks for quantities/vehicles, repeated path links, unknown IDs, multiple layers,
zero/equal/negative comparison values, visibility and export precision. Check relative stroke/node
sizes, not rendered pixels. Source inspection found that `displayPath` updates local min/max
variables but later width scaling reads the instance fields; an unequal-flow case should expose this.

For playback use controlled EDT scheduling: period order, end boundary, Enter/Escape, one completion
callback, restored label/key listeners, bad time settings, SQL failure and project close during
playback. The current timer is local to the scheduling method and is not registered for project
cleanup, so specifically check that it cannot query a closed project or update the next one.
Keep ordinary node/link result calculation tests separate from timer/lifecycle tests.

### 9. Complete incremental/Frank–Wolfe hybrid assignment

Evidence: [IncFrankWolfeAssignment.java](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/assign/IncFrankWolfeAssignment.java:67).
The entire coordinator has zero coverage. Testing IncrementalAssignment and FrankWolfeAssignment
separately does not exercise its four warm-up increments or the transition between phases.

Reuse the analytical parallel-route fixture. Check conservation across the warm-up/FW transition,
nonnegative network and path flows, equilibrium within the declared vehicle/storage rounding
tolerance, distinct handling of warm-up versus FW iteration counts, and correct completion reason.
Cover zero-FW/iteration-limit behavior, lambda endpoints and cancellation in both phases. Compare
serial, parallel and repeated outputs, with path saving both enabled and disabled.

### 10. Service shortest-path construction

Evidence: [ServiceHandler.findShortestServicePath](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/services/ServiceHandler.java:826).
Its graph builder/reconstruction path receives no coverage. Current service-routing tests load
predefined routes; they do not test generating routes from the physical network.

Add a tiny graph with an independently known length-minimizing route. Check disabled links,
mode/means restrictions and ALL_MEANS, parallel links, sparse real node IDs, reverse endpoints,
ordered waypoints, disconnected segments and changed geometry/speed attributes. Reconstruct
ordered link IDs and round-trip a generated service through SQL. Failed route construction must
leave an existing service intact; test explicit replacement and save failure separately.

## Useful follow-ups below the must-have group

- Plugin discovery/fault isolation: broken constructor/getProperties, malformed menu IDs, missing
  dependencies, lazy resource loading and reopening a project. Normal scope/disposal behavior is
  already covered by `ExtensionApiCompatibilityTest`; avoid duplicating it.
- Multi-flow path admission: explicit MAX_DETOUR boundaries, duplicate alternative elimination
  and cheapest-intermodal-only behavior through both full coordinators. Edge-penalty primitives
  and modal-model fitting already have substantial coverage.
- Reachability observer and computing-time accounting: assert instrumentation leaves routes and
  weights unchanged for both graph representations; reset knowledge on a new source/restriction
  sequence. Use a deterministic clock for accounting, not wall-clock performance assertions.
- OD-table discovery (distinct from tested demand loading): valid schemas, reserved result/service
  prefixes, quoted identifiers and preservation of connection state.
- TableSorter model/view mapping, updates and null values; these can run headlessly. Give these
  lower priority than computation/persistence faults.
- Additional map-tile/WMS parsing and purely visual UI behavior. Do not count dialog constructors,
  paint methods or trivial accessors as must-have solely because coverage is low.

## Artifacts and reproduction

All audit artifacts are under `/private/tmp/nodus-source-audit/`:

- `build-audit.xml`: temporary copy of the existing Ant test target with a JaCoCo JVM argument.
- `tests.log`: full passing suite output; ordinary JUnit reports remain in `test-build/reports/`.
- `coverage.exec`, `coverage.tsv`, `methods.tsv`: recorded execution and source/method summaries.
- `AuditProbe.java`: endpoint geometry, scenario rename and vehicle-characteristic probes.
- `LayerValidationProbe.java`: malformed/truncated DBF schema probes.
- `XmlProbe.java`: external-entity probe using only a newly created local sentinel file.

The probes characterize current behavior; they are not committed regression tests. Tests for
observed defects should assert the corrected behavior, not preserve the defective output.
