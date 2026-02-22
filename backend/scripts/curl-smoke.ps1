$ErrorActionPreference = "Stop"
$backendDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$proc = Start-Process -FilePath "node" -ArgumentList "src/server.js" -WorkingDirectory $backendDir -PassThru
try {
    Start-Sleep -Seconds 2

    Write-Output "GET /centres"
    curl.exe -s "http://127.0.0.1:8080/centres"
    Write-Output ""

    Write-Output "GET /centres/colchester/routes"
    curl.exe -s "http://127.0.0.1:8080/centres/colchester/routes"
    Write-Output ""

    Write-Output "GET /centres/colchester/hazards"
    curl.exe -s "http://127.0.0.1:8080/centres/colchester/hazards"
    Write-Output ""

    Write-Output "GET /hazards/route (dynamic route-scoped hazards)"
    curl.exe -s "http://127.0.0.1:8080/hazards/route?south=51.878&west=0.922&north=51.883&east=0.929&types=TRAFFIC_SIGNAL,ROUNDABOUT,SCHOOL_ZONE,BUS_STOP&centreId=colchester"
    Write-Output ""

    Write-Output "GET /config"
    curl.exe -s "http://127.0.0.1:8080/config"
    Write-Output ""

    Write-Output "POST /telemetry"
    $payloadFile = Join-Path $backendDir ".tmp_telemetry_payload.json"
    '{"event_type":"session_summary","centre_id":"colchester","route_id":"colchester-hythe-town-loop","organisation_id":"org_default","stress_index":42,"complexity_score":55,"confidence_score":61,"off_route_count":1,"completion_flag":true,"payload_json":{"source":"curl","pack_versions":{"routes":"routes-v1","hazards":"hazards-v1"}}}' | Set-Content -Path $payloadFile -Encoding ascii
    curl.exe -s -X POST "http://127.0.0.1:8080/telemetry" -H "Content-Type: application/json" --data-binary "@$payloadFile"
    Remove-Item -Path $payloadFile -Force -ErrorAction SilentlyContinue
    Write-Output ""

    Write-Output "POST /instructor/session"
    $instructorPayloadFile = Join-Path $backendDir ".tmp_instructor_payload.json"
    '{"organisationId":"org_default","centreId":"colchester","routeId":"colchester-hythe-town-loop","stressIndex":49,"offRouteCount":1,"hazardCounts":{"roundabout":3,"trafficSignal":2},"payload":{"source":"curl"}}' | Set-Content -Path $instructorPayloadFile -Encoding ascii
    curl.exe -s -X POST "http://127.0.0.1:8080/instructor/session" -H "Content-Type: application/json" --data-binary "@$instructorPayloadFile"
    Remove-Item -Path $instructorPayloadFile -Force -ErrorAction SilentlyContinue
    Write-Output ""

    Write-Output "GET /analytics/centre/colchester"
    curl.exe -s "http://127.0.0.1:8080/analytics/centre/colchester?page=1&pageSize=25"
    Write-Output ""

    Write-Output "GET /organisation/org_default/stats"
    curl.exe -s "http://127.0.0.1:8080/organisation/org_default/stats"
    Write-Output ""
}
finally {
    if ($proc -and !$proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
    }
}
