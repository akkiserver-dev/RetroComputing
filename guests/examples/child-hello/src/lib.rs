#![no_std]

// RetroOS child program: echo argv. Single module, no allocator, no WASI.
use core::panic::PanicInfo;
use rc_shuttle_abi::sys;

rc_shuttle_abi::declare_abi!();

const SYS_EXIT: i32 = 1;
const SYS_WRITE: i32 = 2;
const SYS_ARGV: i32 = 9;

const FALLBACK: &[u8] = b"hello from child";

fn u32le(bytes: &[u8], at: usize) -> u32 {
    u32::from_le_bytes([bytes[at], bytes[at + 1], bytes[at + 2], bytes[at + 3]])
}

unsafe fn write_stdout(data: &[u8]) {
    if !data.is_empty() {
        unsafe { sys::call(SYS_WRITE, 1, data.as_ptr() as i64, data.len() as i64, 0) };
    }
}

/// argv block: [u32 argc][u32 offset * argc][NUL-terminated strings...].
/// Writes argv[1..] joined by single spaces; false if the block is malformed.
fn write_args(buf: &[u8]) -> bool {
    let argc = u32le(buf, 0) as usize;
    let table = 4 + 4 * argc;
    if argc == 0 || table > buf.len() {
        return false;
    }
    for i in 1..argc {
        let start = u32le(buf, 4 + 4 * i) as usize;
        let end = if i + 1 < argc {
            u32le(buf, 4 + 4 * (i + 1)) as usize
        } else {
            buf.len()
        };
        if start < table || end > buf.len() || start > end {
            return false;
        }
        let mut item = &buf[start..end];
        while item.last() == Some(&b'\0') {
            item = &item[..item.len() - 1];
        }
        if i > 1 {
            unsafe { write_stdout(b" ") };
        }
        unsafe { write_stdout(item) };
    }
    true
}

#[unsafe(no_mangle)]
pub extern "C" fn _start() {
    unsafe {
        let mut buf = [0u8; 256];
        let len = sys::call(SYS_ARGV, buf.as_mut_ptr() as i64, 0, 0, 0);
        if len > 0 && (len as usize) <= buf.len() {
            write_stdout(b"hello from child:");
            if !write_args(&buf[..len as usize]) {
                write_stdout(FALLBACK);
            }
            write_stdout(b"\n");
        } else {
            write_stdout(FALLBACK);
            write_stdout(b"\n");
        }
        sys::call(SYS_EXIT, 0, 0, 0, 0);
    }
}

#[panic_handler]
fn panic(_: &PanicInfo) -> ! {
    // No firmware imports in child land; sit still until reaped.
    loop {
        core::hint::spin_loop();
    }
}
