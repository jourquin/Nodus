# Example Groovy, Python and R scripts

The Groovy, Python and R examples are functionally identical. They show how to use the running Nodus instance from an external script.

They:

- Fetch the `NodusMapPanel` instance of the running Nodus.
- Test if a project is loaded and stop if not.
- Print the project name in the console.
- Print the list of all node layers in the project.
- Hide the first node layer in Nodus.
- Print the data associated with the first 10 nodes of the first node layer.

The Py4J and J4R packages must be installed in your Python or R environment if you want to run Python or R scripts. Py4J can be installed with the Python `pip` command. The J4R R package is not available on CRAN, so it is provided in this directory.

The jar files for both packages are already provided in the Nodus `lib` directory. Nodus must also launch the Py4J or J4R servers. More information can be found in `nodus.groovy`, which is also available in the Nodus main directory.

Note: the examples were tested with Groovy 3.0, Python 3.9 and R 4.0.

## Other sample Groovy scripts

- `nodus.groovy`: auto-executable script that zooms onto Europe when Nodus is launched. It must be put in the main Nodus installation directory. It also launches a Py4J and a J4R server.
- `AddDistancesODTable.groovy`: takes the OD table of a project and creates a new OD table that contains the computed distance between each OD pair.
- `ExtractView.groovy`: extracts the shapes and the OD entries located within the screen rectangle currently displayed.
- `CreateShortestPathService.groovy`: computes the shortest enabled path between two nodes for a given mode and means, then creates and saves the corresponding service line.
- `ARGBConverter.groovy`: generates the ARGB string for a chosen color.
- `NetworkSimplifier.groovy`: simplifies loaded freight transport network layers by removing unnecessary intermediate transit nodes and merging the two connected line objects when the merge is topologically safe.

See `NetworkSimplifier.md` for a complete description of the network simplifier script.
