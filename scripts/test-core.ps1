param(
    [string]$ShuttleDirectory = (Join-Path $PSScriptRoot '../../shuttle'),
    [string]$Maven,
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet('dev', 'release')]
    [string]$CargoProfile = 'dev',
    [switch]$SkipShuttleBuild
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
if ($JavaHome) {
    if ((Split-Path $JavaHome -Leaf) -eq 'bin') { $JavaHome = Split-Path $JavaHome -Parent }
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'JavaHome must point to a JDK directory.' }
    $env:JAVA_HOME = $JavaHome
}
if (-not $SkipShuttleBuild) {
    if (-not $Maven) {
        $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($command) { $Maven = $command.Source }
        else { $Maven = Join-Path $env:LOCALAPPDATA 'Programs/IntelliJ IDEA/plugins/maven-plugin/lib/maven3/bin/mvn.cmd' }
    }
    if (-not (Test-Path -LiteralPath $Maven)) { throw 'Maven not found. Pass -Maven with the path to mvn.cmd.' }
    $shuttleRoot = (Resolve-Path -LiteralPath $ShuttleDirectory).Path
    $cargoOutputFolder = if ($CargoProfile -eq 'release') { 'release' } else { 'debug' }
    & $Maven -f (Join-Path $shuttleRoot 'pom.xml') -pl shuttle-java -am install '-Dspotbugs.skip=true' "-Dcargo.profile=$CargoProfile" "-Dcargo.profile.folder=$cargoOutputFolder"
    if ($LASTEXITCODE -ne 0) { throw 'Shuttle build or tests failed.' }
}
Push-Location $repoRoot
try {
    & './gradlew.bat' ':common:test' ':architecture-shuttle:test' ':emulator:test' ':1.21.1-common:test' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw 'RC core tests failed.' }
} finally { Pop-Location }
