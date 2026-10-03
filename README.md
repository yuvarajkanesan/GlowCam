# GlowCam

A live beauty camera for Android: real-time face retouching, makeup, filters, subject-aware effects, pro camera controls, photo editor, collage maker, and one-tap sharing. Fully on-device; the app has no internet permission.

- **Package:** `com.glowcam`
- **Android:** 5.0+ (API 21) up to Android 16 (API 36); phones and tablets, 64-bit and 32-bit
- **Stack:** Kotlin, Jetpack Compose, CameraX, OpenGL ES 3.0, ML Kit (face mesh + selfie segmentation, bundled models)

## Build and run

Requirements: JDK 17, Android SDK (platform 36), a phone with USB or Wireless debugging.

```
./gradlew.bat installDebug            # debug build on the connected phone
./gradlew.bat testDebugUnitTest       # unit tests
./gradlew.bat lintRelease             # Android lint
./gradlew.bat assembleRelease         # shrunk release APK (see RELEASE.md for signing)
./gradlew.bat bundleRelease           # AAB for Google Play
```

## How it works

```
CameraX preview --> SurfaceTexture --> one GLSL shader (LiveRenderer) --> screen / video encoder
CameraX analysis --> ML Kit face mesh + person mask --> shader uniforms
Photo capture / editor / export --> the same shader on a Bitmap (OfflineRenderer)
```

One shader and one parameter object (`EffectParams`) drive the live preview, saved photos, video, editor and collage, so what you see is what you save.

```
app/src/main/java/com/glowcam/
  camera/   CameraEngine (zoom, focus, HDR/Night/Pro), VideoRecorder, MediaSaver, sharing
  gl/       Shaders, EffectProgram, LiveRenderer, OfflineRenderer, FaceGeometry, Filters, Looks
  face/     FaceTracker, SubjectSegmenter (ML Kit wrappers)
  ui/       Camera, Editor, Gallery, Result, Settings, Collage screens and shared components
```

## Docs

- `RELEASE.md`: signing and publishing
- `STORE_LISTING.md`: Play Store text, data-safety answers, screenshot plan
- `PRIVACY.md`: privacy policy
- `QA_CHECKLIST.md`: manual test plan
- `.planning/`: roadmap and project state
