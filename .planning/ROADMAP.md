# Roadmap: GlowCam v1

Progress (2026-10-03): phases 1-7 implemented in a first pass; see STATE.md for what is verified on device.

Each phase ends with a demoable build on a real device.

## Phase 1: Foundation and camera shell
Goal: app boots, shows live camera, captures a photo, saves to gallery.
- Native Android project (Kotlin, Compose, CameraX), Gradle Kotlin DSL, CI build
- CameraX preview, front/back switch, permissions flow
- Photo capture + basic save (CAP-01, SHR-01 basic), grid + aspect ratio (CAP-07)
- Done when: photo saved from the app on Android

## Phase 2: Realtime GPU pipeline and filters
Goal: preview rendered through OpenGL ES shaders; filters work live.
- CameraX frame -> GL texture pipeline; capture reuses the same shader chain at full res
- Filter engine (LUT/param data) + 24-30 filters, strip UI, intensity (FLT-01..04)
- Prototype the video-encode path here (de-risk Phase 4)
- Done when: 30 fps with a filter on a mid-range device; captured photo matches preview
- Reqs: FLT-*, BEA-06 (pipeline), NFR-01

## Phase 3: Face landmarks and beauty effects
Goal: live beauty sliders.
- MediaPipe face landmarker plugin; One-Euro smoothing against jitter
- Skin smooth + brighten with skin mask (BEA-01, 02)
- Warp shader: slim face, enlarge eyes, jaw (BEA-03..05)
- Multi-face, no-face fallback, reset, hold-to-compare (BEA-07, 08)
- Done when: five sliders live, no visible jitter, 30 fps with one face

## Phase 4: Capture modes (parallel with 5 after Phase 3)
Goal: full capture feature set.
- Timer + countdown, touch-to-shoot, tap-to-focus (CAP-03, 04)
- Front screen-glow flash (CAP-05), rear torch (CAP-06)
- Short video with effects baked in + audio (CAP-02)
- Done when: 60 s video with beauty + filter plays back correctly

## Phase 5: Photo editor
Goal: edit any photo, captured or imported.
- Editor screen, parameter stack, undo/redo (EDT-06, 07)
- Crop, rotate, flip, straighten (EDT-01, 02)
- Brightness/contrast/saturation, blur, vignette (EDT-03, 04)
- Gallery import with beauty/filters (EDT-05)

## Phase 6: Retouch tools
Goal: targeted fixes.
- Teeth whiten, dark circles, red-eye via landmark masks (RTC-02..04)
- Blemish heal brush (RTC-01), intensity controls (RTC-05)
- Landmarks run on still images

## Phase 7: Save, share, polish, release
Goal: shippable v1.
- High-res export + resolution choice (SHR-01, 04)
- Direct WhatsApp / Instagram / Snapchat + fallback (SHR-02, 03)
- Perf, thermal, battery tuning; crash reporting (NFR-*)
- Icon, onboarding, privacy policy, store listing, Play internal testing beta

## Coverage
| Area | Phase |
|---|---|
| CAP-01, 07 | 1 |
| FLT, BEA-06 | 2 |
| BEA | 3 |
| CAP-02..06 | 4 |
| EDT | 5 |
| RTC | 6 |
| SHR, NFR final | 7 |

## Top risks
1. Realtime face warp perf on low-end Android: lower-res landmark pass, quality tiers, early device testing.
2. Video with baked-in effects: prototype the encode path in Phase 2.
3. Instagram/Snapchat share APIs change: keep system share fallback.
4. Landmark jitter: temporal smoothing tuned early.
