package jp.akkiserver.rc.architecture.shuttle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Validates RC-specific declarations before handing binary validation to Wasmtime.
 */
final class WasmImage {
    static long minimumMemory(byte[] bytes) {
        validate(bytes);
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        data.position(8);
        while (data.hasRemaining()) {
            int id = Byte.toUnsignedInt(data.get()),
                size = length(data),
                end = data.position() + size;
            if (id == 5) {
                if (length(data) != 1)
                    throw new IllegalArgumentException("Exactly one guest memory is required");
                int flags = length(data);
                if ((flags & ~1) != 0)
                    throw new IllegalArgumentException("Only unshared wasm32 memory is supported");
                long pages = length(data);
                return Math.addExact(pages, 16) * 65536L;
            }
            data.position(end);
        }
        throw new IllegalArgumentException("Guest requires a defined memory");
    }

    static void validate(byte[] bytes) {
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (data.remaining() < 8 || data.getInt() != 0x6d736100 || data.getInt() != 1)
            throw new IllegalArgumentException("Expected a WebAssembly core binary");
        boolean abi = false, memory = false;
        while (data.hasRemaining()) {
            int id = Byte.toUnsignedInt(data.get()), length = length(data);
            if (length > data.remaining())
                throw new IllegalArgumentException("Truncated section");
            ByteBuffer section = data.slice().order(ByteOrder.LITTLE_ENDIAN);
            section.limit(length);
            data.position(data.position() + length);
            if (id == 8)
                throw new IllegalArgumentException("Use _start, not a Wasm start section");
            if (id == 0) {
                String name = name(section);
                if (name.equals("rc.abi")) {
                    if (abi
                        || section.remaining() != 8
                        || section.getInt() != 0
                        || Integer.toUnsignedLong(section.getInt()) > 1)
                        throw new VersionException("Unsupported rc.abi version");
                    abi = true;
                }
            } else if (id == 7) {
                int count = length(section);
                for (int i = 0; i < count; i++) {
                    String name = name(section);
                    int kind = Byte.toUnsignedInt(section.get());
                    length(section);
                    if (name.equals("memory") && kind == 2)
                        memory = true;
                }
            }
        }
        if (!abi)
            throw new VersionException("Guest requires rc.abi");
        if (!memory)
            throw new IllegalArgumentException("Guest requires exported memory");
    }

    private static String name(ByteBuffer data) {
        int n = length(data);
        if (n > data.remaining())
            throw new IllegalArgumentException("Truncated name");
        byte[] bytes = new byte[n];
        data.get(bytes);
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Invalid UTF-8", e);
        }
    }

    private static int length(ByteBuffer data) {
        long value = 0;
        for (int i = 0; i < 5; i++) {
            if (!data.hasRemaining())
                throw new IllegalArgumentException("Truncated LEB128");
            int b = Byte.toUnsignedInt(data.get());
            value |= (long) (b & 127) << (i * 7);
            if ((b & 128) == 0) {
                if (value > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Section too large");
                return (int) value;
            }
        }
        throw new IllegalArgumentException("Invalid LEB128");
    }

    static final class VersionException extends IllegalArgumentException {
        VersionException(String message) {
            super(message);
        }
    }
}
