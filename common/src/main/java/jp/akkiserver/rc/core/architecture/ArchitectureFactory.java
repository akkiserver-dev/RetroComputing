package jp.akkiserver.rc.core.architecture;

/**
 * An emulator, interpreter or native runtime; the machine does not depend on its language.
 */
public interface ArchitectureFactory {
    String id();

    Architecture create(ArchitectureHost host) throws Exception;
}
