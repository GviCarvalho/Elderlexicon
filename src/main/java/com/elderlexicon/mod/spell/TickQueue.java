package com.elderlexicon.mod.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Actions waiting for a server tick. Due actions run in the order they are due, and those due on the same tick
 * in the order they were scheduled. An action scheduled while the queue runs waits at least until the next run.
 */
public final class TickQueue {

    private record Entry(long dueTick, long order, Runnable action) { }

    private final PriorityQueue<Entry> entries = new PriorityQueue<>((a, b) -> a.dueTick != b.dueTick
            ? Long.compare(a.dueTick, b.dueTick)
            : Long.compare(a.order, b.order));
    private long nextOrder;

    public synchronized void schedule(long now, int delayTicks, Runnable action) {
        entries.add(new Entry(now + Math.max(1, delayTicks), nextOrder++, action));
    }

    /** Removes and returns what is due at {@code now}, so the caller runs it outside the lock. */
    public synchronized List<Runnable> takeDue(long now) {
        List<Runnable> due = new ArrayList<>();
        while (!entries.isEmpty() && entries.peek().dueTick <= now) {
            due.add(entries.poll().action);
        }
        return due;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
