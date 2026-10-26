package jp.akkiserver.rc.architecture.shuttle;

import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.api.video.PixelFormat;
import jp.akkiserver.rc.core.runtime.WireType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuestAbiTest {
    private static Map<String, Integer> parse(String source, String pattern) {
        Map<String, Integer> values = new TreeMap<>();
        Matcher matcher = Pattern.compile(pattern).matcher(source);
        while (matcher.find())
            values.put(matcher.group(1), Integer.decode(matcher.group(2)));
        return values;
    }

    @Test
    void guestConstantsMatchHostErrorWireAndPixelCodes() throws Exception {
        String c = Files.readString(Path.of("abi/include/rc_abi.h"));
        String rust = Files.readString(Path.of("abi/rust/src/constants.rs"));
        Map<String, Integer> cValues = parse(c, "#define RC_(\\w+) \\((-?(?:0x[0-9a-fA-F]+|[0-9]+))\\)");
        Map<String, Integer> rustValues = parse(rust, "pub const (\\w+): \\w+ = (-?(?:0x[0-9a-fA-F]+|[0-9]+));");
        assertEquals(cValues, rustValues);
        for (var field : StdErrors.class.getFields())
            assertEquals(field.getInt(null), cValues.get(field.getName()));
        for (var kind : WireType.Kind.values())
            assertEquals(kind.code(), cValues.get("WIRE_" + kind.name()));
        assertEquals(WireType.ARRAY_FLAG, cValues.get("WIRE_ARRAY_FLAG"));
        for (var format : PixelFormat.values())
            assertEquals(format.code(), cValues.get("PIXEL_" + format.name()));
    }
}
