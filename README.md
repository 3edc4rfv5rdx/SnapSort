# SnapSort

**Go through your phone's photo and video clutter one at a time: keep it, or trash it.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/3edc4rfv5rdx/SnapSort)](https://github.com/3edc4rfv5rdx/SnapSort/releases/latest)

SnapSort shows one photo or video at a time, full screen, from a folder you
pick. A tap sends it to the app's own trash instead of deleting it outright;
moving on to the next one with the back/forward buttons is all it takes to
keep it.

## Features

- **One file at a time**: every photo and video in the picked folder, in
  order, with its name, folder, date taken, size and place in the queue on
  screen. The bin
  trashes the current one; back and forward (or a swipe) move to the previous
  or next, so nothing happens to a file just by looking at it. Undo, in the
  ⋮ menu, restores the last trashed one to its place.
- **Viewing**: pinch to zoom and drag while zoomed. A landscape photo turns
  with the phone even though the app itself stays portrait. A video shows a
  still from its middle; the play button opens it in the phone's own player.
- **Folder access**: pick any folder once, in SnapSort's own folder browser —
  internal storage or an SD card. It needs "All files access" instead of the
  system picker, which would ask for permission again on every change.
  SnapSort remembers the folder, and the file it left off on. It goes through
  that folder alone, or — with "Go into subfolders" in Settings — every
  subfolder too, except hidden ones and the sorting folders.
- **Trash**: `Documents/SnapSort/.Trash` at the root of each storage, so
  moving a file there is instant, not a copy. Each file is kept under its
  deletion time, with a record of its name and folder beside it. Open it from
  the ⋮ menu: each entry shows a thumbnail, its original name and folder, and
  when it was trashed; tap one to view it full-screen, and its own menu
  restores it or deletes it for good. A button at the top empties the whole
  trash with a progress bar. Anything left there for 30 days is deleted on
  its own.
- **RAW pairs**: a `.dng` beside a photo of the same name is not in the queue
  itself, but goes wherever its photo goes — into the trash as one entry, into
  a sorting folder or a year — and comes back with it on undo or restore.
- **⋮ menu**: Undo, Change folder, Trash, Disk space, Sort into years, Reset
  position, Settings and About.
- **Sorting folders**: buttons down the right edge put the current file into a
  folder beside it — `-Best` and `-Docs` to begin with, up to five, each with
  its own name and icon in Settings. The dash keeps them out of the queue,
  whatever they are called; pick one as a folder of its own to go through what
  landed there. Undo takes the last one back.
- **Sort into years**: from the ⋮ menu, the photos and videos at the top of
  the picked folder go into `2018`, `2019` … beside them, by the date the
  camera recorded or the one in the file name. A RAW goes with the photo of
  its name. This year's files and those with no date stay put; subfolders are
  not touched. A dialog shows the count per year first; there is no undo.
- **Settings**: light/dark theme, accent colour, language (English, Русский,
  Українська), the sorting folders, the order the queue goes in (by name, or
  by date taken oldest or newest first), going into subfolders, remembering
  the position in a folder, and an update check.
- **Private**: no account, no ads, no analytics. The app goes online only to
  check its own GitHub release for a newer version, and its settings are left
  out of cloud backups and device transfers.

## Install

Download the APK from the [latest release](https://github.com/3edc4rfv5rdx/SnapSort/releases/latest):

`snapsort-<version>-arm64-v8a.apk` runs on almost every modern phone;
`snapsort-<version>-armeabi-v7a.apk` is for a 32-bit one or a TV box.

## Build

```bash
./00-MakeAll.sh     # signed release build, installed on the emulator and the phone
./05-Lint.sh        # Android Lint
./06-Test.sh        # unit tests
```

Requires JDK 21 and the Android SDK (compileSdk 36). Release signing reads
`~/.my-safe/key.properties`. Without it the build still works but the APK is unsigned.

The in-app updater and the About dialog are compiled from the sibling folders
`../updater` and `../about`. Clone them next to this repository.

## License

[GNU General Public License v3.0](LICENSE).

## Note

This codebase was developed with the help of artificial intelligence tools.
