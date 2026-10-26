/* RC firmware ABI 0.1. Freestanding C/C++, wasm32, no WASI.
 * Emit RC_ABI_DECLARE() once per final guest; export memory and _start(void).
 * Lengths/capacities: signed i32, 0..INT32_MAX. Pointers: caller linear memory.
 * Strings: UTF-8 without NUL. Wire scalars: LE; UUID bytes: RFC/network order.
 * Outputs return required byte count or negative error. Short buffers are not
 * written. dev_call still executes: use dev_result(RC_HELD_RESULT, ...) rather
 * than repeating side effects.
 */
#ifndef RC_SHUTTLE_ABI_H
#define RC_SHUTTLE_ABI_H
#include <stdint.h>
#include <stddef.h>
#define RC_ABI_MAJOR 0
#define RC_ABI_MINOR 1
#define RC_ABI_STRING_INNER(value) #value
#define RC_ABI_STRING(value) RC_ABI_STRING_INNER(value)
#define RC_ABI_DECLARE() \
    __asm__(".section .custom_section.rc.abi,\"\",@\n" \
            ".int32 " RC_ABI_STRING(RC_ABI_MAJOR) "\n" \
            ".int32 " RC_ABI_STRING(RC_ABI_MINOR) "\n.text\n")
#if defined(__wasm32__)
#define RC_ABI_IMPORT(module, name) __attribute__((import_module(module), import_name(name)))
#else
#define RC_ABI_IMPORT(module, name)
#endif

#define RC_E_INVAL (-1)
#define RC_E_FAULT (-2)
#define RC_E_NODEV (-3)
#define RC_E_NOMETHOD (-4)
#define RC_E_BADHANDLE (-5)
#define RC_E_NOMEM (-6)
#define RC_E_UNSUPPORTED (-7)
#define RC_E_VERSION (-8)
#define RC_E_AGAIN (-9)
#define RC_E_NOPOWER (-10)
#define RC_E_IO (-11)
#define RC_E_INTERNAL (-12)
#define RC_E_MCHK (-13)
#define RC_E_TRAP (-14)
#define RC_E_NOMEDIA (-256)
#define RC_E_READONLY (-257)
#define RC_CPUID_VENDOR (0)
#define RC_CPUID_MODEL (1)
#define RC_CPUID_ARCH (2)
#define RC_CPUID_FEATURES (3)
#define RC_CPUID_BUDGET (0x100)
#define RC_CPUID_MEMORY (0x101)
#define RC_HELD_RESULT (-1)
#define RC_EVENT_ATTACH (1)
#define RC_EVENT_DETACH (2)
#define RC_EVENT_COMPLETE (3)
#define RC_EVENT_SIGNAL (4)
#define RC_EVENT_POWER (5)
#define RC_EVENT_OVERFLOW (6)
#define RC_CO_REASON_BUDGET (0)
#define RC_CO_REASON_SYSCALL (1)
#define RC_CO_REASON_RETURNED (2)
#define RC_CO_REASON_TRAPPED (3)
#define RC_CO_STATUS_LIVE (0)
#define RC_CO_STATUS_RETURNED (1)
#define RC_CO_STATUS_TRAPPED (2)
#define RC_WIRE_VOID (0x00)
#define RC_WIRE_BOOL (0x01)
#define RC_WIRE_I8 (0x02)
#define RC_WIRE_U8 (0x03)
#define RC_WIRE_I16 (0x04)
#define RC_WIRE_U16 (0x05)
#define RC_WIRE_I32 (0x06)
#define RC_WIRE_U32 (0x07)
#define RC_WIRE_I64 (0x08)
#define RC_WIRE_U64 (0x09)
#define RC_WIRE_F32 (0x0a)
#define RC_WIRE_F64 (0x0b)
#define RC_WIRE_STRING (0x0c)
#define RC_WIRE_ARRAY_FLAG (0x80)
#define RC_DESCRIBE_VERSION (1)
#define RC_CLASS_EEPROM (0x0301)
#define RC_CLASS_VIDEO (0x0100)
#define RC_CLASS_KEYBOARD (0x0200)
#define RC_CLASS_DISK (0x0300)
#define RC_PIXEL_TEXT16 (0x01)
#define RC_PIXEL_TEXT256 (0x02)
#define RC_PIXEL_IDX1 (0x11)
#define RC_PIXEL_IDX2 (0x12)
#define RC_PIXEL_IDX4 (0x14)
#define RC_PIXEL_IDX8 (0x18)
#define RC_PIXEL_RGB565 (0x30)
#define RC_KEY_PRESS (1)
#define RC_KEY_RELEASE (2)
#define RC_KEY_TEXT (3)
#define RC_MOD_SHIFT (1)
#define RC_MOD_CONTROL (2)
#define RC_MOD_ALT (4)
#define RC_MOD_SUPER (8)
#define RC_MOD_CAPS_LOCK (16)
#define RC_MOD_NUM_LOCK (32)

typedef struct rc_device_info {
    uint16_t class_code, vendor, device, reserved;
    uint8_t uuid[16];
} rc_device_info;

/* Payload immediately follows this 20-byte wire header. */
typedef struct __attribute__((packed)) rc_event_header {
    uint16_t kind, slot;
    int32_t parameter;
    uint64_t tick;
    uint32_t payload_length;
} rc_event_header;

typedef struct rc_child_info {
    int32_t reason, number;
    int64_t arguments[4];
    int64_t budget_used;
} rc_child_info;

typedef struct rc_describe_header {
    uint8_t version, reserved;
    uint16_t method_count;
} rc_describe_header;

typedef struct rc_keyboard_signal {
    uint8_t kind, modifiers;
    uint16_t key;
    uint32_t code_point;
} rc_keyboard_signal;

#ifdef __cplusplus
#define RC_ABI_ASSERT static_assert
extern "C" {
#else
#define RC_ABI_ASSERT _Static_assert
#endif
RC_ABI_ASSERT(sizeof(rc_device_info) == 24, "device info layout");
RC_ABI_ASSERT(sizeof(rc_event_header) == 20, "event header layout");
RC_ABI_ASSERT(offsetof(rc_event_header, tick) == 8, "event tick layout");
RC_ABI_ASSERT(sizeof(rc_child_info) == 48, "child info layout");
RC_ABI_ASSERT(sizeof(rc_describe_header) == 4, "describe header layout");
RC_ABI_ASSERT(sizeof(rc_keyboard_signal) == 8, "keyboard layout");

/* Virtual time in ticks. */
RC_ABI_IMPORT("fw", "time") int64_t rc_fw_time(void);
/* Zero on normal startup; otherwise the saved reset reason. */
RC_ABI_IMPORT("fw", "reset_reason") int32_t rc_fw_reset_reason(void);
/* UTF-8 string leaves; budget and memory leaves return u64 LE. */
RC_ABI_IMPORT("fw", "cpuid") int32_t rc_fw_cpuid(int32_t leaf, void * out, int32_t capacity);
/* UTF-8 debug log; at most 4096 bytes. */
RC_ABI_IMPORT("fw", "log") void rc_fw_log(const void * text, int32_t length);
/* Suspend until the next execution slice. */
RC_ABI_IMPORT("fw", "yield") void rc_fw_yield_now(void);
/* Zero timeout waits indefinitely. Returns 1 for event, 0 for timeout. */
RC_ABI_IMPORT("fw", "hlt") int32_t rc_fw_hlt(int64_t timeout_ticks);
/* Power off. No normal continuation. */
RC_ABI_IMPORT("fw", "shutdown") void rc_fw_shutdown(void);
/* Reboot. No normal continuation. */
RC_ABI_IMPORT("fw", "reboot") void rc_fw_reboot(void);
/* Number of physical slots including empty ones; probe with dev_info. */
RC_ABI_IMPORT("fw", "dev_slots") int32_t rc_fw_dev_slots(void);
/* 24-byte device info, or E_NODEV. */
RC_ABI_IMPORT("fw", "dev_info") int32_t rc_fw_dev_info(int32_t slot, void * out, int32_t capacity);
/* Method table v1; see README for variable-length entries. */
RC_ABI_IMPORT("fw", "dev_describe") int32_t rc_fw_dev_describe(int32_t slot, void * out, int32_t capacity);
/* Sync call. Executes even with zero capacity. Short buffers hold the result at ticket -1 until dev_result or the next dev_call. */
RC_ABI_IMPORT("fw", "dev_call") int32_t rc_fw_dev_call(int32_t slot, int32_t method, const void * args, int32_t args_length, void * out, int32_t capacity);
/* Submit an async method; returns positive ticket or negative error. */
RC_ABI_IMPORT("fw", "dev_submit") int32_t rc_fw_dev_submit(int32_t slot, int32_t method, const void * args, int32_t args_length);
/* E_AGAIN while pending. Complete reads and terminal errors consume the ticket; short buffers retain it. */
RC_ABI_IMPORT("fw", "dev_result") int32_t rc_fw_dev_result(int32_t ticket, void * out, int32_t capacity);
/* Zero if empty, else 20-byte header plus payload. Short buffers retain the event. */
RC_ABI_IMPORT("fw", "poll_event") int32_t rc_fw_poll_event(void * out, int32_t capacity);
/* Validate and copy image; returns positive handle or negative error. */
RC_ABI_IMPORT("fw", "image_load") int32_t rc_fw_image_load(const void * wasm, int32_t length);
/* Release image handle; existing children keep their image. */
RC_ABI_IMPORT("fw", "image_free") int32_t rc_fw_image_free(int32_t image);
/* Replace top-level guest. Only errors return. At most 4096 argument bytes. */
RC_ABI_IMPORT("fw", "exec") int32_t rc_fw_exec(int32_t image, const void * args, int32_t args_length);
/* Argument bytes from exec; empty on fresh boot. */
RC_ABI_IMPORT("fw", "boot_args") int32_t rc_fw_boot_args(void * out, int32_t capacity);
/* Create child with memory reservation; returns handle or negative error. */
RC_ABI_IMPORT("co", "co_create") int32_t rc_co_create(int32_t image, int64_t memory_bytes);
/* Write 48 bytes to info. Returns reason or negative error. */
RC_ABI_IMPORT("co", "co_resume") int32_t rc_co_resume(int32_t child, int64_t budget, int64_t syscall_result, rc_child_info * info);
/* LIVE=0, RETURNED=1, TRAPPED=2, or E_BADHANDLE. */
RC_ABI_IMPORT("co", "co_status") int32_t rc_co_status(int32_t child);
/* Destroy child and release its reservation. */
RC_ABI_IMPORT("co", "co_close") int32_t rc_co_close(int32_t child);
/* Copy child memory to caller. Returns 0 or negative error. */
RC_ABI_IMPORT("co", "co_peek") int32_t rc_co_peek(int32_t child, uint32_t child_address, void * out, int32_t length);
/* Copy caller memory to child. Returns 0 or negative error. */
RC_ABI_IMPORT("co", "co_poke") int32_t rc_co_poke(int32_t child, uint32_t child_address, const void * data, int32_t length);
/* Child only: suspend to the parent OS, then receive its i64 result. Numbers are OS-defined. */
RC_ABI_IMPORT("sys", "call") int64_t rc_sys_call(int32_t number, int64_t a0, int64_t a1, int64_t a2, int64_t a3);

#ifdef __cplusplus
}
#endif
#undef RC_ABI_ASSERT
#endif /* RC_SHUTTLE_ABI_H */
