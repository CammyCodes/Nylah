# Installs the built Nylah jar into both CurseForge instances.
#
# Each instance gets the jar for ITS Minecraft line, read from the instance's
# minecraftinstance.json (26.1.2 -> nylah-<version>+26.1.jar, 26.2 -> +26.2).
# Build the lines you need first:  ./gradlew build   and   ./gradlew build -Pmc=26.2
#
# Guards, per instance (the same ones Nethermine's delivery uses):
#   1. Never copy into a RUNNING game. A jar replaced under a running game can
#      corrupt that session ("ZipFile invalid LOC header"), so a running
#      instance is detected by the --gameDir on its java command line and waits.
#   2. Never downgrade: if a newer nylah jar is installed, refuse.
#   3. Never leave two nylah jars (a duplicate mod id stops Fabric booting):
#      old ones are removed first, and the copy only happens if that worked.
#   4. Verify by hash after copying.
#
# Waits up to four hours for a running instance to close, retrying every 15s.
# Usage:  powershell -File deliver.ps1

$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot
$instances = @(
    "C:\Users\Cammy\curseforge\minecraft\Instances\Fabulously Optimized (1)\mods",
    "C:\Users\Cammy\curseforge\minecraft\Instances\Fabulously Optimized 2\mods"
)

$version = (Get-Content (Join-Path $repo 'gradle.properties') |
    Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
if (-not $version) { Write-Host 'Could not read mod_version'; exit 1 }

# Matches every Nylah jar ever shipped: nylah-1.0.1.jar and nylah-1.0.2+26.2.jar.
$pattern = '^nylah-\d+\.\d+\.\d+(\+[\d.]+)?\.jar$'

# The built jar for an instance's Minecraft line, or $null if there is none.
function Get-Build([string]$mods) {
    $info = Join-Path (Split-Path -Parent $mods) 'minecraftinstance.json'
    try { $game = (Get-Content $info -Raw | ConvertFrom-Json).gameVersion } catch { $game = $null }
    if (-not ($game -match '^(\d+\.\d+)')) { Write-Host "Cannot read the Minecraft version of $(Split-Path -Parent $mods)"; return $null }
    $line = $Matches[1]
    $name = "nylah-$version+$line.jar"
    $path = Join-Path $repo "build\libs\$name"
    if (-not (Test-Path $path)) {
        Write-Host "No $name in build\libs for Minecraft $game (${mods})"
        return $null
    }
    return @{ Name = $name; Path = $path; Hash = (Get-FileHash $path -Algorithm SHA256).Hash }
}

function Test-InstanceRunning([string]$mods) {
    $instanceDir = Split-Path -Parent $mods
    try {
        $procs = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction Stop
    } catch {
        return $false
    }
    foreach ($p in $procs) {
        $cl = $p.CommandLine
        if ($cl -and $cl -match '--gameDir' -and $cl.IndexOf($instanceDir, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }
    return $false
}

function Get-Ver([string]$name) {
    if ($name -match 'nylah-(\d+)\.(\d+)\.(\d+)') {
        return [version]("{0}.{1}.{2}" -f $Matches[1], $Matches[2], $Matches[3])
    }
    return [version]'0.0.0'
}
$mine = [version]$version

# 0 = delivered, 2 = waiting, 3 = refused
$state = @{}
foreach ($mods in $instances) { $state[$mods] = 2 }
$said = @{}

for ($pass = 0; $pass -lt 960; $pass++) {
    foreach ($mods in $instances) {
        if ($state[$mods] -ne 2) { continue }
        if (-not (Test-Path $mods)) { Write-Host "No mods folder: $mods"; $state[$mods] = 3; continue }
        $build = Get-Build $mods
        if (-not $build) { $state[$mods] = 3; continue }
        $targetName = $build.Name
        $installed = Get-ChildItem $mods -Filter '*.jar' | Where-Object { $_.Name -match $pattern }
        $newer = $installed | Where-Object { (Get-Ver $_.Name) -gt $mine }
        if ($newer) {
            Write-Host "REFUSING ${mods}: a newer Nylah is installed ($($newer.Name -join ', '))"
            $state[$mods] = 3
            continue
        }
        $same = $installed | Where-Object { $_.Name -eq $targetName }
        if ($same -and (Get-FileHash $same.FullName -Algorithm SHA256).Hash -eq $build.Hash -and $installed.Count -eq 1) {
            Write-Host "Already delivered: $mods\$targetName"
            $state[$mods] = 0
            continue
        }
        if (Test-InstanceRunning $mods) {
            if (-not $said[$mods]) { Write-Host "Waiting: Minecraft is running from $(Split-Path -Parent $mods)"; $said[$mods] = $true }
            continue
        }
        try {
            $installed | ForEach-Object { Remove-Item $_.FullName -Force -ErrorAction Stop }
            $target = Join-Path $mods $targetName
            Copy-Item $build.Path $target -Force -ErrorAction Stop
            if ((Get-FileHash $target -Algorithm SHA256).Hash -ne $build.Hash) { throw "hash mismatch after copy" }
            Write-Host "Delivered $targetName -> $mods"
            $state[$mods] = 0
        } catch {
            # Locked or half-done: try again next pass.
        }
    }
    if (-not ($state.Values -contains 2)) { break }
    Start-Sleep -Seconds 15
}

$bad = $state.GetEnumerator() | Where-Object { $_.Value -ne 0 }
if ($bad) { exit 2 }
exit 0
