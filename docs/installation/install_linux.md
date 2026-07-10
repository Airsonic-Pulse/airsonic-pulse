# Installing Airsonic-Pulse - Linux

## Contents

- [Installing Airsonic-Pulse - Linux](#installing-airsonic-pulse---linux)
  - [Contents](#contents)
  - [Prerequisites](#prerequisites)
  - [Automated Installation](#automated-installation)
  - [Manual Installation](#manual-installation)
    - [1. Install Java 21](#1-install-java-21)
    - [2. Create the system user](#2-create-the-system-user)
    - [3. Create the data directory](#3-create-the-data-directory)
    - [4. Download the WAR](#4-download-the-war)
    - [5. Install the systemd unit](#5-install-the-systemd-unit)
    - [6. Enable and start](#6-enable-and-start)
    - [7. Verify](#7-verify)
  - [Configuration](#configuration)
    - [Data directory](#data-directory)
    - [User configuration file](#user-configuration-file)
    - [Environment overrides](#environment-overrides)
  - [Transcoding](#transcoding)
  - [Default Media Folders](#default-media-folders)
  - [Database](#database)
  - [Reverse Proxy](#reverse-proxy)
  - [Troubleshooting](#troubleshooting)
    - [View logs](#view-logs)
    - [Common issues](#common-issues)

---

## Prerequisites

- **Java 21** — OpenJDK 21 headless recommended (`java-21-openjdk-headless` on RHEL/CentOS, `openjdk-21-jre-headless` on Debian/Ubuntu)
- **ffmpeg** and **ffprobe** — optional, required for audio transcoding and video metadata parsing
- **systemd** — the provided unit file targets systemd-based Linux distributions
- **Minimum 1 GB RAM** — 2 GB or more recommended for libraries over 50,000 tracks (adjust `-Xmx` accordingly)

---

## Automated Installation

Installer scripts are provided for the two major Linux families:

| Distribution                                 | Script                               |
| -------------------------------------------- | ------------------------------------ |
| RHEL, CentOS, AlmaLinux, Rocky Linux, Fedora | `install/centos/install-airsonic.sh` |
| Debian, Ubuntu, and derivatives              | `install/debian/install-airsonic.sh` |

The scripts handle Java installation, system user creation, WAR download, systemd unit setup, and optional ffmpeg and firewall configuration.

**Usage:**

```bash
# Install latest release
sudo ./install-airsonic.sh

# Install a specific version
sudo ./install-airsonic.sh v13.0.0
```

The version argument must match a GitHub release tag (e.g. `v13.0.0`, `v13.0.0-beta.1`). If omitted the script queries the GitHub API for the latest release.

---

## Manual Installation

### 1. Install Java 21

**RHEL / CentOS / AlmaLinux / Rocky Linux / Fedora:**

```bash
sudo dnf install java-21-openjdk-headless
```

**Debian / Ubuntu:**

```bash
sudo apt-get update
sudo apt-get install openjdk-21-jre-headless
```

Verify the installed version:

```bash
java -version
```

If multiple Java versions are installed, set Java 21 as the default:

```bash
# RHEL/CentOS
sudo alternatives --config java

# Debian/Ubuntu
sudo update-alternatives --config java
```

### 2. Create the system user

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin airsonic
```

### 3. Create the data directory

```bash
sudo mkdir -p /var/airsonic
sudo chown airsonic:airsonic /var/airsonic
```

### 4. Download the WAR

Replace `VERSION` with the desired release tag (e.g. `v13.0.0`):

```bash
VERSION=v13.0.0
sudo curl -L \
  "https://github.com/Airsonic-Pulse/airsonic-pulse/releases/download/${VERSION}/airsonic.war" \
  -o /var/airsonic/airsonic.war
sudo chown airsonic:airsonic /var/airsonic/airsonic.war
```

### 5. Install the systemd unit

**Option A — from a cloned repository:**

```bash
sudo cp install/systemd/airsonic.service /etc/systemd/system/airsonic.service
sudo systemctl daemon-reload
```

**Option B — download directly from GitHub:**

Replace `VERSION` with the release tag you installed (e.g. `v13.0.0`):

```bash
VERSION=v13.0.0
sudo curl -fsSL \
  "https://raw.githubusercontent.com/Airsonic-Pulse/airsonic-pulse/${VERSION}/install/systemd/airsonic.service" \
  -o /etc/systemd/system/airsonic.service
sudo systemctl daemon-reload
```

### 6. Enable and start

```bash
sudo systemctl enable --now airsonic
```

### 7. Verify

```bash
sudo systemctl status airsonic
```

Then open a browser and navigate to `http://hostname:4040`. The default admin credentials are set during the first-run setup wizard.

---

## Configuration

### Data directory

All runtime files are stored under `/var/airsonic/` by default:

| Path                                | Contents                                            |
| ----------------------------------- | --------------------------------------------------- |
| `/var/airsonic/airsonic.war`        | Application WAR                                     |
| `/var/airsonic/airsonic.properties` | User configuration (auto-created on first run)      |
| `/var/airsonic/airsonic.log`        | Application log                                     |
| `/var/airsonic/db/`                 | Embedded HSQLDB database files                      |
| `/var/airsonic/transcode/`          | Optional: place custom ffmpeg/ffprobe binaries here |
| `/var/airsonic/index20/`            | Lucene search index                                 |

### User configuration file

`/var/airsonic/airsonic.properties` is created automatically on first run. Edit it to change application settings — changes take effect after a restart. Do not edit this file while Airsonic is running.

### Environment overrides

Override the port, heap size, and other JVM options **without** editing the systemd unit file. Create the appropriate override file for your distribution:

**RHEL / CentOS / AlmaLinux / Rocky Linux / Fedora:** `/etc/sysconfig/airsonic`

**Debian / Ubuntu:** `/etc/default/airsonic`

Example override file:

```bash
# Uncomment and adjust as needed

# AIRSONIC_HOME=/var/airsonic
# PORT=4040
# CONTEXT_PATH=/
# JAVA_OPTS="-Xmx1024m -Xms512m"
```

| Variable        | Default              | Description                                                  |
| --------------- | -------------------- | ------------------------------------------------------------ |
| `AIRSONIC_HOME` | `/var/airsonic`      | Data directory                                               |
| `PORT`          | `4040`               | HTTP listen port                                             |
| `CONTEXT_PATH`  | `/`                  | Servlet context path (e.g. `/airsonic` for sub-path deploys) |
| `JAVA_OPTS`     | `-Xmx1024m -Xms512m` | JVM options including heap size                              |

The default heap (`-Xmx1024m`) is adequate for most deployments. For libraries over 50,000 tracks, consider raising it to `-Xmx2048m` or higher.

After editing the override file, restart the service:

```bash
sudo systemctl restart airsonic
```

---

## Transcoding

Transcoding requires `ffmpeg` and `ffprobe`.

**RHEL / CentOS (requires EPEL):**

```bash
sudo dnf install epel-release
sudo dnf install ffmpeg-free
```

**Debian / Ubuntu:**

```bash
sudo apt-get install ffmpeg
```

Airsonic searches for transcoding binaries in the following order:

1. `${airsonic.home}/transcode/` (e.g. `/var/airsonic/transcode/`)
2. `/usr/bin/`
3. `/usr/local/bin/`

To use a custom ffmpeg build, copy or symlink the binaries into `/var/airsonic/transcode/`.

---

## Default Media Folders

On first run, Airsonic seeds three default media folders:

| Folder    | Default path     |
| --------- | ---------------- |
| Music     | `/var/music`     |
| Podcasts  | `/var/podcasts`  |
| Playlists | `/var/playlists` |

These can be changed in the web UI under **Settings → Media Folders** after the initial setup. To override them at install time, pass JVM properties to the WAR:

```bash
# In /etc/sysconfig/airsonic or /etc/default/airsonic:
JAVA_OPTS="-Xmx1024m -Xms512m \
  -Dairsonic.defaultMusicFolder=/srv/music \
  -Dairsonic.defaultPodcastFolder=/srv/podcasts \
  -Dairsonic.defaultPlaylistFolder=/srv/playlists"
```

---

## Database

Airsonic uses an embedded HSQLDB database by default, stored in `/var/airsonic/db/`. This is suitable for personal use and small deployments.

For production deployments with multiple users or large libraries, a dedicated database is recommended:

- **PostgreSQL** (recommended)
- **MariaDB 10.5+**
- **MySQL 8.0+**

Configure an external database by adding `spring.datasource.*` properties to `/var/airsonic/airsonic.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/airsonic
spring.datasource.username=airsonic
spring.datasource.password=secret
spring.datasource.driver-class-name=org.postgresql.Driver
```

> **Note:** Detailed database configuration and migration instructions will be covered in a dedicated database configuration guide.

---

## Reverse Proxy

Airsonic is commonly deployed behind a reverse proxy (nginx, Apache, Caddy). See [Proxy Prerequisites](../proxy/README.md) for setup instructions.

The systemd unit already includes `-Dserver.forward-headers-strategy=framework`, which tells Spring Boot to trust `X-Forwarded-*` headers from the proxy. Ensure your proxy sets these headers correctly.

---

## Troubleshooting

### View logs

```bash
# Follow the systemd journal (live)
journalctl -u airsonic -f

# Or read the log file directly
tail -f /var/airsonic/airsonic.log
```

### Common issues

**`UnsupportedClassVersionError` on startup**
The WAR was built for Java 21 but an older JVM is running it. Verify with `java -version` and update your Java installation or fix the `alternatives` / `update-alternatives` configuration.

**`Permission denied` on `/var/airsonic`**
The `airsonic` user does not own the data directory. Fix with:
```bash
sudo chown -R airsonic:airsonic /var/airsonic
```

**Port 4040 already in use**
Another process is listening on port 4040. Either stop that process or change the port by setting `PORT=XXXX` in the override file (see [Configuration](#configuration)).

For additional troubleshooting steps, see [TroubleShooting](../troubleshooting.md).
