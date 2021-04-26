#Sample Groovy scripts

- nodus.groovy : auto executable script that zooms onto Europe when Nodus is 
  launched (must be put in the main Nodus installation directory).
- AddDistancesToODTable.groovy : takes the OD table of an opened project and creates
  a new OD table that contains the computed distance between each OD pair. 
  Usefull for multi-distance classes assignments.
- ExtractView.groovy : extracts the shapes and the OD entries located within the screen
  rectangle currently displayed.
- ARGB-Converter : Generates the ARGB string for a chosen color.

#Sample Python script
- test.py illustrates how to interact with the Nodus API from your favorite Python environment through a 
Py4J bridge. As for Groovy, the entry point that is passed from Nodus is the NodusMapPanel instance of (a running) Nodus. 