# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- E: A zoomed photo can no longer be dragged off the screen and left there
- E: Photos thrown away under a previously picked folder no longer come back into the queue when a folder above it is picked; hidden folders such as .thumbnails are skipped too
- E: Picking a new folder no longer reopens it at its last photo, from the previous folder's saved position
- E: Opening the trash before a folder is picked no longer leaves a screen that loads forever
- E: Changing the language no longer closes the Settings screen
- E: Pinch-to-zoom now works on the first try instead of needing a second attempt — the swipe detector was swallowing the pinch
- F: Secondary text — empty states, the trash's "empty" line and each entry's original path — is now full contrast instead of grey on grey
- N: A photo left in the trash for 30 days is now deleted for good, checked whenever a folder opens
- F: Shortened the Russian and Ukrainian "reset position" menu labels
- I: Release builds are minified and shrunk again (R8), off since it was slowing down iteration
- N: Reopening a folder continues from the last-viewed photo, with a Settings toggle to turn that off and "Reset position" in the ⋮ menu
- N: The ⋮ menu shows disk space: used of total with a percentage, a bar that fills red as the disk fills, and how much is free
- E: A stray status-bar inset no longer pushes the file name pill away from the buttons above it
- F: Trash and Settings are now full screens instead of pop-up dialogs, each with a back button
- F: The trash screen shows how many photos are in it, next to the total size
- F: "Empty trash" moved to a red button at the top of the trash screen, now just labelled "Clear"
- N: Tapping a photo in the trash shows it full-screen; each row also shows a small thumbnail
- F: The trash list's file name is no longer cut short, and its size now sits next to the date instead
- F: The trash screen's per-file menu and the whole Settings screen use bigger text
- F: The photo no longer shows behind the status bar
- F: Back/forward now use the theme's own light/dark-swapping colours instead of a fixed white, and sit lower, right above the file name and path
- F: The file name and path pills now use the full width available instead of a fixed guess, and the path's front-truncation sizes itself to that width
- F: The ⋮ menu's text is bigger
- F: The launcher icon's strike is shorter, clear of the plate's edges
- N: Explicit back/forward buttons next to trash, plus a current/total counter, so paging works without swiping too
- F: "Change folder" in the ⋮ menu switches folders without waiting for a fresh pick every launch — the last folder is remembered again
- F: Undo now lives in the ⋮ menu instead of its own button; trash/back/forward all work on one single position in the list, so paging back to an already-trashed spot can't show a broken "could not load" any more
- F: Trash's icon is a plain bin instead of a thumbs-down, in black instead of white
- E: A landscape photo turning with the phone's tilt now actually fills the screen instead of shrinking into a square in the middle
- N: The photo view now zooms with a pinch and pans while zoomed
- N: A landscape photo turns with the phone's tilt to fill the screen, even though the app itself stays locked portrait
- N: The current photo's file name and folder show as small labels over and under it
- E: A photo taken in portrait no longer shows rotated on its side
- F: The trash and settings screens now have a visible back button
- F: The folder-pick button is bigger and filled with the accent colour
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
