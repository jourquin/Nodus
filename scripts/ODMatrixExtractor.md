# Master OD script and dataset contract

[ExtractMasterOD.groovy](ExtractMasterOD.groovy) contains the complete, dataset-specific
OD extraction implementation. `ExtractMasterOD` holds editable settings and the
Nodus map-panel entry point; `ODMatrixExtractor` is a helper class declared in the
**same Groovy file**. It is not a Nodus core class or public application API.
Groovy compiles both classes in memory when the script runs. No companion JAR,
Python, DuckDB, Hadoop installation or external SQL server is required.

Nodus core remains schema-neutral. The script uses the generic
`ParquetFiles.openProjectedReader(Path, List<String>)` to stream selected Parquet
columns and `TableImport` to stage database replacements. All commodity rules,
centroid field names, catalogue requirements and OD table layouts live in the script.
The helper classes use `@CompileStatic` to keep per-row processing compiled and fast.
Use a Nodus build containing the generic projected-reader method; no OD-specific
class is installed in its application JAR.

## Desktop use

Open the relevant project and its centroid layers. In **Tools → Groovy scripts**, open
[ExtractMasterOD.groovy](ExtractMasterOD.groovy), choose the
geography and commodity grouping, and run. Select the resulting table in the
assignment dialog. Save any work first and keep the project, layers and database
unchanged until extraction finishes. Do not run an assignment concurrently.

The script obtains the open project from `nodusMapPanel.getNodusProject()` and
reads its directory with `getLocalProperty(NodusC.PROP_PROJECT_DOTPATH)`. An empty
`masterDirectory` field (the default) uses that directory, containing `catalog.json`
and the referenced Parquet files. Set the field only when your masters are stored
elsewhere. No path is inferred from `NODUS_HOME` or the working directory.

```groovy
import edu.uclouvain.core.nodus.utils.ODMatrixExtractor

ODMatrixExtractor.extract(nodusMapPanel.getNodusProject(), [
    masterDirectory: '/data/2024.3_masters',
    modes: ['road', 'rail', 'iww'],
    nutsLevel: 2,
    commodityLevel: 'CHAPTER',
    groups: [0, 9],
    tablePrefix: 'OD2024',
    missingPolicy: 'AVAILABLE_WITH_REPORT'
])
```

This creates `OD2024_L2_CHAPTER_ROAD`, `OD2024_L2_CHAPTER_RAIL` and
`OD2024_L2_CHAPTER_IWW`. Each has exactly `GRP SMALLINT, ORG BIGINT, DST BIGINT,
QTY DOUBLE PRECISION`. Quantities are annual tonnes. Direction, intrazonal demand
and existing proxy estimates are retained. Positive available quantities alone are
written; there is no cost, distance or modal-access filter. Source records are never
changed. Re-running replaces the selected destination tables through native staging.

## Java-style Groovy example and bundled sample

[ExtractMasterOD.groovy](ExtractMasterOD.groovy) follows the other Nodus scripts:
a named `ExtractMasterOD` class, explicitly typed editable fields, constructors,
ordinary Java collections and semicolons. Its final line is:

```java
new ExtractMasterOD(nodusMapPanel);
```

The `NodusMapPanel` constructor obtains the current project and resolves an empty
`masterDirectory` from `NodusC.PROP_PROJECT_DOTPATH`. For explicit automation, use
the overload `new ExtractMasterOD(nodusMapPanel, options)`; options override the
class settings. The ordinary entry point reads the editable fields directly.

The [tiny synthetic sample](master-od-sample/README.md) contains a **1.6 KB Parquet**
with eight records and 26 available annual tonnes, a catalogue, matching centroid
and road shapefiles, and `sample.nodus`. Copy the sample folder to a writable
location and open its project. Leave `masterDirectory = ""`: the script reads the
copied project's own catalogue and Parquet. The prefix `SAMPLEOD` identifies these
example outputs. All sample zone codes and quantities are fictional.

For the real 2024 masters, use `tablePrefix = "OD2024"`. If they remain in a separate
master-delivery folder rather than beside your `.nodus` project, set
`masterDirectory` explicitly to that folder. The embedded helper requires its `masterDirectory` option; the entry-point
class supplies it from the open project by default. Sample details and expected outputs at all four resolutions are in its README.

## Embedded helper and options

The normal desktop entry point stays at the end of the file:

```java
new ExtractMasterOD(nodusMapPanel);
```

For automation, the same script can use its explicit constructor overload:

```java
new ExtractMasterOD(nodusMapPanel, options);
```

The helper's internal `extract(project, options)` writes JSON reports; its
four-argument overload accepts log/report callbacks. These are script-local
implementation methods, not imports available to other Java plugins through core.
Callbacks receive an unmodifiable snapshot once per started mode, including
failure or cancellation. Preflight errors propagate without a mode report.

| Option | Default | Meaning |
| --- | --- | --- |
| `masterDirectory` | required | Local folder containing `catalog.json` and the master files. |
| `modes` | all catalogue modes | List of distinct mode names; case is normalized to lower case. |
| `nutsLevel` | `3` | Integer `2` or `3`; no implicit level-1 centroid mapping. |
| `commodityLevel` | `'NSTR'` | `'NSTR'` detailed groups or `'CHAPTER'` main chapters. |
| `groups` | `[]` | Integer codes at the chosen commodity level; empty means all declared groups. Code `00` is numeric `0`, not octal or a string. |
| `tablePrefix` | `'OD' + catalogue year` | 1–60 characters: starts with a letter, then letters, digits or underscores. Names are normalized through Nodus's SQL identifier rules. |
| `missingPolicy` | `'AVAILABLE_WITH_REPORT'` | Extract known components and report gaps, or use `'REQUIRE_COMPLETE'` to reject any selected missing component, unavailable quantity or source-absent commodity. |
| `verifyHashes` | `true` | Check SHA-256 for every selected source before publication. Size and file-identity checks always run. |
| `batchSize` | `5000` | Positive integer, clamped to 100–10000 rows per JDBC batch. |
| `reportDirectory` | project/`ODExtractionReports` | Report destination for the two-argument overload; ignored by the callback overload. |

Unknown options and incompatible values are rejected. Year is catalogue metadata,
not hardcoded to 2024. Extra modes can be declared in the catalogue using names
matching `[a-z][a-z0-9_]{0,19}`. This script-specific version-1 contract uses NST/R and annual tonnes.
It does not constrain Nodus's generic Parquet importer or row-reader API.

## Catalogue contract (schema version 1)

The UTF-8 `catalog.json` must contain:

| Field | Contract |
| --- | --- |
| `schema_version` | Integer `1`. |
| `year` | Integer 1–9999; used for the default table prefix and reports. |
| `unit` | `'annual tonnes'`. |
| `release`, `source_release` | Nonempty provenance strings. |
| `supported_nuts_levels` | Array containing `2` and `3`. |
| `classifications` | Array containing `'NSTR'` and `'CHAPTER'`. |
| `commodities` | Nonempty array of unique integer `id` (0–99) and `chapter` (0–9). Chapters must be nondecreasing when IDs are sorted. Other descriptive fields are allowed. |
| `zones` | Nonempty array of unique positive signed-32-bit `id`, `level` (2/3), nonempty `code`, and integer `parent` (0–2147483647). Every level-3 parent must identify a catalogued level-2 zone. |
| `modes` | Nonempty object keyed by mode name, with each value described below. |
| `unlocated_iww_tonnes` | Optional nonnegative all-goods ledger outside the spatial master. It is reported for IWW, never assigned to invented centroids or commodities. |

Each mode has `file` (path within the catalogue directory, including after symlink
resolution), `bytes` (nonnegative integer file size), `sha256` (64 lower-case hex
digits), `groups` (array) and `source_absent_nstr_groups` (array of integer IDs).
Each `groups` entry contains `id`, `unestimated_components` and
`unavailable_quantity_rows` (nonnegative integers). Every catalogue commodity must
be declared exactly once as present in `groups` or source-absent. Other statistics,
labels and provenance fields are allowed. Group metadata must be derived from the
same file before its size and hash are recorded.

The strict policy uses selected group metadata in preflight and checks selected
records again while reading. It rejects source-absent NST/R groups even when the
selection is specified by chapter. Passing strict mode establishes completeness of
the declared recorded components only; absent OD pairs are not certified zero flows.

## Parquet contract

Columns may appear in any order and additional flat provenance columns are allowed.
The extractor projects these fields, whose physical types must match exactly:

| Field | Parquet physical type | Value contract |
| --- | --- | --- |
| `GRP` | `INT32` | Required NST/R code declared in the catalogue. |
| `ORG`, `DST` | `INT64` | Required positive signed-32-bit centroid IDs at level 3. |
| `ORG_L2`, `DST_L2` | `INT64` | Required catalogue parent IDs at level 2. |
| `CHAPTER` | `INT32` | Required chapter matching `GRP` in the catalogue. |
| `QTY_AVAILABLE_PART` | `DOUBLE` | Nullable; otherwise finite, nonnegative annual tonnes. Known component of an incomplete cell is allowed. |
| `UNESTIMATED_CELLS` | `INT32` | Required nonnegative count of unestimated source components, not missing tonnes. |
| `PROXY_APPLIED` | `BOOLEAN` | Required flag; proxy demand is retained. |
| `PROXY_QTY` | `DOUBLE` | Nullable; otherwise finite, nonnegative proxy tonnes. |
| `SPATIAL_REVIEW` | `BOOLEAN` | Required flag marking spatial estimates requiring review. |

Fields must be scalar (not repeated). “Required” means a value is present in every
record; the Parquet declaration may be required or optional. Rows must have strictly
unique keys sorted by `GRP, ORG, DST`. A chapter must not reappear after a later one.
GZIP, SNAPPY and UNCOMPRESSED are supported through the shared Nodus reader, with
a 64 MiB decoded-page limit. Other codecs fail explicitly.

The catalogue maps every fine endpoint and parent, including non-NUTS fallback
zones when present. Loaded node-layer models identify centroid layers by `LEVEL`
and `ZONE_CODE`; they must also contain `NUM` and `PARENT_ID`. IDs, codes, levels and
parents of selected endpoints must match, including zero/unavailable records.
Geometry may change. For a level-2 extraction the relevant level-2 centroids must
be loaded; the fine-to-parent mapping is still checked against the catalogue.

## Streaming, reconciliation and recovery

Detailed level-3/NST/R output streams directly to prepared JDBC batches. Other
choices aggregate one **output commodity group at a time**, using compensated
summation per OD pair. Memory therefore depends on the largest selected group's
pair count; no fixed heap size can cover every future dataset. Each group is sorted
before writing. Source order, duplicate keys, chapter mapping, quantities and
centroid identity are checked. Output counts are checked in staging and after
publication, and exported totals must match selected available totals within
**0.05 tonne**. This tolerance concerns arithmetic, not statistical uncertainty.

All selected file sizes/hashes and strict-policy metadata are checked before any
mode is published. Source identity is rechecked before publication. Each mode uses
`TableImport.replace`, retaining Nodus's protections for existing tables, transactions,
indexes and constraints. Modes are **independent publications**: a later failure
leaves earlier completed modes available. Use another prefix if an existing table
is protected by Nodus. The main project connection is borrowed and never closed.

Calling-thread interruption requests cooperative cancellation. A private JDBC
worker polls the request during hashing, record reading, aggregate flushing and
before publication. The caller waits for staging recovery and consumes its interrupt
flag. Native-console interrupt instrumentation is covered as well: even an
exception injected before the waiting loop must wait on an uninterruptible Java
completion signal before releasing the execution flag. Such console instrumentation
can surface `InterruptedException` instead of the helper's `InterruptedIOException`. The worker is never interrupted. Interrupting JDBC's file I/O can close an
embedded database's file channel; do not use `Future.cancel(true)`, interrupt the
worker, or close the borrowed connection to stop extraction. A mode already past its
last check may publish before the request is observed.

Callbacks run on that private worker, not Swing's event-dispatch thread. They must
not block, interrupt the worker, alter the database/layers or switch projects.
Marshal any UI updates to Swing appropriately. The entry point shares a JDK execution flag through the map panel's existing
generic object store, so separate compilations of this script cannot run on that
panel simultaneously. The helper also guards concurrent calls within a compilation.
Other database operations must be coordinated by the caller.

If reporting fails after publication, that table stays published and the callback
exception propagates. If extraction also fails, reporting failure is attached as a
suppressed exception. The default report writer uses a unique run ID and
`CREATE_NEW`, so reports are not silently overwritten.

## Reconciliation reports

Reports identify mode, table, year, units, geography, commodity grouping, selected
groups, policy, source/master release, file path, source hash and whether it was
verified. `status` is `PUBLISHED`, `FAILED` or `CANCELLED`; errors after completed
publication retain `PUBLISHED` and an `error` field. Counts include scanned/selected
source rows, positive/zero rows, unavailable quantities and unestimated components.

Quantity fields include selected/exported available tonnes and their difference,
proxy tonnes, spatial-review tonnes and largest buffered group. Proxy and spatial
flags can overlap, so their totals must not be added. Failure counts describe work
attempted in staging, not demand published to the destination. Reports also list
`source_absent_detailed_nstr_groups_in_selection` and explicitly mark absence as
not an observed zero. Unlocated IWW tonnes are an unallocated all-goods ledger,
not a commodity-specific addition. Elapsed seconds cover that mode's extraction,
not preflight hash verification.

## Tests and maintenance

```sh
ant -f build-tests.xml '-Dtest.includes=**/ODMatrixExtractorTest.class' Test
ant -f build-tests.xml Test
```

Tests compile the actual Groovy implementation and use synthetic Parquet files,
private H2/HSQLDB databases and loaded centroid stubs. They exercise the real map-panel
entry point, its explicit configuration overload and execution across separate
script class loaders. They also assert that no core OD extractor class exists. They cover every geography and
commodity combination by cell, chapter zero, filters and absence, missing/partial
and proxy quantities, strict policy, invalid options/schema/order/parent mapping,
source changes/hashes, cancellation, concurrent-call refusal, table recovery,
report failures and default JSON persistence. A child JVM runs the Groovy example
with installed libraries only and confirms Hadoop and DuckDB are absent. The bundled Parquet sample and centroid DBFs are also checked at all four resolutions.
No live Nodus project or research dataset is accessed by these tests.

`database.parquet.ParquetFiles.openProjectedReader(Path, List<String>)` is a generic
streaming facade for arbitrary top-level Parquet columns. It exposes schemas and
materialized groups while hiding low-level methods whose signatures reference
Hadoop. It preserves nested/scalar values, NULLs and original type annotations,
and advances across row groups. Ordinary imports continue to use the generic
`openReader(Path)` factory. Neither method knows anything about OD matrices.
`ParquetFilesTest` covers column order, exact integers, Unicode, NULLs, empty files,
invalid projections and closure. Script tests also run with installed libraries
only, without Hadoop, DuckDB or a core `ODMatrixExtractor` class.
