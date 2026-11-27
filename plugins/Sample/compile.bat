@echo off
setlocal
pushd "%~dp0" || exit /b 1

set "build_dir=%TEMP%\nodus-sample-plugin-%RANDOM%-%RANDOM%"
mkdir "%build_dir%" || (popd & exit /b 1)
mkdir "%build_dir%\classes" || goto failure

javac --release 11 -encoding UTF-8 -cp "..\..\nodus9.jar;..\..\lib\*" -d "%build_dir%\classes" NodusSamplePlugin.java || goto failure
jar cf "%build_dir%\NodusSamplePlugin.jar" -C "%build_dir%\classes" . || goto failure
move /y "%build_dir%\NodusSamplePlugin.jar" "..\NodusSamplePlugin.jar" >nul || goto failure
set "result=0"
goto cleanup

:failure
set "result=1"

:cleanup
rmdir /s /q "%build_dir%"
popd
exit /b %result%
