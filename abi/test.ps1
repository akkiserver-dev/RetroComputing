param([string]$Clang = 'clang')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$output = Join-Path $repoRoot 'build/abi'
$previousTarget = $env:CARGO_TARGET_DIR
New-Item -ItemType Directory -Force $output | Out-Null
try {
    $env:CARGO_TARGET_DIR = Join-Path $output 'rust'
    & cargo test --manifest-path "$PSScriptRoot/rust/Cargo.toml" --locked --offline
    if ($LASTEXITCODE -ne 0) { throw 'Rust ABI tests failed.' }
    & cargo build --manifest-path "$PSScriptRoot/rust/Cargo.toml" --release --target wasm32-unknown-unknown --locked --offline
    if ($LASTEXITCODE -ne 0) { throw 'Rust ABI build failed.' }
    $library = Join-Path $env:CARGO_TARGET_DIR 'wasm32-unknown-unknown/release/librc_shuttle_abi.rlib'
    foreach ($role in @('parent', 'child')) {
        $exports = @('-Wl,--no-entry,--export=_start,--export-memory')
        if ($role -eq 'parent') { $exports += '-Wl,--export=rc_import_probe' }
        & $Clang --target=wasm32 -std=c11 -Wall -Wextra -Werror -ffreestanding -nostdlib "-I$PSScriptRoot/include" "$PSScriptRoot/tests/$role.c" @exports -o "$output/c-$role.wasm"
        if ($LASTEXITCODE -ne 0) { throw "C $role fixture failed." }
        & $Clang --target=wasm32 -x c++ -std=c++17 -Wall -Wextra -Werror -ffreestanding -fsyntax-only "-I$PSScriptRoot/include" "$PSScriptRoot/tests/$role.c"
        if ($LASTEXITCODE -ne 0) { throw "C++ $role header check failed." }
        & rustc --edition=2024 --crate-type=cdylib --target wasm32-unknown-unknown -C panic=abort --extern "rc_shuttle_abi=$library" "$PSScriptRoot/tests/$role.rs" -o "$output/rust-$role.wasm"
        if ($LASTEXITCODE -ne 0) { throw "Rust $role fixture failed." }
    }
    Write-Host 'Fixtures built. Run :architecture-shuttle:test to verify every import against the native runtime.'
} finally { $env:CARGO_TARGET_DIR = $previousTarget }
