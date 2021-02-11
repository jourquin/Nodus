# Change log

This file logs the changes introduced in Nodus since the initial release of version 7.1 on November 14, 2018. The switch 
from v7.0 to v7.1 was needed because of the use of Groovy 2.5.x that changes the directory structure of a Nodus installation.

On February 21, 2020, version 7.2 was released, using Groovy 3.x. The release number was changed because this version
of Groovy may introduce some incompatibilities with scripts written for older versions.

DOI of latest release : <a href="https://zenodo.org/badge/latestdoi/111554354"><img src="https://zenodo.org/badge/111554354.svg" alt="DOI"></a>

## v7.1 - Build20181114
- Initial v7.1 release.

## v7.1 - Build20181213
- Fix exception thrown in SQL Console when script with Mac or Linux EOL runs on a Windows platform (bug fix).
- R logit model in the demo project replaced by a simpler but more realistic univariate specification. 

## v7.1 - Build20181217
- Add this change log file.
- ExportCSV now correctly handles null values (bug fix).
- The installer now checks if the installed Java on the target computer version is at least 1.8.
- The R code in the demo project now automatically adds the path to hsqldb.jar.

## v7.1 - Build20190205
- Change copyright date to 2019 
- Upgrade Poi to version 4.0.1
- Upgrade Rsyntaxtextarea to version 3.0.3
- Upgrade Parsii to version 4.0
- Upgrade Groovy to version 2.5.6
- Use a prepared statement in exportxls and exportxlsx

## v7.1 - Build20190307
- Upgrade XCharts to version 3.5.4
- Fix deprecated call to Poi's getCellTypeEnum()in ImportXLS

## v7.1 - Build20190312
- Replace R calibrated cost functions file in demo to be in line with R logit model of Build201810213 

## v7.1 - Build20190515
- Fix a French translation in the omGraphics GUI.
- Give focus to the SQL console or the cost editor even if already open. This allows to put them in the foreground if hidden beyond 
the main frame. 
- Virtual networks, path headers and path details are now saved using batch prepared statements, if supported by the used DBMS. 
This reduces drastically the assignment duration when detailed paths are to be saved. The value of the batch size can be set 
in the project file, using the "maxSqlBatchSize" property. If not set, the default batch size in set to 1000.  
- Delete all the tables of an assignment if the latest didn't succeed.  
- Add a "setScenarioDescription" field to the AssignmentParameters.
- Update build.xml with latest external lib versions. 
- More consistent internal handling of cost functions (Properties vs file name), which was buggy.
   
## v7.1 - Build20190618
- Add a "Save" button in the Assignment dialog. It saves the state of the settings, while "Close" now closes the window 
without saving these settings.
- Upgrade various JDBC drivers to their latest version.
- Upgrade to latest OpenMap 6.0 beta.
- Change "max detour" spinner model in order to make all values visible in the spinner.
- Replace the "lost path" message with an SQL command that allows to later delete the "lost" OD relations in the demand table. 

## v7.1 - Build20190712
- "Iterations" label replaced by "Nb routes" in the assignment GUI for multiflow assignments.
- Upgrade to Groovy 2.5.7
- Don't delete existing "_results" layers when results are displayed.
- Stats dialog is now also accessible from the results dialog.

## v7.1 - Build20190716
- Make StatDlg non modal when called from SQL console.
- Upgrade to HSQLDB 2.5
- Upgrade to mariadb-java-client 2.4.2
- Upgrade to postrgesql jdbc driver 42.2.6
- Upgrade to poi 4.1.0

## v7.1 - Build20190909
- Max width of links and size of nodes in the "result" view are now computed using only the visible links and nodes on screen.
- Just for fun : add touchbar support for MacBook Pro.
- Move embedded DBMS'S to Nodus core lib directory.

## v7.1 - Build20190913
- Several bug fixes in map editor.
- Logger now works when Nodus is used with HSQLDB.
- Save SQL query for semi-dynamic assignments in properties.
- Warn if a WMS or a tile server is not reachable.
 
## v7.1 - Build20190916
- Improved server availability detection for GoogleMaps layers.

## v7.1 - Build2019129
- Improved export and import of CSV and DBF files.

## v7.1 - Build20191213
- Upgrade H2 to latest version.
- Make StatDlg always modal to avoid double display of it.

## v7.2 - Build20200221
- Upgrade to Groovy 3.01
- Change project date to 2020.
- Upgrade to commons-collections 4.4.4
- Upgrade to commons-compress 1.20
- Upgrade to commons-csv 1.8
- Upgrade to commons-io 2.6
- Upgrade to guava 28.2
- Upgrade to poi 4.1.2
- Upgrade to rsyntaxtextarea 3.0.6
- Upgrade to xchart 3.6.1
- Upgrade to mariadb-java-client 2.5.4
- Upgrade to postgresql jdbc driver 42.2.10
- Upgrade to sqlite-jdbc 3.30.1
- Disable Nodus specific checkstyle because of a bug in checkstyle for Eclipse. Use Google standard by default, but this generate  
warnings (for more than 3 successive capital letters in class names and a blank line between import groups).
- Upgrade to latest google-java-format-eclipse-plugin in devtools
- Add DOI tag in change log

## v7.2 - Buil20200325 
- Resolve deprecated call to Groovy Console
- Improved compliance with Google coding standard
- Re-enable Nodus specific checkstyle
- Improved "ExtractView.groovy" script
- Better loading of available OD tables list in Preferences dialog

## v7.2 - Build20200427
- Load list of available OD tables in Preference dialog the same ways as in Assignment dialog. Seems to be OK now.
- Add the setScenario(int) and setScenario(int, String) methods to NodusProject. Useful to set a scenario from a script.

## v7.2 - Build20200507
- Bug fix : intercept exception when vnet and path writers are in batch mode and there is nothing to write in the tables.
- Replace the DBF related methods with a Nodus specific fork of JavaDBF. This should not break any user script as this
specific version of JavaDBF has a "Nodus compatible" API. This lib is more flexible and opens the way to handle more DBF
data types in Nodus.
- Get rid of the "RJDBC" stuff in the R script provided with the demo project. Therefore, CreateMLogitInput.groovy saves
its results in a DBF file that is read by the MLogit.R script. "RJDBC" is indeed sometimes tricky to install.
- Bug fix : Back to Guava 19, as the latest version breaks Jung. 
- Bug fix : Labels were incorrect if the objects in their layer where filtered with a SQL "where" clause.

## v8.0 - BuildXXXXXXXX

- New functionalities: 
    - Make exclusions "direction sensitive". This allows excluding loading operations for a mode (and means) at a centroid 
    but keeping unloading possible for instance. 
    - Exclusions can be defined "all but" (exclusions) or "nothing bur" (inclusions).
    - Add the possibility to include transit time functions in cost functions files. These functions follow the same structure as 
    the cost functions, but use the '@' separator. Example "ld.1,1=" for a "loading" cost function and "ld@1,1=" for a loading time 
    function. The old, undocumented, possibility to partially compute transit times using 'xx_DURATION' variables is removed. If such variables 
    are present,  a message is displayed to warn the user and an automatic upgrade of the cost functions file is proposed.
    - Introduce map rendering with antialiasing. Somewhat slower, but fonts (labels for instance) are much better. Use a 
    BufferedMapBean instead of a BufferedLayerMapBean as the background layers of the latest cannot be rendered with 
    antialiasing (bug in OpenMap).
    - Add the possibility to specify the color to use for each mode in the pie diagrams in the project file.
    - Allow non numerical variables in the costs functions files. The names of these variables must start with a '@'.
    - Cost parser improved : variables can themselves contain formulas and make reference to other variables.

- DBF files:
    - Improved DBF structure editor for the Nodus layers. The GUI now takes care of the Nodus mandatory structures.
    - Automatically change LOGICAL DBF fields to NUMERIC(1,0) fields, as Booleans are not supported by all DBMS's.
    - Accept SQL DATE in the DBF files.
    - Use JavaDbf4Nodus 1.12.1, that uses UTF-8 encoding by default.
    - Better support of non legacy DBF files (such as those written by many GIS softwares.
    - Handle DBF files with records marked as deleted.

- Nodes and link labels:
    - Reload labels after 'exportdbf' of a layer in order to display changes.
    - The labels of a Nodus layer are now displayed only if the layer itself is visible (both are now synchronized).

- Miscellaneous:
    - Upgrade to mariadb-java-client 2.7
    - Upgrade to HSQLDB 2.5.1
    - Add stop and switch costs and durations (used with services) in the path header tables.  
    - Remove the "-" button in the layer panel. Not useful.
    - New compass image in ScaleAndCompassLayer.
    - Add "font" properties to ScaleAndCompassLayer for label and scale fonts.
    - Add legends in virtual network viewer.
    - Replace 'importTables" property by 'import.tables'. Projects with the old property name are still accepted.
    - Simplify virtual network visualizer GUI.
    - Allow PLAF change without restarting Nodus.
    - (Partially) language (Locale) change without restarting Nodus.
    - No more exception thrown when statistics are gathered for a non existing group in StatPieDlg.
    - Limit max heap size to 1.4Go if run on a 32bit JVM
    - Warn the user if the number of threads set for an assignment is larger than the number of physical cores.
    - Tested with Java 11 and 15
    - The assignments are now canceled (with an error message) if a path has a non strictly positive cost.
    - SystemInfoDlg replaces ArchInfoDlg and gives now also information about the system hardware.
    - Change default install directory to the user home directory.
    - Replace Windows Nodus.exe with a Windows shortcut because the .exe generation was often intercepted by anti-virus software.
    - Disable the "services". Still too buggy to remain in public distribution.
    - Initial scale and center lat/lon point are now reset when a project is closed.
    - Don't close open project when users cancels the opening of another one.
    - Nb equivalent standard vehicles are now also computed for (un)loading and transit virtual links.
    - Export of results now only save a DBF table with the value of the result for each node or link ID. 
    - Remove the JFlowMap companion app (old fashioned and not really useful).
    - Detect not permitted file access on Mac OS.
    - The Ant ApiDoc task is now compatible with the old and new javadoc API.
    - Get rid of the foxtrot API which causes illegal reflective access warnings. Use the native "SecondaryLoop" Java interface.
    - Upgrade to groovy-3.0.6, oshi-5.3.6, rsyntaxtextarea-3.1.1 and xchart-3.6.6
    - Upgrade to latest OpenMap 6b snapshot
    - The ESV (Equivalent Standard Vehicle) variable name has been replaced by the PCU (Passenger Car Units) variable name. PCU is
    a more common acronym used in the literature. An automatic upgrade is proposed to the user.
    - The FLOW variable name has been replaced by the VOLUME variable name to be more in-line with terminology used the literature that covers 
    volume / delay functions. An automatic upgrade is proposed to the user.
    - Predefined Volume-Delay functions (BPR and CONICAL) are now provided and recognized by the cost parser.
    - Tested with GraalVM (faster that regular JVM). Compilation to native code with GraalVM's "native-image" tool doesn't work yet.
    - The displayed results are now reset when the scenario is changed.
    - Add a sample Groovy script in the demo that performs an assignment.
    - Add a sample costs file and OD matrix for equilibrium assignments in the demo.
    - Disable Frank-Wolfe and incremental + Frank-Wolfe assignment methods. They need more validation and are useless.
    - Add the HOURS, MINUTES and SECONDS functions that can be used in the costs functions for a given LENGHT and SPEED.

- Breaking changes:
    - Simplified API for modal split methods. This breaks existing plugin's. If an incompatible plugin is found, an error 
    message is displayed to inform the user.

    
