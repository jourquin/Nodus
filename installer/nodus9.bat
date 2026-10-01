@echo off
rem This script launches the Nodus application

rem Get the path to this script
set HERE=%~dp0
set NODUS9_HOME=%HERE:~0,-1%

set JAVABIN=javaw.exe

set "LIBDIR=%NODUS9_HOME%/lib/*;%NODUS9_HOME%/lib/groovy/*"
set "JDBCDIR=%NODUS9_HOME%/jdbcDrivers/*"
set "NODUSJAR=%NODUS9_HOME%/nodus9.jar"

rem Set classpath
set NODUSCP="%NODUSJAR%;%LIBDIR%;%JDBCDIR%;%NODUS9_HOME%;"

rem Set default values for the JVM heap sizes if not yet set
%JAVABIN% -cp %NODUSCP% -DNODUS_HOME="%NODUS9_HOME%" edu.uclouvain.core.nodus.utils.SetJVMArgs
call "%NODUS9_HOME%\jvmargs.bat"

start %JAVABIN% -cp %NODUSCP% %JVMARGS% -DNODUS_HOME="%NODUS9_HOME%" edu.uclouvain.core.nodus.Nodus "%~1" 
