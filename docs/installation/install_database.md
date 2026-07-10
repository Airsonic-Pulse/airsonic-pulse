# Database configuration

Airsonic-Pulse stores its metadata (users, playlists, ratings, scan results) in a relational database. Three engines are supported: HSQLDB, PostgreSQL, and MariaDB/MySQL. The media files themselves are never stored in the database; only the metadata is.

The same four properties configure the database regardless of how you run the server (standalone WAR or Docker) and regardless of the operating system. That is why this is one shared page. The install guides link here rather than repeating it.

## Drivers are already bundled

You do not need to download or add any JDBC driver. The WAR ships with the HSQLDB, PostgreSQL, MariaDB, and MySQL drivers on the classpath. Point the server at your database with the properties below and it connects.

## Which engine should you use?

**HSQLDB (default, zero configuration).** This is what runs if you set nothing. The database lives in a single file under your data directory. It is fine for evaluation and for small personal libraries. It is not the right choice for large libraries or for setups where you want external backups, replication, or standard database tooling. If you outgrow it, move to PostgreSQL or MariaDB.

**PostgreSQL (recommended for anything beyond evaluation).** Well supported and the most exercised external engine. One connection-string flag is required (see below); miss it and the server fails to start.

**MariaDB / MySQL (supported).** The bundled MariaDB driver connects to both MariaDB and MySQL. Character-set setup needs care (see the MariaDB section).

## How the configuration works

Set these four properties. `driver-class-name` is optional; the server infers the driver from the URL scheme, so you set only the URL, username, and password.

```
spring.datasource.url=...
spring.datasource.username=...
spring.datasource.password=...
spring.datasource.driver-class-name=...   # optional, inferred from the URL
```

You can supply them in three interchangeable ways:

- In `airsonic.properties` inside your data directory (default `/var/airsonic/airsonic.properties`, or `C:\airsonic\airsonic.properties` on Windows).
- As JVM arguments: `-Dspring.datasource.url=...`
- As environment variables (Docker and relaxed binding): `spring_datasource_url=...`

After changing the datasource, restart the server. On first start against an empty database, the schema is created automatically by Liquibase.

---

## HSQLDB (default)

Do nothing. With no datasource configured, the server creates and uses a file-based HSQLDB database at `<data-dir>/db/airsonic`. The connection is opened in MVCC mode with a clean-shutdown flag; those internals are handled for you and do not belong in your config.

To move an existing HSQLDB install to another engine, see "Switching engines" at the bottom.

---

## PostgreSQL

Create a database and a user:

```sql
CREATE USER airsonic WITH PASSWORD 'change-me';
CREATE DATABASE airsonicdb OWNER airsonic;
```

Configure the datasource. The `stringtype=unspecified` parameter in the URL is **required**, not optional. PostgreSQL is strict about implicit type casts, and without this flag some of the server's writes are rejected and startup fails.

```
spring.datasource.url=jdbc:postgresql://localhost:5432/airsonicdb?stringtype=unspecified
spring.datasource.username=airsonic
spring.datasource.password=change-me
```

For Docker, the same values go in the environment block as `spring_datasource_url`, `spring_datasource_username`, `spring_datasource_password`, with the host set to your Postgres service name instead of `localhost`. The bundled Postgres compose file already includes `stringtype=unspecified`.

You do not need to set `userTableQuote` or any `DatabaseUsertableQuote` property. The server quotes the user table correctly for PostgreSQL on its own. Setting it manually has no effect; the key is obsolete.

---

## MariaDB / MySQL

The server does not enforce a character set. Liquibase creates tables with no `DEFAULT CHARSET` clause, so they inherit the database default, and the JDBC URL is used exactly as you supply it with no encoding parameters added. Charset is entirely your responsibility. If you point Airsonic-Pulse at a database whose default is `latin1` (common on older or hand-provisioned MariaDB and MySQL servers), non-ASCII artist and album names are stored incorrectly, and there is no code-side guardrail to catch it. Create the database as Unicode explicitly and do not rely on the server default.

Create the database with a Unicode character set and collation. Do not let it default to `latin1`, or non-ASCII artist and album names will corrupt:

```sql
CREATE DATABASE airsonicdb
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
CREATE USER 'airsonic'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON airsonicdb.* TO 'airsonic'@'%';
FLUSH PRIVILEGES;
```

Configure the datasource:

```
spring.datasource.url=jdbc:mariadb://localhost:3306/airsonicdb
spring.datasource.username=airsonic
spring.datasource.password=change-me
```

For a MySQL server, keep the same bundled driver and use a `jdbc:mysql://` URL instead. For Docker, move these into the environment block with the `spring_datasource_*` names and set the host to your database service name.

If you run MariaDB in Docker, do not depend on the image default. The default character set of the `mariadb:11` tag depends on the exact 11.x version it resolves to at pull time; per MariaDB's documentation the server default became utf8mb4 in 11.6, and earlier versions default to latin1. Force it on the container so the result is the same regardless of when the image was pulled:

```yaml
command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

---

## What the server handles for you

You should not set these. They are managed internally or were dropped:

- **User-table quoting** is applied per engine automatically. `DatabaseUsertableQuote` and `spring.liquibase.parameters.userTableQuote` are obsolete and ignored.
- **`DatabaseConfigType`** from the old Airsonic property scheme is dropped. There is no config-type switch; the engine is determined by your datasource URL.

If you are upgrading from an old Airsonic or Airsonic-Advanced install, the legacy embedded-database keys are migrated automatically on startup: `DatabaseConfigEmbedDriver`, `DatabaseConfigEmbedUrl`, `DatabaseConfigEmbedUsername`, and `DatabaseConfigEmbedPassword` are mapped to their `spring.datasource.*` equivalents. You can leave them in place, but prefer the modern names for new installs.

---

## Switching engines

There is no automatic data migration between engines. Moving from HSQLDB to PostgreSQL, for example, does not carry your existing metadata across on its own. Before changing the datasource on a populated install, back up your data directory, and treat the new engine as a fresh database unless you have separately exported and imported your data. Verify the export/import path for your version before relying on it.