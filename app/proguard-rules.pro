# GlowCam release rules.
# CameraX, Compose and ML Kit ship their own consumer rules. Keep stack traces readable for the local crash log.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Camera2 interop is accessed through annotated experimental APIs.
-dontwarn androidx.camera.camera2.interop.**

# Remove debug / verbose / info logging from release builds (warnings and errors stay for the crash log).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
