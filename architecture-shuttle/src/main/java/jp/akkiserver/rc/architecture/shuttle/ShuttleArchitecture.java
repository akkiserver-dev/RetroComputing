package jp.akkiserver.rc.architecture.shuttle;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.core.architecture.Architecture;
import jp.akkiserver.rc.core.architecture.ArchitectureException;
import jp.akkiserver.rc.core.architecture.ArchitectureHost;
import jp.akkiserver.rc.core.architecture.ExecutionResult;
import jp.akkiserver.rc.core.data.MapDataCompound;
import jp.akkiserver.rc.core.data.NbtIO;
import jp.akkiserver.rc.core.data.UuidBytes;
import jp.akkiserver.rc.core.machine.MachineEvent;
import jp.akkiserver.rc.core.runtime.CallResult;
import jp.akkiserver.rc.core.runtime.MethodCodec;
import jp.akkiserver.rc.core.runtime.MethodTable;
import jp.akkiserver.rc.core.runtime.SavedValue;
import jp.akkiserver.shuttle.WasmRuntimeException;
import jp.akkiserver.shuttle.WeaveCaller;
import jp.akkiserver.shuttle.WeaveInstance;
import jp.akkiserver.shuttle.WeaveResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * The complete Wasm firmware lives here, outside the language-independent machine.
 */
final class ShuttleArchitecture implements Architecture {
    private final ArchitectureHost host;
    private final ShuttleConfig config;
    private final CompiledModules modules;
    private final Map<Integer, byte[]> images = new LinkedHashMap<>();
    private final Map<Integer, Child> children = new LinkedHashMap<>();
    private CompiledModules.Lease module;
    private WeaveInstance vm;
    private byte[] source, bootArgs = new byte[0];
    private boolean started, replay;
    private String replayImport;
    private long replayValue;
    private ExecutionResult exit;
    private byte[] nextArgs;
    private Prepared replacement;
    private CallResult held;
    private long tickBudget;
    private int nextImage = 1, nextChild = 1;

    ShuttleArchitecture(ArchitectureHost host, ShuttleConfig config, CompiledModules modules) {
        this.host = host;
        this.config = config;
        this.modules = modules;
    }

    private static byte[] childInfo(int reason, int number, long[] args, long used) {
        ByteBuffer b =
            ByteBuffer.allocate(48)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(reason)
                .putInt(number);
        for (long arg : args)
            b.putLong(arg);
        return b.putLong(used).array();
    }

    private static void checkSnapshot(byte[] snapshot, long memoryLimit) {
        try (var stream = new GZIPInputStream(new ByteArrayInputStream(snapshot))) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int n;
            while ((n = stream.read(buffer)) != -1) {
                total += n;
                if (total > memoryLimit + (4L << 20))
                    throw new IllegalArgumentException("Runtime snapshot exceeds memory capacity");
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid runtime snapshot", error);
        }
    }

    @Override
    public void boot(byte[] image) {
        install(image, null);
    }

    private CompiledModules.Lease compile(byte[] bytes, boolean child) {
        return modules.acquire(bytes, child);
    }

    private void install(byte[] bytes, byte[] saved) {
        install(prepare(bytes, saved));
        started = saved != null;
    }

    private void install(Prepared prepared) {
        module = prepared.module();
        vm = prepared.instance();
        source = prepared.source();
        started = false;
    }

    private Prepared prepare(byte[] bytes, byte[] saved) {
        long precheckStart = LoadTimings.start();
        if (WasmImage.minimumMemory(bytes) > config.memoryBytes())
            throw new IllegalArgumentException("Initial memory exceeds CPU capacity");
        if (saved != null)
            checkSnapshot(saved, config.memoryBytes());
        LoadTimings.finish("root.precheck", precheckStart, "bytes=" + bytes.length);

        CompiledModules.Lease compiled = compile(bytes, false);
        try {
            long importsStart = LoadTimings.start();
            WeaveInstance.Builder builder = imports(compiled.module().instance());
            LoadTimings.finish("root.imports", importsStart, "");
            long buildStart = LoadTimings.start();
            WeaveInstance instance = saved == null ? builder.build() : builder.restore(saved);
            LoadTimings.finish("root.instance-build", buildStart,
                "restore=" + (saved != null) + " includes-native-compilation-on-cold-module");
            if (instance.memorySize() > config.memoryBytes()) {
                instance.close();
                throw new IllegalArgumentException("Initial memory exceeds CPU capacity");
            }
            instance.setMemoryLimit(config.memoryBytes());
            return new Prepared(bytes.clone(), compiled, instance);
        } catch (Throwable error) {
            compiled.close();
            throw error;
        }
    }

    @Override
    public ExecutionResult run(long budget) throws Exception {
        tickBudget = budget;
        exit = null;
        vm.setPollBudget(budget);
        vm.setMemoryLimit(config.memoryBytes() - childReservations());
        try {
            WeaveResult result = started ? vm.resume() : vm.call("_start");
            started = true;
            if (replacement != null) {
                Prepared ready = replacement;
                replacement = null;
                byte[] args = nextArgs;
                nextArgs = null;
                closeGuest();
                bootArgs = args;
                replay = false;
                replayImport = null;
                held = null;
                images.clear();
                nextImage = 1;
                install(ready);
                return ExecutionResult.yielded();
            }
            if (exit != null)
                return exit;

            return result.isDone() ? ExecutionResult.stopped() : ExecutionResult.yielded();
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null && cause.getCause() != cause)
                cause = cause.getCause();
            throw new ArchitectureException(
                cause instanceof Error ? StdErrors.E_MCHK : StdErrors.E_TRAP, error);
        }
    }

    private WeaveInstance.Builder define(
        WeaveInstance.Builder builder, String name, String signature, Function function) {
        return builder.define(
            "fw",
            name,
            signature,
            (caller, args) -> {
                long value;
                try {
                    value = function.call(caller, args);
                } catch (GuestMemory.Fault fault) {
                    value = fault.code;
                }
                return signature.endsWith(">") ? new long[0] : new long[]{value};
            });
    }

    private WeaveInstance.Builder imports(WeaveInstance.Builder b) {
        define(b, "time", ">l", (c, a) -> host.time());
        define(b, "reset_reason", ">i", (c, a) -> host.resetReason());
        define(b, "cpuid", "iii>i", (c, a) -> cpuid(new GuestMemory(c), (int) a[0], a[1], a[2]));
        define(
            b,
            "log",
            "ii>",
            (c, a) -> {
                if (a[1] < 0 || a[1] > 4096)
                    throw new GuestMemory.Fault(StdErrors.E_INVAL);
                host.log(new GuestMemory(c).string(a[0], a[1]));
                return 0;
            });
        define(b, "yield", ">", (c, a) -> suspend(c, "yield", ExecutionResult.yielded(), 0));
        define(
            b,
            "hlt",
            "l>i",
            (c, a) -> {
                if (replay && "hlt".equals(replayImport))
                    return resumeImport("hlt", host.wokeByEvent() ? 1 : 0);
                if (a[0] < 0)
                    return StdErrors.E_INVAL;
                if (host.peekEvent() != null)
                    return 1;
                return suspend(c, "hlt", ExecutionResult.waiting(a[0]), 0);
            });
        define(b, "shutdown", ">", (c, a) -> suspend(c, "shutdown", ExecutionResult.stopped(), 0));
        define(b, "reboot", ">", (c, a) -> suspend(c, "reboot", ExecutionResult.reboot(), 0));
        define(b, "dev_slots", ">i", (c, a) -> host.slotCount());
        define(
            b,
            "dev_info",
            "iii>i",
            (c, a) -> {
                DeviceInfo info = host.deviceInfo((int) a[0]);
                if (info == null)
                    return StdErrors.E_NODEV;
                UUID id = info.address();
                byte[] bytes =
                    ByteBuffer.allocate(24)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) info.deviceClass())
                        .putShort((short) info.vendorId())
                        .putShort((short) info.deviceId())
                        .putShort((short) 0)
                        .put(UuidBytes.encode(id))
                        .array();
                return new GuestMemory(c).output(a[1], a[2], bytes);
            });
        define(
            b,
            "dev_describe",
            "iii>i",
            (c, a) -> {
                MethodTable table = host.methods((int) a[0]);
                return table == null
                    ? StdErrors.E_NODEV
                    : new GuestMemory(c).output(a[1], a[2], MethodCodec.describe(table));
            });
        define(b, "dev_call", "iiiiii>i", (c, a) -> deviceCall(new GuestMemory(c), a, false));
        define(b, "dev_submit", "iiii>i", (c, a) -> deviceCall(new GuestMemory(c), a, true));
        define(
            b,
            "dev_result",
            "iii>i",
            (c, a) -> {
                int ticket = (int) a[0];
                CallResult value = ticket == -1 ? held : host.result(ticket);
                if (value == null)
                    return StdErrors.E_BADHANDLE;
                int result = outputResult(new GuestMemory(c), a[1], a[2], value);
                if (value.status() != StdErrors.E_AGAIN
                    && (value.status() < 0 || a[2] >= result)) {
                    if (ticket == -1)
                        held = null;
                    else host.releaseResult(ticket);
                }
                return result;
            });
        define(
            b,
            "poll_event",
            "ii>i",
            (c, a) -> {
                GuestMemory memory = new GuestMemory(c);
                if (a[1] < 0)
                    throw new GuestMemory.Fault(StdErrors.E_INVAL);
                if (a[1] != 0)
                    memory.check(a[0], a[1]);
                MachineEvent event = host.pollEvent(a[1] >= 20 ? (int) a[1] - 20 : -1);
                if (event == null)
                    return 0;
                byte[] payload = event.payload();
                byte[] bytes =
                    ByteBuffer.allocate(20 + payload.length)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((short) event.kind())
                        .putShort((short) event.slot())
                        .putInt(event.parameter())
                        .putLong(event.tick())
                        .putInt(payload.length)
                        .put(payload)
                        .array();
                return memory.output(a[0], a[1], bytes);
            });
        define(
            b,
            "image_load",
            "ii>i",
            (c, a) -> {
                if (images.size() >= config.maxImages())
                    return StdErrors.E_AGAIN;
                byte[] bytes = new GuestMemory(c).read(a[0], a[1]);
                try (CompiledModules.Lease ignored = compile(bytes, false)) {
                    int handle = allocateImage();
                    images.put(handle, bytes);
                    return handle;
                } catch (WasmImage.VersionException e) {
                    return StdErrors.E_VERSION;
                } catch (IllegalArgumentException e) {
                    return StdErrors.E_INVAL;
                } catch (WasmRuntimeException e) {
                    return StdErrors.E_INVAL;
                }
            });
        define(
            b,
            "image_free",
            "i>i",
            (c, a) -> images.remove((int) a[0]) == null ? StdErrors.E_BADHANDLE : 0);
        define(
            b,
            "exec",
            "iii>i",
            (c, a) -> {
                byte[] image = images.get((int) a[0]);
                if (image == null)
                    return StdErrors.E_BADHANDLE;
                if (a[2] < 0 || a[2] > 4096)
                    return StdErrors.E_INVAL;
                byte[] args = new GuestMemory(c).read(a[1], a[2]);
                if (WasmImage.minimumMemory(image) > config.memoryBytes())
                    return StdErrors.E_NOMEM;
                try {
                    replacement = prepare(image, null);
                } catch (WasmRuntimeException | IllegalArgumentException error) {
                    return StdErrors.E_INVAL;
                }
                nextArgs = args;
                return suspend(c, "exec", ExecutionResult.yielded(), 0);
            });
        define(b, "boot_args", "ii>i", (c, a) -> new GuestMemory(c).output(a[0], a[1], bootArgs));
        b.define(
            "co", "co_create", "il>i", (c, a) -> new long[]{createChild(c, (int) a[0], a[1])});
        b.define(
            "co",
            "co_resume",
            "illi>i",
            (c, a) -> {
                try {
                    return new long[]{resumeChild(c, (int) a[0], a[1], a[2], a[3])};
                } catch (GuestMemory.Fault fault) {
                    return new long[]{fault.code};
                }
            });
        b.define(
            "co",
            "co_status",
            "i>i",
            (c, a) ->
                new long[]{
                    children.containsKey((int) a[0])
                        ? children.get((int) a[0]).status
                        : StdErrors.E_BADHANDLE
                });
        b.define(
            "co",
            "co_close",
            "i>i",
            (c, a) -> {
                Child child = children.remove((int) a[0]);
                if (child == null)
                    return new long[]{StdErrors.E_BADHANDLE};
                child.close();
                c.setMemoryLimit(config.memoryBytes() - childReservations());
                return new long[]{0};
            });
        for (boolean poke : new boolean[]{false, true})
            b.define(
                "co",
                poke ? "co_poke" : "co_peek",
                "iiii>i",
                (c, a) -> {
                    Child child = children.get((int) a[0]);
                    if (child == null)
                        return new long[]{StdErrors.E_BADHANDLE};
                    try {
                        GuestMemory mem = new GuestMemory(c);
                        mem.check(a[2], a[3]);
                        long address = GuestMemory.address(a[1]);
                        if (a[3] < 0 || address + a[3] > child.memorySize())
                            return new long[]{StdErrors.E_FAULT};
                        if (poke)
                            child.write(address, mem.read(a[2], a[3]));
                        else mem.write(a[2], child.read(address, (int) a[3]));
                        return new long[]{0};
                    } catch (GuestMemory.Fault fault) {
                        return new long[]{fault.code};
                    }
                });
        return b;
    }

    private long suspend(WeaveCaller caller, String name, ExecutionResult result, long value) {
        if (replay && name.equals(replayImport))
            return resumeImport(name, replayValue);
        replay = true;
        replayImport = name;
        replayValue = value;
        exit = result;
        caller.suspend();
        return value;
    }

    private long resumeImport(String name, long value) {
        replay = false;
        replayImport = null;
        return value;
    }

    private int cpuid(GuestMemory mem, int leaf, long ptr, long cap) {
        byte[] bytes =
            switch (leaf) {
                case 0 -> config.vendor().getBytes(StandardCharsets.UTF_8);
                case 1 -> config.model().getBytes(StandardCharsets.UTF_8);
                case 2 -> "wasm32".getBytes(StandardCharsets.UTF_8);
                case 3 -> "simd,multi-value,bulk-memory,reference-types"
                    .getBytes(StandardCharsets.UTF_8);
                case 0x100 -> ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(tickBudget)
                    .array();
                case 0x101 -> ByteBuffer.allocate(8)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putLong(config.memoryBytes())
                    .array();
                default -> null;
            };
        return bytes == null ? StdErrors.E_INVAL : mem.output(ptr, cap, bytes);
    }

    private int deviceCall(GuestMemory mem, long[] a, boolean async) {
        int slot = (int) a[0], method = (int) a[1];
        MethodTable table = host.methods(slot);
        if (!async)
            held = null;
        if (table == null)
            return StdErrors.E_NODEV;
        if (!table.contains(method))
            return StdErrors.E_NOMETHOD;
        if (table.method(method).async() != async)
            return StdErrors.E_INVAL;

        byte[] bytes = mem.read(a[2], a[3]);
        Object[] args;
        try {
            args = MethodCodec.decodeArguments(table.javaMethod(method), bytes);
        } catch (IllegalArgumentException error) {
            return StdErrors.E_INVAL;
        }
        if (async)
            return host.submit(slot, method, args);

        if (a[5] != 0)
            mem.check(a[4], a[5]);
        held = host.invoke(slot, method, args);

        int result = outputResult(mem, a[4], a[5], held);
        if (held.status() < 0 || a[5] >= result)
            held = null;
        return result;
    }

    private int outputResult(GuestMemory mem, long ptr, long capacity, CallResult value) {
        if (!value.succeeded())
            return value.status();

        byte[] encoded;
        try {
            encoded = value.encoded();
        } catch (IllegalArgumentException error) {
            return StdErrors.E_INTERNAL;
        }
        return mem.output(ptr, capacity, encoded);
    }

    private int allocateImage() {
        while (images.containsKey(nextImage))
            nextImage = nextImage == Integer.MAX_VALUE ? 1 : nextImage + 1;
        return nextImage;
    }

    private long childReservations() {
        long size = 0;
        for (Child child : children.values())
            size += child.limit;
        return size;
    }

    private int createChild(WeaveCaller caller, int image, long requested) {
        byte[] bytes = images.get(image);
        if (bytes == null)
            return StdErrors.E_BADHANDLE;
        if (requested < 1)
            return StdErrors.E_INVAL;
        if (children.size() >= config.maxChildren())
            return StdErrors.E_AGAIN;
        if (requested > config.memoryBytes() - childReservations() - caller.memorySize())
            return StdErrors.E_NOMEM;

        try {
            Child child = new Child(bytes, requested, null);
            while (children.containsKey(nextChild))
                nextChild = nextChild == Integer.MAX_VALUE ? 1 : nextChild + 1;
            int id = nextChild;
            children.put(id, child);
            caller.setMemoryLimit(config.memoryBytes() - childReservations());
            return id;
        } catch (IllegalArgumentException error) {
            return StdErrors.E_NOMEM;
        } catch (WasmRuntimeException error) {
            return StdErrors.E_INVAL;
        }
    }

    private int resumeChild(WeaveCaller caller, int id, long budget, long retval, long info) {
        GuestMemory memory = new GuestMemory(caller);
        memory.check(info, 48);
        Child child = children.get(id);
        if (child == null)
            return StdErrors.E_BADHANDLE;
        if (budget < 0)
            return StdErrors.E_INVAL;
        if (child.status != 0)
            return StdErrors.E_BADHANDLE;

        long remaining = caller.pollBudget(),
            allowed = remaining < 0 ? budget : Math.min(remaining, budget);
        if (allowed == 0) {
            memory.write(info, childInfo(0, 0, new long[4], 0));
            return 0;
        }
        if (child.awaiting && !child.delivering) {
            child.retval = retval;
            child.delivering = true;
        }
        child.instance.setPollBudget(allowed);
        int reason;
        try {
            WeaveResult result =
                child.started ? child.instance.resume() : child.instance.call("_start");
            child.started = true;
            if (result.isDone()) {
                reason = 2;
                child.status = 1;
            } else reason = child.awaiting && !child.delivering ? 1 : 0;
        } catch (WasmRuntimeException error) {
            Throwable cause = error;
            while (cause.getCause() != null && cause.getCause() != cause)
                cause = cause.getCause();
            if (cause instanceof Error fatal)
                throw fatal;
            reason = 3;
            child.status = 2;
        }
        long left = child.instance.pollBudget(),
            used = left < 0 ? allowed : allowed - Math.min(allowed, left);
        if (remaining >= 0)
            caller.setPollBudget(Math.max(0, remaining - used));
        memory.write(
            info,
            childInfo(
                reason,
                reason == 1 ? child.number : reason == 3 ? StdErrors.E_TRAP : 0,
                reason == 1 ? child.arguments : new long[4],
                used));
        return reason;
    }

    @Override
    public byte[] snapshot() throws Exception {
        MapDataCompound data = new MapDataCompound();
        data.putByteArray("source", source);
        data.putBoolean("started", started);
        if (started)
            data.putByteArray("vm", vm.snapshot());
        data.putByteArray("args", bootArgs);
        data.putBoolean("replay", replay);
        data.putString("replayImport", replayImport == null ? "" : replayImport);
        data.putLong("replayValue", replayValue);
        data.putInt("nextImage", nextImage);
        data.putInt("nextChild", nextChild);
        if (held != null)
            data.putCompound("held", SavedValue.save(held));
        data.putCompoundArray(
            "images",
            images.entrySet().stream()
                .map(
                    entry -> {
                        MapDataCompound item = new MapDataCompound();
                        item.putInt("id", entry.getKey());
                        item.putByteArray("source", entry.getValue());
                        return item;
                    })
                .toArray(DataCompound[]::new));

        List<DataCompound> savedChildren = new ArrayList<>();
        for (var entry : children.entrySet()) {
            Child child = entry.getValue();
            MapDataCompound item = new MapDataCompound();
            item.putInt("id", entry.getKey());
            item.putByteArray("source", child.source);
            item.putLong("limit", child.limit);
            item.putInt("status", child.status);
            item.putBoolean("started", child.started);
            item.putBoolean("awaiting", child.awaiting);
            item.putBoolean("delivering", child.delivering);
            item.putLong("retval", child.retval);
            item.putInt("number", child.number);
            item.putLongArray("arguments", child.arguments);
            if (child.started && child.status == 0)
                item.putByteArray("vm", child.instance.snapshot());
            if (child.status != 0 || !child.started)
                item.putByteArray("memory", child.read(0, Math.toIntExact(child.memorySize())));
            savedChildren.add(item);
        }
        data.putCompoundArray("children", savedChildren.toArray(DataCompound[]::new));

        return data.write();
    }

    @Override
    public void restore(byte[] image, byte[] snapshot) throws Exception {
        MapDataCompound data = NbtIO.read(snapshot);
        byte[] code = data.getByteArray("source");
        bootArgs = Objects.requireNonNull(data.getByteArray("args"));
        replay = data.getBoolean("replay");
        replayImport = data.getString("replayImport");
        replayValue = data.getLong("replayValue");
        if (data.contains("held"))
            held = SavedValue.load(data.getCompound("held"));
        nextImage = data.getInt("nextImage");
        nextChild = data.getInt("nextChild");
        DataCompound[] savedImages = data.getCompoundArray("images"),
            savedChildren = data.getCompoundArray("children");
        if (nextImage < 1
            || nextChild < 1
            || savedImages == null
            || savedChildren == null
            || savedImages.length > config.maxImages()
            || savedChildren.length > config.maxChildren())
            throw new IllegalArgumentException("Invalid runtime snapshot");

        for (DataCompound item : savedImages) {
            int id = item.getInt("id");
            if (id < 1
                || images.put(id, Objects.requireNonNull(item.getByteArray("source"))) != null)
                throw new IllegalArgumentException("Invalid image handle");
        }
        install(
            code,
            data.getBoolean("started")
                ? Objects.requireNonNull(data.getByteArray("vm"))
                : null);

        for (DataCompound item : savedChildren) {
            int id = item.getInt("id");
            long limit = item.getLong("limit");
            if (id < 1
                || limit < 1
                || children.containsKey(id)
                || limit > config.memoryBytes() - vm.memorySize() - childReservations())
                throw new IllegalArgumentException("Invalid child reservation");
            Child child = new Child(item.getByteArray("source"), limit, item.getByteArray("vm"));
            child.status = item.getInt("status");
            child.awaiting = item.getBoolean("awaiting");
            child.delivering = item.getBoolean("delivering");
            child.retval = item.getLong("retval");
            child.started = item.getBoolean("started");
            if (child.status != 0) {
                child.frozen = Objects.requireNonNull(item.getByteArray("memory"));
                if (child.frozen.length > limit) {
                    child.close();
                    throw new IllegalArgumentException("Invalid frozen child memory");
                }
            } else if (!child.started) {
                byte[] memory = Objects.requireNonNull(item.getByteArray("memory"));
                if (memory.length != child.instance.memorySize()) {
                    child.close();
                    throw new IllegalArgumentException("Invalid unstarted child memory");
                }
                child.instance.writeMemory(0, memory);
            }
            child.number = item.getInt("number");
            child.arguments = item.getLongArray("arguments");
            if (child.arguments == null
                || child.arguments.length != 4
                || child.status < 0
                || child.status > 2) {
                child.close();
                throw new IllegalArgumentException("Invalid child state");
            }
            children.put(id, child);
        }
        vm.setMemoryLimit(config.memoryBytes() - childReservations());
    }

    private void closeGuest() {
        for (Child child : children.values())
            child.close();
        children.clear();
        if (vm != null) {
            vm.close();
            vm = null;
        }
        if (module != null) {
            module.close();
            module = null;
        }
    }

    @Override
    public void close() {
        if (replacement != null) {
            replacement.instance().close();
            replacement.module().close();
            replacement = null;
        }
        closeGuest();
    }

    private interface Function {
        long call(WeaveCaller caller, long[] args);
    }

    private record Prepared(byte[] source, CompiledModules.Lease module, WeaveInstance instance) {
    }

    private final class Child implements AutoCloseable {
        final byte[] source;
        final long limit;
        final CompiledModules.Lease module;
        final WeaveInstance instance;
        boolean started, awaiting, delivering;
        long retval;
        int status, number;
        long[] arguments = new long[4];
        byte[] frozen;

        Child(byte[] source, long limit, byte[] snapshot) {
            if (WasmImage.minimumMemory(source) > limit)
                throw new IllegalArgumentException("Initial child memory exceeds reservation");
            if (snapshot != null)
                checkSnapshot(snapshot, limit);
            this.source = source.clone();
            this.limit = limit;
            this.module = compile(source, true);
            try {
                var builder =
                    module.module()
                        .instance()
                        .define(
                            "sys",
                            "call",
                            "illll>l",
                            (caller, args) -> {
                                if (delivering) {
                                    delivering = false;
                                    awaiting = false;
                                    return new long[]{retval};
                                }
                                awaiting = true;
                                number = (int) args[0];
                                System.arraycopy(args, 1, arguments, 0, 4);
                                caller.suspend();
                                return new long[]{0};
                            });
                instance = snapshot == null ? builder.build() : builder.restore(snapshot);
                if (instance.memorySize() > limit) {
                    instance.close();
                    throw new IllegalArgumentException("Child memory exceeds reservation");
                }
                instance.setMemoryLimit(limit);
                started = snapshot != null;
            } catch (Throwable error) {
                module.close();
                throw error;
            }
        }

        long memorySize() {
            return frozen == null ? instance.memorySize() : frozen.length;
        }

        byte[] read(long offset, int length) {
            return frozen == null
                ? instance.readMemory(offset, length)
                : Arrays.copyOfRange(frozen, (int) offset, (int) offset + length);
        }

        void write(long offset, byte[] data) {
            if (frozen == null)
                instance.writeMemory(offset, data);
            else System.arraycopy(data, 0, frozen, (int) offset, data.length);
        }

        public void close() {
            instance.close();
            module.close();
        }
    }
}
