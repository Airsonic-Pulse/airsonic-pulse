[![Release](https://github.com/litebito/airsonic-pulse/actions/workflows/release.yml/badge.svg)](https://github.com/litebito/airsonic-pulse/actions/workflows/release.yml) [![CI - Pull Requests](https://github.com/litebito/airsonic-pulse/actions/workflows/pr_ci.yml/badge.svg)](https://github.com/litebito/airsonic-pulse/actions/workflows/pr_ci.yml)  [![CI - Main](https://github.com/litebito/airsonic-pulse/actions/workflows/pm_ci.yml/badge.svg)](https://github.com/litebito/airsonic-pulse/actions/workflows/pm_ci.yml) [![Trivy](https://github.com/litebito/airsonic-pulse/actions/workflows/any_trivy_scan.yml/badge.svg)](https://github.com/litebito/airsonic-pulse/actions/workflows/any_trivy_scan.yml)

# Airsonic-Pulse

(this readme is being rewritten)

## 1. What is Airsonic-Pulse?

Airsonic-Pulse is a continuation of Kagemomiji's [Airsonic-Advanced](https://github.com/kagemomiji/airsonic-advanced), a free, web-based media streamer providing ubiquitous access to your music. Airsonic-Pulse picks up where Airsonic-Advanced left off, with a focus on modernization, stability, and long-term maintenance.

**Fork lineage:** Subsonic → Airsonic → Airsonic-Advanced → **Airsonic-Pulse**

### Why Airsonic-Pulse?
Several Airsonic-Advanced forks are seemingly abandoned by their maintainers. Airsonic-Pulse is an attempt to continue the project with:
- Active maintenance and security updates
- Modernization of the Java platform (Java 21+)
- Planned frontend overhaul
- Planned new features
- Continued Subsonic API compatibility

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
[GitHub](https://github.com/litebito/airsonic-pulse/releases).

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

For a long (but non-exhaustive) list of features inherited from Airsonic-Advanced, read the "Feature Enhancements" section in [History.md](https://github.com/litebito/airsonic-pulse/blob/main/docs/HISTORY.md)

## 4. Docker
[GHCR](https://ghcr.io/litebito/airsonic-pulse). Docker releases are recently multiplatform, which means ARM64 is also released to Dockerhub. However, automated testing for those archs is not currently done in the CI/CD pipeline (only Linux platform is tested).

Please note that for Docker images, the volume mounting points have changed and are different from Airsonic. Airsonic mount points are at `/airsonic/*` inside the container. Airsonic-Advanced tries to use the same volume locations as the default war image at `/var/*` in order to remain consistent if people want to switch between the containers and non-containers.
  - `Music:/airsonic/music` -> `Music:/var/music`
  - `Podcasts:/airsonic/podcast` -> `Podcasts:/var/podcast`
  - `Playlists:/airsonic/playlists` -> `Playlists:/var/playlists`
  - `/airsonic/data` -> `/var/airsonic`

Also note that the Docker image will by default run as user root (0), group root (0), and so any files created in the external volume will be owned as such. You may change the user running the internal process in one of two ways:
  - Specifying `--user` when invoking the `docker run` command, and providing it with one or both in the format `uid:gid`
  - Specifying the `PUID` or `PGID` environment variables to the container image when invoking the `docker run` command (`-e PUID=uid -e PGID=gid`)

Please use the [Airsonic documentation](https://airsonic.github.io/docs/) for instructions on running Airsonic. For the most part (currently) Airsonic-Pulse shares similar running instructions unless stated otherwise. 
Notable exceptions will be available in the /docs folder (and if you think something is missing in the documentaion, please raise a documentation issue).

### Docker Compose
(to rewrite/update)

To evaluate Airsonic in Docker Compose try our compose files in [install/compose](./install/compose) directory. There are 3 variants: 
- embedded database (HSQLDB),
- PostgreSQL
- MariaDB

You can run from within directory by command:
```shell
docker compose -p airsonic-hsqldb -f docker-compose.hsqldb.yaml up
```

### Building/Compiling
(to rewrite/update)

You may compile the code yourself by using Maven. A sample invocation would be (in the root):
```
mvn clean package
```
Requires Java 21 and Maven 3.9+. The WAR file will be at `airsonic-main/target/airsonic.war`.

### Configuration

See the [Configuration](./docs/configures/README.md)

## Documentation

For Airsonic-Pulse-specific documentation, see the [`docs/`](./docs/README.md)
directory in this repository.

## Compatibility Notes:

### Airsonic-Pulse 12.x
Version 12.0.0 is the initial Airsonic-Pulse release. It is based on the final version of kagemomiji/airsonic-advanced (11.1.4) and remains fully compatible with it. Future 13.x releases will modernize the codebase (Java 21 exclusively) and may introduce breaking changes. Migration notes will be documented here when applicable.

**WARNING: Always take backups before trying new versions!**

## Troubleshooting
See the [Troubleshooting](./docs/troubleshooting.md)

## History

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

Bugs, feature requests, and discussions for Airsonic-Pulse can be raised as issues on the [Airsonic-Pulse GitHub page](https://github.com/litebito/airsonic-pulse).

For more historical context, you can read more [here](https://github.com/litebito/airsonic-pulse/blob/main/docs/HISTORY.md), or check out the the upstream project at [kagemomiji/airsonic-advanced](https://github.com/kagemomiji/airsonic-advanced).
