#![no_std]

// A starting point, not an OS. No allocator, WASI, or third-party crates required.
use core::panic::PanicInfo;
use rc_shuttle_abi::fw::{dev_call, dev_describe, dev_info, dev_slots, hlt, log, poll_event};

rc_shuttle_abi::declare_abi!();

fn u16le(bytes: &[u8], offset: usize) -> u16 {
    u16::from_le_bytes([bytes[offset], bytes[offset + 1]])
}

// Find the video card by class, then resolve its method by name. No fixed method IDs.
fn video() -> Option<(i32, i32)> {
    let count = unsafe { dev_slots() };
    for slot in 0..count {
        let mut info = [0u8; 24];
        if unsafe { dev_info(slot, info.as_mut_ptr(), 24) } != 24 || u16le(&info, 0) != 0x0100 {
            continue;
        }
        let mut table = [0u8; 4096];
        let len = unsafe { dev_describe(slot, table.as_mut_ptr(), table.len() as i32) };
        if len < 4 || len as usize > table.len() {
            continue;
        }
        let bytes = &table[..len as usize];
        if bytes[0] != 1 {
            continue;
        }
        let mut at = 4;
        for _ in 0..u16le(bytes, 2) {
            if at + 4 > bytes.len() {
                return None;
            }
            let id = u16le(bytes, at) as i32;
            let name_len = bytes[at + 3] as usize;
            at += 4;
            if at + name_len + 1 > bytes.len() {
                return None;
            }
            let name = &bytes[at..at + name_len];
            at += name_len;
            let argc = bytes[at] as usize;
            at += 1;
            if at + argc + 1 > bytes.len() {
                return None;
            }
            if name == b"write" {
                return Some((slot, id));
            }
            at += argc + 1;
        }
    }
    None
}

fn write(video: (i32, i32), x: u16, y: u16, text: &str, color: u16) {
    let mut args = [0u8; 512];
    let len = text.len();
    if len > args.len() - 10 {
        return;
    }
    args[0..2].copy_from_slice(&x.to_le_bytes());
    args[2..4].copy_from_slice(&y.to_le_bytes());
    args[4..8].copy_from_slice(&(len as u32).to_le_bytes());
    args[8..8 + len].copy_from_slice(text.as_bytes());
    args[8 + len..10 + len].copy_from_slice(&color.to_le_bytes());
    let result = unsafe {
        dev_call(
            video.0,
            video.1,
            args.as_ptr(),
            (len + 10) as i32,
            core::ptr::null_mut(),
            0,
        )
    };
    if result < 0 {
        panic!("video write failed");
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn _start() {
    let Some(video) = video() else {
        panic!("video not found");
    };
    write(video, 3, 2, "Hello from Rust!", 11);
    write(video, 3, 4, "no_std / no WASI / RC firmware ABI", 15);
    write(
        video,
        3,
        6,
        "Type ASCII text below. Backspace clears a character.",
        7,
    );
    let mut line = [b' '; 70];
    let mut used = 0;
    let mut event = [0u8; 256];
    loop {
        let len = unsafe { poll_event(event.as_mut_ptr(), event.len() as i32) };
        // Event header: kind:u16, slot:u16, parameter:u32, tick:u64, payload_len:u32.
        if len >= 28 && u16le(&event, 0) == 4 {
            let key_kind = event[20];
            let key = u16le(&event, 22);
            let cp = u32::from_le_bytes([event[24], event[25], event[26], event[27]]);
            if key_kind == 3 && (32..127).contains(&cp) && used < line.len() {
                line[used] = cp as u8;
                used += 1;
            } else if key_kind == 1 && key == 259 && used > 0 {
                used -= 1;
                line[used] = b' ';
            } else {
                continue;
            }
            write(video, 3, 8, core::str::from_utf8(&line).unwrap(), 15);
        } else if len <= 0 {
            unsafe {
                hlt(0);
            }
        }
    }
}

#[panic_handler]
fn panic(_: &PanicInfo) -> ! {
    let message = "Rust screen guest panic";
    unsafe {
        log(message.as_ptr(), message.len() as i32);
    }
    loop {
        unsafe {
            hlt(0);
        }
    }
}
