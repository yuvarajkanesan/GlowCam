# GlowCam

A YouCam-style beauty camera for mobile. Live face retouching, filters, a photo editor, retouch tools, and one-tap sharing.

## Core value
Selfies that look good *live in the viewfinder* (not just after the shot), saved in high resolution and shared in one tap.

## Target users
Mobile-first social users (selfie-heavy, WhatsApp / Instagram / Snapchat), Android only (no iOS).

## MVP (v1) scope
1. **Live beauty camera**: skin smoothing, brighten, face slim, eye enlarge, jaw reshape. One slider per effect.
2. **Filters**: 20-30 live filters (warm, film, vintage, K-beauty soft glow, B&W, ...).
3. **Photo editor**: crop, rotate, adjust (brightness / contrast / saturation), blur, vignette.
4. **Retouch tools**: blemish removal, teeth whitening, dark-circle removal, red-eye fix.
5. **Capture modes**: photo, short video, timer, touch-to-shoot, front flash (screen glow).
6. **Save and share**: high-res export; share to WhatsApp, Instagram, Snapchat.

## Out of scope for v1
Accounts/cloud, AR stickers/makeup try-on, AI generative edits, makeup looks, body editing, collage, in-app purchases, analytics backend. Revisit for v2.

## Key decisions
Stack mirrors typical native Android apps (the WA Status Saver style: Kotlin, MediaStore, share intents). Not React Native, not Expo, not Flutter.

| Decision | Choice | Rationale |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose (single-activity, Navigation-Compose) | Standard modern native stack |
| Camera | CameraX (Preview, ImageCapture, VideoCapture, ImageAnalysis) | Lifecycle-aware, photo + video, flash, focus, front/back |
| GPU rendering | OpenGL ES 3.0 (GLSurfaceView / custom renderer) with GLSL shaders | Realtime filters, smoothing, warps; same shaders reused for export |
| Face landmarks | ML Kit Face Mesh (468 pts, bundled model, no download) + One-Euro smoothing | On-device mesh for warp, teeth, eyes, under-eye masks |
| Video with effects | OpenGL -> MediaCodec encoder (Surface input) + MediaMuxer | Bakes effects into video |
| Editor | Same GL pipeline on a Bitmap; crop/rotate with Compose UI | WYSIWYG with camera |
| Save | MediaStore (Pictures/GlowCam, Movies/GlowCam), scoped storage | No broad storage permission on API 29+ |
| Share | Intent ACTION_SEND + FileProvider; setPackage com.whatsapp / com.instagram.android / com.snapchat.android; chooser fallback | Direct targets |
| DI / state | Hilt (or manual DI), ViewModel + StateFlow | Standard |
| Build | Gradle Kotlin DSL, version catalog, minSdk 26, targetSdk 35 | |
| Privacy | 100% on-device processing, no upload | |

## Platform
Android 8.0+ (API 26), arm64. Distribution: Play Store / APK. Save to `Pictures/GlowCam` and `Movies/GlowCam` via MediaStore; in-app Saved gallery.

## Constraints
- Live preview >= 30 fps on mid-range Android.
- Photo export at full sensor resolution (12 MP+), same look as preview.
- Works offline.
- Camera, microphone, and photo-library permissions handled gracefully.
