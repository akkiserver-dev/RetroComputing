package jp.akkiserver.rc.core.machine;

import java.util.Objects;

public record MachineEvent(int kind, int slot, int parameter, long tick, byte[] payload) {
    public static final int ATTACH = 1,
        DETACH = 2,
        COMPLETE = 3,
        SIGNAL = 4,
        POWER = 5,
        OVERFLOW = 6;

    public MachineEvent {
        if (kind < 1 || kind > 0xffff || slot < 0 || slot > 0xffff)
            throw new IllegalArgumentException();
        payload = Objects.requireNonNull(payload).clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
