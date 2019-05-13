# Change log

This file logs the changes introduced in Nodus since the initial release of version 7.1 on November 14, 2018. The switch 
from v7.0 to v7.1 was needed because of the use of Groovy 2.5.x that changes the directory structure of a Nodus installation.

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

## v7.1 - Buildxxxxxxxx
- Fix a French translation in the omGraphics GUI
- Give focus to the SQL console even it is already open. This allows to put it in the foreground if it is hidden beyond 
the main frame. 
- Path headers and details are now saved using a batch prepared statement. This reduces drastically the assignment duration when 
detailed paths are to be saved. The value of the batch size can be set in the project file, using the "maxSqlBatchSize"
property. If not set, the batch size in set to 1000.  

