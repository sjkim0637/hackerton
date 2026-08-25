param(
    [switch]$SkipBuild,
    [switch]$BindOnly
)

$ErrorActionPreference = 'Stop'
$utf8Encoding = [System.Text.UTF8Encoding]::new()
[Console]::OutputEncoding = $utf8Encoding
$OutputEncoding = $utf8Encoding

$experimentRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$apkPath = Join-Path $experimentRoot 'app\android\app\build\outputs\apk\debug\app-debug.apk'
$buildScript = Join-Path $PSScriptRoot 'android-build.ps1'
$sdkAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adbPath = if ($adbCommand) {
    $adbCommand.Source
} elseif (Test-Path -LiteralPath $sdkAdb) {
    $sdkAdb
} else {
    throw 'ADB was not found. Install Android SDK Platform Tools.'
}

function Invoke-Adb {
    param([string[]]$Arguments)

    & $adbPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: adb $($Arguments -join ' ')"
    }
}

if ($BindOnly) {
    Write-Host '[1/3] Binding only; build and install will be skipped.'
} elseif (-not $SkipBuild) {
    Write-Host '[1/5] Running Android unit tests and debug APK build.'
    & $buildScript
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host '[1/5] Using the existing debug APK.'
}

if (-not $BindOnly -and -not (Test-Path -LiteralPath $apkPath)) {
    throw "Debug APK was not found. Run the full build task first: $apkPath"
}

$deviceStep = if ($BindOnly) { '[2/3]' } else { '[2/5]' }
Write-Host "$deviceStep Checking connected Android devices."
Invoke-Adb -Arguments @('start-server')
$connectedDevices = @(
    & $adbPath devices |
        Select-String -Pattern '^([^\s]+)\s+device$' |
        ForEach-Object { $_.Matches[0].Groups[1].Value }
)

$requestedSerial = $env:ANDROID_SERIAL
if ($requestedSerial) {
    if ($requestedSerial -notin $connectedDevices) {
        throw "ANDROID_SERIAL '$requestedSerial' is not connected."
    }
    $serial = $requestedSerial
} elseif ($connectedDevices.Count -eq 1) {
    $serial = $connectedDevices[0]
} elseif ($connectedDevices.Count -eq 0) {
    throw 'No Android device is ready. Check USB connection and USB debugging authorization.'
} else {
    throw "Multiple Android devices are connected. Set `$env:ANDROID_SERIAL='<serial>' and retry: $($connectedDevices -join ', ')"
}

$bindingStep = if ($BindOnly) { '[3/3]' } else { '[3/5]' }
Write-Host "$bindingStep Binding device ports 8000 (API) and 9000 (Media) to the development services: $serial"
Invoke-Adb -Arguments @('-s', $serial, 'reverse', 'tcp:8000', 'tcp:8000')
Invoke-Adb -Arguments @('-s', $serial, 'reverse', 'tcp:9000', 'tcp:9000')

if ($BindOnly) {
    Write-Host "Done: API and Media bindings are ready on $serial."
    exit 0
}

Write-Host '[4/5] Installing the debug APK with replacement enabled.'
Invoke-Adb -Arguments @('-s', $serial, 'install', '-r', $apkPath)

Write-Host '[5/5] Restarting the Geo-Time AR app.'
Invoke-Adb -Arguments @('-s', $serial, 'shell', 'am', 'force-stop', 'com.geotime.ar')
Invoke-Adb -Arguments @('-s', $serial, 'shell', 'am', 'start', '-n', 'com.geotime.ar/.MainActivity')

Write-Host "Done: APK installed and app started on $serial."
