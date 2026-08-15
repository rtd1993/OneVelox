# Aggiorna app/src/main/assets/poi/italia.db da OpenStreetMap (Overpass).
# Uso:
#   powershell -ExecutionPolicy Bypass -File tools/aggiorna-italia-db.ps1
#   oppure doppio clic su aggiorna-italia-db.bat
param(
    [switch]$FillOnly
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Fetch = Join-Path $PSScriptRoot "fetch-italia-db.ps1"
$OutDir = Join-Path $Root "app\src\main\assets\poi"
$DbPath = Join-Path $OutDir "italia.db"
$MetaPath = Join-Path $OutDir "italia.meta.json"
$LogDir = Join-Path $env:TEMP "onevelox-italia-update"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$LogPath = Join-Path $LogDir "aggiorna-$stamp.log"

function Write-Log([string]$message) {
    $line = "[{0:HH:mm:ss}] {1}" -f (Get-Date), $message
    Write-Host $line
    Add-Content -Path $LogPath -Value $line -Encoding UTF8
}

function Read-Meta([string]$path) {
    if (-not (Test-Path $path)) { return $null }
    return Get-Content -Path $path -Raw -Encoding UTF8 | ConvertFrom-Json
}

if (-not (Test-Path $Fetch)) { throw "Manca $Fetch" }

Write-Host ""
Write-Host "OneVelox - aggiornamento italia.db"
Write-Host "Log: $LogPath"
Write-Host "Destinazione: $DbPath"
Write-Host "Puo richiedere 10-40 minuti (Overpass a volte va in timeout)."
Write-Host ""

$oldMeta = Read-Meta $MetaPath
if ($oldMeta) {
    Write-Log ("Snapshot attuale: {0} POI, OSM {1}, incomplete={2}" -f $oldMeta.count, $oldMeta.remoteTimestamp, $oldMeta.incomplete)
}

if ((Test-Path $DbPath) -and -not $FillOnly) {
    $bak = Join-Path $OutDir "italia.db.bak"
    Copy-Item $DbPath $bak -Force
    Copy-Item $DbPath (Join-Path $LogDir "italia-$stamp.bak") -Force
    if (Test-Path $MetaPath) {
        Copy-Item $MetaPath (Join-Path $OutDir "italia.meta.json.bak") -Force
    }
    Write-Log "Backup: $bak"
}

$sw = [Diagnostics.Stopwatch]::StartNew()
Start-Transcript -Path $LogPath -Append | Out-Null
try {
    if ($FillOnly) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Fetch -FillFailed
    } else {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Fetch
    }
    $fetchExit = $LASTEXITCODE
} finally {
    Stop-Transcript | Out-Null
    $sw.Stop()
}

$meta = Read-Meta $MetaPath
if ($meta -and $meta.incomplete -eq $true -and -not $FillOnly) {
    Write-Log "Alcuni tile sono incompleti. Riprovo i mancanti..."
    Start-Transcript -Path $LogPath -Append | Out-Null
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Fetch -FillFailed
        $fetchExit = $LASTEXITCODE
    } finally {
        Stop-Transcript | Out-Null
    }
    $meta = Read-Meta $MetaPath
}

Write-Host ""
if ($meta) {
    $mb = if (Test-Path $DbPath) { [math]::Round((Get-Item $DbPath).Length / 1MB, 2) } else { 0 }
    Write-Log ("Fatto in {0:n1} min" -f $sw.Elapsed.TotalMinutes)
    Write-Log ("POI: {0}  |  OSM: {1}  |  generatedAt: {2}" -f $meta.count, $meta.remoteTimestamp, $meta.generatedAt)
    Write-Log ("File: {0} ({1} MB)" -f $DbPath, $mb)
    if ($meta.incomplete) {
        $failed = @($meta.failedTiles) -join ", "
        Write-Log "ATTENZIONE: snapshot ancora parziale. Tile: $failed"
        Write-Log "Rilancia: powershell -ExecutionPolicy Bypass -File tools/aggiorna-italia-db.ps1 -FillOnly"
        exit 1
    }
    Write-Log "italia.db completo. Per metterlo nell'APK: .\gradlew.bat :app:assemblePhoneRelease"
    exit 0
}

Write-Log "Aggiornamento fallito (exit $fetchExit). Vedi il log."
if ((Test-Path (Join-Path $OutDir "italia.db.bak")) -and -not (Test-Path $DbPath)) {
    Copy-Item (Join-Path $OutDir "italia.db.bak") $DbPath -Force
    Write-Log "Ripristinato italia.db.bak"
}
exit 1
