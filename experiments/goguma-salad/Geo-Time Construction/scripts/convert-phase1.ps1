param(
    [string]$ConverterPath = $env:ODA_FILE_CONVERTER
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$rawRoot = Join-Path $projectRoot 'docs\raw'
$output = Join-Path $projectRoot 'data\derived'
$targetDwg = Get-ChildItem -LiteralPath $rawRoot -Recurse -File -Filter 'ET-1101*.dwg' | Select-Object -First 1

if (-not $targetDwg) {
    throw 'ET-1101 DWG was not found below docs/raw.'
}

$rawCad = $targetDwg.DirectoryName
$sheetDwg = Get-ChildItem -LiteralPath $rawCad -File -Filter 'XR_SHEET.dwg' | Select-Object -First 1
$unitDwg = Get-ChildItem -LiteralPath $rawCad -File -Filter 'XR_*.dwg' |
    Where-Object { $_.Name -ne 'XR_SHEET.dwg' -and $_.Length -gt 7MB } |
    Select-Object -First 1

if (-not $sheetDwg -or -not $unitDwg) {
    throw 'Required XR_SHEET and unit-plan Xref DWG files were not found.'
}

$requestedConverterPath = $ConverterPath
$command = Get-Command ODAFileConverter.exe -ErrorAction SilentlyContinue
$candidatePaths = @(
    $ConverterPath,
    $(if ($command) { $command.Source }),
    $(Join-Path $env:ProgramFiles 'ODA\ODAFileConverter\ODAFileConverter.exe'),
    $(Join-Path ${env:ProgramFiles(x86)} 'ODA\ODAFileConverter\ODAFileConverter.exe'),
    $(Join-Path $env:LOCALAPPDATA 'Programs\ODA\ODAFileConverter\ODAFileConverter.exe'),
    $(Join-Path ([System.IO.Path]::GetTempPath()) 'ODAFileConverterPortable\ODAFileConverter.exe')
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

$ConverterPath = $candidatePaths | Select-Object -First 1
if (-not $ConverterPath) {
    $requestedMessage = if ($requestedConverterPath) {
        " Requested path was not found: $requestedConverterPath"
    }
    else {
        ''
    }
    throw "Install ODA File Converter or set ODA_FILE_CONVERTER to its executable path.$requestedMessage"
}

Write-Host "ODA File Converter: $ConverterPath"

$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("gtc-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
$temporaryOutput = Join-Path $temporaryRoot 'output'
New-Item -ItemType Directory -Path $temporaryOutput, $output -Force | Out-Null

try {
    @(
        @{ Input = $targetDwg.Name; Output = ([System.IO.Path]::GetFileNameWithoutExtension($targetDwg.Name) + '.dxf') },
        @{ Input = $unitDwg.Name; Output = ([System.IO.Path]::GetFileNameWithoutExtension($unitDwg.Name) + '.dxf') },
        @{ Input = $sheetDwg.Name; Output = ([System.IO.Path]::GetFileNameWithoutExtension($sheetDwg.Name) + '.dxf') }
    ) | ForEach-Object {
        & $ConverterPath $rawCad $temporaryOutput 'ACAD2018' 'DXF' '0' '0' $_.Input
        $deadline = [DateTime]::UtcNow.AddSeconds(90)
        while (@(Get-ChildItem -LiteralPath $temporaryOutput -Filter $_.Output -File).Count -lt 1) {
            if ([DateTime]::UtcNow -ge $deadline) {
                throw "DXF output $($_.Output) was not created within 90 seconds. Check the ODA File Converter error dialog."
            }
            Start-Sleep -Milliseconds 500
        }
    }

    Copy-Item -LiteralPath (Get-ChildItem -LiteralPath $temporaryOutput -Filter '*.dxf').FullName -Destination $output -Force
}
finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($temporaryRoot)
    $systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemp.StartsWith($systemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Get-ChildItem -LiteralPath $output -Filter '*.dxf' | Select-Object Name, Length
