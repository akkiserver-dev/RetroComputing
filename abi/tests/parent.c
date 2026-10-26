#include "rc_abi.h"
RC_ABI_DECLARE();

/* Exported probe retains every parent import for host-side signature checks. */
int64_t rc_import_probe(int32_t selector) {
    uint8_t buffer[4096] = {0};
    switch (selector) {
    case 0: return rc_fw_time();
    case 1: return rc_fw_reset_reason();
    case 2: return rc_fw_cpuid(1, buffer, 1);
    case 3: rc_fw_log(buffer, 1); return 0;
    case 4: rc_fw_yield_now(); return 0;
    case 5: return rc_fw_hlt(1);
    case 6: rc_fw_shutdown(); return 0;
    case 7: rc_fw_reboot(); return 0;
    case 8: return rc_fw_dev_slots();
    case 9: return rc_fw_dev_info(1, buffer, 1);
    case 10: return rc_fw_dev_describe(1, buffer, 1);
    case 11: return rc_fw_dev_call(1, 1, buffer, 1, buffer, 1);
    case 12: return rc_fw_dev_submit(1, 1, buffer, 1);
    case 13: return rc_fw_dev_result(1, buffer, 1);
    case 14: return rc_fw_poll_event(buffer, 1);
    case 15: return rc_fw_image_load(buffer, 1);
    case 16: return rc_fw_image_free(1);
    case 17: return rc_fw_exec(1, buffer, 1);
    case 18: return rc_fw_boot_args(buffer, 1);
    case 19: return rc_co_create(1, 1);
    case 20: return rc_co_resume(1, 1, 1, (rc_child_info *)buffer);
    case 21: return rc_co_status(1);
    case 22: return rc_co_close(1);
    case 23: return rc_co_peek(1, 1, buffer, 1);
    case 24: return rc_co_poke(1, 1, buffer, 1);
    default: return -1;
    }
}
void _start(void) { const char message[] = "C ABI OK"; rc_fw_log(message, 8); }
