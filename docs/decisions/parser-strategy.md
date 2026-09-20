# ADR (Architecture Decision Record): Audio metadata parser strategy

**ADR Status:** Accepted
**ADR Date:** 2026-09-20

## Context

Airsonic-Pulse reads audio metadata through two parsers behind `MetaDataParserFactory`, which picks the first applicable parser by Spring `@Order`:

| Format / extension | Parser | Tag system read |
|---|---|---|
| mp3 | JaudiotaggerParser (`@Order(0)`) | ID3v1 / ID3v2 |
| flac, ogg | JaudiotaggerParser | Vorbis comments |
| m4a, m4b, m4p, aac | JaudiotaggerParser | MP4 / iTunes atoms |
| wav, aif, aiff | JaudiotaggerParser | RIFF / ID3 |
| wma | JaudiotaggerParser | ASF |
| dsf | JaudiotaggerParser | ID3 |
| opus | FFmpegParser (`@Order(100)`, any regular file) | ffprobe JSON (flattened format/stream tags) + in-house `OpusHeaderReader` for base gain |
| all video formats, mpc, wv, ape, mka and other exotic audio | FFmpegParser | ffprobe JSON |

The Jaudiotagger accept list is `{"mp3", "m4a", "m4b", "m4p", "aac", "ogg", "flac", "wav", "aif", "dsf", "aiff", "wma"}` (`JaudiotaggerParser:536`), with an explicit note that Opus is excluded because jaudiotagger 3.0.1 has no Opus reader. FFmpegParser accepts any regular file and spawns `ffprobe -v quiet -print_format json -show_format -show_streams -show_chapters <file>` per parse.

jaudiotagger is abandoned upstream: Maven Central lists 3.0.1 as the latest release with metadata last updated 2021-10-14 (`net.jthink:jaudiotagger`, versions 2.2.5 / 3.0.0 / 3.0.1). Nearly five years without a release, while this project's metadata work keeps colliding with its limits first-hand:

- No Opus reader at all (confirmed: zero Opus classes in the 3.0.1 jar), forcing the ffprobe route plus a hand-written header reader for base gain.
- `FieldKey.LYRICS_SYNCHRONISED` does not exist, so SYLT extraction (#280) needs frame-level ID3 access rather than the clean FieldKey API.
- `ID3v1Tag.getFields()` throws `UnsupportedOperationException`, complicating generic tag iteration.
- The MP4 ReplayGain lookup was case-sensitive while the TXXX branch was not — fixed downstream in this repo (#251) because there is no upstream to take the patch.

Pending work needs more from tag reading, not less: the full embedded-tag persistence design (#209) and synchronised lyrics (#280) both go past "read the usual scalar fields".

Performance rules out simply routing everything to ffprobe. Project measurements (ffprobe 7.1.5), replicating the exact `FFmpegParser` invocation against repo test fixtures (44-358 KB):

| File | jaudiotagger in-JVM (mean / median) | ffprobe subprocess (mean / median) |
|---|---|---|
| piano.mp3 (102 KB) | 2.44 / 1.51 ms | 390 / 392 ms |
| eyes like dull hazlenuts.mp3 (44 KB) | 1.35 / 1.12 ms | 414 / 419 ms |
| Goldberg Aria.flac (358 KB) | 0.56 / 0.55 ms | 415 / 417 ms |
| m4btest.m4b (172 KB) | 1.05 / 0.95 ms | 438 / 448 ms |

Per-file delta ≈ 400 ms. Extrapolated to a 300,000-file rescan: roughly 5 minutes in-JVM versus roughly 34 hours of subprocess time, sequentially. Two caveats on the extrapolation: fixtures are small files, so the in-JVM parse time under-represents large real-world files, while process-spawn overhead is size-independent and is the reliable component of the delta. Scanner parallelism divides the wall clock for both paths equally; the per-file gap stands.

## Options considered

**1. Keep 3.0.1 frozen, patch around bugs downstream (status quo).**
Every jaudiotagger defect becomes an Airsonic-Pulse workaround (#251 is already one), and gaps like SYLT force frame-level contortions in our code instead of a fix where it belongs. Workarounds will  accumulate instead of the fix landing at the source. 

**2. Migrate to ffprobe as the primary parser.**
One actively maintained parser for everything, no Java library to own. Costs the measured ~400 ms per file at library scale, tens of hours per full rescan of a large library; and ffprobe flattens tags into generic key/value JSON, which is the opposite of what the frame-level work in #209/#280 needs.

**3. Adopt the RouHim fork as a dependency.**
Actively maintained (pushed 2026-09-12, releases weekly) and it includes a full Opus reader package. But it descends from the Kaned1as hard-fork of the pre-3.0 Android line — its own README states the lineage — versioned 2.x, currently targeting Java 25, distributed via JitPack only, and its activity is overwhelmingly tooling: 9 of 290 commits touch `src/`. Not API-compatible with our 3.0.1 usage without a migration of unknown size, and it swaps one third-party maintainer for another.

**4. Fork jthink's jaudiotagger 3.0.1 into the Airsonic-Pulse org for internal use. (CHOSEN)**
Drop-in by construction, package names kept, so runtime behaviour changes only when we choose to patch. At-source fixes (the #251 class of bug, SYLT support, ID3v1 iteration) land in the fork instead of accumulating as workarounds.

**5. Full public revival now: Maven Central publishing, external issue intake, open governance.**
More work and commitment than option 4 for the same benefit to this project. Deferred, but not rejected, it stays available if external demand appears.

## Decision

Fork jthink's jaudiotagger 3.0.1 into the Airsonic-Pulse org for internal use.

- The fork is created when the first at-source patch needs a home, not preemptively.
- Package names are kept so the fork is a drop-in replacement; LGPL-2.1 is preserved.
- FFmpeg/ffprobe remains the gap-filler: Opus, video, and anything jaudiotagger cannot read.
- Porting Opus support from the RouHim/Kaned1as 2.x line is a possible later enhancement, taken only after its own study; it is not a prerequisite of this decision.
- A full public revival (Maven Central, external governance) is a deferred option, exercised only if external demand appears.

## Consequences

**Positive.** The #209 and #280 designs can rely on full-tag and frame-level access: the working rule "do not expand reliance on an abandoned library" re-scopes to "route no new formats to it" once the library is ours. Fixes like #251 land at the source instead as workarounds.

**Negative.** The project owns the fork's bug and security response from the first patch onward, and gains a second repository to run once it exists. If the fork ever becomes unsustainable, the exit path is option 2 at the measured cost above.

**Neutral.** Nothing changes at runtime until the first fork patch ships; until then the dependency coordinates and behaviour are exactly today's.

## References

- #35 (OpenSubsonic programme), #202 (extended CUE sheets), #209 (embedded tag persistence), #251 (MP4 ReplayGain case fix), #280 (synchronised lyrics)
- `net.jthink:jaudiotagger:3.0.1` (Maven Central; metadata last updated 2021-10-14)
- RouHim fork: https://github.com/RouHim/jaudiotagger (fork of Kaned1as/jaudiotagger, pre-3.0 lineage)
