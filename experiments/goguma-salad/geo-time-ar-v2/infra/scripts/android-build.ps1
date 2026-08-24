$ErrorActionPreference = 'Stop'

$androidRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\app\android')
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$studioJdk = 'C:\Program Files\Android\Android Studio\jbr'

if (-not (Test-Path (Join-Path $sdkRoot 'platforms\android-36\android.jar'))) {
    throw 'Android SDK Platform 36 is not installed.'
}
if (-not (Test-Path (Join-Path $studioJdk 'bin\java.exe'))) {
    throw 'Android Studio JDK was not found.'
}

$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:JAVA_HOME = $studioJdk

Push-Location $androidRoot
try {
    & .\gradlew.bat testDebugUnitTest assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Write-Host "APK: $androidRoot\app\build\outputs\apk\debug\app-debug.apk"

