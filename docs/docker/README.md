# Docker

Airsonic-Pulse is available as a multi-architecture Docker image from the GitHub Container Registry.

## Image

```
ghcr.io/airsonic-pulse/airsonic-pulse
```

Supported architectures: `linux/amd64`, `linux/arm64`

## Quick Start

```bash
docker run -d \
  --name airsonic-pulse \
  -p 4040:4040 \
  -v ./data/airsonic:/var/airsonic \
  -v ./data/music:/var/music \
  -v ./data/playlists:/var/playlists \
  -v ./data/podcasts:/var/podcasts \
  -e PUID=1000 \
  -e PGID=1000 \
  -e TZ=Etc/UTC \
  ghcr.io/airsonic-pulse/airsonic-pulse:latest
```

Then open `http://localhost:4040` in your browser.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PUID` | `0` | User ID for the application process |
| `PGID` | `0` | Group ID for the application process |
| `TZ` | `Etc/UTC` | Container timezone |
| `AIRSONIC_PORT` | `4040` | HTTP listen port inside the container |
| `CONTEXT_PATH` | `/` | Servlet context path |
| `UPNP_PORT` | `4041` | DLNA/UPnP port |
| `JAVA_OPTS` | _(empty)_ | Additional JVM options (appended to defaults) |

The default heap is `-Xmx1024m -Xms512m`. Override via `JAVA_OPTS` if needed:

```bash
-e JAVA_OPTS="-Xmx2048m -Xms1024m"
```

## Volumes

| Container Path | Description |
|---|---|
| `/var/airsonic` | Application data, config, database, logs |
| `/var/music` | Default music library |
| `/var/playlists` | Default playlists folder |
| `/var/podcasts` | Default podcasts folder |

These are the same paths used by the standalone WAR deployment, making it straightforward to switch between Docker and non-Docker installations.

## User Permissions

By default the container runs as root (UID 0, GID 0). The entrypoint creates a user with the specified `PUID`/`PGID`, sets ownership of the data directories, and drops privileges before starting the application.

You can set the user in two ways:

- **`PUID` / `PGID` environment variables** (recommended): The entrypoint handles user creation and ownership.
- **`--user uid:gid` flag**: Bypasses the entrypoint's user management. You must ensure the volumes are pre-owned by the specified UID/GID.

## Docker Compose

Compose files are provided in [`install/compose/`](../../install/compose/) for three database configurations:

**HSQLDB (embedded, default):**

```bash
cd install/compose
docker compose -f docker-compose.hsqldb.yaml up -d
```

**PostgreSQL:**

```bash
cd install/compose
docker compose -f docker-compose.postgres.yaml up -d
```

**MariaDB:**

```bash
cd install/compose
docker compose -f docker-compose.mariadb.yaml up -d
```

## External Database

To connect to an external database, set the Spring datasource properties via environment variables. Use lowercase with underscores — Spring Boot's relaxed binding maps them to the corresponding properties:

**PostgreSQL:**

```bash
-e spring_datasource_url="jdbc:postgresql://dbhost:5432/airsonic?stringtype=unspecified"
-e spring_datasource_username="airsonic"
-e spring_datasource_password="secret"
```

**MariaDB:**

```bash
-e spring_datasource_url="jdbc:mariadb://dbhost:3306/airsonic"
-e spring_datasource_username="airsonic"
-e spring_datasource_password="secret"
```

The JDBC driver is auto-detected from the URL — there is no need to set `spring.datasource.driver-class-name`.

## Ports

| Port | Protocol | Description |
|---|---|---|
| 4040 | TCP | HTTP web interface and API |
| 4041 | TCP | DLNA/UPnP (optional) |
| 1900 | UDP | DLNA/UPnP discovery (optional) |

## Health Check

The image includes a built-in health check that polls the `/rest/ping` endpoint every 15 seconds.

## Building the Image Locally

```bash
# Build the WAR first
mvn clean package -pl airsonic-main -am -DskipTests -q

# Prepare build context
mkdir -p install/docker/target/dependency
cp airsonic-main/target/airsonic.war install/docker/target/dependency/airsonic-main.war

# Build
cd install/docker
docker build -t airsonic-pulse:local .
```

## Transcoding

The Docker image includes `ffmpeg` and `lame` pre-installed. Symlinks are created automatically in `/var/airsonic/transcode/` at container startup.


