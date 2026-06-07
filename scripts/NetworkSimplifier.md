# Nodus Network Shapefile Simplifier — Groovy Script

A Nodus-native Groovy script for simplifying loaded freight transport network layers by removing unnecessary intermediate transit nodes and merging the two connected line objects when the merge is topologically safe.

The script is intended to be run **directly from Nodus**. It works on the layers already loaded in the open Nodus project and uses the Nodus/OpenMap layer API rather than GeoTools or external shapefile libraries.


## What the script does

For each selected Nodus line/link layer, the script looks for nodes that can be removed without changing the logical network topology.

A node can be removed when all of the following conditions are met:

1. the node is stored in one of the relevant point layers;
2. its `Tranship` attribute is `0`;
3. exactly two line endpoints touch the node, considering the target line layer and any connector/blocking layers;
4. both touching line endpoints belong to the current target line layer;
5. the two line geometries can be concatenated while preserving their visible path;
6. descriptive attributes are either identical or are handled according to the selected conflict policy.

When a merge is performed:

- the two line geometries are merged into one polyline;
- one original `NUM` identifier is retained;
- `NODE1` and `NODE2` are updated to the new start and end nodes;
- the dropped line record is removed;
- the intermediate point is removed from the point layer that contains it;
- the affected Nodus layers are marked dirty;
- the process repeats until no more eligible nodes can be removed.


## Main features

- Runs inside Nodus as a Groovy script.
- Uses loaded Nodus/OpenMap layers directly.
- Can scan all loaded line layers automatically.
- Can also run on an explicit list of target line layers.
- Can auto-detect one or several relevant point layers for each target line layer.
- Can auto-detect connector/blocking line layers.
- Supports networks where a single line layer references nodes stored in several point layers.
- Supports attribute conflict policies: skip, prompt, keep-left, keep-right.
- Writes a conflict CSV file for each processed target line layer.
- Backs up original shapefile components before saving modified layers.
- Asks the user to reload the Nodus project after saving.


## Requirements

You need:

- a working Nodus installation;
- an open Nodus project;
- loaded Nodus point and line layers;

The script is designed for Nodus-compatible ESRI shapefile network layers, but it does not read shapefiles itself. It modifies the in-memory Nodus layer objects and then asks Nodus to save them.


## Data model expected by the script

### Target line layers

Each line layer that may be simplified contains these fields:

| Field | Meaning |
|---|---|
| `NUM` | Unique identifier of the line object. |
| `NODE1` | Identifier of the start node. |
| `NODE2` | Identifier of the end node. |

All other fields are treated as descriptive attributes. They are compared before merging two lines.

### Point layers

Each relevant point layer contains:

| Field | Meaning |
|---|---|
| `NUM` | Unique identifier of the node. |
| `Tranship` | If `0`, the node may be removed when the topological conditions are met. Other values prevent removal. |

### Connector or blocking line layers

Connector layers are ordinary Nodus line layers that also contain `NUM`, `NODE1`, and `NODE2`. They are used to prevent unsafe node deletion.

If a removable-looking node is also touched by a connector or another blocking line layer, the node will not be removed unless the only two touching objects are both in the current target line layer.

Field matching is case-insensitive, so `NUM`, `num`, `Node1`, and `NODE1` are treated as equivalent.


## Recommended first run

Always test on a copy of a project first.

Open the script and set:

```java
private boolean dryRun = true;
```

Then run the script from Nodus.

In dry-run mode, the script:

- resolves the line, point, and connector layers;
- scans for candidate merges;
- writes conflict CSV files;
- prints processing summaries;
- does not modify or save any layer.

After checking the console output and conflict CSV files, set:

```java
private boolean dryRun = false;
```

Run again to apply the simplification.


## Running on all loaded line layers

By default, the script inspects every loaded Nodus line/link layer and build simplification jobs automatically.

The key setting is:

```java
private boolean scanAllLinkLayers = true;
```

When this is `true`, the script ignores the explicit `jobsToRun` list and:

1. loops through all loaded link layers;
2. keeps layers with `NUM`, `NODE1`, and `NODE2` fields;
3. auto-detects the point layer or point layers referenced by `NODE1` and `NODE2`;
4. auto-detects connector/blocking line layers;
5. simplifies every layer that can be resolved.

To limit the scan to visible line layers only:

```java
private boolean scanOnlyVisibleLinkLayers = true;
```

To skip layers that cannot be resolved:

```java
private boolean skipUnresolvableScannedLayers = true;
```

If this is `false`, the first unresolved layer stops the script.


## Excluding layers from full-project scan

In full-scan mode, you may want to prevent some layers from being simplified as target layers while still allowing them to act as blockers for other layers.

Exclude exact layer names:

```java
private List<String> scanExcludedLayerNames = new ArrayList<String>(Arrays.asList(
        "some_layer_name",
        "another_layer_name"
));
```

Exclude layers by name fragment:

```java
private List<String> scanExcludedNameHints = new ArrayList<String>(Arrays.asList(
        "conl",
        "connector"
));
```

The comparison is case-insensitive.


## Running on an explicit list of line layers

To disable full-project scanning, set:

```java
private boolean scanAllLinkLayers = false;
```

Then edit the `jobsToRun` list:

```java
private final List<JobSpec> jobsToRun = new ArrayList<JobSpec>(Arrays.asList(
        JobSpec.line("iww_polylines"),
        JobSpec.line("road_polylines"),
        JobSpec.line("rail_polylines")
));
```

With this form, only the target line layer is specified. The script will try to auto-detect point layers and connector layers.


## Explicit point-layer configuration

If automatic point-layer detection is ambiguous, configure the point layers manually.

One target line layer with one point layer:

```java
JobSpec.withPointLayer(
        "road_polylines",
        "road_points")
```

One target line layer with several point layers:

```java
JobSpec.withPointLayers(
        "multimodal_polylines",
        Arrays.asList("iww_points", "rail_points"))
```

One target line layer with point layers and explicit connector layers:

```java
JobSpec.withPointLayersAndConnectors(
        "rail_polylines",
        Arrays.asList("rail_points", "station_points"),
        Arrays.asList("rail_conl2", "rail_conl3"))
```

A target line layer with no connector blockers:

```java
JobSpec.withPointLayersAndConnectors(
        "custom_links",
        Arrays.asList("custom_nodes"),
        new ArrayList<String>())
```

Layer names are complete Nodus layer or table names. They are not prefixes.


## Multiple point layers for one line layer

Some networks may store nodes in more than one point layer while a single line layer references them through `NODE1` and `NODE2`.

The script supports this with:

```java
private boolean allowMultiplePointLayers = true;
```

When this setting is enabled, automatic point-layer detection can select several point layers whose `NUM` values together cover the endpoint IDs used by the target line layer.

When a middle node is deleted after a merge, the script searches the relevant point layers and deletes the node from the layer that actually contains it.


## Connector and blocking layer detection

Connector layers are used to block unsafe merges.

Automatic connector detection is controlled by:

```java
private boolean autoDetectConnectorLayers = true;
```

The default connector mode is:

```java
private boolean autoConnectorsAreAllTouchingLinkLayers = true;
```

When this is `true`, every other loaded line layer that touches at least one endpoint node of the target line layer is considered a connector/blocking layer.

If you prefer name-based detection, set:

```java
private boolean autoConnectorsAreAllTouchingLinkLayers = false;
```

and configure the name fragments:

```java
private List<String> autoConnectorNameHints = new ArrayList<String>(Arrays.asList(
        "con",
        "connector",
        "conl"
));
```

If an explicitly listed connector layer is missing, this setting controls whether the script stops:

```java
private boolean requireConnectors = false;
```


## Attribute conflict handling

Before two lines are merged, the script compares all descriptive attributes. The key fields are not compared as descriptive attributes:

```text
NUM
NODE1
NODE2
```

The conflict policy is configured with:

```java
private String onConflict = "skip";
```

Available values:

| Value | Behavior |
|---|---|
| `skip` | Do not merge the pair; write attribute differences to the conflict CSV. This is the safest default. |
| `prompt` | Ask the user whether to merge and which line's attributes to keep. |
| `keep-left` | Merge and keep descriptive attributes from the first candidate line. |
| `keep-right` | Merge and keep descriptive attributes from the second candidate line. |

Conflict CSV files are written in the current project directory:

```text
<line_layer_name>_merge_conflicts.csv
```

with these columns:

```text
middle_node,num_left,num_right,attribute,left_value,right_value
```


## Retaining line identifiers

A merge combines two line records but keeps one original `NUM` value.

Configure the retained identifier policy with:

```java
private String retainId = "left";
```

Available values:

| Value | Meaning |
|---|---|
| `left` | Keep the `NUM` of the first candidate line. |
| `right` | Keep the `NUM` of the second candidate line. |
| `min-num` | Keep the smallest of the two `NUM` values. |
| `max-num` | Keep the largest of the two `NUM` values. |


## Backups and saving

When `dryRun = false`, the script modifies loaded Nodus layer objects in memory. If saving is enabled, it then asks Nodus to write the modified ESRI layers back to the current project directory.

Relevant settings:

```java
private boolean createBackups = true;
private boolean timestampBackups = true;
private boolean saveProject = true;
private boolean askBeforeApplying = true;
```

When backups are enabled:

- `.shp`, `.shx`, and `.dbf` files for modified layers are moved to backup files;
- `.prj` and `.cpg` files are copied to backup files and left in place;
- if timestamping is enabled, backup names include a timestamp.

Example backup names:

```text
iww_polylines.shp.20260607-141530.bak
iww_polylines.shx.20260607-141530.bak
iww_polylines.dbf.20260607-141530.bak
```

After saving, the script displays a message asking the user to reload the Nodus project. Reloading is recommended before running assignments or other analyses.


## Console and confirmation dialogs

The script can open the Nodus console before processing:

```java
private boolean openConsole = true;
```

For non-dry-run execution, it can ask for a final confirmation before applying changes:

```java
private boolean askBeforeApplying = true;
```

## Output summary

For each processed line layer, the console summary includes:

```text
line_layer
point_layers
connector_layers
input_lines
input_points
output_lines
output_points
merges
auto_merges
conflict_merges
deleted_nodes
remaining_candidates
conflict_rows_written
conflict_csv
```

The final dialog shows the global summary for all processed layers.


## Typical workflow

1. Open the Nodus project.
2. Make a copy of the project directory.
3. Load the relevant node and line layers.
4. Open the Groovy script.
5. Set `dryRun = true`.
6. Run the script.
7. Inspect the Nodus console and conflict CSV files.
8. Adjust layer detection, exclusions, or conflict policy if needed.
9. Set `dryRun = false`.
10. Run the script again.
11. Confirm the modification dialog.
12. Reload the Nodus project.


## Troubleshooting


### Too many layers are processed in full-scan mode

Use one or more of these settings:

```java
private boolean scanOnlyVisibleLinkLayers = true;
private List<String> scanExcludedLayerNames = new ArrayList<String>(Arrays.asList("layer_to_skip"));
private List<String> scanExcludedNameHints = new ArrayList<String>(Arrays.asList("conl", "connector"));
```

### Connector layers are being simplified as target layers

Exclude them from full-scan target selection:

```java
private List<String> scanExcludedNameHints = new ArrayList<String>(Arrays.asList("conl", "connector"));
```

They can still be detected as connector/blocking layers for other target line layers.

### Many conflicts are written to CSV

This means the two candidate lines have different descriptive attributes. Keep the default `onConflict = "skip"` for a conservative run, then inspect the CSV files. Use `prompt`, `keep-left`, or `keep-right` only when you are confident about the attribute policy.


## Notes and limitations

- The script relies on endpoint topology stored in `NODE1` and `NODE2`; it does not infer topology from geometric intersections alone.
- Nodes with `Tranship != 0` are never deleted.
- Connector and blocking layers prevent unsafe deletion at shared nodes.
- The script expects line geometries to be OpenMap `EsriPolyline` objects.
- The script modifies loaded Nodus layer objects when `dryRun = false`.
- Test on a project copy before using the script on production data.
- Reload the project after a successful non-dry-run simplification.


## License and third-party components

This script is intended to run inside Nodus and uses Nodus/OpenMap classes available in the Nodus runtime.

If you distribute the script, include the license you choose for the script itself and keep the license notices for Nodus, OpenMap, JavaDBF4Nodus, and any other bundled third-party components. The script does not bundle these libraries; it relies on the Nodus runtime.

