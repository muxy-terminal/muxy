package com.termux.terminal;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class TerminalInputQueueTest {
    @Test public void enqueueCopiesMutableInputAndPreservesChunkOrdering() throws Exception {
        TerminalInputQueue queue = new TerminalInputQueue(16);
        byte[] first = {1, 2, 3};
        assertEquals(TerminalInputQueue.Result.ACCEPTED, queue.offer(first, 0, 3));
        first[0] = 9;
        assertEquals(TerminalInputQueue.Result.ACCEPTED, queue.offer(new byte[]{4, 5}, 0, 2));
        byte[] result = new byte[4];
        assertEquals(4, queue.read(result));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, result);
        assertEquals(1, queue.read(result));
        assertEquals(5, result[0]);
    }

    @Test(timeout = 2000) public void fullQueueRejectsAnEntireWriteWithoutBlockingOrLosingAcceptedBytes() throws Exception {
        TerminalInputQueue queue = new TerminalInputQueue(4);
        assertEquals(TerminalInputQueue.Result.ACCEPTED, queue.offer(new byte[]{1, 2, 3}, 0, 3));
        assertEquals(TerminalInputQueue.Result.FULL, queue.offer(new byte[]{4, 5}, 0, 2));
        byte[] bytes = new byte[4];
        assertEquals(3, queue.read(bytes));
        assertArrayEquals(new byte[]{1, 2, 3, 0}, bytes);
        assertEquals(TerminalInputQueue.Result.FULL, queue.offer(new byte[5], 0, 5));
        assertEquals(TerminalInputQueue.Result.ACCEPTED, queue.offer(new byte[]{7}, 0, 1));
        assertEquals(1, queue.read(bytes));
        assertEquals(7, bytes[0]);
    }

    @Test public void wraparoundPreservesBytesAcrossPartialReads() throws Exception {
        TerminalInputQueue queue = new TerminalInputQueue(5);
        assertEquals(TerminalInputQueue.Result.ACCEPTED, queue.offer(new byte[]{1, 2, 3, 4}, 0, 4));
        byte[] prefix = new byte[3];
        assertEquals(3, queue.read(prefix));
        assertArrayEquals(new byte[]{1, 2, 3}, prefix);
        assertEquals(TerminalInputQueue.Result.ACCEPTED, queue.offer(new byte[]{5, 6, 7, 8}, 0, 4));
        byte[] remaining = new byte[5];
        assertEquals(5, queue.read(remaining));
        assertArrayEquals(new byte[]{4, 5, 6, 7, 8}, remaining);
    }

    @Test public void invalidCapacityAndRangesAreRejectedAtTheBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new TerminalInputQueue(0));
        TerminalInputQueue queue = new TerminalInputQueue(4);
        assertThrows(IndexOutOfBoundsException.class, () -> queue.offer(new byte[1], -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> queue.offer(new byte[1], 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> queue.offer(new byte[1], Integer.MAX_VALUE, 1));
        assertEquals(TerminalInputQueue.Result.ACCEPTED, queue.offer(new byte[0], 0, 0));
    }

    @Test public void closeWakesTheConsumerAndRejectsLateWrites() throws Exception {
        TerminalInputQueue queue = new TerminalInputQueue(4);
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean();
        Thread consumer = new Thread(() -> {
            waiting.countDown();
            try { closed.set(queue.read(new byte[4]) == -1); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        });
        consumer.start();
        assertTrue(waiting.await(1, TimeUnit.SECONDS));
        queue.close();
        consumer.join(1000);
        assertFalse(consumer.isAlive());
        assertTrue(closed.get());
        assertEquals(TerminalInputQueue.Result.CLOSED, queue.offer(new byte[]{1}, 0, 1));
    }
}
