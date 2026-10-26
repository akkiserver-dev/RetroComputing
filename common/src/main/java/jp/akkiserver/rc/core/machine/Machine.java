package jp.akkiserver.rc.core.machine;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.device.BootSource;
import jp.akkiserver.rc.api.hardware.device.Device;
import jp.akkiserver.rc.api.hardware.device.DeviceContext;
import jp.akkiserver.rc.api.hardware.device.DeviceInfo;
import jp.akkiserver.rc.api.hardware.device.DeviceStorage;
import jp.akkiserver.rc.api.runtime.StdErrors;
import jp.akkiserver.rc.core.architecture.Architecture;
import jp.akkiserver.rc.core.architecture.ArchitectureException;
import jp.akkiserver.rc.core.architecture.ArchitectureFactory;
import jp.akkiserver.rc.core.architecture.ArchitectureHost;
import jp.akkiserver.rc.core.architecture.ExecutionResult;
import jp.akkiserver.rc.core.data.MapDataCompound;
import jp.akkiserver.rc.core.runtime.CallResult;
import jp.akkiserver.rc.core.runtime.MethodCodec;
import jp.akkiserver.rc.core.runtime.MethodInvoker;
import jp.akkiserver.rc.core.runtime.MethodTable;
import jp.akkiserver.rc.core.runtime.SavedValue;
import jp.akkiserver.rc.core.storage.MachineStore;
import jp.akkiserver.rc.core.storage.MemoryStorage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Language-independent machine controller. Call tick on one server/emulator thread. Requests are
 * safe from other threads. A worker never mutates the physical bus.
 */
public final class Machine {
    private final MachineConfig config;
    private final ArchitectureFactory factory;
    private final Executor workers;
    private final Function<UUID, DeviceStorage> storage;
    private final Consumer<String> logger;
    private final EventQueue events;
    private final Slot[] slots;
    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Map<Integer, CallResult> results = new HashMap<>();
    private final Map<UUID, Long> lastDeviceError = new HashMap<>();
    private final ArchitectureHost host = new Host();
    private volatile State state = State.OFF;
    private volatile long time;
    private volatile Throwable failure;
    private volatile int resetReason;
    private int pendingResetReason;
    private boolean devicesPowered;
    private volatile boolean wokeByEvent;
    private long wakeAt = Long.MAX_VALUE;
    private int nextTicket = 1;
    private Architecture architecture;
    private byte[] bootImage = new byte[0];
    private Job job;
    private Thread tickThread;
    private int logCount;
    /* A request may schedule a worker job; its future then follows that job. */
    private CompletableFuture<?> activeRequest;

    public Machine(MachineConfig config, ArchitectureFactory factory, Executor workers) {
        this(config, factory, workers, memoryStores(), message -> {
        });
    }

    public Machine(
        MachineConfig config,
        ArchitectureFactory factory,
        Executor workers,
        Function<UUID, DeviceStorage> storage,
        Consumer<String> logger) {
        this.config = Objects.requireNonNull(config);
        this.factory = Objects.requireNonNull(factory);
        if (factory.id() == null || factory.id().isBlank())
            throw new IllegalArgumentException("architecture id");
        this.workers = Objects.requireNonNull(workers);
        this.storage = Objects.requireNonNull(storage);
        this.logger = Objects.requireNonNull(logger);
        this.slots = new Slot[config.slots()];
        this.events = new EventQueue(config.eventCapacity());
    }

    private static Function<UUID, DeviceStorage> memoryStores() {
        Map<UUID, DeviceStorage> stores = new HashMap<>();
        return id -> stores.computeIfAbsent(id, ignored -> new MemoryStorage());
    }

    public State state() {
        return state;
    }

    public long time() {
        return time;
    }

    public Throwable failure() {
        return failure;
    }

    public String architectureId() {
        return factory.id();
    }

    public CompletableFuture<Void> requestPowerOn() {
        return enqueue(() -> start(null, 0));
    }

    /**
     * Explicit boot bytes are useful for emulators without a physical boot device.
     */
    public CompletableFuture<Void> requestPowerOn(byte[] image) {
        byte[] copy = Objects.requireNonNull(image).clone();
        return enqueue(() -> start(copy, 0));
    }

    public CompletableFuture<Void> requestPowerOff() {
        return enqueue(() -> stop(false));
    }

    public CompletableFuture<Void> requestReboot() {
        return enqueue(() -> stop(true));
    }

    public CompletableFuture<Void> requestAttach(int slot, Device device) {
        Objects.requireNonNull(device);
        return enqueue(() -> attach(slot, device));
    }

    public CompletableFuture<Void> requestDetach(int slot) {
        return enqueue(() -> detach(slot));
    }

    /**
     * Runs a short host operation at a safe tick boundary, e.g. copying display updates.
     */
    public CompletableFuture<Void> requestHostAction(Runnable action) {
        return enqueue(Objects.requireNonNull(action));
    }

    /**
     * Device copying happens on tick; runtime copying happens on the idle worker.
     */
    public CompletableFuture<MachineSnapshot> requestSnapshot() {
        CompletableFuture<MachineSnapshot> future = new CompletableFuture<>();
        requests.add(
            new Request(
                () -> {
                    MapDataCompound data = captureHost();
                    if (!data.getBoolean("powered")) {
                        data.putByteArray("runtime", new byte[0]);
                        activeRequest = null;
                        future.complete(new MachineSnapshot(data));
                        return;
                    }
                    schedule(
                        () -> architecture.snapshot(),
                        value -> {
                            data.putByteArray("runtime", (byte[]) value);
                            activeRequest = null;
                            future.complete(new MachineSnapshot(data));
                        },
                        error -> {
                            activeRequest = null;
                            future.completeExceptionally(error);
                        });
                },
                future));
        return future;
    }

    /**
     * Holds execution until runtime, devices and journal generation have been committed.
     */
    public CompletableFuture<Void> requestSave(MachineStore store) {
        Objects.requireNonNull(store);
        return enqueue(
            () -> {
                MapDataCompound data = captureHost();
                schedule(
                    () -> {
                        data.putByteArray(
                            "runtime",
                            data.getBoolean("powered")
                                ? architecture.snapshot()
                                : new byte[0]);
                        store.save(new MachineSnapshot(data));
                        return null;
                    },
                    ignored -> completeActive(null),
                    this::completeActive);
            });
    }

    public CompletableFuture<Void> requestRestore(MachineSnapshot snapshot) {
        MapDataCompound saved = Objects.requireNonNull(snapshot).data();
        return enqueue(
            () -> {
                if (state != State.OFF || architecture != null)
                    throw new IllegalStateException("Restore requires power off");

                if (!factory.id().equals(saved.getString("architecture")))
                    throw new IllegalArgumentException("Architecture mismatch");

                if (saved.getInt("slots") != slots.length)
                    throw new IllegalArgumentException("Slot count mismatch");

                try {
                    if (saved.getBoolean("powered")) setDevicePower(true);
                    restoreHost(saved);
                } catch (Throwable error) {
                    try {
                        setDevicePower(false);
                    } catch (Throwable cleanup) {
                        if (cleanup != error) error.addSuppressed(cleanup);
                    }
                    throw error;
                }
                failure = null;
                if (!saved.getBoolean("powered")) {
                    state = saved.getBoolean("failed") ? State.FAILED : State.OFF;
                    return;
                }
                state = State.STARTING;
                schedule(
                    () -> {
                        Architecture created = factory.create(host);
                        try {
                            created.restore(
                                bootImage.clone(), saved.getByteArray("runtime"));
                            return created;
                        } catch (Throwable error) {
                            try {
                                created.close();
                            } catch (Throwable close) {
                                error.addSuppressed(close);
                            }
                            throw error;
                        }
                    },
                    value -> {
                        architecture = (Architecture) value;
                        state = saved.getBoolean("waiting") ? State.WAITING : State.RUNNING;
                        completeActive(null);
                    },
                    this::fail);
            });
    }

    private MapDataCompound captureHost() {
        MapDataCompound data = new MapDataCompound();
        data.putInt("version", 1);
        data.putBoolean("powered", state == State.RUNNING || state == State.WAITING);
        data.putBoolean("failed", state == State.FAILED);
        data.putString("architecture", factory.id());
        data.putInt("slots", slots.length);
        data.putByteArray("boot", bootImage);
        data.putLong("time", time);
        data.putInt("resetReason", resetReason);
        data.putInt("pendingResetReason", pendingResetReason);
        data.putBoolean("waiting", state == State.WAITING);
        data.putLong("wakeAt", wakeAt);
        data.putBoolean("wokeByEvent", wokeByEvent);
        data.putInt("nextTicket", nextTicket);
        List<DataCompound> devices = new ArrayList<>();
        for (int i = 0; i < slots.length; i++)
            if (slots[i] != null) {
                Device device = slots[i].device();
                MapDataCompound item = new MapDataCompound(), state = new MapDataCompound();
                device.save(state);
                if (state.contains("_version"))
                    throw new IllegalArgumentException("_version is reserved for the framework");
                state.putInt("_version", device.dataVersion());
                item.putInt("slot", i);
                item.putUUID("uuid", device.info().address());
                item.putInt("version", device.dataVersion());
                item.putCompound("state", state);
                devices.add(item);
            }
        data.putCompoundArray("devices", devices.toArray(DataCompound[]::new));
        data.putCompoundArray(
            "events",
            events.snapshot().stream()
                .map(
                    event -> {
                        MapDataCompound item = new MapDataCompound();
                        item.putInt("kind", event.kind());
                        item.putInt("slot", event.slot());
                        item.putInt("parameter", event.parameter());
                        item.putLong("tick", event.tick());
                        item.putByteArray("payload", event.payload());
                        return item;
                    })
                .toArray(DataCompound[]::new));
        data.putCompoundArray(
            "pending",
            pending.stream()
                .map(
                    call -> {
                        MapDataCompound item = new MapDataCompound();
                        int index = -1;
                        for (int i = 0; i < slots.length; i++)
                            if (slots[i] == call.slot())
                                index = i;
                        item.putInt("slot", index);
                        item.putUUID("uuid", call.slot().device().info().address());
                        item.putInt("ticket", call.ticket());
                        item.putInt("method", call.method());
                        item.putByteArray(
                            "args",
                            MethodCodec.encodeArguments(
                                call.slot()
                                    .methods()
                                    .javaMethod(call.method())
                                    .getParameterTypes(),
                                call.args()));
                        return item;
                    })
                .toArray(DataCompound[]::new));
        data.putCompoundArray(
            "results",
            results.entrySet().stream()
                .map(
                    entry -> {
                        MapDataCompound item = new MapDataCompound();
                        item.putInt("ticket", entry.getKey());
                        item.putCompound("value", SavedValue.save(entry.getValue()));
                        return item;
                    })
                .toArray(DataCompound[]::new));
        return data;
    }

    private void restoreHost(MapDataCompound data) {
        long savedTime = data.getLong("time");
        int ticket = data.getInt("nextTicket");
        DataCompound[] savedDevices = data.getCompoundArray("devices"),
            savedEvents = data.getCompoundArray("events"),
            savedPending = data.getCompoundArray("pending"),
            savedResults = data.getCompoundArray("results");
        if (savedTime < 0
            || ticket < 1
            || savedDevices == null
            || savedEvents == null
            || savedPending == null
            || savedResults == null
            || savedPending.length + savedResults.length > config.maxTickets())
            throw new IllegalArgumentException("Invalid snapshot host state");
        List<MachineEvent> restoredEvents = new ArrayList<>();
        for (DataCompound event : savedEvents)
            restoredEvents.add(
                new MachineEvent(
                    event.getInt("kind"),
                    event.getInt("slot"),
                    event.getInt("parameter"),
                    event.getLong("tick"),
                    event.getByteArray("payload")));
        EventQueue restoredQueue = new EventQueue(config.eventCapacity());
        restoredQueue.restore(restoredEvents);
        Map<Integer, CallResult> restoredResults = new HashMap<>();
        ArrayDeque<Pending> restoredPending = new ArrayDeque<>();
        Set<Integer> tickets = new HashSet<>();
        for (DataCompound item : savedResults) {
            int id = item.getInt("ticket");
            if (id < 1 || !tickets.add(id))
                throw new IllegalArgumentException("Invalid saved ticket");
            restoredResults.put(id, SavedValue.load(item.getCompound("value")));
        }
        for (DataCompound item : savedPending) {
            int id = item.getInt("ticket"),
                index = item.getInt("slot"),
                method = item.getInt("method");
            if (id < 1 || !tickets.add(id))
                throw new IllegalArgumentException("Invalid saved ticket");
            Slot slot = index >= 0 && index < slots.length ? slots[index] : null;
            if (slot == null || !slot.device().info().address().equals(item.getUUID("uuid"))) {
                restoredResults.put(id, CallResult.failure(StdErrors.E_NODEV));
                restoredQueue.add(
                    new MachineEvent(
                        MachineEvent.COMPLETE,
                        0xffff,
                        id,
                        savedTime,
                        ByteBuffer.allocate(4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(StdErrors.E_NODEV)
                            .array()));
            } else {
                if (!slot.methods().contains(method) || !slot.methods().method(method).async())
                    throw new IllegalArgumentException("Method changed");
                restoredPending.add(
                    new Pending(
                        id,
                        slot,
                        method,
                        MethodCodec.decodeArguments(
                            slot.methods().javaMethod(method),
                            item.getByteArray("args"))));
            }
        }
        for (DataCompound item : savedDevices) {
            int index = item.getInt("slot");
            checkSlot(index);
            Slot slot = slots[index];
            if (slot != null && slot.device().info().address().equals(item.getUUID("uuid"))) {
                Device device = slot.device();
                DataCompound state = item.getCompound("state");
                int version = item.getInt("version");
                if (state == null || version > device.dataVersion())
                    throw new IllegalArgumentException("Unsupported device data version");
                if (version != device.dataVersion())
                    device.migrate(state, version);
                state.putInt("_version", device.dataVersion());
                device.load(state);
            } else
                restoredQueue.add(
                    new MachineEvent(MachineEvent.DETACH, index, 0, savedTime, new byte[0]));
        }
        for (int i = 0; i < slots.length; i++)
            if (slots[i] != null) {
                int index = i;
                if (Arrays.stream(savedDevices)
                    .noneMatch(
                        item ->
                            item.getInt("slot") == index
                                && slots[index]
                                .device()
                                .info()
                                .address()
                                .equals(item.getUUID("uuid"))))
                    restoredQueue.add(
                        new MachineEvent(MachineEvent.ATTACH, i, 0, savedTime, new byte[0]));
            }
        bootImage = data.getByteArray("boot");
        time = savedTime;
        resetReason = data.getInt("resetReason");
        pendingResetReason = data.getInt("pendingResetReason");
        lastDeviceError.clear();
        wakeAt = data.getLong("wakeAt");
        wokeByEvent = data.getBoolean("wokeByEvent");
        nextTicket = ticket;
        pending.clear();
        pending.addAll(restoredPending);
        results.clear();
        results.putAll(restoredResults);
        events.restore(restoredQueue.snapshot());
    }

    private CompletableFuture<Void> enqueue(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        requests.add(new Request(action, future));
        return future;
    }

    /**
     * Never joins an executing worker; budget is not accumulated on skipped ticks.
     */
    public synchronized void tick() {
        if (tickThread == null)
            tickThread = Thread.currentThread();
        if (tickThread != Thread.currentThread())
            throw new IllegalStateException("tick thread changed");
        if (time != Long.MAX_VALUE)
            time++;
        logCount = 0;
        if (job != null) {
            if (!job.task().isDone()) return;
            Job finished = job;
            job = null;
            try {
                finished.success().accept(finished.task().get());
            } catch (ExecutionException e) {
                finished.failure().accept(e.getCause());
            } catch (Throwable e) {
                finished.failure().accept(e);
            }
            if (job != null) return;
        }
        Request request;
        while ((request = requests.poll()) != null) {
            activeRequest = request.future();
            try {
                request.action().run();
                if (job == null)
                    completeActive(null);
            } catch (Throwable e) {
                if (e instanceof Error)
                    fail(e);
                else completeActive(e);
            }
            if (job != null) return;
        }
        if (state != State.RUNNING && state != State.WAITING)
            return;
        while (!pending.isEmpty()) {
            Pending call = pending.removeFirst();
            CallResult result;
            try {
                result =
                    contains(call.slot())
                        ? invokeDevice(call.slot(), call.method(), call.args())
                        : CallResult.failure(StdErrors.E_NODEV);
            } catch (Throwable error) {
                fail(error);
                return;
            }
            results.put(call.ticket(), result);
            events.add(completion(call.ticket(), result.status()));
        }
        if (state == State.WAITING) {
            if (events.isEmpty() && time < wakeAt)
                return;
            wokeByEvent = !events.isEmpty();
            state = State.RUNNING;
        }
        if (state == State.RUNNING)
            schedule(
                () -> architecture.run(config.budgetPerTick()),
                value -> finishedRun((ExecutionResult) value),
                this::fail);
    }

    private void finishedRun(ExecutionResult result) {
        switch (result.reason()) {
            case YIELDED -> state = State.RUNNING;
            case WAITING -> {
                state = State.WAITING;
                wakeAt =
                    result.timeoutTicks() == 0 || Long.MAX_VALUE - time < result.timeoutTicks()
                        ? Long.MAX_VALUE
                        : time + result.timeoutTicks();
            }
            case STOPPED -> stop(false);
            case REBOOT -> stop(true);
        }
    }

    private void start(byte[] explicit, int reason) {
        if (state != State.OFF && state != State.FAILED)
            throw new IllegalStateException("Machine is powered");
        if (architecture != null)
            throw new IllegalStateException("Dispose the failed architecture first");
        byte[] image = explicit;
        if (image == null) {
            for (Slot slot : slots)
                if (slot != null && slot.device() instanceof BootSource source) {
                    image = source.bootImage();
                    break;
                }
        }
        if (image == null || image.length == 0)
            throw new IllegalStateException("No boot image");
        bootImage = image.clone();
        resetReason = reason == 0 ? pendingResetReason : reason;
        pendingResetReason = 0;
        failure = null;
        events.clear();
        pending.clear();
        results.clear();
        nextTicket = 1;
        wokeByEvent = false;
        state = State.STARTING;
        lastDeviceError.clear();
        try {
            setDevicePower(true);
        } catch (Throwable error) {
            fail(error);
            return;
        }
        schedule(
            () -> {
                Architecture created = factory.create(host);
                try {
                    created.boot(bootImage.clone());
                    return created;
                } catch (Throwable error) {
                    try {
                        created.close();
                    } catch (Throwable close) {
                        error.addSuppressed(close);
                    }
                    throw error;
                }
            },
            value -> {
                architecture = (Architecture) value;
                state = State.RUNNING;
                completeActive(null);
            },
            this::fail);
    }

    private void stop(boolean reboot) {
        if (architecture == null) {
            state = State.OFF;
            if (reboot)
                start(null, 1);
            return;
        }
        state = State.STOPPING;
        Architecture closing = architecture;
        schedule(
            () -> {
                closing.close();
                return null;
            },
            ignored -> {
                architecture = null;
                state = State.OFF;
                setDevicePower(false);
                pending.clear();
                results.clear();
                events.clear();
                if (reboot)
                    start(bootImage, 1);
                else completeActive(null);
            },
            error -> {
                architecture = null;
                fail(error);
            });
    }

    private void fail(Throwable error) {
        failure = error;
        resetReason = error instanceof ArchitectureException a ? a.resetReason() : StdErrors.E_MCHK;
        pendingResetReason = resetReason;
        try {
            setDevicePower(false);
        } catch (Throwable powerError) {
            if (powerError != error)
                error.addSuppressed(powerError);
        }
        state = State.FAILED;
        completeActive(error);
    }

    private void completeActive(Throwable error) {
        CompletableFuture<?> future = activeRequest;
        activeRequest = null;
        if (future != null) {
            if (error == null)
                future.complete(null);
            else future.completeExceptionally(error);
        }
    }

    private void setDevicePower(boolean powered) {
        if (devicesPowered == powered)
            return;
        devicesPowered = powered;
        Throwable failure = null;
        for (Slot slot : slots)
            if (slot != null)
                try {
                    slot.device().powerChanged(powered);
                } catch (Throwable error) {
                    if (failure == null)
                        failure = error;
                    else if (error instanceof Error && !(failure instanceof Error)) {
                        error.addSuppressed(failure);
                        failure = error;
                    } else if (error != failure) failure.addSuppressed(error);
                }
        if (failure instanceof Error error)
            throw error;
        if (failure instanceof RuntimeException error)
            throw error;
    }

    private <T> void schedule(
        Callable<T> action, Consumer<Object> success, Consumer<Throwable> failure) {
        if (job != null)
            throw new IllegalStateException("Concurrent machine operation");
        FutureTask<T> task = new FutureTask<>(action);
        job = new Job(task, success, failure);
        try {
            workers.execute(task);
        } catch (Throwable error) {
            job = null;
            failure.accept(error);
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= slots.length)
            throw new IllegalArgumentException("slot");
    }

    private boolean powered() {
        return state == State.RUNNING || state == State.WAITING || state == State.STARTING;
    }

    private boolean contains(Slot wanted) {
        for (Slot slot : slots)
            if (slot == wanted)
                return true;
        return false;
    }

    private void attach(int index, Device device) {
        checkSlot(index);
        if (slots[index] != null)
            throw new IllegalStateException("Occupied slot");
        MethodTable table = MethodTable.create(device);
        Objects.requireNonNull(device.info());
        DeviceStorage store = Objects.requireNonNull(storage.apply(device.info().address()));
        DeviceContext context =
            new DeviceContext() {
                public int slot() {
                    return index;
                }

                public void signal(byte[] payload) {
                    Objects.requireNonNull(payload);
                    synchronized (Machine.this) {
                        Slot slot = slots[index];
                        if (slot != null && slot.context() == this && powered())
                            events.add(
                                new MachineEvent(
                                    MachineEvent.SIGNAL, index, 0, time, payload));
                    }
                }

                public DeviceStorage storage() {
                    return store;
                }

                public boolean hasPower() {
                    return powered();
                }
            };
        Slot added = new Slot(device, table, context);
        slots[index] = added;
        try {
            device.attached(context);
            if (devicesPowered)
                device.powerChanged(true);
        } catch (Throwable error) {
            slots[index] = null;
            try {
                device.powerChanged(false);
                device.detached();
            } catch (Throwable cleanup) {
                if (cleanup != error)
                    error.addSuppressed(cleanup);
            }
            throw error;
        }
        if (powered())
            events.add(new MachineEvent(MachineEvent.ATTACH, index, 0, time, new byte[0]));
    }

    private void detach(int index) {
        checkSlot(index);
        Slot previous = slots[index];
        if (previous == null)
            return;
        slots[index] = null;
        try {
            if (devicesPowered)
                previous.device().powerChanged(false);
            previous.device().detached();
        } finally {
            if (powered())
                events.add(new MachineEvent(MachineEvent.DETACH, index, 0, time, new byte[0]));
        }
    }

    private MachineEvent completion(int ticket, int status) {
        return new MachineEvent(
            MachineEvent.COMPLETE,
            0xffff,
            ticket,
            time,
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(status).array());
    }

    private boolean hasPending(int ticket) {
        for (Pending p : pending)
            if (p.ticket() == ticket)
                return true;
        return false;
    }

    private CallResult invokeDevice(Slot slot, int method, Object[] args) {
        return new MethodInvoker(
            error -> {
                UUID id = slot.device().info().address();
                synchronized (Machine.this) {
                    Long previous = lastDeviceError.get(id);
                    if (previous != null && time - previous < 1200)
                        return;
                    lastDeviceError.put(id, time);
                }
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                logger.accept(
                    "Device " + id + " method " + method + " failed: " + trace);
            })
            .invoke(slot.methods(), method, args);
    }

    public enum State {
        OFF,
        STARTING,
        RUNNING,
        WAITING,
        STOPPING,
        FAILED
    }

    private record Slot(Device device, MethodTable methods, DeviceContext context) {
    }

    private record Request(Runnable action, CompletableFuture<?> future) {
    }

    private record Pending(int ticket, Slot slot, int method, Object[] args) {
    }

    private record Job(FutureTask<?> task, Consumer<Object> success, Consumer<Throwable> failure) {
    }

    private final class Host implements ArchitectureHost {
        public long time() {
            return time;
        }

        public int resetReason() {
            return resetReason;
        }

        public int slotCount() {
            return slots.length;
        }

        private Slot slot(int index) {
            return index >= 0 && index < slots.length ? slots[index] : null;
        }

        public DeviceInfo deviceInfo(int index) {
            Slot slot = slot(index);
            return slot == null ? null : slot.device().info();
        }

        public MethodTable methods(int index) {
            Slot slot = slot(index);
            return slot == null ? null : slot.methods();
        }

        public CallResult invoke(int index, int method, Object[] arguments) {
            Slot slot = slot(index);
            if (slot == null) return CallResult.failure(StdErrors.E_NODEV);
            if (!slot.methods().contains(method)) return CallResult.failure(StdErrors.E_NOMETHOD);
            if (slot.methods().method(method).async()) return CallResult.failure(StdErrors.E_INVAL);
            return invokeDevice(slot, method, arguments);
        }

        public int submit(int index, int method, Object[] arguments) {
            Slot slot = slot(index);
            if (slot == null) return StdErrors.E_NODEV;
            if (!slot.methods().contains(method)) return StdErrors.E_NOMETHOD;
            if (!slot.methods().method(method).async()) return StdErrors.E_INVAL;
            Object[] copy;
            try {
                Class<?>[] types = slot.methods().javaMethod(method).getParameterTypes();
                copy =
                    MethodCodec.decodeArguments(
                        types, MethodCodec.encodeArguments(types, arguments));
            } catch (IllegalArgumentException | NullPointerException error) {
                return StdErrors.E_INVAL;
            }
            synchronized (Machine.this) {
                if (pending.size() + results.size() >= config.maxTickets())
                    return StdErrors.E_AGAIN;
                int ticket = nextTicket;
                do {
                    nextTicket = nextTicket == Integer.MAX_VALUE ? 1 : nextTicket + 1;
                } while (results.containsKey(nextTicket) || hasPending(nextTicket));
                pending.addLast(new Pending(ticket, slot, method, copy));
                return ticket;
            }
        }

        public CallResult result(int ticket) {
            synchronized (Machine.this) {
                CallResult result = results.get(ticket);
                return result != null
                    ? result
                    : CallResult.failure(
                    hasPending(ticket) ? StdErrors.E_AGAIN : StdErrors.E_BADHANDLE);
            }
        }

        public void releaseResult(int ticket) {
            synchronized (Machine.this) {
                results.remove(ticket);
            }
        }

        public MachineEvent peekEvent() {
            return events.peek();
        }

        public MachineEvent pollEvent() {
            return events.poll();
        }

        public MachineEvent pollEvent(int maxPayloadBytes) {
            return events.poll(maxPayloadBytes);
        }

        public boolean wokeByEvent() {
            return wokeByEvent;
        }

        public void log(String message) {
            Objects.requireNonNull(message);
            synchronized (Machine.this) {
                if (logCount++ >= 64) return;
            }
            logger.accept(message.length() > 4096 ? message.substring(0, 4096) : message);
        }
    }
}
