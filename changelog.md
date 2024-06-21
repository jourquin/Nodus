# Change log

This file logs the changes introduced in Nodus since the initial release of version 7.1 on November 14, 2018. The switch 
from v7.0 to v7.1 was needed because of the use of Groovy 2.5.x that changes the directory structure of a Nodus installation.

On February 21, 2020, version 7.2 was released, using Groovy 3.x. The release number was changed because this version
of Groovy may introduce some incompatibilities with scripts written for older versions.

Version 8.0 was released on March 4, 2021. This version is compatible with projects developed for Nodus 7.x but introduces
an new (simpler and improved) API for user defined modal-choice models. More than 50 improvements are introduced, which are listed 
at the v8.0 tag of this document. 

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

## v8.0 - Build20210304

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
    - Predefined Volume-Delay functions (BPR and CONICAL) are now provided and recognized by the cost parser.
    - Add the HOURS, MINUTES and SECONDS functions that can be used in the costs functions for a given LENGTH and SPEED.
    - The results of semi-dynamic assignments can now be displayed both for transported quantities and needed vehicles.
    - DBF files:
        - Improved DBF structure editor for the Nodus layers. The GUI now takes care of the Nodus mandatory structures.
        - Automatically change LOGICAL DBF fields to NUMERIC(1,0) fields, as Booleans are not supported by all DBMS's.
        - Accept SQL DATE in the DBF files.
        - Use JavaDbf4Nodus 1.12.1, that uses UTF-8 encoding by default.
        - Better support of non legacy DBF files (such as those written by many GIS softwares).
        - Handle DBF files with records marked as deleted.


- Miscellaneous:
    - Reload labels after 'exportdbf' of a layer in order to display changes.
    - The labels of a Nodus layer are now displayed only if the layer itself is visible (both are now synchronized).
    - Add stop and switch costs and durations (used with services) in the path header tables.  
    - Remove the "-" button in the layer panel. Not useful.
    - New compass image in ScaleAndCompassLayer.
    - Add "font" properties to ScaleAndCompassLayer for label and scale fonts.
    - Add legends in virtual network viewer.
    - Replace 'importTables" project property by 'import.tables'. Projects with the old property name are still accepted.
    - Simplify virtual network visualizer GUI.
    - Allow PLAF change without restarting Nodus.
    - (Partially) language (Locale) change without restarting Nodus.
    - No more exception thrown when statistics are gathered for a non existing group in StatPieDlg.
    - Limit max heap size to 1.4Go if run on a 32bit JVM
    - Warn the user if the number of threads set for an assignment is larger than the number of physical cores.
    - Tested with Java 11 and 15.
    - Tested with GraalVM (faster that regular JVM).
    - The assignments are now canceled (with an error message) if a path has a non strictly positive cost.
    - SystemInfoDlg replaces ArchInfoDlg and gives now also information about the system hardware.
    - Change default install directory to the user home directory.
    - Replace Windows Nodus.exe with a Windows shortcut because the .exe generation was often intercepted by anti-virus software.
    - Disable the "services". Still too buggy to remain in public distribution.
    - Initial scale and center lat/lon point are now reset when a project is closed.
    - Don't close open project when users cancels the opening of another one.
    - Export of results now only save a DBF table with the value of the result for each node or link ID (and not the other fields of the DBF file). 
    - Detect not permitted file access on Mac OS (Catalina and Big Sur).
    - The Ant ApiDoc task is now compatible with the old and new javadoc API.
    - The ESV (Equivalent Standard Vehicle) variable name has been replaced by the PCU (Passenger Car Units) variable name. PCU is
    a more common acronym used in the literature. An automatic upgrade is proposed to the user.
    - PCU's are now also computed for (un)loading and transit virtual links.
    - The FLOW variable name has been replaced by the VOLUME variable name to be more in-line with terminology used the literature that covers 
    volume / delay functions. An automatic upgrade is proposed to the user. Some API method names are changed accordingly.   
    - The displayed results are now reset when the scenario is changed.
    - Add a sample Groovy script in the demo that performs an assignment.
    - Add a sample costs file and OD matrix for equilibrium assignments in the demo.
    - Disable Frank-Wolfe and incremental + Frank-Wolfe assignment methods. They are buggy and useless for non urban transport.
    - Prevent network edition (add, move, delete... nodes and links) when results are displayed.
    - The SQL parser now correctly handles variables which value is changed at a later place in a script.
    

- External libs
    - Get rid of the foxtrot API which causes illegal reflective access warnings. Use the native "SecondaryLoop" Java interface.
    - Upgrade to mariadb-java-client 2.7.2
    - Upgrade to HSQLDB 2.5.1
    - Upgrade to groovy-3.0.7, oshi-5.6.0, rsyntaxtextarea-3.1.2 and xchart-3.8.0
    - Upgrade to latest OpenMap 6b snapshot
    - Remove the JFlowMap companion app (old fashioned and not really useful).
    
   
- Breaking changes:
    - Simplified API for modal split methods. This breaks existing plugin's. If an incompatible plugin is found, an error 
    message is displayed to inform the user.

## v8.0 - Build20210310
- Add a "release.md" file.
- Bug fix in SQLConsole that didn't correctly parse all the user defined ("@@xxx") variables.

## v8.0 - Build20210317
- Bug fix in SQLconsole that didn't correctly handle single SQL statement that didn't end with a semicolon.

## v8.0 - Build20210415
- Upgrade to HSQLDB 2.6 (JDK8 version)
- Upgrade to sql-jdbc 3.34.0
- Use int instead of byte for scenarios

## v8.1 - Build20210426
- set the --illegal-access=permit flag to the JVM at runtime if JVM version >=9. This flag was introduced in JAVA 9 with its default
value set to "permit". This is not anymore the case since Java 16. It must therefore be set explicitly. 
- Allows Python scripting through a Py4J bridge

## v8.1 - Build20210506
- Remove the (deprecated) nodusMainFrame variable passed to Groovy scripts
- Pass startNodus and quitNodus boolean variables to Groovy when it tries to run the nodus.groovy script
- Add the storeObject and retrieveObject methods in NodusMapPanel. 
- The nodus.groovy script is now also run when Nodus is shutdown
- Pass openProject and closeProject boolean variables to Groovy when it tries to run the "project".groovy script
- The "project".groovy script is now also run when a project is closed
- Improved Python example script
- The Py4J server is now launched from the nodus.groovy script (not anymore hard coded)
- Allows R scripting through a J4R bridge (version >= 1.1). The server is launched and stopped via the nodus.groovy script (same as for Py4J)
- Add an example R and Groovy script (functionally identical to the Python script)
- Add more documentation to the nodus.groovy script and rewrite the README.md file of the script lib

## v8.1 - Build20210511
- Upgrade to latest J4R_1.1.0-220, that fixes an UTF encoding/decoding problem on Windows

## v8.1 - Build20210520
- Upgrade to latest J4R_1.1.0-222, that fixes an UTF encoding/decoding problem on Windows
- Delete paths tables on assignment error

## v8.1 - Build20210615
- Add an example using Biogeme to solve the logit model in the Demo

## v8.1 - Build20210819
- Remove deprecated API calls in MLogit.R
- Properly compute the weights for Biogeme in MLogit.py 
- Fix some Javadoc missing comments
- Quick and dirty fix of application closing for Mac OS with Java 15.
 
## v8.1 - Build20210916
- Upgrade to latest openmap 6b lib, that fixes a bug with projection changes

## v8.1 - Build20211012
- Upgrade to Parsii 5.0.1 (handles scientific notation)

## v8.1 - Build20211223
- Remove support for MacBook touchbar. Not useful and not anymore present on the latest MacBook's
- Reformat some code to apply latest checkstyle rules.
- Upgrade to Groovy 3.0.9
- Upgrade to commons-io-2.11.0 and commons-csc-1.9.0
- Upgrade to HSQLDB 2.6.1
- Fix call to a deprecated commons-cvs API
- Upgrade to latest version of the J4R R package
- Upgrade to OSHI 5.8.3
- Upgrade to POI 5.0
- Upgrade to rsyntaxtextarea 3.1.3
- A double-click in the System info dialog now launches a OSHI console (easter egg)
- Upgrade to openmap 6.0 (non beta release)

## v8.1 - Build20220103
- Update copyright date to 2022
- Install the "Times" font if needed on Mac OS Monterey machines

## v8.2 - Build20220218
- Removal of deprecated API calls. Nodus now needs Java 11 or above to run.
- Launch the Py4J and J4R bridges in in the project's Groovy script 
- Upgrade to J4R-1.1.1
- Upgrade to commons-compress-1.21
- Replace HSQLDB jar with the Java 11 version
- Upgrade to OSHI 6.1.2
- Upgrade to POI 5.2
- Upgrade to rsyntaxtextarea-3.1.6
- Upgrade to Groovy 4.0.0
- Upgrade to Derby 10.15.2
- Upgrade to sqlite-jdbc 3.36.0.3
- Upgrade to H2 version 2. Existing projects using the H2 database need a migration of the database. 
See https://www.h2database.com/html/migration-to-v2.html.
- Upgrade to XChart 3.8.1 and get rid of hacked classes that are not needed anymore
- The embedded HSQLDB, H2 and Derby database engines now run in server mode, allowing for external connections. The "hsqldbserverport",
"h2serverport" or "derbyserverport" properties can be set in the project's file if the server has to listen a specific port. Defaults 
are 9001 for HSQLDB, 9092 for H2 and 1527 for Derby.
- The R and Python MNLogit scripts of the demo project now use a JDBC connection to the HSQLDB server. 
- Update URL of WMS server in demo project
- The MNLogit.R script now runs also from the command line
- The MNLogit.py script now uses another approach to read JDBC results to avoid a Pandas warning

## v8.2 - Build20220718
- Fix small bug (wrong JDBC ResultSet closing) in nodes rules reading.
- Tested with Java 18.
- Rename "stpduration" field to "stduration" to comply the maximum allowed length of DBF fields.
- Rename "stpcost" field to "stcost" to keep variable names consistent.
- Upgrade to POI 5.2.2 and use a log4j to slf4j bridge

## v8.2 - Build20230201
- Update copyright to 1991-2023
- Upgrade to Groovy 4.0.8
- Upgrade to H2 2.2.214
- Upgrade to HSQLDB 2.7.1
- Upgrade to OSHI 6.4.0
- Upgrade to POI 5.2.3
- Upgrade to sqlite-jdbc 3.40.0
- Upgrade to xchart 3.8.3

## v8.2 - Build20231101
- Allow ant targets from within the build-user.xml file
- Workaround to avoid crash when Nodus runs on MacOS Sonoma with Homebrew OpenJDK

## v8.2 - Build20240205
- Improved workaround to avoid crash when Nodus runs on MacOS Sonoma with Homebrew OpenJDK as MacOS 14.2 partially solves the problem.
- Update "htmlpreview" API calls to new URL.
- Update copyright to 1991-2024

## v8.2 - Build20240618
- Use the "--release" javac option in the ant file instead of the "target" / "source" pair.
- Use the "--release" javac option in the compile.bat and compile.sh scripts in the demo project.
- Upgrade to Groovy 4.0.21

## v8.2 - BuildXXXXXXXX
- Upgrade MariaDB JDBC driver to version 3.4.0
- Upgrade Postgresql JDBC driver to version 42.7.3
- Upgrade H2 database ton version 2.2.224
- Upgrade HSQLDB database to version 2.7.3
- Upgrade to JNA 5.14.0
- Upgrade to OSHI 6.6.1
- Upgrade to POI 5.2.5
- Upgrade to commons-io 2.16.1
- Upgrade to commons-compress 126.2
- Upgrade to commons-csv 1.11.0
- Upgrade to commons-collection4-4.5.0
- Better exception handling for the exportxls(x) command
- Upgrade rsyntaxtextarea to version 3.4.0
- Upgrade to sqlite-jdbc 3.46.0.0
- Upgrade to xchart 3.8.8
- Add default constructor in several classes to avoid Javadoc warningd
- Code refactoring (some variables were declared too far from their first use)

##v8.2 - BuildXXXXXXXX
- Add --illegal-access=deny to the Java parameters to hide Groovy console warning
- Change the theme of the Nodus Groovy console and add some imorovements





