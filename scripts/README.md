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

Since Nodus 9.1, scripts run by Nodus may declare a class with the same name as
their `.groovy` file.
For example, `ARGBConverter.groovy` declares `class ARGBConverter` and creates it with
`new ARGBConverter(nodusMapPanel)`. Nodus gives Groovy's generated script wrapper a
leading underscore (`_ARGBConverter`), so the two classes have different names.
This applies to application and project hooks, the Nodus script editor and the native
Groovy console. Running the same source directly with standard Groovy still requires
different script and class names.

Both script editors stay above the main Nodus window when a script opens a dialog owned
by that main window. The script dialog keeps keyboard focus and stays above the editor.

[example.groovy](example.groovy) illustrates the main entry point:

```groovy
def project = nodusMapPanel.getNodusProject()
if (project.isOpen()) {
    println project.getName()
}
```

## Connecting Python and R

Follow the [Python and R bridge guide](Python_and_R_bridges.md) to enable a bridge
for a project, install the matching client and run [example.py](example.py) or
[example.R](example.R). The guide covers credentials, ports, network access and
automatic shutdown. Review the [example limitations](#basic-examples-and-current-limitations)
below before running either client.

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

Use `nodusMapPanel.getNodusProject().registerProjectCleanup(name, action)` for
project-owned servers or other resources that must stop even when a lost
database connection prevents the closing hook from running. `storeObject` and
`retrieveObject` can still share ordinary values between scripts, but clearing
those values does not close resources. Cleanup actions run on a background
thread and should not access the project database or update Swing directly.

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
  `dryRun=true`, `saveProject=true`, backups enabled and confirmation before applying.
  After reviewing the preview, set `dryRun=false` to apply changes. Existing service lines
  cannot be preserved: a separate warning asks for consent to delete all service tables
  and unsaved services before editing the network. Shapefile backups do not include these
  tables; back up the database first and recreate services afterward. See the
  [Network Simplifier guide](NetworkSimplifier.md) for layer selection, conflicts and backups.

## Bundled versions and documentation

The current repository bundles **Groovy 5.1.3**. Python and R bridge versions and
client installation instructions are in the [bridge guide](Python_and_R_bridges.md).
See the [license inventory](../licenses/LICENSES.md) for bundled library licenses.

For Nodus API details, use the API documentation generated from the current sources
with `ant ApiDoc`; see the [development tools guide](../devtools/README.md).
