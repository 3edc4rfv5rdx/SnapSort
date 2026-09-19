# SnapSort

**Go through your phone's photo clutter one at a time: keep it, or trash it.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/3edc4rfv5rdx/SnapSort)](https://github.com/3edc4rfv5rdx/SnapSort/releases/latest)

SnapSort shows one photo at a time, full screen, from a folder you pick. One
tap keeps it and moves to the next; another sends it to the app's own trash
instead of deleting it outright.

## Features

- **One photo at a time**: every image under the picked folder, in order.
  Tap the check to keep it, the bin to trash it — either way the next photo
  comes up. Undo steps back one photo, restoring it from the trash first if
  that is where it just went.
- **Folder access**: pick any folder once, through the system's own folder
  picker; SnapSort remembers it and goes through every photo in every
  subfolder under it.
- **Trash**: `Documents/SnapSort/.Trash` on the same storage, so moving a
  photo there is instant, not a copy. Open it from the ⋮ menu; each entry's
  own menu restores it or deletes it for good, and one button empties the
  whole trash.
- **⋮ menu**: Trash, Settings and About.
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
