package jp.akkiserver.rc.core.architecture;

import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.core.machine.MachineEvent;
import jp.akkiserver.rc.core.runtime.CallResult;
import jp.akkiserver.rc.core.runtime.MethodTable;

/**
 * Semantic host operations. Pointer validation and language ABI encoding belong to the adapter.
 */
public interface ArchitectureHost {
    long time();

    int resetReason();

    int slotCount();

    DeviceInfo deviceInfo(int slot);

    MethodTable methods(int slot);

    CallResult invoke(int slot, int method, Object[] arguments);

    int submit(int slot, int method, Object[] arguments);

    CallResult result(int ticket);

    void releaseResult(int ticket);

    MachineEvent peekEvent();

    MachineEvent pollEvent();

    /**
     * Returns the next event, retaining it when its payload exceeds the capacity. Negative means
     * peek only.
     */
    default MachineEvent pollEvent(int maxPayloadBytes) {
        MachineEvent event = peekEvent();
        return event != null && maxPayloadBytes >= event.payload().length ? pollEvent() : event;
    }

    /**
     * Result of the wait that most recently resumed this guest.
     */
    boolean wokeByEvent();

    void log(String message);
}
