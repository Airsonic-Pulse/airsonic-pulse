# Security Policy

## Supported versions

Airsonic-Pulse is a community continuation of Airsonic / Airsonic-Advanced, currently maintained by a single person on a best-effort basis (see [CONTRIBUTING.md](CONTRIBUTING.md)). Security fixes are provided only for the most recent release line.

| Version | Supported          |
| ------- | ------------------ |
| 13.x    | :white_check_mark: |
| < 13.0  | :x:                |

If you are running an older release, please upgrade before reporting a security issue.

## Reporting a vulnerability

**Please do not open public issues, pull requests, or discussions for security vulnerabilities.**

Use GitHub's private vulnerability reporting feature:

➡️ **[Report a vulnerability](https://github.com/litebito/airsonic-pulse/security/advisories/new)**

This delivers the report privately to the maintainers; the contents are not visible to the public.

### What to include

To help triage quickly, please include where possible:

- Affected version(s) of Airsonic-Pulse
- A clear description of the vulnerability and its impact
- Steps to reproduce, or a proof-of-concept
- Your environment (OS, Java version, database, deployment method)
- Whether the vulnerability is already public, or has been shared with anyone else

### What to expect

This is a best-effort, non-funded project maintained in spare time. Realistic expectations:

- **Acknowledgement** of receipt: within 14 days
- **Initial assessment**: within 30 days
- **Fix or mitigation timeline**: depends on severity, complexity, and maintainer availability

You will be credited (with your consent) in the published security advisory once a fix is released.

## Coordinated disclosure

We follow coordinated disclosure. Please do not publicly disclose the vulnerability until a fix has been released, or until **90 days** have passed since your initial report — whichever comes first. If more time is needed, we are open to discussing an extension.

## Out of scope

The following are generally out of scope for security advisories:

- Vulnerabilities in third-party dependencies — please report those upstream; Airsonic-Pulse will pick up the fix once a patched version of the dependency is released.
- Issues that require the attacker to already have administrator access on the Airsonic-Pulse instance.
- Self-XSS or social-engineering attacks targeting administrators.
- Missing security headers without a demonstrable exploit path.
- Denial-of-service from clearly excessive request volume — Airsonic-Pulse does not ship a rate limiter by design; this is expected to be handled by a fronting reverse proxy.
- Issues reproducible only on unsupported or end-of-life configurations.

## Hall of fame

Researchers who responsibly disclose valid security issues will be acknowledged in the published advisory and (with their permission) listed here once advisories are released.