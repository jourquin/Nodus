# Configuration property audit

The audit covers property reads from `.nodus9.properties` and `ProjectName.nodus`,
including the merged project-local view. The block of 95 `@hidden` property constants
in `NodusC.java` is excluded, as requested. No application code was modified.

“No built-in writer” means that no Nodus source path was found that generates or
changes the named property value. Loading, copying or saving an existing user-supplied
value is not considered a setter. Arbitrary user scripts and external plugins can
still set properties through the public API.

## Configuration-only properties

| Key or key family | File / lookup | Value or default | Purpose | Read evidence |
| --- | --- | --- | --- | --- |
| `maxSqlBatchSize` | Project/local | Integer; default `1000` | SQL batch size used by import, assignment and service writers. No preferences control writes this key. | [PathWriter.java:125](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/virtual/PathWriter.java:125) |
| `import.tables` | Project file | Space-separated table names; absent means no extra tables | Extra DBF tables imported when opening the project. The reader accepts legacy `importTables` when this key is absent. | [NodusProject.java:1557](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusProject.java:1557) |
| `openmap.layers` | Project file | Companion `.openmap` filename stem; absent means no additional map file | For example, `openmap.layers=demo` loads `demo.openmap` from the project directory. In the project file this is not a list of layer markers. | [NodusProject.java:1636](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusProject.java:1636) |
| `color.mode.<id>` | Project file | Named color or color string; default is the built-in palette | Override mode colors in statistics pie charts. The reader loops through IDs 0–99. Invalid colors fall back to the existing color. | [StatPieDlg.java:480](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/database/gui/StatPieDlg.java:480) |
| `<layer-table>.variableName` | Project/local | Cost-function variable identifier; default is the layer table name | Rename the layer-membership variable exposed to cost expressions; its value is zero or one depending on membership in the layer. | [NodusEsriLayer.java:1195](/Users/jourquinb/git/Nodus/src/com/bbn/openmap/layer/shape/NodusEsriLayer.java:1195) |

These five key families have Javadoc descriptions; they are not explicitly labelled
“undocumented”. They are configuration inputs without a discovered built-in property
writer. Examples of syntax are:

```properties
maxSqlBatchSize=1000
import.tables=od_road od_rail
openmap.layers=demo
color.mode.1=red
road_links.variableName=RoadNetwork
```

The OpenMap file must exist, and layer/table names must match the project. Examples
illustrate value formats; no project or user preference file has been changed.

## Explicitly undocumented hooks

| Key | Lookup | Value / default | Behavior |
| --- | --- | --- | --- |
| `F8` | Project file first, then `.nodus9.properties` | Fully qualified Java class name; absent or blank disables the hook | Pressing F8 invokes a public constructor accepting `NodusMapPanel`. |
| `F9` | Project file first, then `.nodus9.properties` | Fully qualified Java class name; absent or blank disables the hook | Same mechanism for F9. |

The key handlers explicitly say “not documented” at
[NodusMapPanel.java:656](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusMapPanel.java:656) and
[NodusMapPanel.java:661](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusMapPanel.java:661).
The lookup and constructor requirements are implemented at
[NodusMapPanel.java:499](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusMapPanel.java:499).
No property writer for either key was found. The project lookup uses the main
project properties, not the `.nodus.local` view.

## Legacy inputs still read but no longer written

| Legacy key | File / lookup | Value | Current replacement | Evidence |
| --- | --- | --- | --- | --- |
| `importTables` | Project file | Space-separated extra DBF table names | `import.tables` | [NodusProject.java:1557](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/NodusProject.java:1557) |
| `logitCalibration` | Project/local | Legacy calibration-settings JSON | `modalChoiceCalibration` | [ModalChoiceEstimationDlg.java:196](/Users/jourquinb/git/Nodus/src/edu/uclouvain/core/nodus/compute/modalsplit/ModalChoiceEstimationDlg.java:196) |

The modal-estimation dialog reads `logitCalibration` only when the current mapping
property is unavailable, then saves the mapping under `modalChoiceCalibration`.
The legacy model/assignment flags in its JSON are not restored as current workflow
controls. `importTables` is also present in the repository's demo project, so the
compatibility reader remains useful.

## File boundaries and false positives checked

- Project preferences are normally saved in `ProjectName.nodus.local`. The project
  opener loads that sidecar and merges the main `.nodus` values over it. Consequently,
  absence of a setter that rewrites the main file does not by itself make a setting
  read-only. Both the local and main property setters were checked.
- The `openmap.layers` marker list, startup layers, scale/copyright settings and
  tile settings inside a companion `.openmap` file are separate from the requested
  file formats. They were traced to that loader rather than reported as `.nodus`
  inputs. The same spelling `openmap.layers` has different meanings in the two files.
- `look&feel` is read as a literal string but written using `PROP_LOOK_AND_FEEL` in
  the look-and-feel chooser; it is not read-only.
- Modern `modalChoiceEstimation.*`, `modalChoiceCalibration` and
  `performanceIndicators.*` values have corresponding dialog save paths, including
  dynamically constructed keys. They are not read-only.
- Bridge credential keys are generated and saved by `BridgeCredentials`; no actual
  user credentials or personal configuration files were inspected.
- JVM `System.getProperty` values, plugin metadata, cost-function files,
  `shapes.properties`, styles and report metadata were excluded from the two-file
  inventory.
- Public boolean fields such as `includeEstimationWorkflowDiagnostics` are runtime
  Groovy switches, not keys read from `.nodus9.properties` or `.nodus` files.

## Method and limits

All 289 Java files under `src/` were searched for property getters, setters,
`PropUtils` conversions, string constants, dynamic keys and undocumented comments.
Candidate reads were checked against literal-key aliases, helper methods, generic
property copying and save/load paths. This is a source audit of built-in Nodus code;
it is not a claim about arbitrary external plugins or user scripts. No runtime
settings or application source were modified, and no test run was required.
