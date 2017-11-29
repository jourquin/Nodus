@echo off
rem This will launch the Nodus application
rem YOU MAY HAVE TO EDIT THE SETTINGS IN THIS FILE TO MATCH YOUR CONFIGURATION

set NODUS7_HOME="$INSTALL_PATH"
set JAVABIN=javaw.exe

set LIBDIR=%NODUS7_HOME%/lib/*
set PLAFSDIR=%NODUS7_HOME%/plafs/*
set JDBCDIR=%NODUS7_HOME%/jdbcDrivers/*

rem Set default values for the JVM heap sizes
start %JAVABIN% -cp %NODUS7_HOME%/nodus7.jar -DNODUS_HOME=%NODUS7_HOME% edu.uclouvain.core.nodus.utils.SetDefaultHeapSizes
call jvmargs.bat

set NODUSCP="%NODUS7_HOME%/nodus7.jar;%LIBDIR%;%PLAFSDIR%;%JDBCDIR%;%NODUS7_HOME%;"

start %JAVABIN% -cp %NODUSCP% %JVMARGS% -DNODUS_HOME=%NODUS7_HOME% edu.uclouvain.core.nodus.Nodus7 "%~1"

