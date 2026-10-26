param(
    [string]$Boot,
    [switch]$RustExample,
    [string]$Screenshot,
    [int]$Ticks = 200
)
$ErrorActionPreference = 'Stop'
$previousTarget = $env:CARGO_TARGET_DIR
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if ($env:JAVA_HOME -and (Split-Path $env:JAVA_HOME -Leaf) -eq 'bin') {
        $env:JAVA_HOME = Split-Path $env:JAVA_HOME -Parent
    }
    if ($RustExample) {
        $env:CARGO_TARGET_DIR = Join-Path $root 'build/cargo'
        & cargo build --manifest-path guests/examples/screen/Cargo.toml --target wasm32-unknown-unknown --release --locked --offline
        if ($LASTEXITCODE -ne 0) { throw 'Rust guest build failed (install rustup target add wasm32-unknown-unknown first).' }
        $Boot = Join-Path $env:CARGO_TARGET_DIR 'wasm32-unknown-unknown/release/rc_screen_guest.wasm'
    }
    $arguments = @()
    if ($Boot) { $arguments += '--boot'; $arguments += '"' + $Boot + '"' }
    if ($Screenshot) { $arguments += '--screenshot'; $arguments += '"' + $Screenshot + '"'; $arguments += '--ticks'; $arguments += $Ticks }
    & ./gradlew.bat :emulator:run --console=plain "--args=$($arguments -join ' ')"
    if ($LASTEXITCODE -ne 0) { throw 'Emulator failed.' }
} finally {
    $env:CARGO_TARGET_DIR = $previousTarget
    Pop-Location
}
