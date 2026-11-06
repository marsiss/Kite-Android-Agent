# Start the Shizuku server over ADB and stop the system from killing it.
#
# Shizuku's server dies on every reboot and cannot restart itself without root.
# This script starts it, then whitelists the app from battery optimisation so
# the vendor ROM is less likely to kill the shell in the background.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\start-shizuku.ps1
#   powershell -ExecutionPolicy Bypass -File tools\start-shizuku.ps1 -Serial ABC123
#
# Do NOT run `adb shell am force-stop moe.shizuku.privileged.api` afterwards:
# that kills shizuku_server and you will have to run this again.

param(
    # Omit to use the only connected device, or the first one if several are attached.
    [string]$Serial
)

$ErrorActionPreference = "Stop"

# Reuse the shared adb resolver so this works on any machine.
. (Join-Path $PSScriptRoot "adb-env.ps1") | Out-Null
$adb = $env:ADB

if (-not $Serial) {
    $devices = & $adb devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '^\S+\s+device$' } |
        ForEach-Object { ($_ -split '\s+')[0] }

    if (-not $devices) { throw "No authorised device found. Connect the phone and accept the USB debugging prompt." }
    if ($devices.Count -gt 1) {
        Write-Host "Multiple devices attached; using the first. Pass -Serial to pick another."
        Write-Host ("  " + ($devices -join ", "))
    }
    $Serial = $devices[0]
}

Write-Host "Target device: $Serial"

$package = "moe.shizuku.privileged.api"
$pathLine = & $adb -s $Serial shell pm path $package
if (-not $pathLine) {
    throw "Shizuku ($package) is not installed on $Serial. Get it from https://shizuku.rikka.app/"
}

# /data/app/~~hash==/moe.shizuku.privileged.api-hash==/base.apk
#   -> /data/app/~~hash==/moe.shizuku.privileged.api-hash==
$baseApk = ($pathLine -replace '^package:', '').Trim()
$appDir = $baseApk.Substring(0, $baseApk.LastIndexOf('/'))

# Pick the ABI the device actually uses instead of assuming arm64.
$abi = (& $adb -s $Serial shell getprop ro.product.cpu.abi).Trim()
switch -Wildcard ($abi) {
    "arm64*" { $libDir = "arm64" }
    "armeabi*" { $libDir = "arm" }
    "x86_64*" { $libDir = "x86_64" }
    "x86*" { $libDir = "x86" }
    default { $libDir = "arm64" }
}

$starter = "$appDir/lib/$libDir/libshizuku.so"
Write-Host "Starting Shizuku: $starter"
& $adb -s $Serial shell $starter
Start-Sleep -Seconds 2

Write-Host "`n--- shizuku_server process ---"
& $adb -s $Serial shell "ps -A | grep shizuku_server"

# Keep the vendor ROM from freezing the shell.
& $adb -s $Serial shell "dumpsys deviceidle whitelist +$package" | Out-Null
& $adb -s $Serial shell "am set-standby-bucket $package active" | Out-Null
Write-Host "`nShizuku server started; battery whitelist and standby bucket applied."
Write-Host "Now open the Shizuku app and authorise Kite (it must be running to grant)."
