# Example Groovy, Python and R scripts

These scripts demonstrate access to the Nodus Java API through the running application's
`NodusMapPanel`. Groovy runs inside Nodus; Python and R run in separate processes and
connect through a bridge started by a Nodus Groovy script.

## Running Groovy scripts

1. Open the project to work with. Most examples require an open project; the ARGB color
   chooser can also run without one.
2. Choose **Tools → Groovy scripts**, then open the desired `.groovy` file.
3. Review its parameters, save any changes, and use the editor's **Run** command.

Nodus supplies the variable `nodusMapPanel` to both its script editor and the optional
native Groovy console. A standalone `groovy example.groovy` command does not supply that
variable or attach to an existing Nodus process.

[example.groovy](example.groovy) illustrates the main entry point:

```groovy
def project = nodusMapPanel.getNodusProject()
if (project.isOpen()) {
    println project.getName()
}
```

## Connecting Python and R

The Java server libraries are bundled with Nodus as `lib/py4j0.10.9.2.jar` and
`lib/j4r-1.1.1.jar`. Install the corresponding client in the Python or R environment
and start the bridge before running the client example.

### Start the bridge in Nodus

[demo/demo.groovy](../demo/demo.groovy) starts both bridges when the demo project opens
and stops them when it closes. For another project, adapt its bridge blocks in a
`<projectname>.groovy` file beside `<projectname>.nodus`, retaining any existing hooks.
Open the project and wait for loading to finish before connecting the client.

The examples use these settings:

| Bridge | Client settings |
| --- | --- |
| Py4J | Default gateway at `127.0.0.1:25333`. |
| J4R | Ports `18000:18001`, internal ports `50000:50001`, `public=TRUE`, key `212`. |

If several Nodus instances run simultaneously, give their bridges different ports and
adjust the clients accordingly. Start each bridge in one lifecycle scope, so application
and project hooks do not compete for the same ports.

The repository-root [nodus.groovy](../nodus.groovy) changes the initial map view and
assignment settings; it does not currently start either bridge.
[scripts/nodus.groovy](nodus.groovy) is another application-wide bridge example.
When adapting it, use the current lifecycle variable `quitNodus` and J4R's public
`requestShutdown()` method, as used in the demo project hook.

These bridge settings are separate from Nodus's database listeners. The J4R sample uses
public-server mode, a fixed demonstration key, and sockets without an explicit loopback
binding. The local-only HSQLDB/H2/Derby settings do not restrict that bridge's listeners.
See the [database connection guide][database-connections]
for direct JDBC access, which does not require a Py4J or J4R bridge.

[database-connections]: ../ADVANCED.md#local-database-servers-and-external-jdbc-connections

### Python client

Install the client matching the bundled Py4J server version:

```sh
python3 -m pip install py4j==0.10.9.2
```

With Nodus, its project and the bridge running, execute this from the repository root:

```sh
python3 scripts/example.py
```

[example.py](example.py) uses `JavaGateway()` and obtains `nodusMapPanel` from
`gateway.entry_point`. A custom server port also requires a matching client configuration.

### R client

A matching R client is included as [J4R_1.1.1-228.tar.gz](J4R_1.1.1-228.tar.gz).
From an R session whose working directory is the repository root, install it with:

```r
install.packages("scripts/J4R_1.1.1-228.tar.gz", repos = NULL, type = "source")
```

After starting the Nodus bridge, review the indexing limitation below before running:

```r
source("scripts/example.R")
```

[example.R](example.R) connects with `connectToJava(...)`, retrieves the map panel with
`getMainInstance()`, and calls `shutdownClient()` at the end. The project hook owns the
server's lifetime; disconnecting this client does not close the Nodus project.

## Automatic scripts and lifecycle variables

Nodus supplies `nodusMapPanel` to every lifecycle script, together with these flags:

| Script location | Opening/startup flags | Closing/exit flags |
| --- | --- | --- |
| `NODUS_HOME/nodus.groovy` | `startNodus=true`, `quitNodus=false` | The values are reversed. |
| Beside `<projectname>.nodus` | `openProject=true`, `closeProject=false` | Flags reversed. |

The project hook must be named `<projectname>.groovy`. `NODUS_HOME` is a Java system
property; when absent, Nodus looks in the current working directory for `nodus.groovy`.
The lifecycle flags are not automatically supplied to scripts run manually in the editor.
The project-open hook runs before `project.isOpen()` becomes true, so scripts guarded by
that check are intended to run after project loading, not unchanged inside the open hook.

Use `nodusMapPanel.storeObject(key, value)` and `retrieveObject(key)` to keep bridge
instances or other objects between calls, as the demo hook does.

## Basic examples and current limitations

[example.groovy](example.groovy), [example.py](example.py) and [example.R](example.R)
illustrate the same operations: obtain the project, check whether it is open, print its
name and node layers, hide the first node layer, and print node attributes. They modify
layer visibility and are not identical in their handling of rows or empty data:

- All three assume that at least one node layer exists.
- The Groovy example attempts rows 0–9 without checking the row count, and calls
  `toString()` on each value without a null check.
- The Python example prints at most ten rows, bounded by the actual row count.
- The R example currently starts `getRecord()` at 1, although Java record indices start
  at 0. It skips the first record and can request a row past the end of a small table.
  When adapting it, iterate over `seq_len(min(10L, dbf$getRowCount()))` and pass
  `as.integer(i - 1L)` to `getRecord()`.

## Utility scripts

Review each script's editable parameters and output names before running it. The data
processing examples write tables, files or services in the current project; use a copy
when trying them for the first time.

- [AddDistancesODTable.groovy](AddDistancesODTable.groovy) computes shortest network
  distances for distinct OD pairs and writes `<odTableName>_dst` with `grp`, `org`, `dst`,
  rounded `qty` and `length`. Only OD rows joined to saved paths enter the result.
  Defaults are table `od`, two threads, temporary table `tmpOD`,
  and assignment scenario **99**. It replaces the destination table without confirmation,
  runs scenario 99 without the assignment overwrite prompt, then removes that scenario
  and the temporary table. Choose names and a scenario that do not contain work to keep.
- [ExtractView.groovy](ExtractView.groovy) exports node/link shapefiles and OD DBFs with
  the suffix `_extract` in the project directory. It keeps nodes inside the displayed
  geographic rectangle, links whose two endpoints are inside, and OD entries whose two
  centroids are inside. It does not geometrically clip lines at the viewport boundary.
  It processes valid OD tables through temporary `_extract` tables, exports those tables
  to DBF, then drops them. Repeated runs reuse the same output names.
- [CreateShortestPathServicesFromOD.groovy](CreateShortestPathServicesFromOD.groovy)
  creates one service per distinct **unordered** OD pair, using shortest physical length
  for the selected mode/means. OD quantities do not determine frequency. Defaults are
  table `OD`, mode 1, means 1 and five services per week, stored as annual frequency.
  Only the origin and destination are initially stops. Existing service tables prompt
  for **Add**, **Clear** or **Cancel**. Set `previewOnly=true` to inspect the result without
  saving services; the checked-in default is `false`.
- [ARGBConverter.groovy](ARGBConverter.groovy) opens a color chooser and displays the
  selected color as an ARGB hexadecimal string.
- [NetworkSimplifier.groovy](NetworkSimplifier.groovy) merges eligible adjacent links
  and removes intermediate transit nodes, subject to topology, connector and attribute
  checks. It scans loaded link layers by default. The checked-in settings include
  `dryRun=false`, `saveProject=true`, backups enabled and confirmation before applying.
  Set `dryRun=true` for a preview, then follow the
  [Network Simplifier guide](NetworkSimplifier.md) for layer selection, conflicts and backups.

## Bundled versions and documentation

The current repository bundles **Groovy 5.0.6**, **Py4J 0.10.9.2** and **J4R 1.1.1**;
its R client archive is version **1.1.1-228**. These are bundled versions, not a statement
that every example has been tested with every Python or R release.

For Nodus API details, use the API documentation generated from the current sources 
with `ant ApiDoc`; see the [development tools guide](../devtools/README.md).
