# GlowCam QA checklist

Run this on the release build (`app-release.apk`) before every store submission. Test on at least: one Samsung flagship, one mid-range phone (4 GB RAM), one low-end phone, and one with Android 8 or 9.

Mark each: PASS / FAIL / N/A.

## First launch and permissions
- [ ] Fresh install shows the friendly camera-permission screen
- [ ] Deny camera: screen offers "Allow camera" and "Open app settings"; both work
- [ ] Allow camera: live preview appears within 2 seconds
- [ ] Switching to Video asks for the microphone; deny it and record: video saves without sound and a message says so
- [ ] Settings > Location tag: asks for permission; deny leaves the switch off

## Camera
- [ ] Preview is upright and not stretched on the rear and front cameras, in 4:3, 16:9 and 1:1
- [ ] Selfie preview is mirrored by default; Settings > Mirror selfie off un-mirrors preview and saved photo
- [ ] Switch camera works repeatedly (20 times) without a freeze
- [ ] Pinch zoom and the 0.6x / 1x / 2x / 3x / 5x / 10x buttons work
- [ ] Tap to focus shows the ring and brightness slider; dragging the slider changes brightness
- [ ] Long-press locks focus and exposure; tapping unlocks
- [ ] Flash (rear) fires; screen glow (front) lights the screen and restores brightness afterwards
- [ ] Timer 3 / 5 / 10 s counts down and can be cancelled by tapping the shutter
- [ ] Touch-to-shoot (Settings or Capture tab) takes a photo on tap
- [ ] Volume keys take photos on the camera screen only (not in Gallery or Editor)
- [ ] HDR, Night and Pro mode change the image; Pro ISO, shutter and white balance respond
- [ ] Sharp mode: a "Hold steady" hint appears while shaking; saved photos are sharp
- [ ] Original quality (rear): file opens normally in Gallery and is not mirrored or rotated
- [ ] Grid styles, level line (turns green when straight) and histogram work

## Live effects
- [ ] Each Beauty slider changes the face live; no visible jitter; reset works; Compare shows the original
- [ ] Makeup: lipstick, blush, eyebrows and eye shadow sit on the right places and follow the face
- [ ] Looks apply in one tap; "Save look" stores a named look that survives an app restart
- [ ] Filters: all 26+ show real previews; Premium subject filters cut out the person cleanly
- [ ] Background replace (solid, gradient, blur) works live
- [ ] Two or three faces in frame: effects apply to all
- [ ] No face in frame: no crash, a "Looking for a face" hint appears
- [ ] Effects stay smooth (about 30 fps) on the mid-range phone

## Saving
- [ ] Photo saves to Pictures/GlowCam at full resolution with the effects baked in
- [ ] Video saves to Movies/GlowCam, plays back with sound and effects, in 720p, 1080p and 4K, at 30 and 60 fps
- [ ] Recording stops cleanly when the app is sent to the background, and the file plays
- [ ] A 60 second video stops automatically
- [ ] Storage nearly full: a clear error message, no crash

## Gallery and results
- [ ] Thumbnail on the camera screen opens the Gallery; all saved items appear, newest first
- [ ] Long-press selects; select all, share several, delete several (with confirmation)
- [ ] Tapping an item opens the result screen; Share, Edit and Delete work
- [ ] Three-dot menu: Share to WhatsApp / Instagram / Snapchat (or a clear message when not installed), More apps, Open in Gallery, Set as wallpaper, Details, Delete
- [ ] Deleting the last photo clears the thumbnail on the camera screen

## Editor
- [ ] Import a photo from the library: opens in the editor
- [ ] "Edit with GlowCam" works from another app's share sheet and "Open with"
- [ ] Beauty, Makeup, Looks, Background, Filters, Adjust, Retouch, Text and Crop tools all work
- [ ] Crop (free and ratios), rotate 90, flip, straighten
- [ ] Blemish tap removes a spot; teeth, dark circles and red-eye respond
- [ ] Text and stickers: add, drag, pinch to resize and rotate, delete; they appear in the saved photo
- [ ] Undo and redo step back through changes; Reset all clears everything
- [ ] Save at Standard, High and Original sizes; result matches the preview
- [ ] Very large photo (50 MP+) opens and saves without crashing

## Collage
- [ ] Pick 2, 3, 4, 5 and 6 photos; each offers layouts; spacing, corners, shape and background change the preview
- [ ] Saved collage matches the preview

## Settings and polish
- [ ] Every setting persists after killing and reopening the app
- [ ] Privacy policy dialog opens; crash report row says "No crashes recorded"
- [ ] Back button and back gesture behave on every screen
- [ ] Rotate the phone: the app stays portrait, nothing breaks
- [ ] TalkBack announces the buttons sensibly
- [ ] Large font size (Settings > Display): text is not cut off
- [ ] Dark and light system themes both look right
- [ ] 15 minutes of continuous use: the phone does not get uncomfortably hot, memory stays stable

## Release readiness
- [ ] `./gradlew.bat lintRelease testDebugUnitTest` pass
- [ ] Release APK is signed with the real key (`apksigner verify --print-certs app-release.apk`)
- [ ] Version code and name are bumped
- [ ] Play pre-launch report shows no crashes
