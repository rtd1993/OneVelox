# OneVelox - diagnostica conteggio POI Overpass Italia
# Uso: powershell -ExecutionPolicy Bypass -File tools/poi_diagnostic.ps1

$ErrorActionPreference = "Continue"
$endpoint = "https://overpass-api.de/api/interpreter"
$headers = @{
    "Content-Type" = "application/x-www-form-urlencoded; charset=UTF-8"
    "Accept"         = "application/json"
    "User-Agent"     = "OneVelox-Diagnostic/1.0"
}

function Invoke-OverpassQuery {
    param([string]$Query, [string]$Label)
    $body = "data=$([uri]::EscapeDataString($Query))"
    try {
        $resp = Invoke-WebRequest -Uri $endpoint -Method Post -Body $body -Headers $headers -TimeoutSec 120 -UseBasicParsing
        $json = $resp.Content | ConvertFrom-Json
        if ($json.remark) {
            Write-Host ("  {0,-22} ERRORE: {1}" -f $Label, $json.remark)
            return 0
        }
        $count = @($json.elements).Count
        Write-Host ("  {0,-22} {1,6} elementi" -f $Label, $count)
        return $count
    } catch {
        Write-Host ("  {0,-22} ERRORE: {1}" -f $Label, $_.Exception.Message)
        return 0
    }
}

Write-Host ""
Write-Host "=== OneVelox POI Diagnostic ===" -f Cyan
Write-Host "Endpoint: $endpoint"
Write-Host ""

Write-Host "1) Query monolitica app (vecchia, va in timeout):" -ForegroundColor Yellow
$legacy = @"
[out:json][timeout:120][maxsize:1073741824];
area["ISO3166-1"="IT"][admin_level=2]->.it;
(
  node["highway"="speed_camera"](area.it);
  node["man_made"="speed_camera"](area.it);
  node["enforcement"="maxspeed"](area.it);
);
out ids;
"@
$legacyCount = Invoke-OverpassQuery -Query $legacy -Label "legacy speed nodes"

Write-Host ""
Write-Host "2) Query nodi Italia (bbox semplificato):" -ForegroundColor Yellow
$simple = @"
[out:json][timeout:90];
(
  node["highway"="speed_camera"](36.6,6.6,47.1,18.8);
  node["man_made"="speed_camera"](36.6,6.6,47.1,18.8);
  node["enforcement"="maxspeed"](36.6,6.6,47.1,18.8);
  node["enforcement"="average_speed"](36.6,6.6,47.1,18.8);
  node["camera:type"="speed"](36.6,6.6,47.1,18.8);
  node["camera:type"="red_light"](36.6,6.6,47.1,18.8);
);
out ids;
"@
$simpleCount = Invoke-OverpassQuery -Query $simple -Label "nodi bbox IT"

Write-Host ""
Write-Host "3) Strategia tile 3x4 (nuova app):" -ForegroundColor Green
$tiles = @(
    @{ id = "r1c1"; s = 36.619; w = 6.626; n = 40.110; e = 9.669 },
    @{ id = "r1c2"; s = 36.619; w = 9.669; n = 40.110; e = 12.712 },
    @{ id = "r1c3"; s = 36.619; w = 12.712; n = 40.110; e = 15.755 },
    @{ id = "r1c4"; s = 36.619; w = 15.755; n = 40.110; e = 18.797 },
    @{ id = "r2c1"; s = 40.110; w = 6.626; n = 43.601; e = 9.669 },
    @{ id = "r2c2"; s = 40.110; w = 9.669; n = 43.601; e = 12.712 },
    @{ id = "r2c3"; s = 40.110; w = 12.712; n = 43.601; e = 15.755 },
    @{ id = "r2c4"; s = 40.110; w = 15.755; n = 43.601; e = 18.797 },
    @{ id = "r3c1"; s = 43.601; w = 6.626; n = 47.092; e = 9.669 },
    @{ id = "r3c2"; s = 43.601; w = 9.669; n = 47.092; e = 12.712 },
    @{ id = "r3c3"; s = 43.601; w = 12.712; n = 47.092; e = 15.755 },
    @{ id = "r3c4"; s = 43.601; w = 15.755; n = 47.092; e = 18.797 }
)

$tileTotal = 0
foreach ($tile in $tiles) {
    $bbox = "$($tile.s),$($tile.w),$($tile.n),$($tile.e)"
    $q = @"
[out:json][timeout:55];
(
  node["highway"="speed_camera"]($bbox);
  node["man_made"="speed_camera"]($bbox);
  node["enforcement"="maxspeed"]($bbox);
  node["enforcement"="average_speed"]($bbox);
  node["camera:type"="speed"]($bbox);
  node["camera:type"="red_light"]($bbox);
);
out ids;
"@
    $c = Invoke-OverpassQuery -Query $q -Label $tile.id
    $tileTotal += $c
    Start-Sleep -Milliseconds 600
}

Write-Host ""
Write-Host "=== RIEPILOGO ===" -ForegroundColor Cyan
Write-Host "  Legacy monolitica : $legacyCount (spesso timeout -> pochi POI in app)"
Write-Host "  Nodi bbox unico   : $simpleCount"
Write-Host "  Somma tile 3x4    : $tileTotal (stima POI speed importabili)"
Write-Host ""
Write-Host "Nota: l'app importa anche ZTL, tutor, sorpassometri e corsie bus."
Write-Host "628 POI = tipico risultato parziale da timeout/rate-limit Overpass."
Write-Host ""
