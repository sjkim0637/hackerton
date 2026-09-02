# server/ 전체 흐름 E2E 검증 실행기 (Windows).
#   사용: experiments/shinym87/interior/server 에서  .\scripts\run_e2e.ps1
#   추가 인자는 그대로 e2e_check.py 로 전달된다 (예: .\scripts\run_e2e.ps1 --no-start).
$ErrorActionPreference = "Stop"
$server = Resolve-Path (Join-Path $PSScriptRoot "..")
$py = Join-Path $server ".venv\Scripts\python.exe"
if (-not (Test-Path $py)) {
    Write-Error ".venv 가 없습니다. 먼저: py -3 -m venv .venv; .\.venv\Scripts\python.exe -m pip install -r requirements.txt"
}
Set-Location $server
& $py "scripts\e2e_check.py" @args
exit $LASTEXITCODE
