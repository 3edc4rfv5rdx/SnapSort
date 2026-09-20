# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- N: The photo view now zooms with a pinch and pans while zoomed
- N: A one-finger swipe looks through already-seen photos without moving the keep/trash cursor
- N: A landscape photo turns with the phone's tilt to fill the screen, even though the app itself stays locked portrait
- N: The current photo's file name and folder show as small labels over and under it
- E: A photo taken in portrait no longer shows rotated on its side
- F: The trash and settings screens now have a visible back button
- F: The folder-pick button is bigger and filled with the accent colour; the undo button now has its own colour instead of a muted tone
- F: The launcher icon's strike-through is smaller and shallower, from the other corner
- I: File access is now entirely off SAF, onto plain file paths under "All files access"
- I: Added the "All files access" permission screen, ahead of moving file access off SAF entirely
- E: A photo that fails to load through the picked folder now shows as failed instead of spinning forever
- F: The trash/keep buttons now show a thumbs-down/thumbs-up icon instead of a bin/checkmark
- F: Redrew the launcher icon — a white photo/mountain/sun glyph on the same blue plate
- N: The main screen shows one photo at a time from every photo under a picked folder, kept or trashed with a tap, with undo one step back
- N: Deleting a photo moves it to the app's own trash (Documents/SnapSort/.Trash) instead of removing it outright; the trash screen restores it or empties the trash for good
- N: Settings: theme, accent colour, language (English, Русский, Українська) and a start-up update check
- I: SnapSort started from the DiskMap build template, with folder access through a picked SAF folder tree instead of all-files access
