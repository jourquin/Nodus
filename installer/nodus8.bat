@echo off
rem This script launches the Nodus application

rem Get the path to this script
set HERE=%~dp0
set NODUS8_HOME=%HERE:~0,-1%

set JAVABIN=javaw.exe

set "LIBDIR=%NODUS8_HOME%/lib/*;%NODUS8_HOME%/lib/groovy/*;%NODUS8_HOME%/lib/groovy/extras-jaxb/*"
set "JDBCDIR=%NODUS8_HOME%/jdbcDrivers/*"
set "NODUSJAR=%NODUS8_HOME%/nodus8.jar"

rem Set classpath
set NODUSCP="%NODUSJAR%;%LIBDIR%;%JDBCDIR%;%NODUS8_HOME%;"

rem Set default values for the JVM heap sizes if not yet set
%JAVABIN% -cp %NODUSCP% -DNODUS_HOME="%NODUS8_HOME%" edu.uclouvain.core.nodus.utils.SetDefaultHeapSizes
call jvmargs.bat

start %JAVABIN% -cp %NODUSCP% %JVMARGS% -DNODUS_HOME="%NODUS8_HOME%" edu.uclouvain.core.nodus.Nodus "%~1" 
