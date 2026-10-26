package jp.akkiserver.rc.architecture.shuttle;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny binary fixture assembler. Tests execute these bytes in the real native runtime.
 */
final class Wasm {
    final List<String[]> imports = new ArrayList<>();
    final ByteArrayOutputStream code = new ByteArrayOutputStream();
    byte[] data = new byte[0];
    int pages = 2;

    private static void type(ByteArrayOutputStream out, String signature) {
        out.write(0x60);
        String[] sides = signature.split(">", -1);
        for (String side : sides) {
            unsigned(out, side.length());
            for (char c : side.toCharArray())
                out.write(c == 'i' ? 0x7f : 0x7e);
        }
    }

    static void section(ByteArrayOutputStream out, int id, byte[] body) {
        out.write(id);
        unsigned(out, body.length);
        out.writeBytes(body);
    }

    static void name(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        unsigned(out, bytes.length);
        out.writeBytes(bytes);
    }

    static void unsigned(ByteArrayOutputStream out, int value) {
        do {
            int b = value & 127;
            value >>>= 7;
            out.write(value == 0 ? b : b | 128);
        } while (value != 0);
    }

    static void signed(ByteArrayOutputStream out, long value) {
        while (true) {
            int b = (int) value & 127;
            value >>= 7;
            boolean done = value == 0 && (b & 64) == 0 || value == -1 && (b & 64) != 0;
            out.write(done ? b : b | 128);
            if (done)
                return;
        }
    }

    Wasm imp(String module, String name, String signature) {
        imports.add(new String[]{module, name, signature});
        return this;
    }

    Wasm i(int value) {
        code.write(0x41);
        signed(code, value);
        return this;
    }

    Wasm l(long value) {
        code.write(0x42);
        signed(code, value);
        return this;
    }

    Wasm call(int function) {
        code.write(0x10);
        unsigned(code, function);
        return this;
    }

    Wasm op(int... bytes) {
        for (int b : bytes)
            code.write(b);
        return this;
    }

    byte[] bytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0, 97, 115, 109, 1, 0, 0, 0});
        ByteArrayOutputStream custom = new ByteArrayOutputStream();
        name(custom, "rc.abi");
        custom.writeBytes(new byte[]{0, 0, 0, 0, 1, 0, 0, 0});
        section(out, 0, custom.toByteArray());
        ByteArrayOutputStream types = new ByteArrayOutputStream();
        unsigned(types, imports.size() + 1);
        for (String[] imp : imports)
            type(types, imp[2]);
        type(types, ">");
        section(out, 1, types.toByteArray());
        ByteArrayOutputStream bindings = new ByteArrayOutputStream();
        unsigned(bindings, imports.size());
        for (int i = 0; i < imports.size(); i++) {
            name(bindings, imports.get(i)[0]);
            name(bindings, imports.get(i)[1]);
            bindings.write(0);
            unsigned(bindings, i);
        }
        section(out, 2, bindings.toByteArray());
        ByteArrayOutputStream functions = new ByteArrayOutputStream();
        unsigned(functions, 1);
        unsigned(functions, imports.size());
        section(out, 3, functions.toByteArray());
        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        memory.write(1);
        memory.write(0);
        unsigned(memory, pages);
        section(out, 5, memory.toByteArray());
        ByteArrayOutputStream exports = new ByteArrayOutputStream();
        exports.write(2);
        name(exports, "memory");
        exports.write(2);
        exports.write(0);
        name(exports, "_start");
        exports.write(0);
        unsigned(exports, imports.size());
        section(out, 7, exports.toByteArray());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        body.writeBytes(code.toByteArray());
        body.write(0x0b);
        ByteArrayOutputStream bodies = new ByteArrayOutputStream();
        bodies.write(1);
        unsigned(bodies, body.size());
        bodies.writeBytes(body.toByteArray());
        section(out, 10, bodies.toByteArray());
        if (data.length > 0) {
            ByteArrayOutputStream segment = new ByteArrayOutputStream();
            segment.writeBytes(new byte[]{1, 0, 0x41, 0, 0x0b});
            unsigned(segment, data.length);
            segment.writeBytes(data);
            section(out, 11, segment.toByteArray());
        }
        return out.toByteArray();
    }
}
