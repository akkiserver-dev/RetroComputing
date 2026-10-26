#![no_std]
use core::panic::PanicInfo;
use rc_shuttle_abi as abi;
abi::declare_abi!();
#[panic_handler]
fn panic(_: &PanicInfo) -> ! { core::arch::wasm32::unreachable() }
#[unsafe(no_mangle)]
pub extern "C" fn _start() {
    if unsafe { abi::sys::call(7, 11, 22, 33, 44) } != 123 { core::arch::wasm32::unreachable(); }
}
