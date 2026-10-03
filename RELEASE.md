# Releasing GlowCam

## Build

```
./gradlew.bat assembleRelease      # APK  -> app/build/outputs/apk/release/app-release.apk
./gradlew.bat bundleRelease        # AAB  -> app/build/outputs/bundle/release/app-release.aab (for Google Play)
```

The release build uses R8 (code shrinking) and resource shrinking. Current size: about 46 MB. The app targets Android 16 (API 36), which Google Play requires for new apps and updates, and its native libraries are 16 KB page aligned.

## Current release (1.0.0)

- Signed with `glowcam-release.jks` (project root, git-ignored) using the passwords in `keystore.properties` (git-ignored). **Back both files up outside this computer.**
- Output copies live in `release-artifacts/1.0.0/`: `GlowCam-1.0.0.apk` (sideload / phone), `GlowCam-1.0.0.aab` (Google Play), `mapping.txt` (to decode crash traces; keep it for every released version).
- Recommended for Play: enrol in **Play App Signing**. Google then keeps the real app-signing key and this keystore becomes only your upload key, which Google can reset if you lose it.
- To publish: create the app in Play Console, upload the `.aab`, complete the listing (`STORE_LISTING.md`) and data-safety form, then roll out to closed testing first.
- Switch minification off only to debug a release-only problem: `./gradlew.bat assembleRelease -PminifyRelease=false`.

## Signing (do this once before publishing)

Without a keystore the release build is signed with the debug key, which is fine for testing but **not accepted by Google Play**.

1. Easiest: run `powershell -ExecutionPolicy Bypass -File scripts\make-keystore.ps1` (it asks you for the passwords and writes `keystore.properties`). Or do it by hand. Keep the file and passwords safe; losing them means you cannot update the app:

   ```
   keytool -genkeypair -v -keystore glowcam-release.jks -alias glowcam -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties` in the project root (same folder as `settings.gradle.kts`). Never commit it:

   ```
   storeFile=glowcam-release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=glowcam
   keyPassword=YOUR_KEY_PASSWORD
   ```

3. Rebuild. The build picks the keystore up automatically.

## Before uploading to Google Play

- Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- Publish `PRIVACY.md` at a public URL and enter it in the Play Console (Data safety form: no data collected or shared).
- Prepare store listing assets: 512x512 icon, feature graphic (1024x500), at least 2 phone screenshots.
- Content rating questionnaire; target audience: general.
- Test the release APK on a few devices (low-end, mid-range, with and without a front camera flash).

## Known limitations

- Portrait only.
- English only for now (strings are in code; moving them to resources is needed for Tamil/Hindi).
- Samsung's 200 MP mode is not available to third-party apps; GlowCam saves the camera's standard full-resolution JPEG.
- 4K / 60 fps video with beauty effects is demanding; 1080p 30 fps is the safe default.

## Device and Android version support

- **Android versions:** 5.0 (API 21) to 16 (API 36). minSdk 21 is the lowest that CameraX, Compose and ML Kit support.
- **Android 9 and older** ask for the storage permission to save into Pictures / Movies (Android 10+ needs none). On Android 7 and older, video is written through a file path instead of a file descriptor.
- **Hardware needed:** a camera and OpenGL ES 3.0 (almost every phone since 2014). Front camera, autofocus, microphone and gyroscope are optional.
- **Weak phones** (under about 2.5 GB RAM, low-RAM flag, or a 4-core CPU with under 4 GB) get half the shader samples, face analysis on every third frame, smaller photo limits and 720p video by default.
- **Old cameras** that cannot run preview + capture + face analysis together keep working without face effects.
- **GPUs without GL_OES_EGL_image_external_essl3** use a small GLSL ES 1.0 pre-pass (`OesPrepass`). The switch `LiveRenderer.FORCE_PREPASS` forces it on for testing.
- Shader uniform use is about 190 vectors, under the 224 that OpenGL ES 3.0 guarantees.
- Android 16 (API 36) opt-out keeps the portrait lock on large screens. A real tablet layout is future work.
