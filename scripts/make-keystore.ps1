# Creates the release signing key for GlowCam and the keystore.properties file the build reads.
# Run this yourself in PowerShell (it asks you to choose the passwords):
#   powershell -ExecutionPolicy Bypass -File scripts\make-keystore.ps1
#
# BACK UP glowcam-release.jks AND YOUR PASSWORDS. If you lose them you cannot update the app on Google Play.

$root = Split-Path -Parent $PSScriptRoot
$jks = Join-Path $root "glowcam-release.jks"

if (Test-Path $jks) {
    Write-Host "glowcam-release.jks already exists. Not overwriting it."
    exit 1
}

$store = Read-Host "Choose a keystore password (min 6 characters)" -AsSecureString
$key = Read-Host "Choose a key password (can be the same)" -AsSecureString
$sp = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($store))
$kp = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($key))

& keytool -genkeypair -v -keystore $jks -alias glowcam -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass $sp -keypass $kp -dname "CN=GlowCam, O=Your Name or Company, C=IN"

if ($LASTEXITCODE -ne 0) { Write-Host "keytool failed"; exit 1 }

@"
storeFile=glowcam-release.jks
storePassword=$sp
keyAlias=glowcam
keyPassword=$kp
"@ | Set-Content -Path (Join-Path $root "keystore.properties") -Encoding ascii

Write-Host ""
Write-Host "Done. Created glowcam-release.jks and keystore.properties (both are git-ignored)."
Write-Host "Now back them up somewhere safe, then run: gradlew.bat bundleRelease"
