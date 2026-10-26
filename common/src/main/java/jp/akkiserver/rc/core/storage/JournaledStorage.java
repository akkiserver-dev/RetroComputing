package jp.akkiserver.rc.core.storage;

import jp.akkiserver.rc.api.data.DataCompound;
import jp.akkiserver.rc.api.hardware.device.DeviceStorage;
import jp.akkiserver.rc.core.data.MapDataCompound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 4 KiB copy-on-write overlay. Only a committed machine generation may replay a journal.
 */
public final class JournaledStorage implements DeviceStorage {
    public static final int BLOCK_SIZE = 4096;
    private final DirectStorage base;
    private final Map<String, Overlay> overlays = new TreeMap<>();

    public JournaledStorage(Path root) throws IOException {
        base = new DirectStorage(root);
    }

    private static void name(String name) throws IOException {
        Path path = Path.of(name);
        if (path.isAbsolute()
            || name.isBlank()
            || path.normalize().startsWith("..")
            || name.contains(":")) throw new IOException("Invalid name");
    }

    private Overlay overlay(String name) throws IOException {
        name(name);
        Overlay value = overlays.get(name);
        if (value == null) {
            value = new Overlay();
            if (base.exists(name))
                try (var ch = base.open(name, StandardOpenOption.READ)) {
                    value.size = value.baseSize = ch.size();
                }
            overlays.put(name, value);
        }
        return value;
    }

    private byte[] block(String name, Overlay overlay, long index) throws IOException {
        byte[] value = overlay.blocks.get(index);
        if (value != null)
            return value;
        value = new byte[BLOCK_SIZE];
        long start = index * BLOCK_SIZE;
        if (start < overlay.baseSize && base.exists(name))
            try (var channel = base.open(name, StandardOpenOption.READ)) {
                channel.position(start);
                ByteBuffer bytes =
                    ByteBuffer.wrap(
                        value, 0, (int) Math.min(BLOCK_SIZE, overlay.baseSize - start));
                while (bytes.hasRemaining()) {
                    int n = channel.read(bytes);
                    if (n < 0) break;
                    if (n == 0) throw new IOException("No read progress");
                }
            }
        return value;
    }

    @Override
    public synchronized SeekableByteChannel open(String name, OpenOption... options)
        throws IOException {
        name(name);
        Set<OpenOption> flags = new HashSet<>(List.of(options));
        boolean writable =
            flags.contains(StandardOpenOption.WRITE)
                || flags.contains(StandardOpenOption.APPEND);
        boolean readable = flags.isEmpty() || flags.contains(StandardOpenOption.READ),
            append = flags.contains(StandardOpenOption.APPEND);
        for (OpenOption flag : flags)
            if (flag != StandardOpenOption.READ
                && flag != StandardOpenOption.WRITE
                && flag != StandardOpenOption.APPEND
                && flag != StandardOpenOption.CREATE
                && flag != StandardOpenOption.CREATE_NEW
                && flag != StandardOpenOption.TRUNCATE_EXISTING)
                throw new UnsupportedOperationException("Unsupported journal option: " + flag);
        if (append && (readable || flags.contains(StandardOpenOption.TRUNCATE_EXISTING)))
            throw new IllegalArgumentException("Invalid append options");
        boolean exists = exists(name);
        if (exists && writable && flags.contains(StandardOpenOption.CREATE_NEW))
            throw new FileAlreadyExistsException(name);
        if (!exists
            && (!writable
            || !flags.contains(StandardOpenOption.CREATE)
            && !flags.contains(StandardOpenOption.CREATE_NEW)))
            throw new NoSuchFileException(name);
        Overlay file = overlay(name);
        if (!exists) {
            file.deleted = false;
            file.truncated = true;
            file.size = file.baseSize = 0;
            file.blocks.clear();
        }
        if (writable && flags.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
            file.truncated = true;
            file.size = file.baseSize = 0;
            file.blocks.clear();
        }
        return new SeekableByteChannel() {
            boolean open = true;
            long position;

            void check() throws IOException {
                if (!open)
                    throw new ClosedChannelException();
                if (file.deleted)
                    throw new NoSuchFileException(name);
            }

            public int read(ByteBuffer dst) throws IOException {
                synchronized (JournaledStorage.this) {
                    check();
                    if (!readable)
                        throw new NonReadableChannelException();
                    if (!dst.hasRemaining())
                        return 0;
                    if (position >= file.size)
                        return -1;
                    int total = (int) Math.min(dst.remaining(), file.size - position), left = total;
                    while (left > 0) {
                        int off = (int) (position % BLOCK_SIZE),
                            n = Math.min(left, BLOCK_SIZE - off);
                        dst.put(block(name, file, position / BLOCK_SIZE), off, n);
                        position += n;
                        left -= n;
                    }
                    return total;
                }
            }

            public int write(ByteBuffer src) throws IOException {
                synchronized (JournaledStorage.this) {
                    check();
                    if (!writable)
                        throw new NonWritableChannelException();
                    if (append) position = file.size;
                    int total = src.remaining();
                    if (position > Long.MAX_VALUE - total)
                        throw new IOException("File too large");
                    while (src.hasRemaining()) {
                        long index = position / BLOCK_SIZE;
                        int off = (int) (position % BLOCK_SIZE),
                            n = Math.min(src.remaining(), BLOCK_SIZE - off);
                        byte[] bytes = block(name, file, index);
                        src.get(bytes, off, n);
                        file.blocks.put(index, bytes);
                        position += n;
                    }
                    file.size = Math.max(file.size, position);
                    return total;
                }
            }

            public long position() throws IOException {
                synchronized (JournaledStorage.this) {
                    check();
                    return position;
                }
            }

            public SeekableByteChannel position(long value) throws IOException {
                synchronized (JournaledStorage.this) {
                    check();
                    if (value < 0)
                        throw new IllegalArgumentException();
                    position = value;
                    return this;
                }
            }

            public long size() throws IOException {
                synchronized (JournaledStorage.this) {
                    check();
                    return file.size;
                }
            }

            public SeekableByteChannel truncate(long size) throws IOException {
                synchronized (JournaledStorage.this) {
                    check();
                    if (!writable)
                        throw new NonWritableChannelException();
                    if (size < 0)
                        throw new IllegalArgumentException();
                    if (size < file.size) {
                        // Materialize the retained prefix of a partial block before removing its
                        // tail.
                        if (size % BLOCK_SIZE != 0) {
                            long index = size / BLOCK_SIZE;
                            byte[] bytes = block(name, file, index);
                            Arrays.fill(bytes, (int) (size % BLOCK_SIZE), BLOCK_SIZE, (byte) 0);
                            file.blocks.put(index, bytes);
                        }
                        file.blocks
                            .keySet()
                            .removeIf(index -> index >= (size + BLOCK_SIZE - 1) / BLOCK_SIZE);
                        file.size = size;
                        // The durable truncate is replayed before later extending writes.
                        file.baseSize = Math.min(file.baseSize, size);
                        file.truncated = true;
                    }
                    position = Math.min(position, size);
                    return this;
                }
            }

            public boolean isOpen() {
                synchronized (JournaledStorage.this) {
                    return open;
                }
            }

            public void close() {
                synchronized (JournaledStorage.this) {
                    open = false;
                }
            }
        };
    }

    @Override
    public synchronized boolean exists(String name) {
        try {
            name(name);
            Overlay file = overlays.get(name);
            return file == null ? base.exists(name) : !file.deleted;
        } catch (IOException | InvalidPathException e) {
            return false;
        }
    }

    @Override
    public synchronized void delete(String name) throws IOException {
        Overlay file = overlay(name);
        file.deleted = true;
        file.blocks.clear();
        file.size = file.baseSize = 0;
        file.truncated = true;
    }

    public synchronized MapDataCompound journal() {
        MapDataCompound root = new MapDataCompound();
        root.putInt("version", 1);
        root.putCompoundArray(
            "files",
            overlays.entrySet().stream()
                .map(
                    entry -> {
                        Overlay file = entry.getValue();
                        MapDataCompound item = new MapDataCompound();
                        item.putString("name", entry.getKey());
                        item.putLong("size", file.size);
                        item.putBoolean("deleted", file.deleted);
                        item.putBoolean("truncated", file.truncated);
                        item.putLong("baseSize", file.baseSize);
                        item.putLongArray(
                            "indices",
                            file.blocks.keySet().stream()
                                .mapToLong(Long::longValue)
                                .toArray());
                        byte[] blocks =
                            new byte
                                [Math.multiplyExact(
                                file.blocks.size(), BLOCK_SIZE)];
                        int offset = 0;
                        for (byte[] block : file.blocks.values()) {
                            System.arraycopy(block, 0, blocks, offset, BLOCK_SIZE);
                            offset += BLOCK_SIZE;
                        }
                        item.putByteArray("blocks", blocks);
                        return item;
                    })
                .toArray(DataCompound[]::new));
        return root;
    }

    /**
     * Idempotent replay, called only after the machine CURRENT marker has committed.
     */
    public synchronized void replay(DataCompound journal) throws IOException {
        if (journal.getInt("version") != 1)
            throw new IOException("Unsupported journal");
        DataCompound[] files = journal.getCompoundArray("files");
        if (files == null)
            throw new IOException("Missing journal files");
        for (DataCompound item : files) {
            String name = item.getString("name");
            name(name);
            long size = item.getLong("size");
            long[] indices = item.getLongArray("indices");
            byte[] blocks = item.getByteArray("blocks");
            if (size < 0
                || indices == null
                || blocks == null
                || (long) indices.length * BLOCK_SIZE != blocks.length)
                throw new IOException("Invalid journal blocks");
            for (long index : indices)
                if (index < 0 || index > Long.MAX_VALUE / BLOCK_SIZE || index * BLOCK_SIZE >= size)
                    throw new IOException("Invalid block index");
            if (item.getBoolean("deleted")) {
                base.delete(name);
                continue;
            }
            try (var channel =
                     base.open(name, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                long baseSize = item.getLong("baseSize");
                if (baseSize < 0 || baseSize > size)
                    throw new IOException("Invalid journal base size");
                channel.truncate(baseSize);
                for (int i = 0; i < indices.length; i++) {
                    long offset = indices[i] * BLOCK_SIZE;
                    channel.position(offset);
                    ByteBuffer bytes =
                        ByteBuffer.wrap(
                            blocks,
                            i * BLOCK_SIZE,
                            (int) Math.min(BLOCK_SIZE, size - offset));
                    while (bytes.hasRemaining())
                        if (channel.write(bytes) == 0) throw new IOException("No write progress");
                }
                if (channel.size() > size) channel.truncate(size);
                else if (channel.size() < size) {
                    channel.position(size - 1);
                    channel.write(ByteBuffer.wrap(new byte[1]));
                }
                if (channel instanceof FileChannel file) file.force(true);
            }
        }
    }

    /**
     * Keep overlays and existing channels coherent after a successful replay.
     */
    public synchronized void committed() {
        for (Overlay file : overlays.values()) {
            file.blocks.clear();
            file.truncated = false;
            file.baseSize = file.size;
        }
    }

    private static final class Overlay {
        final Map<Long, byte[]> blocks = new TreeMap<>();
        long size, baseSize;
        boolean deleted, truncated;
    }
}
