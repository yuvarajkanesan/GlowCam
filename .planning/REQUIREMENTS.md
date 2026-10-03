# Requirements (v1)

IDs are stable; the roadmap references them.

## Live beauty (BEA)
- BEA-01 Smooth skin slider (0-100), edge-preserving, skin-masked via face mesh
- BEA-02 Brighten slider (face-region lift + global exposure)
- BEA-03 Slim face slider (cheek/face-width warp)
- BEA-04 Enlarge eyes slider
- BEA-05 Reshape jaw slider (jaw/chin line)
- BEA-06 All effects run live on preview and apply identically to captured photo/video
- BEA-07 Multi-face (up to 3); graceful no-face fallback (effects off, no crash)
- BEA-08 Reset-all and hold-to-compare (before/after)

## Filters (FLT)
- FLT-01 20-30 filters: Warm, Cool, Film, Vintage, K-Beauty Soft Glow, B&W, Vivid, Fade
- FLT-02 Live preview with filter; swipe strip selector with thumbnails
- FLT-03 Per-filter intensity slider
- FLT-04 Filters defined as data (LUT or param set); adding one needs no code

## Photo editor (EDT)
- EDT-01 Crop (free, 1:1, 4:3, 3:4, 9:16, 16:9)
- EDT-02 Rotate 90 deg, straighten, flip
- EDT-03 Brightness, contrast, saturation
- EDT-04 Blur (full + radial focus) and vignette with strength sliders
- EDT-05 Apply beauty + filters to imported gallery photos
- EDT-06 Non-destructive: undo/redo, reset
- EDT-07 Edits stored as a parameter stack (re-rendered at export resolution)

## Retouch (RTC)
- RTC-01 Blemish removal: tap/brush spot heal (patch-based)
- RTC-02 Teeth whitening (mouth-landmark mask)
- RTC-03 Dark-circle removal (under-eye mask)
- RTC-04 Red-eye fix (tap, or auto from eye landmarks)
- RTC-05 Adjustable intensity on each tool

## Capture (CAP)
- CAP-01 Photo capture, front/back switch
- CAP-02 Short video (up to 60 s) with beauty + filters baked in, with audio
- CAP-03 Timer: off / 3 / 5 / 10 s with countdown overlay
- CAP-04 Touch-to-shoot: tap viewfinder to capture (toggleable; focus behaviour kept separate)
- CAP-05 Front flash: white/warm screen glow + max brightness during selfie capture
- CAP-06 Rear flash/torch toggle
- CAP-07 Grid overlay, aspect ratio selection

## Save and share (SHR)
- SHR-01 Save photo/video to gallery in high resolution (JPEG quality >= 95, full res)
- SHR-02 Direct WhatsApp, Instagram, Snapchat buttons + generic share
- SHR-03 App-not-installed fallback to system share sheet
- SHR-04 Export resolution choice (Standard / High / Original)

## Non-functional (NFR)
- NFR-01 Preview >= 30 fps on mid-range device; shutter lag < 300 ms
- NFR-02 Cold start < 2.5 s
- NFR-03 On-device processing; no network required
- NFR-04 Runtime permission flows with rationale + settings deep link
- NFR-05 App size < 80 MB
- NFR-06 Crash-free sessions >= 99.5%
