[![Release](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/release.yml/badge.svg)](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/release.yml) [![CI - Pull Requests](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/pr_ci.yml/badge.svg)](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/pr_ci.yml)  [![CI - Main](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/pm_ci.yml/badge.svg)](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/pm_ci.yml) [![Trivy](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/any_trivy_scan.yml/badge.svg)](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/any_trivy_scan.yml) [![CodeQL](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/any_codeql_scan.yml/badge.svg)](https://github.com/Airsonic-Pulse/airsonic-pulse/actions/workflows/any_codeql_scan.yml)

[![Latest Version](https://img.shields.io/github/v/release/Airsonic-Pulse/airsonic-pulse?label=Latest%20Version&color=2f7bd9)](https://github.com/Airsonic-Pulse/airsonic-pulse/releases/latest)
![GitHub Tag](https://img.shields.io/github/v/tag/Airsonic-Pulse/Airsonic-Pulse)
![GitHub Release](https://img.shields.io/github/v/release/Airsonic-Pulse/Airsonic-Pulse)
![GitHub Release Date](https://img.shields.io/github/release-date/Airsonic-Pulse/Airsonic-Pulse)

[![Issues](https://img.shields.io/github/issues/Airsonic-Pulse/airsonic-pulse)](https://github.com/Airsonic-Pulse/airsonic-pulse/issues)
[![Last Commit](https://img.shields.io/github/last-commit/Airsonic-Pulse/airsonic-pulse)](https://github.com/Airsonic-Pulse/airsonic-pulse/commits/main)
![GitHub commit activity](https://img.shields.io/github/commit-activity/m/Airsonic-Pulse/Airsonic-Pulse)
![GitHub commits since latest release](https://img.shields.io/github/commits-since/Airsonic-Pulse/Airsonic-Pulse/latest)



[![License](https://img.shields.io/github/license/Airsonic-Pulse/airsonic-pulse)](https://github.com/Airsonic-Pulse/airsonic-pulse/blob/main/LICENSE) ![Java](https://img.shields.io/badge/Java-21-orange)



# Airsonic-Pulse

## 1. What is Airsonic-Pulse?

Airsonic-Pulse is a continuation of Kagemomiji's [Airsonic-Advanced](https://github.com/kagemomiji/airsonic-advanced), a free, web-based media streamer providing ubiquitous access to your music. Airsonic-Pulse picks up where Airsonic-Advanced left off, with a focus on modernization, stability, and long-term maintenance.

**Fork lineage:** Subsonic → Airsonic → Airsonic-Advanced → **Airsonic-Pulse**

### Why Airsonic-Pulse?
Several Airsonic-Advanced forks are seemingly abandoned by their maintainers. Airsonic-Pulse is an attempt to continue the project with:
- Active maintenance and security updates
- Resolve open bugs
- Modernization of the Java platform (Java 21+)
- Continued Subsonic API compatibility
- Implementation of the OpenSubsonic API
- Planned frontend overhaul, modernization
- Planned new features


### What is Airsonic
Airsonic is a free, web-based media streamer, providing ubiquitous access to your music. Use it to share your music with friends, or to listen to your own music while at work. You can stream to multiple players simultaneously, for instance to one player in your kitchen and another in your living room.

Airsonic is designed to handle very large music collections (hundreds of gigabytes). Although optimized for MP3 streaming, it works for any audio or video format that can stream over HTTP, for instance AAC and OGG. By using transcoder plug-ins, Airsonic supports on-the-fly conversion and streaming of virtually any audio format, including WMA, FLAC, APE, Musepack, WavPack and Shorten.

If you have constrained bandwidth, you may set an upper limit for the bitrate of the music streams. Airsonic will then automatically resample the music to a suitable bitrate.

In addition to being a streaming media server, Airsonic works very well as a local jukebox. The intuitive web interface, as well as search and index facilities, are optimized for efficient browsing through large media libraries. Airsonic also comes with an integrated Podcast receiver, with many of the same features as you find in iTunes.

Written in Java, Airsonic runs on most platforms, including Windows, Mac, Linux and Unix variants.

![Screenshot](contrib/assets/screenshot.png)

## 2. Usage of Airsonic-Pulse
Airsonic-Pulse v12.x.x series are intercompatible with Kagemomiji's [Airsonic-Advanced](https://github.com/kagemomiji/airsonic-advanced).
However, this is no longer the case with vanilla Airsonic 10.6.x series, and may not necessarily be the case with 11.x versions of original Airsonic-Advanced (https://github.com/airsonic-advanced/airsonic-advanced).

Also note that Airsonic-Pulse versions 13.x and higher (and its snapshots) are *breaking* (non-backwards-compatible) version changes. You will not be able to revert back to 10.x.x or 11.x.x versions after upgrading (the system _does_ create a backup of the DB in case such revert is necessary, but it must be manually restored).

### Stand-alone binaries
Airsonic-Pulse can be downloaded from
[GitHub](https://github.com/Airsonic-Pulse/airsonic-pulse/releases).

You need a _minimum_ Java Runtime Environment (JRE) of 17 for 12.x onwards (including snapshots).
- For 12.x releases -> Java 17
- For 13.x releases -> Java 21

Airsonic-Pulse is run similarly to (and in lieu of) vanilla Airsonic or Airsonic-Advanced.

## 3. Feature Enhancements in Airsonic-Pulse:

The following is an incomplete list of features that are enhanced from Airsonic-Advanced:
More modern base frameworks and libraries
  - Spring Boot 3.x (instead of 2.x), Spring Framework 6.x (instead of 5.x). Plus all the additional dependency upgrades due to the base libaries being upgraded (including EhCache, upgraded SQL connectors etc.)
  - Moving to Java 21, dropping support for Java 17
  - (... more to come...)

For a long (but non-exhaustive) list of features inherited from Airsonic-Advanced, read the "Feature Enhancements" section in [History.md](https://github.com/Airsonic-Pulse/airsonic-pulse/blob/main/docs/HISTORY.md)

## 4. Docker

The Airsonic-Pulse Docker image is published to the GitHub Container Registry:
```
ghcr.io/airsonic-pulse/airsonic-pulse
```
Supported architectures: `linux/amd64`, `linux/arm64`.

**Quick start:**

```bash
docker run -d \
  --name airsonic-pulse \
  -p 4040:4040 \
  -v ./data/airsonic:/var/airsonic \
  -v ./data/music:/var/music \
  -e PUID=1000 \
  -e PGID=1000 \
  ghcr.io/airsonic-pulse/airsonic-pulse:latest
```

The image runs as root by default and uses `PUID`/`PGID` to create a non-root user at startup. Volume mount points are at `/var/*` to remain consistent with the standalone WAR deployment.

Docker Compose files for HSQLDB, PostgreSQL, and MariaDB are provided in [`install/compose/`](./install/compose).

For full Docker documentation, see [docs/docker/](./docs/docker/README.md).


[GHCR](https://ghcr.io/airsonic-pulse/airsonic-pulse). Docker releases are multiplatform, which means ARM64 is also released to Dockerhub. However, automated testing for those archs is not currently done in the CI/CD pipeline (only Linux platform is tested).


Please use the [Airsonic documentation](https://airsonic.github.io/docs/) for instructions on running Airsonic. For the most part (currently) Airsonic-Pulse shares similar running instructions unless stated otherwise. 
Notable exceptions will be available in the /docs folder (and if you think something is missing in the documentaion, please raise a documentation issue).


## 5. Building/Compiling
(to rewrite/update)

You may compile the code yourself by using Maven. A sample invocation would be (in the root):
```
mvn clean package
```
Requires Java 21 and Maven 3.9+. The WAR file will be at `airsonic-main/target/airsonic.war`.

## 6. Configuration

See the [Configuration](./docs/configuration/README.md)

## 7. Documentation

For Airsonic-Pulse-specific documentation, see the [`docs/`](./docs/README.md)
directory in this repository.

## 8. Compatibility Notes:

### Airsonic-Pulse 12.x (released)
Version 12.0.0 is the initial Airsonic-Pulse release. It is based on the final version of kagemomiji/airsonic-advanced (11.1.4) and remains fully compatible with it. Future 13.x releases will modernize the codebase (Java 21 exclusively) and may introduce breaking changes. Migration notes will be documented here when applicable.

### Airsonic-Pulse 13.x

#### Airsonic-Pulse 13.0.x (released)

13.x releases will modernize the codebase:
- Java 21 exclusively, java 17 support is dropped
- may introduce breaking changes. 
Migration notes will be documented here when applicable.

#### Airsonic-Pulse 13.1.x (planned)

13.1.0 releases will further refactor the codebase:
- refactoring of the Subsonic API
- implementation of the OpenSubsonic API
- update settings pages (ffmpeg path, some reset options)
- removal of legacy (unsecure) authentication. (WARNING: before upgrading to 13.1.x or higher, make sure none of the userid's still use any legacy authentication)
Migration notes will be documented here when applicable.
**WARNING: before upgrading to 13.1.x or higher, make sure none of the userid's still use any legacy authentication**

**WARNING: Always take backups before trying new versions!**

## 9. Troubleshooting
See the [Troubleshooting](./docs/troubleshooting.md)

## 10. History

The original [Subsonic](http://www.subsonic.org/) is developed by [Sindre Mehus](mailto:sindre@activeobjects.no). Subsonic was open source through version 6.0-beta1, and closed-source from then onwards.

Libresonic was created and maintained by [Eugene E. Kashpureff Jr](mailto:eugene@kashpureff.org). It originated as an unofficial ("Kang") of Subsonic which did not contain the Licensing code checks present in the official builds. With the announcement of Subsonic's closed-source future, a decision was made to make a full fork and rebrand to Libresonic.

#### 2017 (July)

It was discovered that Eugene had different intentions/goals for the project than some contributors had.  Although the developers were hesitant to create a fork as it would fracture/confuse the community even further, it was deemed necessary in order to preserve a community-focused fork. 
To reiterate this more clearly: Airsonic's goal is to provide a full-featured, stable, self-hosted media server based on the Subsonic codebase that is free, open source, and community driven.

#### 2019 (November)
Around November 2019, Airsonic-Advanced was forked off the base Airsonic fork due to differences in pace and review of development. Several key features of the framework were outdated, and attempts to upgrade them occasionally took upto a year. Airsonic-Advanced tries a modern implementation and bleeding edge approach to development, and is thus usually ahead of the base fork in dependencies and features.

#### 2022 (December)
December 2022, Kagemomiji's [Airsonic-Advanced](https://github.com/kagemomiji/airsonic-advanced) repository forked from Airsonic-Advanced.

#### 2026 (April)
Airsonic-Pulse was created as a continuation of kagemomiji/airsonic-advanced, which had become inactive. Airsonic-Pulse aims to modernize the codebase while maintaining the project's core mission as a free, open-source, self-hosted media server.

## License

Airsonic-Pulse, Airsonic-Advanced, and Airsonic are free software and licensed under the [GNU General Public License version 3](http://www.gnu.org/copyleft/gpl.html). The code in this repository (and associated binaries) are free of any "license key" or other restrictions. If you wish to thank the maintainer of this repository, please consider a donation to the [Electronic Frontier Foundation](https://supporters.eff.org/donate).

The [Subsonic source code](https://github.com/airsonic/subsonic-svn) was released under the GPLv3 through version 6.0-beta1. Beginning with 6.0-beta2, source is no longer provided. Binaries of Subsonic are only available under a commercial license. There is a [Subsonic Premium](http://www.subsonic.org/pages/premium.jsp) service which adds functionality not available in Airsonic. Subsonic also offers RPM, Deb, Exe, and other pre-built packages that Airsonic [currently does not](https://github.com/airsonic/airsonic/issues/65).

The cover zooming feature is provided by [jquery.fancyzoom](https://github.com/keegnotrub/jquery.fancyzoom), released under [MIT License](http://www.opensource.org/licenses/mit-license.php).

The icons are from the amazing [feather](https://feathericons.com/) project, and are licensed under [MIT license](https://github.com/feathericons/feather/blob/master/LICENSE).

The cover art functionality supporting multiple image file formats is powered by the [TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys) library, which is released under the [BSD3 License](https://github.com/haraldk/TwelveMonkeys/blob/main/LICENSE.md).  

## Community

Bugs, feature requests, and discussions for Airsonic-Pulse can be raised as issues on the [Airsonic-Pulse GitHub page](https://github.com/Airsonic-Pulse/airsonic-pulse).

For more historical context, you can read more [here](https://github.com/Airsonic-Pulse/airsonic-pulse/blob/main/docs/HISTORY.md), or check out the the upstream project at [kagemomiji/airsonic-advanced](https://github.com/kagemomiji/airsonic-advanced).
