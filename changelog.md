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

## v7.3 - BuildXXXXXXXX
- Make exclusions "direction sensitive". This allows excluding loading operations for a mode (and means) at a centroid but
keeping unloading possible. 
- Reload labels after exportdbf of the layer in order to display changes.
- Remove the possibility to modify the table structure of a layer. This was based on an Openmap GUI that allows column types
that are not supported by Nodus.
- Automatically change LOGICAL DBF fields to NUMERIC(1,0) fields, as Booleans are not supported by all DBMS's.
- Bug fix: Force UTF8 charset when importing DBF file.
  



