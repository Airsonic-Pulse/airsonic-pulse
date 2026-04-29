# Changelog

## [13.0.0] — 2026-04-29

### Breaking
- Java 21 is now required (Java 17 support dropped)
- Docker image moved to `ghcr.io/litebito/airsonic-pulse`
- Docker multi-arch: `linux/amd64` and `linux/arm64` only (`arm/v7` dropped — not supported by Java 21)

### Added
- Multi-database CI testing: HSQLDB, PostgreSQL 16, MariaDB 11 (#38)
- Docker Compose file for MariaDB (#49)
- Docker image build and push in release workflow via GitHub Actions (#51)
- Installation documentation and Linux install scripts for CentOS and Debian (#28)
- Debian-style EnvironmentFile path in systemd unit (#26)

### Fixed
- Default transcode command missing `%S` seek parameter on MariaDB (#42)
- Subsonic API response type rebranded from Airsonic-Advanced to Airsonic-Pulse (#40)
- Duplicate credentials created on password recovery — bcrypt vs hex mismatch (#44)
- VersionService network dependency causing CI failures (#47)
- VersionService logging warning on GitHub API 404 during release pagination (#21)
- UnsupportedOperationException when playing tracks — immutable `Stream.toList()` lists mutated at runtime (#13)
- CodeQL workflow OutOfMemoryError during analysis (#30)

### Changed
- Java 21 required, Java 17 dropped
- Spring Boot 3.5.5, Spring Framework 6.x
- Jakarta namespace migration (javax → jakarta)
- Docker image updated for Java 21 with multi-stage build
- Docker container default heap increased to `-Xmx1024m -Xms512m`
- Docker entrypoint creates data directories as root before dropping privileges
- Docker Compose files: removed deprecated `version` directive, fixed Spring datasource env vars to lowercase
- Removed `fabric8` Docker Maven plugin — image built via standalone `docker build`
- CI workflows split into `pr_ci.yml` and `pm_ci.yml` following naming convention
- All CI workflows run on self-hosted CentOS Stream 10 runners
- Updated `actions/checkout` to v5, `trivy-action` to v0.36.0, `codeql-action` to v4
- Release workflow updated for self-hosted runner compatibility
- Trivy scan switched to table output (SARIF upload requires GitHub Advanced Security)
- CodeQL scan disabled for private repo (will re-enable when public)

### Hardening
- Audit and fix resource leaks: streams, connections, processes (#17)
- Audit ReentrantLock conversions for lock safety (#16)
- Audit records conversion for null safety (#15)
- Audit `Stream.toList()` / `List.of()` / `Collections.unmodifiable` for mutation safety (#14)

## [12.0.0] — 2026-04-08

### Added
- GitHub Actions CI workflow
- Local test deployment script

### Fixed
- ffprobe/ffmpeg resolution from system paths when not in transcode directory (fixes MPC duration parsing and M4B chapter splitting tests)

### Changed
- Version bumped to 12.0.0-SNAPSHOT — first Airsonic-Pulse release
- Removed inherited upstream CI workflows
- Disabled Dependabot (will re-enable after stabilization)

### Removed
- Upstream CI/CD workflows (Edge Deploy, Stable Deploy, Docker builds, Trivy, CodeQL, PR builders)
- Dependabot configuration
