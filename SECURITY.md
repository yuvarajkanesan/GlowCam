# GlowCam security notes

Last reviewed: 2026-10-03

## Design
- **No network.** The app has no INTERNET permission (library-added ones are stripped in the manifest), so it cannot send data anywhere. Cleartext traffic is disabled explicitly as well.
- **No accounts, secrets or keys** in the code (scanned).
- **Backup off:** `allowBackup=false` plus data-extraction rules that exclude everything from cloud backup and device transfer.
- **Not debuggable** in release builds.
- **Minimal permissions:** camera, microphone, optional location, and storage only on Android 9 and older.

## Attack surface
- One exported component: `MainActivity`, needed so other apps can send a photo to GlowCam (VIEW / SEND). Only `content://` images are accepted; `file://` and other schemes are ignored.
- Images from other apps are decoded with a size limit and guarded against out-of-memory.
- No WebView, no JavaScript, no SQL, no custom IPC, no FileProvider; sharing uses MediaStore content URIs with a read-only grant.
- Saved looks and settings are plain app-private preferences with no sensitive data.

## Build hardening
- R8 shrinking, obfuscation and resource shrinking on release (`-PminifyRelease=false` turns it off for debugging only). Verbose, debug and info logging is stripped from release.
- **Gradle wrapper pinned by SHA-256** and **dependency verification** (`gradle/verification-metadata.xml`): a modified or swapped library fails the build. After adding or updating a dependency, regenerate with:
  `./gradlew --write-verification-metadata sha256 assembleRelease bundleRelease lintRelease testDebugUnitTest`
- Release is signed with v1, v2 and v3 schemes using a 4096-bit RSA key. Keystore and passwords are git-ignored.
- Android lint runs with 0 errors; targets Android 16 (API 36); native libraries are 16 KB aligned.

## Not covered by this review
- No external penetration test and no automated CVE database scan of dependencies was run here. Before each release, also check the Google Play pre-launch report, and consider running OWASP dependency-check or Dependabot on the repository.
- The ML Kit libraries are third-party closed-source code; their usage reporting is blocked because the app has no network permission.

## Reporting a problem
Add your security contact email here before publishing.
