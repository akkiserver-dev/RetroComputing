//! Raw `sys` imports. Caller must provide valid guest memory ranges.
#[link(wasm_import_module = "sys")]
unsafe extern "C" {
    /// Child only: suspend to the parent OS, then receive its i64 result. Numbers are OS-defined.
    pub fn call(number: i32, a0: i64, a1: i64, a2: i64, a3: i64) -> i64;
}
