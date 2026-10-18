package com.termux.terminal;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class NativeTerminalLifecycleTest {
    private static class Listener implements NativeTerminal.Listener {
        @Override public void onWrite(byte[] data) {}
        @Override public void onBell() {}
        @Override public void onTitle(byte[] title) {}
        @Override public void onClipboard(byte[] text) {}
        @Override public void onEvent(int kind, byte[] data) {}
    }

    @Test public void resetCallbackCannotDestroyTheLiveNativeStack() {
        NativeTerminal[] terminal = new NativeTerminal[1];
        boolean[] rejected = {false};
        terminal[0] = new NativeTerminal(10, 3, 100, new Listener() {
            @Override public void onEvent(int kind, byte[] data) {
                if (kind == 10) {
                    assertThrows(IllegalStateException.class, () -> terminal[0].close());
                    rejected[0] = true;
                }
            }
        });
        try (NativeTerminal nativeTerminal = terminal[0]) {
            nativeTerminal.reset();
            assertTrue(rejected[0]);
            assertEquals(10, nativeTerminal.state()[0]);
        }
    }

    @Test public void nativeAccessIsConfinedToTheOwnerThread() throws InterruptedException {
        try (NativeTerminal terminal = new NativeTerminal(10, 3, 100, new Listener())) {
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try { terminal.state(); } catch (Throwable thrown) { error.set(thrown); }
            });
            thread.start();
            thread.join();
            assertTrue(error.get() instanceof IllegalStateException);
            assertEquals(10, terminal.state()[0]);
        }
    }

    @Test public void invalidInputRangesCannotChangeNativeState() {
        try (NativeTerminal terminal = new NativeTerminal(10, 3, 100, new Listener())) {
            assertThrows(IndexOutOfBoundsException.class, () -> terminal.write(new byte[1], 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> terminal.write(new byte[1], Integer.MAX_VALUE, 1));
            assertEquals(0, terminal.state()[2]);
        }
    }

    @Test public void failedDimensionsLeaveTheTerminalUsable() {
        try (NativeTerminal terminal = new NativeTerminal(10, 3, 100, new Listener())) {
            assertThrows(IllegalStateException.class, () -> terminal.resize(0, 3, 10, 20));
            assertEquals(10, terminal.state()[0]);
            assertThrows(IllegalStateException.class, () -> terminal.resize(10, 3, -1, 20));
            assertEquals(3, terminal.state()[1]);
        }
    }

    @Test public void idleCompressionCompletesAndRestoredHistoryStillAcceptsOutput() {
        try (NativeTerminal terminal = new NativeTerminal(80, 10, 5000, new Listener())) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 4000; i++) text.append("idle history ").append(i).append(" 한日本\r\n");
            byte[] bytes = text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            terminal.write(bytes, 0, bytes.length);
            int history = terminal.state()[7];
            assertTrue(history > 3000);
            int steps = 0;
            while (terminal.compressHistory()) assertTrue("Compression failed to converge", ++steps < 10000);
            assertTrue(new String(terminal.readRow(-history).text).contains("idle history 0"));
            assertTrue(new String(terminal.readRow(8).text).contains("idle history 3999"));
            byte[] resumed = "RESUMED한\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            terminal.write(resumed, 0, resumed.length);
            assertTrue(new String(terminal.readRow(8).text).contains("RESUMED한"));
            assertTrue(terminal.state()[7] >= history);
        }
    }

    @Test public void closeIsIdempotentAndRejectsReads() {
        NativeTerminal terminal = new NativeTerminal(10, 3, 100, new Listener());
        terminal.close();
        terminal.close();
        assertThrows(IllegalStateException.class, terminal::state);
    }
}
