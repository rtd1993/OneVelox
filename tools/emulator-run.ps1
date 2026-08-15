# OneVelox emulator workflow for Cursor.
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 status
#   powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 run
#   powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 logs
#   powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 screenshot
#   powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 run -Flavor beta

param(
    [Parameter(Position = 0)]
    [ValidateSet("status", "start", "wait", "build", "install", "launch", "run", "logs", "crash", "screenshot", "stop", "geo")]
    [string]$Action = "status",

    [ValidateSet("beta", "phone", "prod")]
    [string]$Flavor = "beta",

    [string]$Avd = "OneVelox_API35",
    [string]$Serial = "",
    [int]$WaitSeconds = 180,
    [int]$LogSeconds = 8,
    [double]$Lon = 9.1900,
    [double]$Lat = 45.4642
)

$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "android-env.ps1")

$OutDir = Join-Path $env:TEMP "onevelox-emulator"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$script:DeviceSerial = $Serial

function Get-AdbArgs {
    if ($script:DeviceSerial) { return @("-s", $script:DeviceSerial) }
    return @()
}

function Invoke-Adb {
    param([string[]]$AdbArgs)
    & $script:Adb (@(Get-AdbArgs) + $AdbArgs)
}

function Get-BootedSerial {
    $lines = @(& $script:Adb "devices")
    $ready = @($lines | Where-Object { $_ -match "device$" -and $_ -notmatch "List of devices" -and $_ -notmatch "offline" -and $_ -notmatch "unauthorized" })
    if ($ready.Count -eq 0) { return $null }
    return ($ready[0] -split "\s+")[0]
}

function Wait-EmulatorBoot {
    param([int]$TimeoutSec = 180)
    Write-Host "Waiting for emulator boot (timeout ${TimeoutSec}s)..."
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $serial = Get-BootedSerial
        if ($serial) {
            $script:DeviceSerial = $serial
            $boot = (Invoke-Adb @("shell", "getprop", "sys.boot_completed") 2>$null | Out-String).Trim()
            if ($boot -eq "1") {
                Write-Host "Emulator ready: $serial"
                return $serial
            }
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Emulator did not finish booting within ${TimeoutSec}s. Check the emulator window."
}

function Start-OneVeloxEmulator {
    $existing = Get-BootedSerial
    if ($existing) {
        $script:DeviceSerial = $existing
        Write-Host "Emulator already running: $existing"
        return $existing
    }
    $listed = & $script:Emulator -list-avds
    if ($listed -notcontains $Avd) {
        throw "AVD '$Avd' not found. Available: $($listed -join ', ')"
    }
    Write-Host "Starting AVD $Avd (window stays open for visual testing)..."
    Start-Process -FilePath $script:Emulator -ArgumentList @("-avd", $Avd, "-netdelay", "none", "-netspeed", "full")
    return Wait-EmulatorBoot -TimeoutSec $WaitSeconds
}

function Invoke-Gradle {
    param([string[]]$GradleArgs)
    $wrapper = Join-Path $Root "gradlew.bat"
    Write-Host "gradlew $($GradleArgs -join ' ')"
    Push-Location $Root
    try {
        & $wrapper @GradleArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Install-OneVelox {
    $pkg = Get-OneVeloxPackageId -Flavor $Flavor
    $task = Get-OneVeloxGradleTask -Verb "install" -Flavor $Flavor
    Invoke-Gradle @($task)
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.ACCESS_FINE_LOCATION") | Out-Null
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.ACCESS_COARSE_LOCATION") | Out-Null
    Write-Host "Installed and granted location to $pkg"
}

function Launch-OneVelox {
    $pkg = Get-OneVeloxPackageId -Flavor $Flavor
    Invoke-Adb @("logcat", "-c") | Out-Null
    Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
    Invoke-Adb @("shell", "am", "start", "-n", "$pkg/com.onevelox.app.MainActivity") | Out-Host
    Start-Sleep -Seconds 3
    $appPid = ((Invoke-Adb @("shell", "pidof", $pkg)) | Out-String).Trim()
    Write-Host "Launched $pkg pid=$appPid"
    return $appPid
}

function Save-Logcat {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $pkg = Get-OneVeloxPackageId -Flavor $Flavor
    $allPath = Join-Path $OutDir "logcat-$stamp.txt"
    $errPath = Join-Path $OutDir "errors-$stamp.txt"
    $crashPath = Join-Path $OutDir "crash-$stamp.txt"

    Invoke-Adb @("logcat", "-d", "-v", "threadtime") | Out-File -FilePath $allPath -Encoding utf8
    Invoke-Adb @("logcat", "-d", "-v", "threadtime", "*:E", "AndroidRuntime:E") | Out-File -FilePath $errPath -Encoding utf8
    Invoke-Adb @("logcat", "-b", "crash", "-d", "-v", "threadtime") | Out-File -FilePath $crashPath -Encoding utf8

    $fatal = Select-String -Path $allPath -Pattern "FATAL EXCEPTION|AndroidRuntime|Process: $([regex]::Escape($pkg))" -ErrorAction SilentlyContinue
    Write-Host "Logcat: $allPath"
    Write-Host "Errors: $errPath"
    Write-Host "Crash buffer: $crashPath"
    if ($fatal) {
        Write-Host "=== FATAL / runtime hits ===" -ForegroundColor Red
        $fatal | Select-Object -First 40 | ForEach-Object { Write-Host $_.Line }
    } else {
        Write-Host "No FATAL EXCEPTION found for $pkg"
    }
    return $errPath
}

function Save-Screenshot {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $remote = "/sdcard/onevelox-screen.png"
    $local = Join-Path $OutDir "screen-$stamp.png"
    Invoke-Adb @("shell", "screencap", "-p", $remote) | Out-Null
    Invoke-Adb @("pull", $remote, $local) | Out-Host
    Write-Host "Screenshot: $local"
    return $local
}

switch ($Action) {
    "status" {
        Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
        Write-Host "JAVA_HOME=$env:JAVA_HOME"
        Write-Host "AVD default=$Avd"
        Write-Host "Flavor=$Flavor package=$(Get-OneVeloxPackageId -Flavor $Flavor)"
        Write-Host ""
        Write-Host "AVDs:"
        & $script:Emulator -list-avds
        Write-Host ""
        Write-Host "Devices:"
        & $script:Adb devices -l
        $javaVer = & "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Select-Object -First 1
        Write-Host "Java: $javaVer"
    }
    "start" { Start-OneVeloxEmulator | Out-Null }
    "wait" { Wait-EmulatorBoot -TimeoutSec $WaitSeconds | Out-Null }
    "build" {
        $task = Get-OneVeloxGradleTask -Verb "assemble" -Flavor $Flavor
        Invoke-Gradle @($task)
    }
    "install" {
        Wait-EmulatorBoot -TimeoutSec $WaitSeconds | Out-Null
        Install-OneVelox
    }
    "launch" {
        Wait-EmulatorBoot -TimeoutSec $WaitSeconds | Out-Null
        Launch-OneVelox | Out-Null
        Start-Sleep -Seconds $LogSeconds
        Save-Logcat | Out-Null
        Save-Screenshot | Out-Null
    }
    "run" {
        Start-OneVeloxEmulator | Out-Null
        Install-OneVelox
        Launch-OneVelox | Out-Null
        Start-Sleep -Seconds $LogSeconds
        Save-Logcat | Out-Null
        Save-Screenshot | Out-Null
        Write-Host ""
        Write-Host "Ready. Emulator window is for visual testing; logs/screenshots are in $OutDir"
    }
    "logs" { Save-Logcat | Out-Null }
    "crash" {
        $path = Join-Path $OutDir "crash-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
        Invoke-Adb @("logcat", "-b", "crash", "-d", "-v", "threadtime") | Out-File -FilePath $path -Encoding utf8
        Write-Host "Crash buffer: $path"
        Get-Content $path
    }
    "screenshot" { Save-Screenshot | Out-Null }
    "stop" {
        Write-Host "Killing emulator..."
        Invoke-Adb @("emu", "kill") 2>$null
        Get-Process -Name qemu-system-x86_64, emulator -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    }
    "geo" {
        Wait-EmulatorBoot -TimeoutSec $WaitSeconds | Out-Null
        Invoke-Adb @("emu", "geo", "fix", "$Lon", "$Lat")
        Write-Host "Set geo fix lon=$Lon lat=$Lat"
    }
}
