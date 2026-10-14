# Advanced property configurations

This note covers property reads from `.nodus9.properties` or `ProjectName.nodus` that are not
controlled by the GUI.

## Configuration-only properties

| Key or key family | File / lookup | Value or default | Purpose |
| --- | --- | --- | --- |
| `dbfCharset`, `<table>.dbfCharset` | Project/local | Java charset name; absent means CPG → DBF code page → UTF-8 | Explicit DBF encoding overrides shared by imports, network layers and exports. See [DBF encoding](dbf-encoding.md). |
| `import.tables` | Project file | Space-separated table names; absent means no extra tables | Extra DBF tables imported when opening the project. The reader accepts legacy `importTables` when this key is absent. |
| `openmap.layers` | Project file | Companion `.openmap` filename stem; absent means no additional map file | For example, `openmap.layers=demo` loads `demo.openmap` from the project directory. In the project file this is not a list of layer markers. |
| `color.mode.<id>` | Project file | Named color or color string; default is the built-in palette | Override mode colors in statistics pie charts. The reader loops through IDs 0–99. Invalid colors fall back to the existing color. |
| `<layer-table>.variableName` | Project/local | Cost-function variable identifier; default is the layer table name | Rename the layer-membership variable exposed to cost expressions; its value is zero or one depending on membership in the layer. |

These five key families have Javadoc descriptions and are configuration inputs without a  built-in property writer. Examples of syntax are:

```properties
import.tables=od_road od_rail
openmap.layers=demo
color.mode.1=red
road_links.variableName=RoadNetwork
```


## Undocumented hooks

| Key | Lookup | Value / default | Behavior |
| --- | --- | --- | --- |
| `F8` | Project file first, then `.nodus9.properties` | Fully qualified Java class name; absent or blank disables the hook | Pressing F8 invokes a public constructor accepting `NodusMapPanel`. |
| `F9` | Project file first, then `.nodus9.properties` | Fully qualified Java class name; absent or blank disables the hook | Same mechanism for F9. |


## Legacy inputs still read but no longer written

| Legacy key | File / lookup | Value | Current replacement |
| --- | --- | --- | --- |
| `importTables` | Project file | Space-separated extra DBF table names | `import.tables` |

The modal-estimation dialog reads and saves its observed-table mapping under
`modalChoiceCalibration` only. `importTables` is present in the repository's demo
project, so its compatibility reader remains useful.

## Other properties

- The `openmap.layers` marker list, startup layers, scale/copyright settings and
  tile settings inside a companion `.openmap` file are separate from the requested
  file formats. They were traced to that loader rather than reported as `.nodus`
  inputs. The same spelling `openmap.layers` has different meanings in the two files.
- Bridge credential keys are generated and saved by `BridgeCredentials`; no actual
  user credentials or personal configuration files were inspected.
- Public boolean fields such as `includeEstimationWorkflowDiagnostics` are runtime
  Groovy switches, not keys read from `.nodus9.properties` or `.nodus` files.
