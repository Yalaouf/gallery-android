# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow the app's `versionName`.

## [Unreleased]

### Fixed
- **Video restarting after a screenshot**: taking a screenshot (or any new photo arriving) while watching a video
  made it restart from the beginning, even when paused. The viewer now follows the item being shown rather than its
  position in the list, and a paused video stays paused where it was.
- **Location when sharing**: if a photo's clean copy couldn't be made, the original was shared instead, with its
  location. Such photos are now left out of the share, with a message.
- **Locked folder**: if its index file were ever damaged, the hidden files could no longer be seen. Files missing
  from the index are now listed anyway, and the index is rebuilt on the next change.

### Added
- Unit tests for the main features (80 tests), run on the computer with Robolectric.

## [2.0] — 2026-11-02

### Added
- **Collections tab**, next to Photos and Albums: On this day, Favorites, all Photos, all Videos, Trash and the Locked folder, so Albums
  only lists your folders. It can also be the tab the app opens on.
- **Favorites**: a heart in the viewer and in the selection bar, a heart on favorite thumbnails, and a Favorites
  collection. It uses Android's own favorite flag, so other gallery apps see the same favorites.
- **Locked folder**: hide photos and videos from the gallery and from every other app. It opens with your
  fingerprint, face or screen lock, locks again as soon as you leave the app, and its screens can't be captured
  (screenshots, recent apps). Items are moved to the app's private storage, excluded from cloud backups, and can be
  put back in their original folder. They are deleted if the app is uninstalled, which the app explains up front.
- **On this day**: photos taken on today's date in previous years, in Collections and as one card per year at the
  top of Photos (that row can be turned off in Settings).
- **Fast scroller**: while scrolling, a handle appears on the right; drag it to jump through the grid, with a
  month and year bubble.
- **Slideshow** from the viewer's ⋮ menu, with an adjustable time per photo (2 to 10 s). A tap stops it.
- **Copy** to another folder, **rename**, and **create an album** (name it, then pick the photos to move or copy).
- **Set as** wallpaper, contact picture… through the apps that support it.
- **More details in Info**: camera, lens, aperture, shutter speed, ISO, focal length, frame rate and bitrate for
  videos, and the location with an "Open in Maps" button (handled by your maps app; Gallery stays offline).
- **Remove location when sharing** (Settings → Privacy, off by default): shared photos are sent as copies without
  GPS data. Formats that can't be edited in place (HEIC…) are sent as JPEG. Videos are shared as they are.
- **Photo editor**: new Adjust (light, contrast, color, warmth), Draw (6 colors, adjustable size, undo) and
  Hide tabs. Hide pixelates what you paint over, in blocks too coarse to recover faces, plates or text.
- **Video editor**: speed (0.25× to 4×, keeping the pitch of the sound), remove the sound, and save the current
  frame as a photo (with the same rotation and crop).
- The app version is shown at the bottom of Settings.

### Changed
- **New icon**: two mountains and an orange sun, in a minimalist line style, with a dark version.
  Settings → App icon: Automatic (follows the system theme, on launchers that support it), Light or Dark.
- The selection bar now has Favorite, Move, Share, Delete and a ⋮ menu (Select all, Copy to, Locked folder).

### Fixed
- "Edit with Gallery" from the share menu crashed on Android 11 and 12.

## [1.5] — 2026-11-02

### Added
- **Swipe to close**: in the viewer, swipe a photo or video up or down to go back. It follows the finger, shrinks
  and the background fades; release it early and it springs back. Zoomed-in pictures and pinches don't trigger it.
- **Animated back gesture**: during Android's back swipe, the current screen shrinks and follows the finger,
  then closes on release or comes back if the swipe is cancelled.

### Fixed
- **Dark squares after deleting from another app**: pictures deleted elsewhere (e.g. in another gallery) stayed as
  empty squares until the app was restarted, because Android drops change notifications while the app is in the
  background. The gallery now refreshes whenever it comes back to the foreground.

## [1.4] — 2026-11-01

### Fixed
- **Stretched photo after cropping**: right after replacing a photo with a smaller (cropped) version, the viewer drew it
  with the old size until it was reopened. The image now takes its new size immediately.
- **Video controls covered by the action bar**: the player's built-in controls sat under the Share / Edit / Move bar,
  so pausing or moving the progress bar could fail. The viewer now has its own controls (play/pause, progress bar
  and time), laid out right above the action bar. A tap shows or hides everything together, and the controls
  hide after 3 seconds of playback.

### Changed
- **Sharper GIFs**: edge-aware dithering (full dithering in smooth areas, much less on text and outlines) and a
  better-refined palette. On a detail test image, errors on details drop by 6% and on text by 12%, with gradients
  just as smooth.
- **Video to GIF**: frames are reduced in sharp halving steps instead of a single bilinear resize, and the sizes are
  now 320, 480, 720 and 1080 px (was up to 640). A note warns that large GIFs are heavy.

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

[Unreleased]: https://github.com/Yalaouf/gallery-android/compare/v2.0...HEAD
[2.0]: https://github.com/Yalaouf/gallery-android/compare/v1.5...v2.0
[1.5]: https://github.com/Yalaouf/gallery-android/compare/v1.4...v1.5
[1.4]: https://github.com/Yalaouf/gallery-android/compare/v1.3...v1.4
[1.3]: https://github.com/Yalaouf/gallery-android/compare/v1.2...v1.3
[1.2]: https://github.com/Yalaouf/gallery-android/compare/v1.1...v1.2
[1.1]: https://github.com/Yalaouf/gallery-android/compare/v1.0...v1.1
[1.0]: https://github.com/Yalaouf/gallery-android/releases/tag/v1.0
