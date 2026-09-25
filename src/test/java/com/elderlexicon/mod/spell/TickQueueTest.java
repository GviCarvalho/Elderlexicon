package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TickQueueTest {

    @Test
    void nothingRunsBeforeItsTick() {
        TickQueue queue = new TickQueue();
        List<String> ran = new ArrayList<>();
        queue.schedule(100, 40, () -> ran.add("impact"));

        queue.takeDue(100).forEach(Runnable::run);
        queue.takeDue(139).forEach(Runnable::run);
        assertTrue(ran.isEmpty(), "a 40 tick delay must not run early, however idle the server is");

        queue.takeDue(140).forEach(Runnable::run);
        assertEquals(List.of("impact"), ran);
        assertEquals(0, queue.size());
    }

    @Test
    void dueActionsRunInTickThenSchedulingOrder() {
        TickQueue queue = new TickQueue();
        List<String> ran = new ArrayList<>();
        queue.schedule(0, 5, () -> ran.add("late"));
        queue.schedule(0, 1, () -> ran.add("first"));
        queue.schedule(0, 1, () -> ran.add("second"));

        queue.takeDue(10).forEach(Runnable::run);
        assertEquals(List.of("first", "second", "late"), ran);
    }

    @Test
    void aZeroDelayStillWaitsForTheNextTick() {
        TickQueue queue = new TickQueue();
        queue.schedule(7, 0, () -> { });
        assertTrue(queue.takeDue(7).isEmpty());
        assertEquals(1, queue.takeDue(8).size());
    }
}
