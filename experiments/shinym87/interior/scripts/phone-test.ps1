[CmdletBinding()]
param(
    [ValidateSet('doctor', 'server-setup', 'server', 'build', 'install', 'reverse', 'launch', 'all', 'logcat')]
    [string]$Action = 'all'
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$env:PYTHONUTF8 = '1'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ServerRoot = Join-Path $ProjectRoot 'server'
$ApkPath = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$PackageName = 'com.hackathon.interior'
$ActivityName = "$PackageName/.HomeActivity"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Get-AdbPath {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { $_ }
    foreach ($root in $sdkRoots) {
        $candidate = Join-Path $root 'platform-tools\adb.exe'
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw 'adb was not found. Install Android SDK Platform-Tools and set PATH or ANDROID_SDK_ROOT.'
}

function Get-ConnectedDevice([string]$Adb) {
    $rows = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' })
    $authorized = @($rows | Where-Object { $_ -match '^([^\s]+)\s+device$' })
    $unauthorized = @($rows | Where-Object { $_ -match '\s+unauthorized$' })

    if ($unauthorized.Count -gt 0) {
        throw 'USB debugging is unauthorized. Accept the RSA prompt on the phone and retry.'
    }
    if ($authorized.Count -eq 0) {
        throw 'No Android device is connected. Enable USB debugging and reconnect the cable.'
    }
    if ($authorized.Count -gt 1) {
        throw 'Multiple Android devices are connected. Leave only the test device connected.'
    }

    return ($authorized[0] -split '\s+')[0]
}

function Get-JavaMajor([string]$JavaHome) {
    $java = Join-Path $JavaHome 'bin\java.exe'
    $javac = Join-Path $JavaHome 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) {
        return $null
    }

    $releaseFile = Join-Path $JavaHome 'release'
    if (-not (Test-Path -LiteralPath $releaseFile)) {
        return $null
    }
    $versionText = Get-Content -LiteralPath $releaseFile -Raw
    if ($versionText -match 'JAVA_VERSION="(?:1\.)?(\d+)') {
        return [int]$Matches[1]
    }
    return $null
}

function Find-CompatibleJdk {
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($jdkHome in @($env:INTERIOR_JAVA_HOME, $env:JAVA_HOME)) {
        if ($jdkHome) { $candidates.Add($jdkHome) }
    }

    foreach ($base in @(
        (Join-Path $env:USERPROFILE '.jdks'),
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java'
    )) {
        if (Test-Path -LiteralPath $base) {
            Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { $candidates.Add($_.FullName) }
        }
    }

    $androidJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $androidJbr) { $candidates.Add($androidJbr) }

    foreach ($jdkHome in $candidates | Select-Object -Unique) {
        $major = Get-JavaMajor $jdkHome
        if ($major -ge 17 -and $major -le 21) {
            return @{ Home = $jdkHome; Major = $major }
        }
    }

    throw @'
JDK 17 or 21 was not found. A JRE alone cannot build the Android app.
Recommended: winget install EclipseAdoptium.Temurin.21.JDK
Then restart VS Code or set INTERIOR_JAVA_HOME to the JDK directory.
'@
}

function Get-Python311 {
    $venvPython = Join-Path $ServerRoot '.venv\Scripts\python.exe'
    if (Test-Path -LiteralPath $venvPython) {
        return @{ Path = $venvPython; Args = @() }
    }

    $launcher = Get-Command py -ErrorAction SilentlyContinue
    if ($launcher) {
        & $launcher.Source -3.11 -c 'import sys; assert sys.version_info[:2] == (3, 11)' 2>$null
        if ($LASTEXITCODE -eq 0) {
            return @{ Path = $launcher.Source; Args = @('-3.11') }
        }
    }

    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        & $python.Source -c 'import sys; assert sys.version_info >= (3, 11)' 2>$null
        if ($LASTEXITCODE -eq 0) {
            return @{ Path = $python.Source; Args = @() }
        }
    }

    throw 'Python 3.11+ was not found. Install Python 3.11 or register it with the py launcher.'
}

function Invoke-Build {
    $jdk = Find-CompatibleJdk
    $env:JAVA_HOME = $jdk.Home
    Write-Step "Build debug APK (JDK $($jdk.Major): $($jdk.Home))"
    Push-Location $ProjectRoot
    try {
        & '.\gradlew.bat' ':app:assembleDebug' '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

function Invoke-Install([string]$Adb, [string]$Serial) {
    if (-not (Test-Path -LiteralPath $ApkPath)) {
        throw "APK not found. Run 'Interior: APK build' first: $ApkPath"
    }
    Write-Step "Install APK ($Serial)"
    & $Adb -s $Serial install -r $ApkPath
    if ($LASTEXITCODE -ne 0) { throw "APK install failed (exit $LASTEXITCODE)" }
}

function Invoke-Reverse([string]$Adb, [string]$Serial) {
    Write-Step 'USB port forwarding: phone 127.0.0.1:8000 -> PC 127.0.0.1:8000'
    & $Adb -s $Serial reverse tcp:8000 tcp:8000
    if ($LASTEXITCODE -ne 0) { throw "adb reverse failed (exit $LASTEXITCODE)" }
}

function Invoke-Launch([string]$Adb, [string]$Serial) {
    Write-Step 'Launch Interior app'
    & $Adb -s $Serial shell am force-stop $PackageName
    & $Adb -s $Serial shell am start -n $ActivityName
    if ($LASTEXITCODE -ne 0) { throw "App launch failed (exit $LASTEXITCODE)" }
    Write-Host 'The Settings screen defaults to http://127.0.0.1:8000 for adb reverse.' -ForegroundColor Yellow
}

function Invoke-ServerSetup {
    $python = Get-Python311
    $venvPython = Join-Path $ServerRoot '.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $venvPython)) {
        Write-Step 'Create Python virtual environment'
        & $python.Path @($python.Args) -m venv (Join-Path $ServerRoot '.venv')
        if ($LASTEXITCODE -ne 0) { throw "Virtual environment creation failed (exit $LASTEXITCODE)" }
    }

    Write-Step 'Install server dependencies'
    & $venvPython -m pip install -r (Join-Path $ServerRoot 'requirements.txt')
    if ($LASTEXITCODE -ne 0) { throw "Server dependency installation failed (exit $LASTEXITCODE)" }
}

function Invoke-Server {
    $venvPython = Join-Path $ServerRoot '.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $venvPython)) {
        throw "Server virtual environment not found. Run the 'Interior: server setup' task first."
    }

    & $venvPython -c 'import fastapi, uvicorn' 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Server packages are missing. Run the 'Interior: server setup' task first."
    }

    Write-Step 'Starting server: http://0.0.0.0:8000'
    Push-Location $ServerRoot
    try {
        & $venvPython -m uvicorn app.main:app --host 0.0.0.0 --port 8000
        if ($LASTEXITCODE -ne 0) { throw "Server stopped (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

switch ($Action) {
    'doctor' {
        Write-Step 'Check development environment'
        $adb = Get-AdbPath
        Write-Host "adb: $adb"
        try {
            $serial = Get-ConnectedDevice $adb
            Write-Host "Android device: $serial" -ForegroundColor Green
        } catch {
            Write-Warning $_.Exception.Message
        }
        try {
            $jdk = Find-CompatibleJdk
            Write-Host "JDK: $($jdk.Major) ($($jdk.Home))" -ForegroundColor Green
        } catch {
            Write-Warning $_.Exception.Message
        }
        try {
            $python = Get-Python311
            Write-Host "Python: $($python.Path) $($python.Args -join ' ')" -ForegroundColor Green
        } catch {
            Write-Warning $_.Exception.Message
        }
    }
    'server-setup' { Invoke-ServerSetup }
    'server' { Invoke-Server }
    'build' { Invoke-Build }
    'install' {
        $adb = Get-AdbPath
        $serial = Get-ConnectedDevice $adb
        Invoke-Install $adb $serial
    }
    'reverse' {
        $adb = Get-AdbPath
        $serial = Get-ConnectedDevice $adb
        Invoke-Reverse $adb $serial
    }
    'launch' {
        $adb = Get-AdbPath
        $serial = Get-ConnectedDevice $adb
        Invoke-Launch $adb $serial
    }
    'all' {
        Invoke-Build
        $adb = Get-AdbPath
        $serial = Get-ConnectedDevice $adb
        Invoke-Install $adb $serial
        Invoke-Reverse $adb $serial
        Invoke-Launch $adb $serial
    }
    'logcat' {
        $adb = Get-AdbPath
        $serial = Get-ConnectedDevice $adb
        $pid = (& $adb -s $serial shell pidof $PackageName).Trim()
        if (-not $pid) {
            throw "The app is not running. Run the 'Interior: launch app' task first."
        }
        Write-Step "Start app Logcat (PID $pid, Ctrl+C to stop)"
        & $adb -s $serial logcat --pid=$pid
    }
}
