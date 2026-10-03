# Releasing GlowCam

## Build

```
./gradlew.bat assembleRelease      # APK  -> app/build/outputs/apk/release/app-release.apk
./gradlew.bat bundleRelease        # AAB  -> app/build/outputs/bundle/release/app-release.aab (for Google Play)
```

The release build uses R8 (code shrinking) and resource shrinking. Current size: about 46 MB. The app targets Android 16 (API 36), which Google Play requires for new apps and updates, and its native libraries are 16 KB page aligned.

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
