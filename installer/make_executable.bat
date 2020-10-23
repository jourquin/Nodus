@echo off
rem This script generates the Nodus executable file 

rem Get the path to this script
set HERE=%~dp0
set NODUS8_HOME=%HERE:~0,-1%

set "pgm=%NODUS8_HOME%\Bat_To_Exe_Converter.exe"
set "bat=/bat %NODUS8_HOME%/nodus8.bat"
set "exe=/exe %NODUS8_HOME%/nodus8.exe"
set "icon=/icon %NODUS8_HOME%/nodus.ico"
set "misc=/overwrite /invisible"

rem Run the batch converted and delete it when finished
start /wait /b %pgm% %bat% %exe% %icon% %misc%

rem Wait a second before deleting. Trick to avoid "access denied"
ping 127.0.0.1 -n 4 > nul
del "%pgm%"
