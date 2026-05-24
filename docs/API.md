# Airsonic-Pulse API Reference

Airsonic-Pulse exposes a music-streaming API that is compatible with the **Subsonic API** and implements a growing set of **OpenSubsonic** extensions. This document is generated from the codebase and reflects the actual state of the implemented endpoints.

- **Subsonic protocol version reported:** `1.16.1`
- **Server type:** `airsonic-pulse`
- **OpenSubsonic:** advertised (`openSubsonic=true`, `serverVersion` present)

## API surfaces

Airsonic-Pulse serves three overlapping API surfaces:

- **Subsonic** — the standard [Subsonic API](http://www.subsonic.org/pages/api.jsp). Airsonic-Pulse implements the complete Subsonic 1.16.1 endpoint surface.
- **OpenSubsonic** — the [OpenSubsonic](https://opensubsonic.netlify.app/) extensions: a compliant response envelope, the `getOpenSubsonicExtensions` discovery endpoint, additional endpoints, and enrichment fields layered onto existing Subsonic response types.
- **Native** — Airsonic-Pulse specific endpoints that are not part of the Subsonic or OpenSubsonic specifications.

### Endpoints and paths

All Subsonic/OpenSubsonic endpoints are served under both `/rest/<endpoint>` and `/rest/<endpoint>.view`, and accept both `GET` and `POST` (`formPost` extension). Most endpoints are also mapped under `/ext/<endpoint>` for JWT-authenticated external/share access. Response format is selected with the `f` request parameter (`xml`, `json`, or `jsonp`).

### Authentication

Standard Subsonic authentication parameters apply to all `/rest/**` endpoints (`u` plus `p`, or `u` plus `t`+`s` token/salt). `getOpenSubsonicExtensions` is the only endpoint that does not require authentication. Removal of legacy authentication methods and an `apiKeyAuthentication` extension are planned — see [#56](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/56) and [#145](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/145).

### How the endpoints were organized

The Subsonic API began as a single `SubsonicRESTController` monolith and was split into 16 domain controllers under tracking issue [#34](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/34), with cross-cutting follow-ups for exception handling ([#98](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/98)), a shared base class ([#112](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/112)), and a hygiene sweep ([#176](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/176)). The endpoint reference below is a single table grouped by the **Domain** column. The **Ref** column lists the issue(s) that implemented or updated that specific endpoint — typically the controller-extraction issue that moved it, plus any endpoint-specific functional change.

## Status legend

| Symbol | Meaning |
|--------|---------|
| ✅ | **Implemented** — fully working in the current codebase |
| 🔧 | **Partial** — exists but incomplete or with known limitations |
| 🚧 | **In Progress** — actively being worked on |
| 📋 | **Planned** — on the roadmap / tracked by an open issue |
| ❌ | **Not Planned** — no plans to implement |

**API Type:** `Subsonic` (standard spec) · `OpenSubsonic` (extension or new endpoint) · `Native` (Airsonic-Pulse specific) · `Legacy` (deprecated / slated for removal).

Airsonic-Pulse registers the complete Subsonic 1.16.1 endpoint surface — there are no entirely-absent Subsonic endpoints. The only functional caveats are `getVideoInfo` (🔧 routed but not implemented) and the chat endpoints (❌ intentionally unsupported).

## Endpoint reference

| Endpoint | Domain | Controller | Status | API Type | Ref | Notes |
|----------|--------|------------|--------|----------|-----|-------|
| `ping` | System | `SubsonicSystemController` | ✅ | Subsonic | [#69](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/69) | |
| `getLicense` | System | `SubsonicSystemController` | ✅ | Subsonic | [#69](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/69) | Always returns a valid license (Airsonic-Pulse is free) |
| `startScan` | System | `SubsonicSystemController` | ✅ | Subsonic | [#69](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/69) | Requires `ADMIN` role |
| `getScanStatus` | System | `SubsonicSystemController` | ✅ | Subsonic | [#69](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/69) | |
| `getOpenSubsonicExtensions` | System | `SubsonicRESTController` | ✅ | OpenSubsonic | [#66](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/66), [#132](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/132), [#141](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/141) | Extension discovery; no auth required. Declares `formPost`, `transcodeOffset`, `songLyrics`, `indexBasedQueue` |
| `tokenInfo` | System | — | ❌ | OpenSubsonic | — | Related to the `apiKeyAuthentication` extension ([#145](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/145)); no tracking issue yet |
| `getChatMessages` | Chat | `SubsonicSystemController` | ❌ | Subsonic | [#69](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/69) | Stub — returns error "Chat messages are not supported on this server" |
| `addChatMessage` | Chat | `SubsonicSystemController` | ❌ | Subsonic | [#69](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/69) | Stub — returns error "Posting chat messages is not supported on this server" |
| `getMusicFolders` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getIndexes` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getMusicDirectory` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getGenres` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getVideos` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getVideoInfo` | Browsing | `SubsonicBrowsingController` | 🔧 | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71), [#59](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/59) | Registered but not implemented — returns a structured error envelope |
| `getNowPlaying` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getAlbumList` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getRandomSongs` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getSongsByGenre` | Browsing | `SubsonicBrowsingController` | ✅ | Subsonic | [#71](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/71) | |
| `getArtists` | Browsing (ID3) | `SubsonicID3Controller` | ✅ | Subsonic | [#80](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/80), [#137](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/137), [#138](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/138) | `ArtistID3` carries OpenSubsonic `musicBrainzId`, `sortName` |
| `getArtist` | Browsing (ID3) | `SubsonicID3Controller` | ✅ | Subsonic | [#80](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/80), [#137](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/137), [#138](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/138) | `ArtistID3` carries OpenSubsonic `musicBrainzId`, `sortName` |
| `getAlbum` | Browsing (ID3) | `SubsonicID3Controller` | ✅ | Subsonic | [#80](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/80), [#129](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/129), [#130](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/130), [#136](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/136) | OpenSubsonic `AlbumID3` metadata bundle pending ([#142](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/142)) |
| `getSong` | Browsing (ID3) | `SubsonicID3Controller` | ✅ | Subsonic | [#80](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/80), [#129](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/129), [#130](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/130), [#134](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/134), [#135](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/135), [#139](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/139), [#143](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/143) | `Child` carries OpenSubsonic enrichments; `contributors[]` still pending ([#144](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/144)) |
| `getAlbumList2` | Browsing (ID3) | `SubsonicID3Controller` | ✅ | Subsonic | [#80](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/80) | |
| `getSimilarSongs` | Album/Artist info | `SubsonicArtistInfoController` | ✅ | Subsonic | [#106](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/106) | |
| `getSimilarSongs2` | Album/Artist info | `SubsonicArtistInfoController` | ✅ | Subsonic | [#106](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/106) | |
| `getTopSongs` | Album/Artist info | `SubsonicArtistInfoController` | ✅ | Subsonic | [#106](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/106) | |
| `getArtistInfo` | Album/Artist info | `SubsonicArtistInfoController` | ✅ | Subsonic | [#106](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/106) | |
| `getArtistInfo2` | Album/Artist info | `SubsonicArtistInfoController` | ✅ | Subsonic | [#106](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/106) | |
| `getAlbumInfo` | Album/Artist info | `SubsonicArtistInfoController` | ✅ | Subsonic | [#106](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/106) | |
| `getAlbumInfo2` | Album/Artist info | `SubsonicArtistInfoController` | ✅ | Subsonic | [#106](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/106) | |
| `search` | Search | `SubsonicSearchController` | ✅ | Subsonic | [#85](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/85), [#178](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/178), [#55](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/55) | Legacy search; superseded by `search2`/`search3`. Security review of query handling open ([#178](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/178)) |
| `search2` | Search | `SubsonicSearchController` | ✅ | Subsonic | [#85](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/85) | |
| `search3` | Search | `SubsonicSearchController` | ✅ | Subsonic | [#85](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/85) | |
| `star` | Annotation | `SubsonicAnnotationController` | ✅ | Subsonic | [#89](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/89) | |
| `unstar` | Annotation | `SubsonicAnnotationController` | ✅ | Subsonic | [#89](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/89) | |
| `setRating` | Annotation | `SubsonicAnnotationController` | ✅ | Subsonic | [#89](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/89) | |
| `scrobble` | Annotation | `SubsonicAnnotationController` | ✅ | Subsonic | [#89](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/89) | |
| `getStarred` | Annotation | `SubsonicAnnotationController` | ✅ | Subsonic | [#89](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/89) | |
| `getStarred2` | Annotation | `SubsonicAnnotationController` | ✅ | Subsonic | [#89](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/89) | |
| `stream` | Media retrieval | `SubsonicMediaController` | ✅ | Subsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110), [#117](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/117), [#132](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/132) | Supports `transcodeOffset` (`timeOffset`); known timeOffset bug across playqueue ([#177](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/177)) |
| `download` | Media retrieval | `SubsonicMediaController` | ✅ | Subsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110) | |
| `hls` | Media retrieval | `SubsonicMediaController` | ✅ | Subsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110) | HLS segment playlist |
| `getCoverArt` | Media retrieval | `SubsonicMediaController` | ✅ | Subsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110) | |
| `getAvatar` | Media retrieval | `SubsonicMediaController` | ✅ | Subsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110), [#115](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/115) | Content-Type fix |
| `getLyrics` | Media retrieval | `SubsonicMediaController` | ✅ | Subsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110) | |
| `getLyricsBySongId` | Media retrieval | `SubsonicMediaController` | ✅ | OpenSubsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110), [#131](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/131), [#157](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/157), [#158](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/158) | `songLyrics` extension (unsynced, read from tags at request time). Synced/LRC + DB storage planned ([#140](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/140)) |
| `getCaptions` | Media retrieval | `SubsonicMediaController` | ✅ | Subsonic | [#110](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/110), [#59](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/59) | Error-envelope fix |
| `getPlaylists` | Playlists | `SubsonicPlaylistController` | ✅ | Subsonic | [#104](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/104) | |
| `getPlaylist` | Playlists | `SubsonicPlaylistController` | ✅ | Subsonic | [#104](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/104) | |
| `createPlaylist` | Playlists | `SubsonicPlaylistController` | ✅ | Subsonic | [#104](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/104) | |
| `updatePlaylist` | Playlists | `SubsonicPlaylistController` | ✅ | Subsonic | [#104](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/104) | |
| `deletePlaylist` | Playlists | `SubsonicPlaylistController` | ✅ | Subsonic | [#104](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/104) | |
| `getPlayQueue` | Play queue | `SubsonicPlayQueueController` | ✅ | Subsonic | [#91](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/91) | |
| `savePlayQueue` | Play queue | `SubsonicPlayQueueController` | ✅ | Subsonic | [#91](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/91) | |
| `getPlayQueueByIndex` | Play queue | `SubsonicPlayQueueController` | ✅ | OpenSubsonic | [#141](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/141) | `indexBasedQueue` extension |
| `savePlayQueueByIndex` | Play queue | `SubsonicPlayQueueController` | ✅ | OpenSubsonic | [#141](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/141) | `indexBasedQueue` extension |
| `getBookmarks` | Bookmarks | `SubsonicBookmarkController` | ✅ | Subsonic | [#93](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/93) | |
| `createBookmark` | Bookmarks | `SubsonicBookmarkController` | ✅ | Subsonic | [#93](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/93) | |
| `deleteBookmark` | Bookmarks | `SubsonicBookmarkController` | ✅ | Subsonic | [#93](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/93) | |
| `getShares` | Sharing | `SubsonicShareController` | ✅ | Subsonic | [#95](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/95) | |
| `createShare` | Sharing | `SubsonicShareController` | ✅ | Subsonic | [#95](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/95) | |
| `updateShare` | Sharing | `SubsonicShareController` | ✅ | Subsonic | [#95](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/95) | |
| `deleteShare` | Sharing | `SubsonicShareController` | ✅ | Subsonic | [#95](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/95) | |
| `getPodcasts` | Podcasts | `SubsonicPodcastController` | ✅ | Subsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | |
| `getNewestPodcasts` | Podcasts | `SubsonicPodcastController` | ✅ | Subsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | |
| `getPodcastEpisode` | Podcasts | `SubsonicPodcastController` | ✅ | OpenSubsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97), [#133](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/133) | Not in the Subsonic spec; OpenSubsonic addition |
| `refreshPodcasts` | Podcasts | `SubsonicPodcastController` | ✅ | Subsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | |
| `createPodcastChannel` | Podcasts | `SubsonicPodcastController` | ✅ | Subsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | |
| `deletePodcastChannel` | Podcasts | `SubsonicPodcastController` | ✅ | Subsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | |
| `deletePodcastEpisode` | Podcasts | `SubsonicPodcastController` | ✅ | Subsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | |
| `downloadPodcastEpisode` | Podcasts | `SubsonicPodcastController` | ✅ | Subsonic | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | |
| `exportPodcasts/opml` | Podcasts | `SubsonicPodcastController` | ✅ | Native | [#97](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/97) | Airsonic OPML export; not part of the Subsonic or OpenSubsonic spec |
| `getInternetRadioStations` | Internet radio | `SubsonicRadioController` | ✅ | Subsonic | [#100](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/100) | |
| `createInternetRadioStation` | Internet radio | `SubsonicRadioController` | ✅ | Subsonic | [#100](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/100), [#123](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/123) | Subsonic 1.16.0 CRUD (admin-only) |
| `updateInternetRadioStation` | Internet radio | `SubsonicRadioController` | ✅ | Subsonic | [#100](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/100), [#123](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/123) | Subsonic 1.16.0 CRUD (admin-only) |
| `deleteInternetRadioStation` | Internet radio | `SubsonicRadioController` | ✅ | Subsonic | [#100](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/100), [#123](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/123) | Subsonic 1.16.0 CRUD (admin-only) |
| `jukeboxControl` | Jukebox | `SubsonicJukeboxController` | ✅ | Subsonic | [#102](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/102) | Direct audio output to the server sound card |
| `changePassword` | User management | `SubsonicUserController` | ✅ | Subsonic | [#108](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/108) | |
| `getUser` | User management | `SubsonicUserController` | ✅ | Subsonic | [#108](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/108) | |
| `getUsers` | User management | `SubsonicUserController` | ✅ | Subsonic | [#108](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/108) | |
| `createUser` | User management | `SubsonicUserController` | ✅ | Subsonic | [#108](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/108) | |
| `updateUser` | User management | `SubsonicUserController` | ✅ | Subsonic | [#108](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/108), [#57](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/57) | `streamRole` mapping bug fixed |
| `deleteUser` | User management | `SubsonicUserController` | ✅ | Subsonic | [#108](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/108) | |

## OpenSubsonic capabilities (non-endpoint)

The items in this section are part of OpenSubsonic compliance but are **not callable endpoints** — they are the response envelope, the capability flags advertised through `getOpenSubsonicExtensions`, and enrichment fields added to existing Subsonic responses. The OpenSubsonic-specific *endpoints* themselves (`getOpenSubsonicExtensions`, `getLyricsBySongId`, `getPodcastEpisode`, `getPlayQueueByIndex`, `savePlayQueueByIndex`) appear in the endpoint reference above with API Type `OpenSubsonic`. Tracking issue: [#35 — Implement OpenSubsonic API support](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/35).

### Base compliance — ✅ Implemented

- Response envelope advertises `openSubsonic=true`, `type="airsonic-pulse"`, and `serverVersion` ([#64](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/64)).
- Protocol version reported as `1.16.1` ([#113](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/113)).
- Extension discovery available via the `getOpenSubsonicExtensions` endpoint ([#66](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/66)).

### Declared extension flags — ✅ Implemented

These are returned by `getOpenSubsonicExtensions`:

| Extension | Version | Status | Ref | Notes |
|-----------|---------|--------|-----|-------|
| `formPost` | 1 | ✅ | [#66](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/66) | All endpoints accept `POST` |
| `transcodeOffset` | 1 | ✅ | [#132](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/132) | `stream` accepts a transcode time offset |
| `songLyrics` | 1 | ✅ | [#131](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/131), [#157](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/157), [#158](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/158) | `getLyricsBySongId`; unsynced lyrics read from tags at request time |
| `indexBasedQueue` | 1 | ✅ | [#141](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/141) | Adds `getPlayQueueByIndex` / `savePlayQueueByIndex` |

### Response field enrichments — ✅ Implemented

| Type | Fields | Ref |
|------|--------|-----|
| `Child` | `played`, `musicBrainzId`, `displayArtist`, `displayAlbumArtist`, `mediaType`, `sortName`, `bpm`, `genres[]`, `replayGain` | [#129](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/129), [#130](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/130), [#134](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/134), [#135](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/135), [#139](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/139), [#143](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/143) |
| `AlbumID3` | `played`, `playCount`, `musicBrainzId`, `displayArtist`, `sortName` | [#129](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/129), [#130](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/130), [#136](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/136) |
| `ArtistID3` | `musicBrainzId`, `sortName`, `mediaType` | [#130](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/130), [#137](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/137), [#138](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/138) |
| `Artist` (legacy, returned by `getIndexes` / `getStarred`) | `userRating`, `averageRating` | [#179](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/179) |

### Response field enrichments — 📋 Planned

| Item | Ref |
|------|-----|
| `contributors[]` — multi-role contributor extraction | [#144](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/144) |
| `AlbumID3` bundle — `isCompilation`, `releaseTypes`, `originalReleaseDate`, `discTitles`, `recordLabels` | [#142](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/142) |
| `songLyrics` — synced / LRC support with DB storage | [#140](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/140) |

### Capability extensions — 📋 Planned (not yet declared)

| Extension | Ref | Notes |
|-----------|-----|-------|
| `apiKeyAuthentication` | [#145](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/145) | Tied to removal of legacy authentication ([#56](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/56)); would add the `tokenInfo` endpoint (see the endpoint reference) |

### Response fields — ❌ Not currently planned

These OpenSubsonic response fields exist in the specification but have no tracking issue yet:

- Audio-detail `Child` fields: `bitDepth`, `samplingRate`, `channelCount`.
- `Child.moods[]`, `explicitStatus`.

## References

- Subsonic API specification: <http://www.subsonic.org/pages/api.jsp>
- OpenSubsonic specification: <https://opensubsonic.netlify.app/>
- OpenSubsonic tracking issue (this project): [#35](https://github.com/Airsonic-Pulse/airsonic-pulse/issues/35)
