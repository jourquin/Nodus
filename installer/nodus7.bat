@echo off
rem This script launches the Nodus application

rem Get the path to this script
set HERE=%~dp0
set NODUS7_HOME=%HERE:~0,-1%

set JAVABIN=javaw.exe

set "LIBDIR=%NODUS7_HOME%/lib/*"
set "PLAFSDIR=%NODUS7_HOME%/plafs/*"
set "JDBCDIR=%NODUS7_HOME%/jdbcDrivers/*"
set "NODUSJAR=%NODUS7_HOME%/nodus7.jar"

rem Set default values for the JVM heap sizes if not yet set
%JAVABIN% -cp "%NODUSJAR%" -DNODUS_HOME="%NODUS7_HOME%" edu.uclouvain.core.nodus.utils.SetDefaultHeapSizes
call jvmargs.bat

set NODUSCP="%NODUSJAR%;%LIBDIR%;%PLAFSDIR%;%JDBCDIR%;%NODUS7_HOME%;"
start %JAVABIN% -cp %NODUSCP% %JVMARGS% -DNODUS_HOME="%NODUS7_HOME%" edu.uclouvain.core.nodus.Nodus7 "%~1" 
