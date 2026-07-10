# Rule

Note: This page was inherited from the upstream Airsonic-Advanced project and has not yet been validated with Airsonic-Pulse. The content may still be correct, but some details (versions, ports, paths, screenshots) may be outdated. If you want to suggest a specific correction, feel free to [raise it as an issue](https://github.com/Airsonic-Pulse/airsonic-pulse/issues)


Airsonic Advanced categorizes directories and files into Album, Artist, Song, and Video using the following logic:

| Type | Description |
| --- | --- |
| Song | A single audio file. If it has the specified extensions which defined in the `Settings` > `General` > `Music files` field, it is considered a song. |
| Video | A single video file. If it has the specified extensions which defined in the `Settings` > `General` > `Video files` field, it is considered a video. |
| Album | A parent directory containing at least one Song or Video. |
| Artist | A parent directory containing albums. If it contains songs or videos directly, it is considered an album. |


Therefore, it is understood as follows:

```
.
├── Artist1
│   ├── Album1
│   │   ├── Song1.flac
│   │   ├── Song2.mp3
│   │   └── Folder.jpg
├── Artist2
│   ├── Album2
│   │   ├── Song3.ogg
│   │   ├── Song4.ogg
│   │   └── Folder.jpg
│   └──Album3
│       ├── Song5.mp3
│       ├── Song6.mp3
│       └── Folder.jpeg
├── Album2
│   ├── Song7.mp3
│   ├── Video1.mp4
│   └── Folder.jpg
```