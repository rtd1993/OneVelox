# Downloads Italy POI from Overpass and writes app/src/main/assets/poi/italia.db
param(
    [string]$OutDir = "",
    [switch]$FillFailed,
    [string[]]$SplitTileIds = @()
)

$ErrorActionPreference = "Stop"
[System.Threading.Thread]::CurrentThread.CurrentCulture = [System.Globalization.CultureInfo]::InvariantCulture
[System.Threading.Thread]::CurrentThread.CurrentUICulture = [System.Globalization.CultureInfo]::InvariantCulture
$inv = [System.Globalization.CultureInfo]::InvariantCulture
$GeneratedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
$SourceDataset = "apk-italia-$GeneratedAt"

$Root = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $Root "app\src\main\assets\poi" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$Sqlite = Join-Path $PSScriptRoot "sqlite3.exe"
if (-not (Test-Path $Sqlite)) { throw "sqlite3.exe missing in tools/" }

$endpoints = @(
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.osm.ch/api/interpreter",
    "https://overpass.openstreetmap.ru/api/interpreter"
)

function Get-Tiles {
    $southLat = 36.619; $northLat = 47.092; $westLon = 6.626; $eastLon = 18.797
    $latBands = 3; $lonBands = 4
    $latStep = ($northLat - $southLat) / $latBands
    $lonStep = ($eastLon - $westLon) / $lonBands
    $tiles = @()
    for ($row = 0; $row -lt $latBands; $row++) {
        for ($col = 0; $col -lt $lonBands; $col++) {
            $s = $southLat + $row * $latStep
            $n = if ($row -eq $latBands - 1) { $northLat } else { $s + $latStep }
            $w = $westLon + $col * $lonStep
            $e = if ($col -eq $lonBands - 1) { $eastLon } else { $w + $lonStep }
            $tiles += [pscustomobject]@{
                id    = "r$($row+1)c$($col+1)"
                south = $s; west = $w; north = $n; east = $e
            }
        }
    }
    $tiles
}

function Get-Query([string]$bbox, [string]$kind = "all") {
    $nodes = @"
  node["highway"="speed_camera"]($bbox);
  node["man_made"="speed_camera"]($bbox);
  node["enforcement"="maxspeed"]($bbox);
  node["enforcement"="average_speed"]($bbox);
  node["camera:type"="speed"]($bbox);
  node["camera:type"="average_speed"]($bbox);
  node["camera:type"="red_light"]($bbox);
  node["highway"="speed_camera"]["box"="yes"]($bbox);
  node["highway"="traffic_signals"]["camera"="yes"]($bbox);
  node["highway"="traffic_signals"]["enforcement"="maxspeed"]($bbox);
  node["highway"="traffic_signals"]["enforcement"="red_light"]($bbox);
  node["traffic_sign"~"ztl|ZTL",i]($bbox);
  node["access:conditional"~"ztl|ZTL",i]($bbox);
  node["zone:traffic"="limited"]($bbox);
  node["barrier"="checkpoint"]($bbox);
  node["man_made"="surveillance"]($bbox);
  node["traffic_surveillance"]($bbox);
  node["surveillance:type"="traffic_surveillance"]($bbox);
  node["enforcement"="overtaking"]($bbox);
  node["overtaking"="no"]($bbox);
  node["highway"="busway"]($bbox);
  node["busway"]($bbox);
  node["lanes:bus"]($bbox);
"@
    $ways = @"
  way["traffic_sign"~"ztl|ZTL",i]($bbox);
  way["access:conditional"~"ztl|ZTL",i]($bbox);
  way["zone:traffic"~"ztl|restricted|limited",i]($bbox);
  way["boundary"="traffic_admin"]($bbox);
  way["boundary"="low_emission_zone"]($bbox);
  way["man_made"="surveillance"]($bbox);
  way["traffic_surveillance"]($bbox);
  way["surveillance:type"="traffic_surveillance"]($bbox);
  way["enforcement"="overtaking"]($bbox);
  way["overtaking"="no"]($bbox);
  way["highway"="busway"]($bbox);
  way["busway"]($bbox);
  way["lanes:bus"]($bbox);
"@
    $body = switch ($kind) {
        "nodes" { $nodes }
        "ways" { $ways }
        default { $nodes + $ways }
    }
    @"
[out:json][timeout:90][maxsize:1073741824];
(
$body
);
out center tags;
"@
}

function Get-Prop($obj, [string]$name) {
    if ($null -eq $obj) { return $null }
    if ($obj -is [System.Collections.IDictionary]) { return $obj[$name] }
    return $obj.$name
}

function Get-Tag($tags, [string]$key) {
    if ($null -eq $tags) { return "" }
    $v = Get-Prop $tags $key
    if ($null -eq $v) { return "" }
    return [string]$v
}

function Get-DangerType($tags) {
    $highway = (Get-Tag $tags "highway").ToLowerInvariant()
    $manMade = (Get-Tag $tags "man_made").ToLowerInvariant()
    $enforcement = (Get-Tag $tags "enforcement").ToLowerInvariant()
    $cameraType = (Get-Tag $tags "camera:type").ToLowerInvariant()
    $camera = (Get-Tag $tags "camera").ToLowerInvariant()
    $surveillanceType = (Get-Tag $tags "surveillance:type").ToLowerInvariant()
    $overtaking = (Get-Tag $tags "overtaking").ToLowerInvariant()
    $trafficSign = (Get-Tag $tags "traffic_sign").ToLowerInvariant()
    $accessConditional = (Get-Tag $tags "access:conditional").ToLowerInvariant()
    $zoneTraffic = (Get-Tag $tags "zone:traffic").ToLowerInvariant()
    $boundary = (Get-Tag $tags "boundary").ToLowerInvariant()
    $barrier = (Get-Tag $tags "barrier").ToLowerInvariant()
    $busway = (Get-Tag $tags "busway").ToLowerInvariant()
    $lanesBus = (Get-Tag $tags "lanes:bus").ToLowerInvariant()
    $box = (Get-Tag $tags "box").ToLowerInvariant()
    $hasOperator = [bool](Get-Tag $tags "operator")
    $hasTrafficSurveillance = [bool](Get-Tag $tags "traffic_surveillance")
    $name = (Get-Tag $tags "name").ToLowerInvariant()

    if ($enforcement -eq "average_speed" -or $cameraType -eq "average_speed" -or $name.Contains("tutor")) { return "TUTOR" }
    if ($box -eq "yes" -or ($highway -eq "speed_camera" -and $hasOperator)) { return "VELOBOX" }
    if ($surveillanceType -eq "traffic_surveillance" -or $hasTrafficSurveillance -or $manMade -eq "surveillance") { return "VELOOK" }
    if ($highway -eq "speed_camera" -or $manMade -eq "speed_camera" -or $enforcement -eq "maxspeed" -or $cameraType -eq "speed") { return "SPEED_CAMERA" }
    if ($cameraType -eq "red_light" -or $enforcement -eq "red_light" -or ($highway -eq "traffic_signals" -and ($camera -eq "yes" -or $enforcement -eq "maxspeed"))) { return "T_RED" }
    if ($enforcement -eq "overtaking" -or $overtaking -eq "no") { return "SURVEILLANCE" }
    if ($highway -eq "busway" -or $busway -or $lanesBus) { return "BUSWAY" }
    if (($boundary -eq "administrative" -and ($zoneTraffic.Contains("ztl") -or $zoneTraffic.Contains("restricted") -or $zoneTraffic.Contains("limited"))) -or ($boundary -eq "traffic_admin" -and $zoneTraffic)) { return "ZONE_AREA" }
    if ($trafficSign.Contains("ztl") -or $accessConditional.Contains("ztl") -or $boundary -eq "traffic_admin" -or $boundary -eq "low_emission_zone" -or $zoneTraffic -or $barrier -eq "checkpoint") { return "ZTL" }
    return $null
}

function Get-DefaultSpeed([string]$type) {
    switch ($type) {
        "SPEED_CAMERA" { 70 }
        "VELOBOX" { 70 }
        "VELOOK" { 70 }
        "TUTOR" { 90 }
        "T_RED" { 50 }
        "ZTL" { 30 }
        "ZONE_AREA" { 30 }
        "SURVEILLANCE" { 50 }
        "BUSWAY" { 30 }
        default { 40 }
    }
}

function Get-DefaultName([string]$type) {
    switch ($type) {
        "SPEED_CAMERA" { "Autovelox" }
        "VELOBOX" { "VeloBox" }
        "VELOOK" { "VeloOK" }
        "TUTOR" { "Tutor" }
        "T_RED" { "T-Red" }
        "ZTL" { "Varco ZTL" }
        "ZONE_AREA" { "Area controllata" }
        "SURVEILLANCE" { "Sorpassometro" }
        "BUSWAY" { "Corsia preferenziale" }
        default { "Pericolo" }
    }
}

function Convert-Element($el) {
    $tags = Get-Prop $el "tags"
    $type = Get-DangerType $tags
    if (-not $type) { return $null }
    $lat = Get-Prop $el "lat"
    $lon = Get-Prop $el "lon"
    if ($null -eq $lat -or $null -eq $lon) {
        $center = Get-Prop $el "center"
        $lat = Get-Prop $center "lat"
        $lon = Get-Prop $center "lon"
    }
    if ($null -eq $lat -or $null -eq $lon) { return $null }
    $osmType = [string](Get-Prop $el "type")
    $osmId = [int64](Get-Prop $el "id")
    $prefix = switch ($osmType) {
        "node" { 1000000000000 }
        "way" { 2000000000000 }
        "relation" { 3000000000000 }
        default { 9000000000000 }
    }
    $id = [math]::Abs($prefix + $osmId)
    $maxSpeed = Get-Tag $tags "maxspeed"
    $digits = ($maxSpeed -replace "[^0-9]", "")
    $speed = if ($digits) { [Math]::Min(130, [Math]::Max(20, [int]$digits)) } else { Get-DefaultSpeed $type }
    $name = Get-Tag $tags "name"
    if (-not $name) { $name = Get-DefaultName $type }
    $sideText = ((Get-Tag $tags "side") + " " + (Get-Tag $tags "placement") + " " + (Get-Tag $tags "location") + " " + (Get-Tag $tags "direction")).ToLowerInvariant()
    $side = if ($sideText.Contains("left")) { "LEFT" } elseif ($sideText.Contains("right")) { "RIGHT" } else { "MAIN" }
    $hours = @(
        (Get-Tag $tags "opening_hours"),
        (Get-Tag $tags "access:conditional"),
        (Get-Tag $tags "motor_vehicle:conditional"),
        (Get-Tag $tags "vehicle:conditional"),
        (Get-Tag $tags "hours"),
        (Get-Tag $tags "restriction:conditional")
    ) | Where-Object { $_ } | Select-Object -First 1
    $road = Get-Tag $tags "name"
    $seg = if ($type -eq "TUTOR") { 1000 } else { $null }
    return [pscustomobject]@{
        id    = $id
        name  = $name
        type  = $type
        speed = $speed
        lat   = [double]$lat
        lon   = [double]$lon
        side  = $side
        road  = $(if ($road) { $road } else { $null })
        hours = $(if ($hours) { $hours } else { $null })
        seg   = $seg
    }
}

function Invoke-Overpass([string]$query, [string]$endpoint) {
    $body = "data=" + [uri]::EscapeDataString($query)
    $resp = Invoke-WebRequest -Uri $endpoint -Method POST -Body $body -ContentType "application/x-www-form-urlencoded; charset=UTF-8" -Headers @{
        "Accept"          = "application/json"
        "Accept-Encoding" = "gzip"
        "User-Agent"      = "OneVelox/1.0 (italia-db)"
    } -TimeoutSec 95
    return [string]$resp.Content
}

function ConvertFrom-JsonSafe([string]$raw) {
    try {
        return ($raw | ConvertFrom-Json)
    } catch {
        Add-Type -AssemblyName System.Web.Extensions -ErrorAction SilentlyContinue
        $ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
        $ser.MaxJsonLength = [int]::MaxValue
        $ser.RecursionLimit = 100
        return $ser.DeserializeObject($raw)
    }
}

function Sql-Quote([string]$value) {
    if ($null -eq $value) { return "NULL" }
    return "'" + ($value -replace "'", "''") + "'"
}

function Sql-Num($value) {
    if ($null -eq $value) { return "NULL" }
    return ([double]$value).ToString("0.######", $inv)
}

function Sql-Int($value) {
    if ($null -eq $value) { return "NULL" }
    return ([int64]$value).ToString($inv)
}

function Initialize-ItaliaDb([string]$dbPath) {
    if (Test-Path $dbPath) { Remove-Item $dbPath -Force }
    $schema = @"
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
CREATE TABLE danger_points (
  id INTEGER NOT NULL,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  allowedSpeedKmh INTEGER NOT NULL,
  distanceMeters INTEGER NOT NULL,
  headingDeg REAL NOT NULL,
  side TEXT NOT NULL,
  branchRoadName TEXT,
  latitudeDeg REAL,
  longitudeDeg REAL,
  segmentEndLatitudeDeg REAL,
  segmentEndLongitudeDeg REAL,
  segmentLengthMeters INTEGER,
  restrictionSchedule TEXT,
  sourceDataset TEXT NOT NULL,
  PRIMARY KEY(id)
);
CREATE TABLE poi_meta (
  k TEXT PRIMARY KEY,
  v TEXT
);
CREATE INDEX IF NOT EXISTS index_danger_points_latitudeDeg_longitudeDeg ON danger_points(latitudeDeg, longitudeDeg);
"@
    $schema | & $Sqlite $dbPath
}

function Insert-Points([string]$dbPath, $points) {
    if (-not $points -or $points.Count -eq 0) { return 0 }
    $tmp = Join-Path $env:TEMP ("onevelox-italia-" + [guid]::NewGuid().ToString("N") + ".sql")
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $writer = New-Object System.IO.StreamWriter($tmp, $false, $utf8)
    try {
        $writer.WriteLine("BEGIN;")
        $i = 0
        foreach ($pt in $points) {
            if ($i % 200 -eq 0) {
                if ($i -gt 0) { $writer.WriteLine(";") }
                $writer.WriteLine("INSERT OR REPLACE INTO danger_points (id,name,type,allowedSpeedKmh,distanceMeters,headingDeg,side,branchRoadName,latitudeDeg,longitudeDeg,segmentEndLatitudeDeg,segmentEndLongitudeDeg,segmentLengthMeters,restrictionSchedule,sourceDataset) VALUES")
            } else {
                $writer.WriteLine(",")
            }
            $road = if ($pt.road) { Sql-Quote $pt.road } else { "NULL" }
            $hours = if ($pt.hours) { Sql-Quote $pt.hours } else { "NULL" }
            $seg = Sql-Int $pt.seg
            $line = "({0},{1},{2},{3},0,0.0,{4},{5},{6},{7},NULL,NULL,{8},{9},{10})" -f `
                (Sql-Int $pt.id), (Sql-Quote $pt.name), (Sql-Quote $pt.type), (Sql-Int $pt.speed), `
                (Sql-Quote $pt.side), $road, (Sql-Num $pt.lat), (Sql-Num $pt.lon), $seg, $hours, (Sql-Quote $SourceDataset)
            $writer.Write($line)
            $i++
        }
        if ($i -gt 0) { $writer.WriteLine(";") }
        $writer.WriteLine("COMMIT;")
    } finally {
        $writer.Dispose()
    }
    $tmpUnix = $tmp.Replace("\", "/")
    & $Sqlite $dbPath ".read $tmpUnix"
    if ($LASTEXITCODE -ne 0) { throw "sqlite3 insert failed for $tmp" }
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    return $i
}

function ConvertFrom-Overpass([string]$raw, [string]$endpoint, [switch]$AllowEmpty) {
    if (-not $raw -or $raw.TrimStart().StartsWith("<")) { throw "Risposta non JSON da $endpoint" }
    $json = ConvertFrom-JsonSafe $raw
    $remark = [string](Get-Prop $json "remark")
    if ($remark -and ($remark -match "timeout|runtime error|out of memory|too large")) {
        throw $remark
    }
    $osm3s = Get-Prop $json "osm3s"
    $tsOsm = Get-Prop $osm3s "timestamp_osm_base"
    $elements = @(Get-Prop $json "elements")
    $points = New-Object System.Collections.Generic.List[object]
    foreach ($el in $elements) {
        $pt = Convert-Element $el
        if ($pt) { [void]$points.Add($pt) }
    }
    $trusted = $endpoint -like "*overpass-api.de*"
    if ($points.Count -eq 0) {
        if (-not $AllowEmpty -or -not $trusted -or -not $tsOsm) {
            if ($remark) { throw $remark }
            throw "Tile vuoto (0 POI) da $endpoint"
        }
    }
    return [pscustomobject]@{
        points    = $points
        timestamp = $(if ($tsOsm) { [string]$tsOsm } else { $null })
    }
}

function Fetch-QueryOnce([string]$query, [string]$endpoint, [switch]$AllowEmpty) {
    $raw = Invoke-Overpass $query $endpoint
    return ConvertFrom-Overpass $raw $endpoint -AllowEmpty:$AllowEmpty
}

function Fetch-Tile($tile, [int]$epIndex, [int]$attempts, [switch]$AllowEmpty) {
    $bbox = "{0},{1},{2},{3}" -f $tile.south.ToString("0.######", $inv), $tile.west.ToString("0.######", $inv), $tile.north.ToString("0.######", $inv), $tile.east.ToString("0.######", $inv)
    $lastError = $null
    for ($attempt = 0; $attempt -lt $attempts; $attempt++) {
        $endpoint = $endpoints[$attempt % $endpoints.Count]
        try {
            Write-Host "  try $($tile.id) $bbox $endpoint"
            $parsed = Fetch-QueryOnce (Get-Query $bbox "all") $endpoint -AllowEmpty:$AllowEmpty
            return [pscustomobject]@{
                ok        = $true
                points    = $parsed.points
                timestamp = $parsed.timestamp
                error     = $null
                epIndex   = 0
            }
        } catch {
            $lastError = $_.Exception.Message
            Write-Host "  FAIL $($tile.id) all ${endpoint}: $lastError"
            Start-Sleep -Seconds (4 * ($attempt + 1))
        }
    }
    Write-Host "  fallback nodes+ways $($tile.id)"
    $collected = New-Object System.Collections.Generic.List[object]
    $ts = $null
    $nodesOk = $false
    $waysOk = $false
    try {
        $n = Fetch-QueryOnce (Get-Query $bbox "nodes") $endpoints[0] -AllowEmpty
        foreach ($p in $n.points) { [void]$collected.Add($p) }
        if ($n.timestamp) { $ts = $n.timestamp }
        $nodesOk = $true
        Write-Host "    nodes $($tile.id) +$($n.points.Count)"
    } catch {
        Write-Host "    FAIL nodes $($tile.id): $($_.Exception.Message)"
        $lastError = $_.Exception.Message
    }
    try {
        $w = Fetch-QueryOnce (Get-Query $bbox "ways") $endpoints[0] -AllowEmpty
        foreach ($p in $w.points) { [void]$collected.Add($p) }
        if ($w.timestamp) { $ts = $w.timestamp }
        $waysOk = $true
        Write-Host "    ways $($tile.id) +$($w.points.Count)"
    } catch {
        Write-Host "    FAIL ways $($tile.id): $($_.Exception.Message)"
        $lastError = $_.Exception.Message
    }
    $complete = $nodesOk -and $waysOk
    if (-not $complete -and -not $AllowEmpty -and $collected.Count -eq 0) {
        return [pscustomobject]@{ ok = $false; points = @(); timestamp = $null; error = $lastError; epIndex = 0 }
    }
    if ($complete -or $collected.Count -gt 0) {
        return [pscustomobject]@{
            ok        = $complete
            points    = $collected
            timestamp = $ts
            error     = $lastError
            epIndex   = 0
        }
    }
    return [pscustomobject]@{
        ok        = $false
        points    = @()
        timestamp = $null
        error     = $lastError
        epIndex   = 0
    }
}

function Split-Tile($tile) {
    $midLat = ($tile.south + $tile.north) / 2.0
    $midLon = ($tile.west + $tile.east) / 2.0
    @(
        [pscustomobject]@{ id = "$($tile.id)a"; south = $tile.south; west = $tile.west; north = $midLat; east = $midLon }
        [pscustomobject]@{ id = "$($tile.id)b"; south = $tile.south; west = $midLon; north = $midLat; east = $tile.east }
        [pscustomobject]@{ id = "$($tile.id)c"; south = $midLat; west = $tile.west; north = $tile.north; east = $midLon }
        [pscustomobject]@{ id = "$($tile.id)d"; south = $midLat; west = $midLon; north = $tile.north; east = $tile.east }
    )
}

function Fetch-TileRecursive([string]$dbPath, $tile, [int]$depth, [int]$maxDepth) {
    $allowEmpty = $depth -ge 3
    $attempts = if ($depth -le 1) { 2 } else { 2 }
    $result = Fetch-Tile $tile $script:epIndex $attempts -AllowEmpty:$allowEmpty
    $script:epIndex = $result.epIndex
    if ($result.points -and $result.points.Count -gt 0) {
        $n = Insert-Points $dbPath $result.points
        if ($result.timestamp) { [void]$script:timestamps.Add($result.timestamp) }
        $countNow = & $Sqlite $dbPath "SELECT COUNT(*) FROM danger_points;"
        Write-Host "  saved $($tile.id) +$n (db $countNow) ok=$($result.ok)"
    }
    if ($result.ok) {
        Write-Host "  OK $($tile.id)"
        Start-Sleep -Seconds 2
        return $true
    }
    if ($depth -ge $maxDepth) {
        Write-Host "  GIVE UP $($tile.id) after depth $depth"
        return $false
    }
    Write-Host "  SPLIT $($tile.id) depth $($depth+1)"
    $ok = $true
    foreach ($sub in (Split-Tile $tile)) {
        if (-not (Fetch-TileRecursive $dbPath $sub ($depth + 1) $maxDepth)) {
            $ok = $false
        }
    }
    return $ok
}

function Write-ItaliaMeta([string]$dbPath, [string]$metaPath, $timestamps, $failed) {
    $count = [int](& $Sqlite $dbPath "SELECT COUNT(*) FROM danger_points;")
    $ts = ($timestamps | Sort-Object -Descending | Select-Object -First 1)
    if (-not $ts) { $ts = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }
    $incomplete = ($failed.Count -gt 0)
    $failedCsv = ($failed -join ",")
    & $Sqlite $dbPath "INSERT OR REPLACE INTO poi_meta(k,v) VALUES('generatedAt','$GeneratedAt'),('remoteTimestamp','$ts'),('count','$count'),('incomplete','$incomplete'),('failedTiles','$failedCsv');"
    $utf8 = New-Object System.Text.UTF8Encoding $false
    $meta = [ordered]@{
        v               = 1
        generatedAt     = $GeneratedAt
        remoteTimestamp = $ts
        count           = $count
        incomplete      = $incomplete
        failedTiles     = @($failed)
    }
    [IO.File]::WriteAllText($metaPath, ($meta | ConvertTo-Json -Depth 4), $utf8)
    & $Sqlite $dbPath "CREATE INDEX IF NOT EXISTS index_danger_points_latitudeDeg_longitudeDeg ON danger_points(latitudeDeg, longitudeDeg);"
    Write-Host "Wrote $dbPath ($([math]::Round((Get-Item $dbPath).Length/1MB, 2)) MB) points=$count failed=$failedCsv"
    if ($count -lt 500) { throw "italia.db too small: $count POI" }
}

$dbPath = Join-Path $OutDir "italia.db"
$metaPath = Join-Path $OutDir "italia.meta.json"
$tiles = Get-Tiles
$failed = New-Object System.Collections.Generic.List[string]
$timestamps = New-Object System.Collections.Generic.List[string]
$epIndex = 0

if ($FillFailed) {
    if (-not (Test-Path $dbPath)) { throw "italia.db missing; run a full fetch first" }
    $ids = @($SplitTileIds)
    if ($ids.Count -eq 0) { $ids = @("r3c1") }
    Write-Host "FillFailed recursive split: $($ids -join ', ')"
    $existingTs = (& $Sqlite $dbPath "SELECT v FROM poi_meta WHERE k='remoteTimestamp';" | Out-String).Trim()
    if ($existingTs) { [void]$timestamps.Add($existingTs) }
    foreach ($id in $ids) {
        $parent = $tiles | Where-Object { $_.id -eq $id } | Select-Object -First 1
        if (-not $parent) { [void]$failed.Add($id); continue }
        $ok = $true
        foreach ($sub in (Split-Tile $parent)) {
            if (-not (Fetch-TileRecursive $dbPath $sub 1 5)) {
                $ok = $false
            }
        }
        if (-not $ok) { [void]$failed.Add($id) }
    }
    Write-ItaliaMeta $dbPath $metaPath $timestamps $failed
    if ($failed.Count -gt 0) { throw "italia.db still incomplete: $($failed -join ', ')" }
    return
}

Initialize-ItaliaDb $dbPath

foreach ($tile in $tiles) {
    $bbox = "{0},{1},{2},{3}" -f $tile.south.ToString("0.######", $inv), $tile.west.ToString("0.######", $inv), $tile.north.ToString("0.######", $inv), $tile.east.ToString("0.######", $inv)
    Write-Host "Fetching $($tile.id) $bbox"
    $result = Fetch-Tile $tile $epIndex 3
    $epIndex = $result.epIndex
    if ($result.ok) {
        $n = Insert-Points $dbPath $result.points
        if ($result.timestamp) { [void]$timestamps.Add($result.timestamp) }
        $countNow = & $Sqlite $dbPath "SELECT COUNT(*) FROM danger_points;"
        Write-Host "  OK $($tile.id) +$n (db $countNow)"
    } else {
        [void]$failed.Add($tile.id)
        Write-Host "  SKIP $($tile.id) after retries"
    }
    Start-Sleep -Seconds 2
}

if ($failed.Count -gt 0) {
    Write-Host "Retry failed tiles: $($failed -join ', ')"
    $retry = @($failed)
    $failed.Clear()
    Start-Sleep -Seconds 12
    foreach ($id in $retry) {
        $tile = $tiles | Where-Object { $_.id -eq $id } | Select-Object -First 1
        Write-Host "Retry $($tile.id)"
        $result = Fetch-Tile $tile $epIndex 4
        $epIndex = $result.epIndex
        if ($result.ok) {
            $n = Insert-Points $dbPath $result.points
            if ($result.timestamp) { [void]$timestamps.Add($result.timestamp) }
            $countNow = & $Sqlite $dbPath "SELECT COUNT(*) FROM danger_points;"
            Write-Host "  OK retry $($tile.id) +$n (db $countNow)"
        } else {
            [void]$failed.Add($tile.id)
            Write-Host "  FAIL retry $($tile.id)"
        }
        Start-Sleep -Seconds 5
    }
}

if ($failed.Count -gt 0) {
    Write-Host "Split remaining failed tiles: $($failed -join ', ')"
    $retry = @($failed)
    $failed.Clear()
    foreach ($id in $retry) {
        $parent = $tiles | Where-Object { $_.id -eq $id } | Select-Object -First 1
        $stillFailed = $false
        foreach ($sub in (Split-Tile $parent)) {
            Write-Host "Fetching $($sub.id)"
            $result = Fetch-Tile $sub $epIndex 3
            $epIndex = $result.epIndex
            if ($result.ok) {
                $n = Insert-Points $dbPath $result.points
                if ($result.timestamp) { [void]$timestamps.Add($result.timestamp) }
                $countNow = & $Sqlite $dbPath "SELECT COUNT(*) FROM danger_points;"
                Write-Host "  OK $($sub.id) +$n (db $countNow)"
            } else {
                $stillFailed = $true
                Write-Host "  FAIL $($sub.id)"
            }
            Start-Sleep -Seconds 3
        }
        if ($stillFailed) { [void]$failed.Add($id) }
    }
}

Write-ItaliaMeta $dbPath $metaPath $timestamps $failed
