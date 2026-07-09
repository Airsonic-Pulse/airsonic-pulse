@echo off
REM ==========================================================================
REM  Airsonic-Pulse - Windows setup (simple, runs as the logged-on user)
REM
REM  Downloads airsonic.war into a folder of your choice and puts the launcher
REM  (start-airsonic.bat) next to it. No admin rights, no service, no
REM  background process. Double-click start-airsonic.bat afterwards to run it.
REM
REM  Requires: curl (built into Windows 10 1803+ and Windows 11) and a Java 21
REM  runtime on PATH.
REM ==========================================================================
setlocal enabledelayedexpansion

echo ============================================
echo   Airsonic-Pulse - Windows setup
echo ============================================
echo.

REM --- Quick Java check (light; does NOT verify the exact major version) ---
where java >nul 2>nul
if errorlevel 1 (
  echo [WARN] Java was not found on PATH.
  echo        Airsonic-Pulse needs a Java 21 runtime, for example:
  echo            install it manually from https://adoptium.net/temurin/releases
  echo            or by using "winget install EclipseAdoptium.Temurin.21.JRE"
  echo        then re-run this script.
  echo.
)

REM --- Choose install folder ---
set "DEFAULT=C:\Airsonic"
set /p "AHOME=Install folder [%DEFAULT%]: "
if "!AHOME!"=="" set "AHOME=%DEFAULT%"

if not exist "!AHOME!" (
  mkdir "!AHOME!" || ( echo [ERROR] Could not create "!AHOME!". & pause & exit /b 1 )
)
echo Using folder: !AHOME!
echo.

REM --- Download the WAR (latest release; no API, no JSON) ---
echo Downloading airsonic.war (latest release)...
curl -L --fail -o "!AHOME!\airsonic.war" "https://github.com/Airsonic-Pulse/airsonic-pulse/releases/latest/download/airsonic.war"
if errorlevel 1 (
  echo [ERROR] Download failed. Download airsonic.war manually into "!AHOME!" and re-run.
  pause
  exit /b 1
)
echo Downloaded: !AHOME!\airsonic.war
echo.

REM --- Download the launcher (static release asset, version-controlled) ---
echo Downloading start-airsonic.bat (latest release)...
curl -L --fail -o "!AHOME!\start-airsonic.bat" "https://raw.githubusercontent.com/Airsonic-Pulse/airsonic-pulse/main/install/windows/start-airsonic.bat"
if errorlevel 1 (
  echo [ERROR] Could not download start-airsonic.bat.
  echo         It ships as a release asset from version 13.2.0 onward.
  echo         Download it manually from the release page into "!AHOME!".
  pause
  exit /b 1
)
set "LAUNCH=!AHOME!\start-airsonic.bat"

echo Created launcher: !LAUNCH!
echo.
echo ============================================
echo   Setup complete.
echo   Start the server by double-clicking:
echo       !LAUNCH!
echo   Then open http://localhost:4040
echo   ^(The server runs only while that window is open.^)
echo ============================================
pause

