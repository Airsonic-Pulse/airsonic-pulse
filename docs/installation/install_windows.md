# Installing Airsonic-Pulse - Windows

This runs Airsonic-Pulse on Windows directly from the WAR, with no Tomcat and no service. It is the quickest way to get running and is aimed at personal use and evaluation.

Read this first. This setup runs Airsonic-Pulse in the foreground, as the user who starts it. The server runs only while that user is logged in and the console window stays open. Closing the window, logging off, or rebooting stops it. There is no background service and no automatic start at boot yet; that is planned for a later release. If you need an always-on server now, run it on Linux (see the [Linux install guide](./README.md) or [Docker](../docker/README.md)).

## Prerequisites

- Windows 10 (1803 or newer) or Windows 11. The installer uses `curl`, which ships with these versions.
- A Java 21 runtime. Open PowerShell or Command Prompt and run `java -version`; it must report 21. If it is missing or older, install a Java 21 build (for example Eclipse Temurin) and make sure `java` is on your `PATH`. On Windows you can use winget: `winget install EclipseAdoptium.Temurin.21.JRE` (verify the package id).

## Option 1: guided setup (recommended)

1. Download `install-airsonic.bat` from the repository. Open [install/windows/install-airsonic.bat](https://github.com/Airsonic-Pulse/airsonic-pulse/blob/main/install/windows/install-airsonic.bat) and use the "Download raw file" button.
2. Double-click the downloaded file, or run it from a console.
3. When prompted, accept the default folder `C:\Airsonic` or enter another path. The script creates the folder, downloads the latest `airsonic.war` into it, and places `start-airsonic.bat` next to it.
4. Double-click `start-airsonic.bat` to start the server. A console window opens and stays open while it runs.
5. Open http://localhost:4040 in your browser and complete the first-run setup.

## Option 2: manual setup

If you prefer not to run the installer:

1. Create a folder, for example `C:\Airsonic`.
2. Download `airsonic.war` from the latest release into that folder.
3. Download [start-airsonic.bat](https://github.com/Airsonic-Pulse/airsonic-pulse/blob/main/install/windows/start-airsonic.bat) into the same folder.
4. Double-click `start-airsonic.bat`.
5. Open http://localhost:4040.

The launcher uses its own folder as the data directory, so keep `start-airsonic.bat` and `airsonic.war` together.

## First run

The first time you open http://localhost:4040, Airsonic-Pulse walks you through creating the administrator account and pointing the server at your music folders. See [first start](./README.md#first-start) for details. If Windows asks whether to allow Java through the firewall and you want to reach the server from other devices, allow it.

## Where your data lives

Everything (configuration, database, logs, cover-art cache) sits in the data directory you chose, `C:\Airsonic` by default. The config file is `C:\Airsonic\airsonic.properties`, created on first run. Because the launcher locates the data directory from its own position, you can relocate the install by moving the whole folder (with `start-airsonic.bat` and `airsonic.war` inside it); no path editing needed.

## Using PostgreSQL or MariaDB

The default database is embedded HSQLDB and needs no setup. For PostgreSQL or MariaDB, add the datasource properties to `C:\Airsonic\airsonic.properties` and restart. The setup is the same on Windows as on Linux; see the database configuration guide. (Note: that guide is a separate document still being added.)

## Stopping and starting

- Stop: close the console window, or press Ctrl+C in it.
- Start again: double-click `start-airsonic.bat`.
- After a reboot or logoff, start it again manually. There is no service yet.

## Memory

The launcher starts Java with a 1 GB heap (`-Xmx1024m`). For very large libraries (roughly 50,000 tracks or more), edit `start-airsonic.bat` and raise it to `-Xmx2048m`.

## Reaching it from other devices

The server listens on port 4040. To use it from phones or other computers on your network, allow inbound TCP 4040 through Windows Defender Firewall (Windows usually prompts on first run). Then browse to `http://<your-pc-name-or-ip>:4040` from the other device.

## Troubleshooting

- "Java was not found on PATH": install a Java 21 runtime and reopen the console so `PATH` refreshes.
- The page loads on a different port: the bundled `start-airsonic.bat` sets port 4040 explicitly. If you wrote your own launcher or run a bare `java -jar airsonic.war`, current releases fall back to 8080 unless you pass `-Dserver.port=4040`.
- Accented or non-Latin names look wrong: the bundled launcher sets UTF-8 encoding. A hand-run `java -jar` on Windows does not, and will mangle non-ASCII tags and filenames.

## Next steps

- Move off the default database: database configuration guide (being added).
- Put it behind HTTPS: [reverse proxy](../proxy/README.md).
- Always-on Windows service: planned for a later release.