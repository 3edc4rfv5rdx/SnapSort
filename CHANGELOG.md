# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- I: Added the "All files access" permission screen, ahead of moving file access off SAF entirely
- E: A photo that fails to load through the picked folder now shows as failed instead of spinning forever
- F: The trash/keep buttons now show a thumbs-down/thumbs-up icon instead of a bin/checkmark
- F: Redrew the launcher icon — a white photo/mountain/sun glyph on the same blue plate
- N: The main screen shows one photo at a time from every photo under a picked folder, kept or trashed with a tap, with undo one step back
- N: Deleting a photo moves it to the app's own trash (Documents/SnapSort/.Trash) instead of removing it outright; the trash screen restores it or empties the trash for good
- N: Settings: theme, accent colour, language (English, Русский, Українська) and a start-up update check
- I: SnapSort started from the DiskMap build template, with folder access through a picked SAF folder tree instead of all-files access
