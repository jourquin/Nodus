@echo off
rem This script creates a Windows shortuct with an icon

rem Get the path to this script
set HERE=%~dp0
set NODUS8_HOME=%HERE:~0,-1%

rem Create the shortcut
set "bat=%NODUS8_HOME%\nodus8.bat"
set "icon=%NODUS8_HOME%\nodus.ico"

%NODUS8_HOME%\nircmdc.exe shortcut %bat% %NODUS8_HOME% "Nodus 8" "" %icon% "" "min"

rem Delete the nircmd utility
del %NODUS8_HOME%\nircmdc.exe