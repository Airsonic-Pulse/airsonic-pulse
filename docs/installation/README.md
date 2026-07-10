# Installation

## Requirements
 
- Java 21 for the WAR methods. A JRE is enough; a JDK also works. The Docker image bundles its own runtime.
- A data directory for Airsonic-Pulse (config, database, logs, cover art). Defaults: `/var/airsonic` on Linux, `C:\Airsonic` on Windows. Docker uses `/var/airsonic` inside the container.

## Choose an installation method

Airsonic-Pulse runs as a single self-contained WAR (Java 21) or as a Docker image. Pick the method that fits your setup:

- **Linux, standalone WAR** — [install_linux.md](install_linux.md). Runs as a systemd service; embedded HSQLDB database by default.
- **Windows, standalone WAR** — [install_windows.md](install_windows.md). Getting-started setup that runs as the logged-on user.
- **Docker** — [../docker/README.md](../docker/README.md). Compose files for HSQLDB, PostgreSQL, and MariaDB.


## Database
 
By default Airsonic-Pulse uses an embedded HSQLDB database, so you can start without setting up a database at all. For PostgreSQL or MariaDB/MySQL, which are the better choice for larger libraries, see [install_database.md](install_database.md). The WAR bundles the drivers for all three engines, so you never add a JDBC jar yourself.

Every method runs on the default embedded HSQLDB database with no extra setup. To use PostgreSQL or MariaDB instead, see [install_database.md](install_database.md); the datasource configuration is identical across all install methods.

## First start

The first time you open Airsonic-Pulse (default `http://localhost:4040`, or your server's address on port 4040), a setup wizard walks you through two things:

1. **Create the administrator account.** Set a strong password. This is the account you use to manage the server, add users, and change settings.
2. **Point Airsonic-Pulse at your music folders.** Add one or more folders containing your library.

Airsonic-Pulse organizes your library by how the files are laid out on disk, not by their embedded tags (tags are still read for presentation and search). For best results, organize each music folder in an **"artist/album/song"** structure.

You can add or change media folders at any time under Settings (you need an administrator account to see this option). For the media-folder defaults and other per-property settings, see [../configuration/detail.md](../configuration/detail.md).

## Next steps
 
- Configuration reference: [../configuration/README.md](../configuration/README.md)
- Reverse proxy and HTTPS: [../proxy/README.md](../proxy/README.md)
- Troubleshooting: [../troubleshooting.md](../troubleshooting.md)