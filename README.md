# SnapSort

**Go through your phone's photo clutter one at a time: keep it, or trash it.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/3edc4rfv5rdx/SnapSort)](https://github.com/3edc4rfv5rdx/SnapSort/releases/latest)

SnapSort shows one photo at a time, full screen, from a folder you pick. A
tap sends it to the app's own trash instead of deleting it outright; moving
on to the next photo with the back/forward buttons is all it takes to
keep one.

## Features

- **One photo at a time**: every image under the picked folder, in order.
  The bin trashes the current photo; back and forward move to the previous
  or next one, so nothing happens to a photo just by looking at it. Undo,
  in the ⋮ menu, restores the last trashed photo to its place.
- **Folder access**: pick any folder once, through the system's own folder
  picker; SnapSort remembers it and goes through every photo in every
  subfolder under it.
- **Trash**: `Documents/SnapSort/.Trash` on the same storage, so moving a
  photo there is instant, not a copy. Open it from the ⋮ menu; tap an entry
  to view the photo full-screen, its own menu restores it or deletes it for
  good, and a button at the top empties the whole trash.
- **⋮ menu**: Undo, Change folder, Trash, Settings and About.
- **Settings**: light/dark theme, accent colour, language (English, Русский,
  Українська) and an update check.
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
