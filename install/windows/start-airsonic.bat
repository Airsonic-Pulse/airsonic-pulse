@echo off
REM ==========================================================================
REM  Airsonic-Pulse launcher (runs as the current user, in this window).
REM
REM  This is a simple launcher. It runs Airsonic-Pulse in the
REM  foreground: it works only while you are logged in and this window stays
REM  open. Close the window (or press Ctrl+C) to stop the server. It does NOT
REM  run in the background and does NOT start automatically after a reboot.
REM  A proper Windows service is planned for a later release.
REM
REM  Keep this file in the same folder as airsonic.war. That folder is used as
REM  the Airsonic-Pulse data directory (config, database, logs, cover art).
REM ==========================================================================
setlocal

REM This script's own folder, with the trailing backslash removed.
set "AHOME=%~dp0"
if "%AHOME:~-1%"=="\" set "AHOME=%AHOME:~0,-1%"
set "WAR=%AHOME%\airsonic.war"

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found on PATH. Install a Java 21 runtime and try again.
  pause
  exit /b 1
)

if not exist "%WAR%" (
  echo Could not find "%WAR%".
  echo Put airsonic.war in this folder, or re-run the installer.
  pause
  exit /b 1
)

echo Starting Airsonic-Pulse...
echo Once it has started, open http://localhost:4040 in your browser.
echo Close this window to stop the server.
echo.

java -Xmx1024m -Xms512m "-Dairsonic.home=%AHOME%" -Dserver.port=4040 -Dserver.servlet.context-path=/ -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dserver.forward-headers-strategy=framework -jar "%WAR%"

echo.
echo Airsonic-Pulse has stopped.
pause