package jp.akkiserver.rc.core.machine;

public record MachineConfig(int slots, long budgetPerTick, int eventCapacity, int maxTickets) {
    public MachineConfig {
        if (slots < 1 || slots > 0xffff || budgetPerTick < 1 || eventCapacity < 1 || maxTickets < 1)
            throw new IllegalArgumentException("Invalid machine limits");
    }

    public static MachineConfig defaults() {
        return new MachineConfig(32, 10_000, 256, 256);
    }
}
