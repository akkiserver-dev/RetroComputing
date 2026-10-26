package jp.akkiserver.rc.core.storage;

import jp.akkiserver.rc.api.hardware.device.DeviceStorage;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Device-scoped files. Names are relative and may not traverse symbolic links.
 */
public final class DirectStorage implements DeviceStorage {
    private final Path root;

    public DirectStorage(Path root) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
    }

    private Path resolve(String name) throws IOException {
        Path relative = Path.of(name);
        if (relative.isAbsolute() || name.isBlank()) throw new IOException("Invalid storage name");
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || path.equals(root))
            throw new IOException("Storage path escapes device root");
        Path current = root;
        for (Path part : root.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current))
                throw new IOException("Symbolic links are not allowed");
        }
        return path;
    }

    @Override
    public SeekableByteChannel open(String name, OpenOption... options) throws IOException {
        Path path = resolve(name);
        Set<OpenOption> set = new HashSet<>(List.of(options));
        if (set.contains(StandardOpenOption.CREATE) || set.contains(StandardOpenOption.CREATE_NEW))
            Files.createDirectories(path.getParent());
        set.add(LinkOption.NOFOLLOW_LINKS);
        return Files.newByteChannel(path, set);
    }

    @Override
    public boolean exists(String name) {
        try {
            return Files.isRegularFile(resolve(name), LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | InvalidPathException e) {
            return false;
        }
    }

    @Override
    public void delete(String name) throws IOException {
        Files.deleteIfExists(resolve(name));
    }
}
