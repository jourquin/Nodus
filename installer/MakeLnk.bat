@echo off

rem Get the path to this script
set HERE=%~dp0
set NODUS8_HOME=%HERE:~0,-1%

SETLOCAL ENABLEDELAYEDEXPANSION
SET LinkName=Nodus 8
SET Esc_LinkDest=%%NODUS8_HOME%%\!LinkName!.lnk
SET Esc_LinkTarget=%%NODUS8_HOME%%\nodus8.bat
SET Esc_Icon=%%NODUS8_HOME%%\nodus.ico
Set Esc_WorkingDir=%%NODUS8_HOME%%\

rem Create a visula basic script and run it
SET cSctVBS=CreateShortcut.vbs
SET LOG=".\%~N0_runtime.log"
((
  echo Set oWS = WScript.CreateObject^("WScript.Shell"^) 
  echo sLinkFile = oWS.ExpandEnvironmentStrings^("!Esc_LinkDest!"^)
  echo Set oLink = oWS.CreateShortcut^(sLinkFile^) 
  echo oLink.TargetPath = oWS.ExpandEnvironmentStrings^("!Esc_LinkTarget!"^)
  echo oLink.IconLocation = oWS.ExpandEnvironmentStrings^("!Esc_Icon!"^)
  echo oLink.WorkingDirectory=oWS.ExpandEnvironmentStrings^("!Esc_WorkingDir!"^)
  echo oLink.WindowStyle=7
  echo oLink.Save
)1>!cSctVBS!
cscript //nologo .\!cSctVBS!

rem Delete the script
DEL !cSctVBS! /f /q
)1>>!LOG! 2>>&1

rem Delete the log
DEL !LOG! /f /q
