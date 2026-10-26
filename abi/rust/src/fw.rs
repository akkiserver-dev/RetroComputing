//! Raw `fw` imports. Caller must provide valid guest memory ranges.
#[link(wasm_import_module = "fw")]
unsafe extern "C" {
    /// Virtual time in ticks.
    pub fn time() -> i64;
    /// Zero on normal startup; otherwise the saved reset reason.
    pub fn reset_reason() -> i32;
    /// UTF-8 string leaves; budget and memory leaves return u64 LE.
    pub fn cpuid(leaf: i32, out: *mut u8, capacity: i32) -> i32;
    /// UTF-8 debug log; at most 4096 bytes.
    pub fn log(text: *const u8, length: i32);
    /// Suspend until the next execution slice.
    #[link_name = "yield"]
    pub fn yield_now();
    /// Zero timeout waits indefinitely. Returns 1 for event, 0 for timeout.
    pub fn hlt(timeout_ticks: i64) -> i32;
    /// Power off. No normal continuation.
    pub fn shutdown();
    /// Reboot. No normal continuation.
    pub fn reboot();
    /// Number of physical slots including empty ones; probe with dev_info.
    pub fn dev_slots() -> i32;
    /// 24-byte device info, or E_NODEV.
    pub fn dev_info(slot: i32, out: *mut u8, capacity: i32) -> i32;
    /// Method table v1; see README for variable-length entries.
    pub fn dev_describe(slot: i32, out: *mut u8, capacity: i32) -> i32;
    /// Sync call. Executes even with zero capacity. Short buffers hold the result at ticket -1 until dev_result or the next dev_call.
    pub fn dev_call(
        slot: i32,
        method: i32,
        args: *const u8,
        args_length: i32,
        out: *mut u8,
        capacity: i32,
    ) -> i32;
    /// Submit an async method; returns positive ticket or negative error.
    pub fn dev_submit(slot: i32, method: i32, args: *const u8, args_length: i32) -> i32;
    /// E_AGAIN while pending. Complete reads and terminal errors consume the ticket; short buffers retain it.
    pub fn dev_result(ticket: i32, out: *mut u8, capacity: i32) -> i32;
    /// Zero if empty, else 20-byte header plus payload. Short buffers retain the event.
    pub fn poll_event(out: *mut u8, capacity: i32) -> i32;
    /// Validate and copy image; returns positive handle or negative error.
    pub fn image_load(wasm: *const u8, length: i32) -> i32;
    /// Release image handle; existing children keep their image.
    pub fn image_free(image: i32) -> i32;
    /// Replace top-level guest. Only errors return. At most 4096 argument bytes.
    pub fn exec(image: i32, args: *const u8, args_length: i32) -> i32;
    /// Argument bytes from exec; empty on fresh boot.
    pub fn boot_args(out: *mut u8, capacity: i32) -> i32;
}
