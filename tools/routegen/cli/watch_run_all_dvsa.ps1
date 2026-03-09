# Auto-generated monitor for run_all_dvsa
$repo = 'C:\Users\ferro\MapboxDrivest'
Set-Location $repo
$log = 'C:\Users\ferro\MapboxDrivest\tools\routegen\output\run_all_dvsa_live.log'
Write-Host "Showing last log lines and current batch status."
if (Test-Path $log) {
    Get-Content -Path $log -Tail 50
} else {
    Write-Host "Log not found: $log"
}
python tools/routegen/cli/check_batch_status.py
