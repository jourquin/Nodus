#Example Groovy, Python and R scripts

These 3 scripts are functionally identical. They:

- Fetch the NodusMapPanel instance of the running Nodus. This is the entry point to the
Nodus API and the data of the loaded project, if any.
- Test if a project is loaded and stop the script if not.
- Print the name of the project in the console.
- Print the list of all the "node" layers of the project.
- Hide the first node layer in Nodus.
- Print the data (content of the .dbf file of an Esri shapefile layer loaded in memory by Nodus)
of the first 10 nodes of the first layer. In the R script, the corresponding data is stored in
a dataframe (although all columns are treated as strings).

The Py4J and/or J4R packages must be installed in your favorite Python or R environment if you 
want running Python or R scripts. Py4J can be installed using the Python "pip" command.  As the 
J4R R package is not available on CRAN, it is provided is this directory. Note that the .jar files
of both packages are already provided in the Nodus "lib" directory. Nodus must also launch the
Py4J and/or the J4R server(s). More information about this can be found in the nodus.groovy
script, which is also available in the Nodus main directory.
   
#Other sample Groovy scripts

- nodus.groovy : auto executable script that zooms onto Europe when Nodus is 
  launched (must be put in the main Nodus installation directory). It also launches
  a Py4J and a J4R server, needed by Python and R scripts that want to interact with
  the Nodus API and the loaded Nodus project.
- AddDistancesToODTable.groovy : takes the OD table of a project and creates
  a new OD table that contains the computed distance between each OD pair.
- ExtractView.groovy : extracts the shapes and the OD entries located within the screen
  rectangle currently displayed.
- ARGB-Converter : Generates the ARGB string for a chosen color.

