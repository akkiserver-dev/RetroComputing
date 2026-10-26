#![no_std]
//! RC firmware ABI 0.1 for wasm32 guests. All imports are raw, unsafe calls.
//!
//! `fw` and `co` are available to top-level BIOS/OS guests; child guests only
//! receive `sys::call`. Export `memory` and `_start() -> ()` and invoke
//! [`declare_abi!`] exactly once in each final Wasm module. No WASI required.
//!
//! Lengths/capacities are signed i32, limited to 0..=i32::MAX. Pointers refer to
//! the calling guest's linear memory. Outputs return a negative error or the
//! required byte count. If capacity is too small, nothing is written; retry
//! with enough space. Strings are UTF-8, never NUL-terminated. Wire integers
//! are little-endian; UUID bytes retain RFC/network order.

pub mod constants;
pub use constants::*;
pub mod co;
pub mod fw;
pub mod sys;
pub mod types;
pub use types::*;

pub const ABI_MAJOR: u32 = 0;
pub const ABI_MINOR: u32 = 1;
pub const ABI_VERSION_BYTES: [u8; 8] = [0, 0, 0, 0, 1, 0, 0, 0];

/// Emit `rc.abi` once in the final guest, rather than in every dependency.
#[macro_export]
macro_rules! declare_abi {
    () => {
        #[used]
        #[unsafe(link_section = "rc.abi")]
        static RC_ABI_VERSION: [u8; 8] = $crate::ABI_VERSION_BYTES;
    };
}
