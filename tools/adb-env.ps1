# Put Android platform-tools on PATH for the current PowerShell session.
# Dot-source it:  . .\tools\adb-env.ps1
#
# Resolution order:
#   1. $env:ADB                      (explicit override)
#   2. $env:ANDROID_HOME             (standard)
#   3. $env:ANDROID_SDK_ROOT         (legacy)
#   4. sdk.dir in <repo>/local.properties
#   5. %LOCALAPPDATA%\Android\Sdk    (Android Studio default on Windows)
#   6. adb already on PATH

$ErrorActionPreference = "Stop"

function Resolve-Adb {
    param([string]$RepoRoot)

    if ($env:ADB -and (Test-Path $env:ADB)) { return $env:ADB }

    $roots = @()
    if ($env:ANDROID_HOME)    { $roots += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $roots += $env:ANDROID_SDK_ROOT }

    $localProps = Join-Path $RepoRoot "local.properties"
    if (Test-Path $localProps) {
        $line = Select-String -Path $localProps -Pattern '^\s*sdk\.dir\s*=' | Select-Object -First 1
        if ($line) {
            $dir = ($line.Line -split '=', 2)[1].Trim()
            # local.properties uses ':' and '\' interchangeably; normalise for Windows.
            $dir = $dir -replace '\\\\', '\'
            if ($dir) { $roots += $dir }
        }
    }

    if ($env:LOCALAPPDATA) { $roots += (Join-Path $env:LOCALAPPDATA "Android\Sdk") }

    foreach ($root in $roots) {
        $candidate = Join-Path $root "platform-tools\adb.exe"
        if (Test-Path $candidate) { return $candidate }
        $candidate = Join-Path $root "platform-tools/adb"   # macOS / Linux
        if (Test-Path $candidate) { return $candidate }
    }

    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    return $null
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = Resolve-Adb -RepoRoot $repoRoot

if (-not $adb) {
    throw @"
adb not found.

Set one of these and retry:
  `$env:ADB           = 'C:\path\to\platform-tools\adb.exe'
  `$env:ANDROID_HOME  = 'C:\path\to\android-sdk'
or put  sdk.dir=C:\path\to\android-sdk  in local.properties
"@
}

$env:ADB = $adb
$adbDir = Split-Path -Parent $adb
if (($env:PATH -split [IO.Path]::PathSeparator) -notcontains $adbDir) {
    $env:PATH = "$adbDir$([IO.Path]::PathSeparator)$env:PATH"
}

Write-Host "adb ready: $env:ADB"
& $env:ADB version
