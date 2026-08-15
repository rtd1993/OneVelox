# OneVelox Android toolchain for Cursor / PowerShell.
# Dot-source before gradle/adb/emulator:  . .\tools\android-env.ps1

$ErrorActionPreference = "Continue"

$script:AndroidSdk = "C:\Users\rtd19\AppData\Local\Android\Sdk"
$script:JbrHome = "C:\Program Files\Android\Android Studio\jbr"
$script:DefaultAvd = "OneVelox_API35"

if (-not (Test-Path $script:AndroidSdk)) {
    throw "Android SDK not found at $script:AndroidSdk"
}
if (-not (Test-Path $script:JbrHome)) {
    throw "Android Studio JBR not found at $script:JbrHome. System Java 8 cannot build this project."
}

$env:ANDROID_HOME = $script:AndroidSdk
$env:ANDROID_SDK_ROOT = $script:AndroidSdk
$env:JAVA_HOME = $script:JbrHome

$toolBins = @(
    (Join-Path $script:JbrHome "bin"),
    (Join-Path $script:AndroidSdk "platform-tools"),
    (Join-Path $script:AndroidSdk "emulator"),
    (Join-Path $script:AndroidSdk "cmdline-tools\latest\bin")
)
foreach ($bin in $toolBins) {
    if ((Test-Path $bin) -and ($env:Path -notlike "*$bin*")) {
        $env:Path = "$bin;$env:Path"
    }
}

$script:Adb = Join-Path $script:AndroidSdk "platform-tools\adb.exe"
$script:Emulator = Join-Path $script:AndroidSdk "emulator\emulator.exe"

function Get-OneVeloxPackageId {
    param([ValidateSet("beta", "phone", "prod")][string]$Flavor = "beta")
    switch ($Flavor) {
        "beta" { "com.onevelox.app.beta" }
        "phone" { "com.onevelox.app.phone" }
        "prod" { "com.onevelox.app" }
    }
}

function Get-OneVeloxGradleTask {
    param(
        [ValidateSet("assemble", "install")][string]$Verb,
        [ValidateSet("beta", "phone", "prod")][string]$Flavor = "beta"
    )
    $cap = $Flavor.Substring(0, 1).ToUpper() + $Flavor.Substring(1)
    return ":app:${Verb}${cap}Debug"
}
