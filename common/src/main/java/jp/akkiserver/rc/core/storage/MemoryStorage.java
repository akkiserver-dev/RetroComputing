package jp.akkiserver.rc.core.storage;

import jp.akkiserver.rc.api.hardware.device.DeviceStorage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Volatile storage for emulators and isolated machines. Channels share file contents.
 */
public final class MemoryStorage implements DeviceStorage {
    private final Map<String, File> files = new HashMap<>();

    private static void validate(String name) throws IOException {
        if (name.isBlank()
            || name.contains("..")
            || name.contains(":")
            || name.startsWith("/")
            || name.startsWith("\\")) throw new IOException("Invalid file name");
    }

    @Override
    public synchronized SeekableByteChannel open(String name, OpenOption... options)
        throws IOException {
        validate(name);
        Set<OpenOption> flags = new HashSet<>(List.of(options));
        boolean write =
            flags.contains(StandardOpenOption.WRITE)
                || flags.contains(StandardOpenOption.APPEND);
        boolean read = flags.isEmpty() || flags.contains(StandardOpenOption.READ);
        if (flags.stream().anyMatch(f -> !(f instanceof StandardOpenOption)))
            throw new UnsupportedOperationException();
        if (flags.contains(StandardOpenOption.DELETE_ON_CLOSE)
            || flags.contains(StandardOpenOption.SYNC)
            || flags.contains(StandardOpenOption.DSYNC))
            throw new UnsupportedOperationException();
        if (flags.contains(StandardOpenOption.APPEND)
            && (read || flags.contains(StandardOpenOption.TRUNCATE_EXISTING)))
            throw new IllegalArgumentException("Invalid append options");
        File file = files.get(name);
        if (file != null && write && flags.contains(StandardOpenOption.CREATE_NEW))
            throw new FileAlreadyExistsException(name);
        if (file == null) {
            if (!write
                || !(flags.contains(StandardOpenOption.CREATE)
                || flags.contains(StandardOpenOption.CREATE_NEW)))
                throw new NoSuchFileException(name);
            file = new File();
            files.put(name, file);
        }
        File target = file;
        if (write && flags.contains(StandardOpenOption.TRUNCATE_EXISTING))
            target.bytes = new byte[0];
        boolean append = flags.contains(StandardOpenOption.APPEND);
        return new SeekableByteChannel() {
            private boolean open = true;
            private int position;

            private void check() throws ClosedChannelException {
                if (!open) throw new ClosedChannelException();
            }

            public int read(ByteBuffer dst) throws IOException {
                synchronized (MemoryStorage.this) {
                    check();
                    if (!read) throw new NonReadableChannelException();
                    if (!dst.hasRemaining()) return 0;
                    if (position >= target.bytes.length) return -1;
                    int n = Math.min(dst.remaining(), target.bytes.length - position);
                    dst.put(target.bytes, position, n);
                    position += n;
                    return n;
                }
            }

            public int write(ByteBuffer src) throws IOException {
                synchronized (MemoryStorage.this) {
                    check();
                    if (!write) throw new NonWritableChannelException();
                    if (append) position = target.bytes.length;
                    int n = src.remaining();
                    if ((long) position + n > Integer.MAX_VALUE - 8)
                        throw new IOException("File too large");
                    if (position + n > target.bytes.length)
                        target.bytes = Arrays.copyOf(target.bytes, position + n);
                    src.get(target.bytes, position, n);
                    position += n;
                    return n;
                }
            }

            public long position() throws IOException {
                synchronized (MemoryStorage.this) {
                    check();
                    return position;
                }
            }

            public SeekableByteChannel position(long value) throws IOException {
                synchronized (MemoryStorage.this) {
                    check();
                    if (value < 0 || value > Integer.MAX_VALUE - 8)
                        throw new IllegalArgumentException();
                    position = (int) value;
                    return this;
                }
            }

            public long size() throws IOException {
                synchronized (MemoryStorage.this) {
                    check();
                    return target.bytes.length;
                }
            }

            public SeekableByteChannel truncate(long size) throws IOException {
                synchronized (MemoryStorage.this) {
                    check();
                    if (!write) throw new NonWritableChannelException();
                    if (size < 0) throw new IllegalArgumentException();
                    if (size < target.bytes.length)
                        target.bytes = Arrays.copyOf(target.bytes, (int) size);
                    if (position > size) position = (int) size;
                    return this;
                }
            }

            public boolean isOpen() {
                synchronized (MemoryStorage.this) {
                    return open;
                }
            }

            public void close() {
                synchronized (MemoryStorage.this) {
                    open = false;
                }
            }
        };
    }

    @Override
    public synchronized boolean exists(String name) {
        return files.containsKey(name);
    }

    @Override
    public synchronized void delete(String name) throws IOException {
        validate(name);
        files.remove(name);
    }

    private static final class File {
        byte[] bytes = new byte[0];
    }
}
