package jp.akkiserver.rc.core.architecture;

/**
 * One isolated guest. All methods are called serially on a platform worker thread.
 */
public interface Architecture extends AutoCloseable {
    void boot(byte[] image) throws Exception;

    ExecutionResult run(long budget) throws Exception;

    byte[] snapshot() throws Exception;

    void restore(byte[] image, byte[] snapshot) throws Exception;

    @Override
    void close() throws Exception;
}
