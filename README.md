# Nova

A simple music player for Android for songs and videos you have downloaded, in the frosted
"liquid glass" look of the Glass toolkit from
[PhilPF/dualsense_manager](https://github.com/PhilPF/dualsense_manager).

Requires **Android 12 or newer** (the glass panels blur what is behind them with `RenderEffect`).

## Features

- **Your folder, nothing else.** Choose a folder with the system's folder picker; Nova reads it
  and all its subfolders (and only it; no storage permission). Settings can change it.
- **Leaving things out.** WhatsApp's and Telegram's audio (voice notes and the like) is left
  out unless turned on in Settings: files in folders named after those apps, or named as
  WhatsApp names them. A song's menu can hide its whole folder; Settings lists hidden folders
  and shows them again.
- **Songs, albums, artists, playlists**, a swipe apart. Read from the files' tags (title, artist, album, album artist,
  track and disc numbers, year, embedded art); without tags, from the file name
  ("Artist - Title"). A folder's `cover.jpg`/`folder.jpg` and yt-dlp's thumbnail files are used
  as art; a video without art shows a frame of itself.
- **Player.** Album art, title and artist, a seek bar, previous, play/pause, next, shuffle and
  repeat. Swipe the art, or the mini player, sideways for the next or previous song; pull the
  player down to close it. Below it, peeking at the bottom, is what plays next: swipe up for
  all of it, in the order it will play. Songs added together (an album, a playlist, an artist,
  a video's tracklist) are grouped under a header that also removes the whole group; a long
  video playing whole lists its songs to come first. Drag a song by its handle to move it, or
  take it out with its ×. Album, artist, playlist and video sheets have Play next and Add to
  queue. It plays in the background with the usual notification, lock-screen and headset controls
  (a Media3 media session).
- **Videos.** A video plays in the art's place at its own shape (the page around it eases to
  the new size), full screen (landscape for wide videos; turning the phone also goes full
  screen), or in picture in picture, over other apps: from its button, or by leaving the app
  while a video plays. A button in its corner switches a video to its art.
- **Double tap** the left or right of the picture (or the full-screen video) to skip 10 s back
  or forward. Forward past the end goes to the next song; back past the start by up to 3 s goes
  to the start, further to the previous song (`core/SkipRule`); a song inside a long video counts
  as a song of its own. The screen stays on while the player is open.
- **Long titles** that do not fit scroll slowly (their start shown first, then their end, then
  back), in the player, the mini player and the song playing in lists; Settings turns it off.
- **Sheets** (an album, an artist, a playlist, a song's menu, …) open up to three quarters of the
  screen; drag them or scroll their list up for their full height. Their head (picture, title,
  Play and Shuffle) stays while the list scrolls under it.
- **Where you left off.** Opening the app again brings back the last queue at the song and the
  time it was left, paused, in that song's colour.
- **Widget.** The song playing with its art on the home screen: square, the art edge to edge
  with play/pause; wide, the art blurred behind with previous, play/pause and next. Its buttons
  work with the app closed: Play picks up the last queue where it was.
- **Playlists.** Make playlists of songs, or of songs *inside* long videos.
- **Long videos as playlists.** A video holding several songs (a mix, a full album, a live set)
  becomes a playlist of its own under *Playlists › From videos*: each song is listed, credited to
  its artist (under *Artists* too), and can be played, queued or added to playlists on its own.
  While the whole video plays, the player names the song playing, marks each song on the seek
  bar, shows the tracklist below (tap to jump), and Next and Previous move between its songs,
  from the notification and headphones too.

The app's name, the song playing and the names of albums, artists and playlists are set in
[Newsreader](https://github.com/productiontype/Newsreader) (SIL Open Font License, in
`res/font`), a free serif close to Tiempos Headline (which is not free to bundle). Another font
replaces it by changing `Headline` in `ui/theme/GlassTheme.kt`.

## Tracklists of long videos (YouTube chapters)

YouTube's chapters are either set by the uploader or read by YouTube from timestamps in the
description ("0:00 Artist - Title" per line). Nova reads a video's tracklist from, in order:

1. **A tracklist entered in Nova** (a video's menu › *Tracklist…*): paste the description or a
   comment holding the tracklist; only the lines with a time are read.
2. **Chapters inside the file.** The best way: download with
   [yt-dlp](https://github.com/yt-dlp/yt-dlp) and `--embed-chapters --embed-metadata`
   (and `--embed-thumbnail` for the art). yt-dlp writes YouTube's chapters into the file. Nova
   reads them from MP4/M4A (Nero `chpl` box or QuickTime chapter track), Matroska/WebM
   (`Chapters`), MP3 (ID3 `CHAP` frames) and Opus/Ogg/FLAC (`CHAPTERxxx` comments).
3. **yt-dlp's info file** next to the video, `<name>.info.json` (`--write-info-json`): its
   `chapters`.
4. **A CUE sheet** next to the file, `<name>.cue` (or one whose `FILE` names it).
5. **Timestamps in the description:** `<name>.description` (`--write-description`), the info
   file's, or the description embedded in the file (`--embed-metadata` writes it).

For example:

```bash
yt-dlp -f "bv*[height<=1080]+ba/b" --embed-chapters --embed-metadata --embed-thumbnail "https://www.youtube.com/watch?v=…"
```

A song's title is read as "Artist - Title" (also with an en or em dash), after any track
number; without a separator, the song is credited to the video's artist.

Why not fetch it from YouTube in the app: YouTube has no public API for chapters (its Data API
gives the description only, and needs a key), and reading its pages breaks whenever YouTube
changes them. yt-dlp already does this well, at download time, and is kept up to date.

## How it works

- `core/` is plain Kotlin, unit-tested in `app/src/test`: the chapter and description readers
  for each format (`EmbeddedTags`), description timestamps (`TimestampText`), CUE sheets,
  yt-dlp info files, which source wins (`ChapterResolver`), artist and title splitting
  (`TrackNames`), the library (albums, artists, songs inside videos), playlists and their storage.
  Which files are messaging apps' audio is `FolderFiles.isMessaging`. The sample files in `app/src/test/resources/media` were written by ffmpeg as yt-dlp does.
- `device/` scans the folders through the Storage Access Framework (`DocumentTree`), reads tags
  with `MediaMetadataRetriever` and chapters with the core readers, and caches the library, so a
  rescan only reads changed files.
- `service/PlaybackService` is ExoPlayer in a `MediaSessionService`. A song inside a video is
  the video clipped to it (`ClippingConfiguration`); `ChapterPlayer` makes Next and Previous
  move between a video's songs, and the notification follows the song playing.
- `ui/theme/Dimens.kt` holds the spacing (a 4 dp grid) and the corner radii: panels and
  controls stand 16 dp from the edge of the screen or sheet, and text and pictures, in rows too,
  start 8 dp further in. `ui/Waves.kt` is Dualsense Manager's press wave, here in a song's
  colour: across the mini player when a song starts, a row when it is played, a card when it is
  opened, and the player's next panel.
- `ui/` is Compose: `glass/` and `theme/` are the Glass toolkit; the library, the player, the
  full-screen video and the sheets.

## Building

A standard Gradle project (AGP 8.5.2, Kotlin 2.0.21, Jetpack Compose, Media3 1.4.1,
compileSdk 34, minSdk 31).

- **GitHub Actions.** `.github/workflows/build-apk.yml` runs on pushes to `claude/` branches and
  from the Actions tab. It runs the unit tests, builds a debug APK and publishes it on the
  **`debug-build`** release as `Nova.apk` (installs straight from a phone browser), then opens
  the app on an emulator with a sample library (made by `.github/demo_media.sh`) and adds
  screenshots to the same release.
- **Android Studio.** Open the project and run it, or `./gradlew assembleDebug`.

Debug builds show the sample library when started with
`adb shell am start -n com.philpf.nova/.MainActivity --ez demo true`, after copying it into the
app's `files/Demo` with `run-as` (see `.github/screenshots.sh`).
