#![no_std]
use core::panic::PanicInfo;
use rc_shuttle_abi as abi;
abi::declare_abi!();
#[panic_handler]
fn panic(_: &PanicInfo) -> ! { core::arch::wasm32::unreachable() }

// Exported probe retains every parent import for host-side signature checks.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn rc_import_probe(selector: i32) -> i64 {
    let mut buffer = [0u8; 4096];
    unsafe { match selector {
        0 => abi::fw::time() as i64,
        1 => abi::fw::reset_reason() as i64,
        2 => abi::fw::cpuid(1, buffer.as_mut_ptr(), 1) as i64,
        3 => { abi::fw::log(buffer.as_ptr(), 1); 0 },
        4 => { abi::fw::yield_now(); 0 },
        5 => abi::fw::hlt(1) as i64,
        6 => { abi::fw::shutdown(); 0 },
        7 => { abi::fw::reboot(); 0 },
        8 => abi::fw::dev_slots() as i64,
        9 => abi::fw::dev_info(1, buffer.as_mut_ptr(), 1) as i64,
        10 => abi::fw::dev_describe(1, buffer.as_mut_ptr(), 1) as i64,
        11 => abi::fw::dev_call(1, 1, buffer.as_ptr(), 1, buffer.as_mut_ptr(), 1) as i64,
        12 => abi::fw::dev_submit(1, 1, buffer.as_ptr(), 1) as i64,
        13 => abi::fw::dev_result(1, buffer.as_mut_ptr(), 1) as i64,
        14 => abi::fw::poll_event(buffer.as_mut_ptr(), 1) as i64,
        15 => abi::fw::image_load(buffer.as_ptr(), 1) as i64,
        16 => abi::fw::image_free(1) as i64,
        17 => abi::fw::exec(1, buffer.as_ptr(), 1) as i64,
        18 => abi::fw::boot_args(buffer.as_mut_ptr(), 1) as i64,
        19 => abi::co::create(1, 1) as i64,
        20 => abi::co::resume(1, 1, 1, buffer.as_mut_ptr().cast::<abi::ChildInfo>()) as i64,
        21 => abi::co::status(1) as i64,
        22 => abi::co::close(1) as i64,
        23 => abi::co::peek(1, 1, buffer.as_mut_ptr(), 1) as i64,
        24 => abi::co::poke(1, 1, buffer.as_ptr(), 1) as i64,
        _ => -1,
    } }
}
#[unsafe(no_mangle)]
pub extern "C" fn _start() { unsafe { abi::fw::log(b"Rust ABI OK".as_ptr(), 11); } }
