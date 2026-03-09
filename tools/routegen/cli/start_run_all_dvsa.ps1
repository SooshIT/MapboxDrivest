# Auto-generated launcher for run_all_dvsa
$repo = 'C:\Users\ferro\MapboxDrivest'
Set-Location $repo
$null = New-Item -ItemType Directory -Force -Path (Join-Path $repo 'tools\routegen\output')
$log = 'C:\Users\ferro\MapboxDrivest\tools\routegen\output\run_all_dvsa_live.log'
$cmd = "Set-Location '$repo'; python tools/routegen/cli/run_all_dvsa.py --resume *>> '$log' 2>&1"
Start-Process -FilePath 'powershell' -WorkingDirectory $repo -ArgumentList @(
    '-NoProfile',
    '-ExecutionPolicy','Bypass',
    '-Command',
    $cmd
)
Write-Host "Batch started. Log file: $log"
