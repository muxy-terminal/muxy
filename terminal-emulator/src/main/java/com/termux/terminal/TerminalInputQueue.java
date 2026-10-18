package com.termux.terminal;

import java.util.Objects;

/** Bounded PTY input backlog which never waits for the child on the producer thread. */
final class TerminalInputQueue {
    enum Result { ACCEPTED, CLOSED, FULL }

    private final byte[] buffer;
    private int queuedBytes;
    private int head;
    private boolean open = true;

    TerminalInputQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Invalid terminal input capacity");
        buffer = new byte[capacity];
    }

    synchronized Result offer(byte[] data, int offset, int count) {
        Objects.requireNonNull(data);
        if (offset < 0 || count < 0 || offset > data.length - count) {
            throw new IndexOutOfBoundsException("Invalid terminal input range");
        }
        if (!open) return Result.CLOSED;
        if (count > buffer.length - queuedBytes) return Result.FULL;
        if (count > 0) {
            int tail = (int) (((long) head + queuedBytes) % buffer.length);
            int first = Math.min(count, buffer.length - tail);
            System.arraycopy(data, offset, buffer, tail, first);
            System.arraycopy(data, offset + first, buffer, 0, count - first);
            queuedBytes += count;
            notifyAll();
        }
        return Result.ACCEPTED;
    }

    synchronized int read(byte[] target) throws InterruptedException {
        Objects.requireNonNull(target);
        if (target.length == 0) throw new IllegalArgumentException("Empty terminal input buffer");
        while (open && queuedBytes == 0) wait();
        if (!open) return -1;
        int count = Math.min(target.length, queuedBytes);
        int first = Math.min(count, buffer.length - head);
        System.arraycopy(buffer, head, target, 0, first);
        System.arraycopy(buffer, 0, target, first, count - first);
        head = (int) (((long) head + count) % buffer.length);
        queuedBytes -= count;
        return count;
    }

    synchronized void close() {
        open = false;
        queuedBytes = 0;
        head = 0;
        notifyAll();
    }
}
