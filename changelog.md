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

