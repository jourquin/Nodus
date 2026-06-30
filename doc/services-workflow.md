# Service Lines Workflow

This document explains how to create, edit, and use service lines in Nodus. 

## Main Concepts

A service line describes a scheduled transport service on top of the physical network. A service has:

- an ID and a name,
- one mode and one means,
- a frequency,
- a connected set of network links,
- optional stop nodes along those links.

When a mode/means combination is declared as service-constrained, traffic for that mode/means can only use links that belong to a defined service. This is controlled in the cost functions file with:

```properties
SERVICELINES.mode,means = true
```

For example:

```properties
SERVICELINES.3,2 = true
```

This constrains only mode 3, means 2. Other means of mode 3 remain unconstrained unless they also have their own `SERVICELINES.mode,means` entry.

## Services Editor

Open the services editor from the Project|Edit services menu (or use F3).

[Screenshot: Services editor]

The upper table lists the existing services. The columns are:

- ID: numeric identifier of the service,
- Name: service name,
- Mode: transport mode used by the service,
- Means: transport means used by the service,
- Frequency: service frequency.

Use the buttons at the bottom of the dialog to manage the list:

- Add creates a new service.
- Edit opens the selected service for editing. A double-click on a service has the same effect.
- Copy duplicates the selected service.
- Delete removes the selected service.
- Save writes all pending service changes to the SQL database and closes the dialog.
- Close closes the dialog without saving pending changes.

The Escape key has the same effect as Close.

## Creating Or Editing A Service Line

When a service is edited, Nodus switches to the service details view.

[Screenshot: Service details editor]

The details view lets you edit the name, mode, means, and frequency. It also activates line editing on the map.

To define the service line:

1. Select links on the map.
2. The selected service line is highlighted in green.
3. Click an already selected end link to remove it.
4. Press Save in the details view to return to the service list. 
5. Press Save in the main services editor to commit the changes to the SQL database.

The details Save button is enabled only when the service is valid. A valid service line must satisfy these rules:

- all links must use the same mode,
- every added link must be connected to the existing service line,
- a cycle is not accepted by the editor,
- the resulting line may branch,
- all links must belong to one connected component,
- the service must have at least two end nodes,
- every end node must allow operations.

The first link must be connected to at least one node where operations are possible. A service line can also end only at nodes where operations are possible.

The Escape key or "Cancel" button leaves the service details view without applying the current details-card changes.

## Stop Nodes

A stop node is a node where a service is allowed to stop. It is not defined directly by the node `tranship` field. It is stored separately in the services stop table.

To edit stop nodes:

1. Open the database fields editor for a node.
2. Press the Services button.
3. The "Services at this node" dialog lists all services that pass through this node.
4. Check the services that should stop at the node.
5. Press Close in the "Services at this node" dialog to apply the checked state back to the DBF editor.
6. Press Save in the fields editor to commit the stop changes.

[Screenshot: Services at this node]

If the fields editor is closed with Cancel or Escape, the stop changes are discarded. 

## The Services Button In The Fields Editor

The Services button appears in the DBF editor when services are enabled.

For a node, it opens the "Services at this node" dialog. This dialog is used to mark whether each service passing through the node is allowed to stop there.

For a link, it opens the "Services at this link" dialog. This dialog lists the services that use the link. Selecting a service in the list highlights it on the map.

[Screenshot: Services at this link]

The Services button does not edit the shape or the database fields of the layer. Service stop changes are stored in SQL service tables.

## Transhipment Codes And Service Changes

The node `tranship` field controls which operations are allowed at a node. The fields editor offers these operation types:

| Code | Meaning |
| ---: | --- |
| 0 | No operation |
| 1 | All operations |
| 2 | Transhipment only |
| 3 | Loading/unloading only |
| 4 | Service change only |

Code 4 is specific to services. It means that traffic may switch from one service to another service of the same mode/means at this node, provided that both services also stop at the node.

Examples:

- `tranship = 4` allows service changes, but does not make the node a loading/unloading point.
- `tranship = 1` allows loading/unloading, transhipment, and service changes.
- `tranship = 3` allows loading/unloading, but not service changes.


## Service Changes, Stops, And Cost Functions

Services add two virtual-link operation types:

- `stp.mode,means`: cost of stopping on a service,
- `sw.fromMode,fromMeans-toMode,toMeans`: cost of switching between services.

If duration functions are used, the equivalent keys are:

```properties
stp@mode,means = ...
sw@fromMode,fromMeans-toMode,toMeans = ...
```

The usual cost functions are still used:

- `mv` for movement,
- `ld` for loading,
- `ul` for unloading,
- `tr` for transit,
- `tp` for transhipment between different mode/means combinations.

When traffic switches service, the cost parser sets the `FREQUENCY` variable to the frequency of the destination service. This allows the `sw` cost or duration function to include a waiting-time component.

## Database Tables

Services are stored in three SQL tables. These tables are not part of the shapefile DBF. The default table prefix is:

```text
<project-name>_services
```

It can be changed with the `servicestableprefix` project property, that can be set in the Project|Preferences dialog. With the default prefix, the three tables are:

```text
<project-name>_services_header
<project-name>_services_links
<project-name>_services_stops
```

### Header Table

The header table contains one row per service.

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | `NUMERIC(4,0)` | Service ID |
| `name` | `VARCHAR(30)` | Service name |
| `mode` | `NUMERIC(2,0)` | Service mode |
| `means` | `NUMERIC(2,0)` | Service means |
| `frequency` | `NUMERIC(5,0)` | Service frequency |

### Links Table

The links table contains the links used by each service.

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | `NUMERIC(4,0)` | Service ID |
| `link` | `NUMERIC(10,0)` | Link ID |

### Stops Table

The stops table contains the stop nodes of each service.

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | `NUMERIC(4,0)` | Service ID |
| `stop` | `NUMERIC(10,0)` | Node ID where the service may stop |

## Practical Checklist

To create a usable service-constrained network:

1. Define `SERVICELINES.mode,means = true` in the cost functions file for each constrained mode/means.
2. Add the needed `stp` and `sw` cost functions, and duration functions if durations are used.
3. Create services in the services editor.
4. Draw each service line by selecting connected links on the map.
5. Ensure all service end nodes allow operations.
6. Use the Node fields editor Services button on nodes to mark service stop nodes.
7. Use transhipment code 4 on nodes where service changes are allowed.
8. Save the service details, then save the services editor.
