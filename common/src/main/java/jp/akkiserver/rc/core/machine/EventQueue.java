package jp.akkiserver.rc.core.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Producers may signal from other threads. Overflow is reported ahead of retained events.
 */
public final class EventQueue {
    private final int capacity;
    private final ArrayDeque<MachineEvent> events = new ArrayDeque<>();
    private long lost, overflowTick;

    public EventQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public synchronized void add(MachineEvent event) {
        if (events.size() == capacity) {
            lost = Math.min(0xffffffffL, lost + 1);
            overflowTick = event.tick();
        } else events.addLast(event);
    }

    public synchronized MachineEvent peek() {
        return lost == 0
            ? events.peekFirst()
            : new MachineEvent(
            MachineEvent.OVERFLOW, 0xffff, (int) lost, overflowTick, new byte[0]);
    }

    public synchronized MachineEvent poll() {
        MachineEvent event = peek();
        if (lost != 0) lost = 0;
        else if (event != null) events.removeFirst();
        return event;
    }

    public synchronized boolean isEmpty() {
        return lost == 0 && events.isEmpty();
    }

    public synchronized MachineEvent poll(int maxPayloadBytes) {
        MachineEvent event = peek();
        return event != null && maxPayloadBytes >= event.payload().length ? poll() : event;
    }

    public synchronized void clear() {
        events.clear();
        lost = 0;
    }

    public synchronized List<MachineEvent> snapshot() {
        var copy = new ArrayList<MachineEvent>();
        if (lost != 0) copy.add(peek());
        copy.addAll(events);
        return List.copyOf(copy);
    }

    public synchronized void restore(List<MachineEvent> saved) {
        if (saved.size() > capacity + 1) throw new IllegalArgumentException("Too many events");
        clear();
        for (MachineEvent event : saved) {
            if (event.kind() == MachineEvent.OVERFLOW) {
                lost = Integer.toUnsignedLong(event.parameter());
                overflowTick = event.tick();
            } else if (events.size() < capacity) events.addLast(event);
            else throw new IllegalArgumentException("Too many events");
        }
    }
}
