$ErrorActionPreference = 'Stop'

$baseUrl = if ($env:GEO_TIME_API_URL) { $env:GEO_TIME_API_URL.TrimEnd('/') } else { 'http://localhost:8000' }
$zoneId = '00000000-0000-4000-8000-000000000101'

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$health = $null
for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        $health = Invoke-RestMethod "$baseUrl/health/ready" -TimeoutSec 3
        break
    } catch {
        if ($attempt -eq 30) { throw }
        Start-Sleep -Seconds 2
    }
}
Assert-True ($health.status -eq 'ready') 'Backend readiness failed'

$nearby = (Invoke-WebRequest "$baseUrl/geozones/nearby?latitude=37.5648801960179&longitude=126.991228638001&radius_m=1000" -UseBasicParsing).Content | ConvertFrom-Json
Assert-True ($nearby.Count -ge 1) 'No nearby GeoZone returned'
Assert-True ($nearby[0].id -eq $zoneId) 'Unexpected nearest GeoZone'

$timeline = (Invoke-WebRequest "$baseUrl/geozones/$zoneId/timeline?limit=20" -UseBasicParsing).Content | ConvertFrom-Json
Assert-True ($timeline.Count -eq 8) "Expected 8 seeded Moments, got $($timeline.Count)"

$campaigns = (Invoke-WebRequest "$baseUrl/geozones/$zoneId/campaigns/active" -UseBasicParsing).Content | ConvertFrom-Json
Assert-True ($campaigns.Count -eq 1) 'Expected one active Campaign'

$candidates = (Invoke-WebRequest "$baseUrl/geozones/$zoneId/content-candidates?moment_window_minutes=5256000" -UseBasicParsing).Content | ConvertFrom-Json
Assert-True ($candidates.Count -ge 9) 'Expected Moment and Campaign candidate set'

$visibilityBody = @{
    camera_position = @{ x = 0; y = 0; z = 0 }
    camera_forward = @{ x = 0; y = 0; z = -1 }
    candidates = @(
        @{
            id = '00000000-0000-4000-8000-000000000001'
            position = @{ x = 0; y = 1; z = -5 }
            max_distance_m = 20
            view_cone_degrees = 70
        },
        @{
            id = '00000000-0000-4000-8000-000000000002'
            position = @{ x = 0; y = 0; z = 5 }
            max_distance_m = 20
            view_cone_degrees = 70
        }
    )
} | ConvertTo-Json -Depth 6
$visible = Invoke-RestMethod "$baseUrl/spatial/select-visible" -Method Post -ContentType 'application/json' -Body $visibilityBody
Assert-True (@($visible.visible).Count -eq 1) '6DoF visibility selection returned an unexpected result'

$asset = Invoke-WebRequest 'http://localhost:9000/geo-time-assets/demo/placeholder.svg' -UseBasicParsing
Assert-True ($asset.StatusCode -eq 200) 'Seed asset is not readable from MinIO'

Write-Host 'Geo-Time AR smoke test passed'
Write-Host "  nearby zones: $($nearby.Count)"
Write-Host "  timeline moments: $($timeline.Count)"
Write-Host "  active campaigns: $($campaigns.Count)"
Write-Host "  candidates: $($candidates.Count)"
Write-Host "  visible in test pose: $(@($visible.visible).Count)"
