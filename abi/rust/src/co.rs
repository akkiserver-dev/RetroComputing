//! Raw `co` imports. Caller must provide valid guest memory ranges.
use crate::ChildInfo;
#[link(wasm_import_module = "co")]
unsafe extern "C" {
    /// Create child with memory reservation; returns handle or negative error.
    #[link_name = "co_create"]
    pub fn create(image: i32, memory_bytes: i64) -> i32;
    /// Write 48 bytes to info. Returns reason or negative error.
    #[link_name = "co_resume"]
    pub fn resume(child: i32, budget: i64, syscall_result: i64, info: *mut ChildInfo) -> i32;
    /// LIVE=0, RETURNED=1, TRAPPED=2, or E_BADHANDLE.
    #[link_name = "co_status"]
    pub fn status(child: i32) -> i32;
    /// Destroy child and release its reservation.
    #[link_name = "co_close"]
    pub fn close(child: i32) -> i32;
    /// Copy child memory to caller. Returns 0 or negative error.
    #[link_name = "co_peek"]
    pub fn peek(child: i32, child_address: u32, out: *mut u8, length: i32) -> i32;
    /// Copy caller memory to child. Returns 0 or negative error.
    #[link_name = "co_poke"]
    pub fn poke(child: i32, child_address: u32, data: *const u8, length: i32) -> i32;
}
