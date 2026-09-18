# video-mid API smoke test (mock mode, backend on 8090)
$Base = if ($env:VIDEO_MID_URL) { $env:VIDEO_MID_URL } else { "http://127.0.0.1:8090" }
$ErrorActionPreference = "Stop"

Write-Host "=== video-mid API test Base=$Base ==="

Write-Host "POST /api/auth/login"
$loginBody = @{ username = "admin"; password = "admin123" } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$Base/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $login.data.token
if (-not $token) { throw "login failed, no token" }
Write-Host "  login OK"
$headers = @{ Authorization = "Bearer $token" }

function Test-Get($path, $name) {
    Write-Host "GET $path ($name)"
    $r = Invoke-RestMethod -Uri "$Base$path" -Method Get -Headers $headers
    if ($null -eq $r) { throw "$name empty response" }
    Write-Host "  OK"
    return $r
}

Test-Get "/actuator/health" "health" | Out-Null

$gb = Test-Get "/api/gb28181/config" "gb28181 config"
Write-Host "  dataSource=$($gb.data.dataSource)"

Test-Get "/api/gb28181/status" "gb28181 status" | Out-Null
Test-Get "/api/gb28181/catalog" "gb28181 catalog" | Out-Null
$sync = Test-Get "/api/gb28181/sync-check" "gb28181 sync-check"
Write-Host "  sync ok=$($sync.data.ok)"

Test-Get "/api/uniview/config" "uniview config" | Out-Null
Test-Get "/api/uniview/devices" "uniview devices" | Out-Null
Test-Get "/api/uniview/ptz/devices" "uniview ptz devices" | Out-Null
Test-Get "/api/uniview/live/readiness" "uniview live readiness" | Out-Null

$catalog = Invoke-RestMethod -Uri "$Base/api/gb28181/catalog" -Method Get -Headers $headers
$ch = $catalog.data[0].channels[0].channelId
Write-Host "POST invite channel=$ch"
$invite = Invoke-RestMethod -Uri "$Base/api/gb28181/channels/$ch/invite" -Method Post -Headers $headers
if (-not $invite.data.playUrl) { throw "invite missing playUrl" }
Write-Host "  playUrl=$($invite.data.playUrl)"

Write-Host "=== ALL PASSED ==="
