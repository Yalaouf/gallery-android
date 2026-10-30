# Gallery

A fast, private photo & video gallery for Android, with photo, GIF and video editing.
No ads, no trackers, no internet access.

> [!NOTE]
> This app was fully vibecoded from the very beginning
> by an AI coding assistant (Claude Code), from my descriptions of what I wanted.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="180" alt="Photos, grouped by day">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="180" alt="Albums and trash">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="180" alt="Photo editor with straighten">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="180" alt="Drag to select">
</p>
<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="180" alt="Move to another folder, with search">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="180" alt="GIF editor with speed and loop">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width="180" alt="Trash">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" width="180" alt="Settings">
</p>

## Download

Get the latest APK from the [**Releases page**](https://github.com/Yalaouf/gallery-android/releases/latest)
(`gallery-vX.Y.apk`). Android 11 or newer is required.

The APK is signed with this certificate (SHA-256), which you can check with
`apksigner verify --print-certs gallery-vX.Y.apk`:

```
E8:BB:B4:0D:FF:52:90:91:5A:14:38:5E:68:03:AF:41:84:B4:5D:F5:D9:33:1D:77:2C:E6:66:73:65:84:39:90
```

An F-Droid release is planned.

## Features

**Browse**
- **Photos**: everything grouped by day. Sort by date, name, size or randomly, show only photos or videos, choose 2 to 6 columns
- **Albums**: your folders, sorted by date modified, date created or name, with a search bar
- Full-screen viewer with pinch and double-tap zoom, video playback and animated GIFs
- Videos start muted in split screen, so the other app (e.g. YouTube) keeps playing

**Organize**
- Long-press and drag to select many items; the grid scrolls by itself at the edges. Select a whole day or everything
- **Move** items to another folder or a new one, with folder search and an A → Z / recent sort
- **Share** and **delete**; deleted items go to the **trash**, where they can be restored for 30 days or deleted for good
- Instant refresh after deleting, replacing or moving

**Edit**
- **Photos**: crop (free or fixed ratios), rotate, **straighten by any angle** (with automatic zoom so no empty corners show), mirror
- **GIFs**: the same tools, plus speed (0.25× to 4×) and looping
- **Videos**: trim with a live preview, crop, rotate, mirror
- **Video to GIF**: export up to 15 seconds as an animated GIF, at the video's speed, 10 to 50 fps
- Save as a copy or replace the original: ask each time or set a default, separately for photos and videos
- Keeps photo EXIF data (date, camera, and optionally the GPS location)
- Opens images and videos from other apps: "Edit with", "Open with", and "Edit with Gallery" in the share menu
  (handy for screenshots, whose Edit button always opens the system editor)

**Look**
- Material You design, light / dark / pure-black themes, wallpaper colors
- English and French, following the system language (or the per-app language on Android 13+)

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

## Privacy

The app has **no internet permission**: nothing ever leaves your phone. There are no ads, trackers or analytics,
and the only libraries are AndroidX, Media3 and Coil.

It needs access to your photos and videos. Optional permissions:
- **Media management** (Android 12+): edit, move and delete without Android asking for confirmation every time.
- **Photo location**: lets Android hand the GPS location to the app, so it's kept in edited photos.

## Building

Requirements: JDK 17 or newer (the one bundled with Android Studio works) and the Android SDK 37.

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # release APK, unsigned unless a release key is configured
./gradlew :app:testDebugUnitTest # unit tests (GIF encoder and decoder)
```

To install a release build directly on your phone, either configure a release key (below) or add
`signReleaseWithDebugKey=true` to `local.properties`, then run `./gradlew :app:installRelease`.

## Releasing

Pushing a `v*` tag (e.g. `v1.4`) runs the [release workflow](.github/workflows/release.yml): it builds and tests the
app, signs the APK and publishes a GitHub release with that version's notes from `CHANGELOG.md`.
To publish an existing tag (e.g. an older version), run the workflow manually from the Actions tab and enter the tag;
such releases are not marked as "Latest".

Signing uses the release key from these sources, in order:
- `local.properties` on a development machine: `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`
- environment variables in CI, fed by the repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
  `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`

Without a key the release APK is unsigned, which is what F-Droid expects (it signs the APK itself).

For each version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add
`fastlane/metadata/android/{en-US,fr-FR}/changelogs/<versionCode>.txt` and a `CHANGELOG.md` entry, then tag.

## License

Copyright (C) 2026 Yalaouf

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the License,
or (at your option) any later version. See [LICENSE](LICENSE).
