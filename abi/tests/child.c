#include "rc_abi.h"
RC_ABI_DECLARE();
void _start(void) {
    if (rc_sys_call(7, 11, 22, 33, 44) != 123) __builtin_trap();
}
