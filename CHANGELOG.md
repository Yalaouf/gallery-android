# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow the app's `versionName`.

## [1.3] — 2026-10-30

### Added
- **Trash**: a Trash folder at the end of the Albums tab lists deleted items with the days left before Android
  removes them. Restore them, delete them for good, or empty the trash. It can be hidden in Settings.
- **Signed APKs on GitHub**: each version is published on the Releases page, signed with a release key.
- **Straighten** photos and GIFs by any angle (−45° to 45°) with a slider, on top of the 90° turns.
  The image zooms in automatically so no empty corners show; the preview updates live.
- **Search in the Albums tab**: filters folders by name as you type (ignores case and accents).
- **30 and 50 fps** for GIF export. 50 fps is the GIF maximum: frame delays are stored in
  hundredths of a second, and players slow down anything shorter than 2/100 s.

### Changed
- **Better GIF quality**: Floyd–Steinberg error diffusion instead of ordered dithering, and a finer,
  refined palette. Gradients are smoother and color blotches are gone (perceived error halved
  on natural gradients), for files about 5–10% larger.
- GIF export now defaults to 15 fps.
- **Instant refresh**: deleting, replacing, moving or copying updates the screen right away instead of
  waiting for Android's change notification. A replaced photo fades from the old to the new version
  with no black frame or second reload, and deleted items vanish without a fade-out.

### Security
- "Edit with", "Open with" and "Share to" now only accept `content://` files. A `file://` path sent by another app
  could point into Gallery's private storage, and saving or sharing it would have leaked those files.
- The GIF decoder refuses images declaring an absurd size (over 50 megapixels) instead of running out of memory,
  and the GIF editor closes with a message when a GIF can't be opened.
- Release workflow: third-party actions pinned to exact commits, the GitHub token no longer kept on disk during
  the build, and the decoded signing key deleted at the end of every run.

## [1.2] — 2026-10-30

### Added
- **Export videos as animated GIFs** (up to 15 seconds), using the current trim, crop, rotation and mirror.
  Choose the size (320, 480 or 640 px), the smoothness (10 or 15 fps) and whether it loops.
  The GIF plays at the same speed as the video and is saved as a new image next to it.
- **GIF editor**: crop, rotate and mirror GIFs like photos, change their speed (0.25× to 4×) and turn looping on or off.
- **Animated GIFs** now play in the viewer.
- **Search when moving files**: a search bar (ignores case and accents) and an A → Z / Recent sort in the "Move to…" list.
  When nothing matches, the list offers to create a folder with that name.
- **Open and edit from other apps**: the app now appears in "Edit with" / "Open with",
  and as "Edit with Gallery" in the share menu — for example from a screenshot's Share button.

### Changed
- Saving tries to write directly before asking Android for permission, which avoids a confirmation
  when the other app (e.g. the screenshot tool) already allowed it.
- Files are flushed to storage when saved, so a save can't leave an empty file if the phone shuts down right after.

## [1.1] — 2026-10-29

### Changed
- Trim, crop and rotate can be combined in one editing session:
  rotating a photo or video keeps the crop instead of resetting it.
- The video preview shows the cropped result while trimming or rotating.
- A dot on the video tools shows which edits are active.

## [1.0] — 2026-10-29

### Added
- Photos view grouped by day, with sorting (date, name, size, random), filtering (photos/videos) and 2–6 columns.
- Albums view, sorted by date modified, date created or name.
- Full-screen viewer with zoom and video playback (muted in split screen).
- Multi-select by long-press and drag, with auto-scroll; select a whole day or everything.
- Move to another folder or a new one, share, and delete (to trash).
- Photo editor: crop (free or fixed ratios), rotate, mirror; EXIF data kept.
- Video editor: trim with live preview, crop, rotate, mirror.
- Save as a copy or replace the original, set separately for photos and videos.
- Settings: theme (system/light/dark, pure black, wallpaper colors), start tab, video options,
  photo quality, keep location, delete confirmation, media management permission.
- English and French, following the system language.
- No internet permission, no trackers.

[1.3]: https://github.com/Yalaouf/gallery-android/compare/v1.2...v1.3
[1.2]: https://github.com/Yalaouf/gallery-android/compare/v1.1...v1.2
[1.1]: https://github.com/Yalaouf/gallery-android/compare/v1.0...v1.1
[1.0]: https://github.com/Yalaouf/gallery-android/releases/tag/v1.0
