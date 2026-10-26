package jp.akkiserver.rc.architecture.shuttle;

import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.shuttle.WeaveCaller;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class GuestMemory {
    private final WeaveCaller caller;

    GuestMemory(WeaveCaller caller) {
        this.caller = caller;
    }

    static long address(long value) {
        return Integer.toUnsignedLong((int) value);
    }

    void check(long ptr, long length) {
        if (length < 0 || length > Integer.MAX_VALUE)
            throw new Fault(StdErrors.E_INVAL);
        long address = address(ptr);
        if (address + length > caller.memorySize())
            throw new Fault(StdErrors.E_FAULT);
    }

    byte[] read(long ptr, long length) {
        check(ptr, length);
        return caller.read(address(ptr), (int) length);
    }

    void write(long ptr, byte[] bytes) {
        check(ptr, bytes.length);
        caller.write(address(ptr), bytes);
    }

    String string(long ptr, long length) {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(read(ptr, length)))
                .toString();
        } catch (CharacterCodingException e) {
            throw new Fault(StdErrors.E_INVAL);
        }
    }

    int output(long ptr, long capacity, byte[] bytes) {
        if (capacity < 0 || capacity > Integer.MAX_VALUE)
            throw new Fault(StdErrors.E_INVAL);
        if (capacity == 0)
            return bytes.length;
        check(ptr, capacity);
        if (capacity >= bytes.length)
            write(ptr, bytes);
        return bytes.length;
    }

    static final class Fault extends RuntimeException {
        final int code;

        Fault(int code) {
            this.code = code;
        }
    }
}
