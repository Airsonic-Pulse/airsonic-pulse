# Changelog

## [12.0.0-edge.1] — 2026-04-08

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
