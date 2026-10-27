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

    @Test public void repeatedZoomAfterIdlePreservesReadableWideCellBoundaries() {
        try (NativeTerminal terminal = new NativeTerminal(80, 24, 2000, new Listener())) {
            StringBuilder history = new StringBuilder();
            for (int i = 0; i < 500; i++) history.append("line ").append(i).append(" 한日😀\r\n");
            byte[] bytes = history.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            terminal.write(bytes, 0, bytes.length);
            StringBuilder line = new StringBuilder("\033[32;44m");
            for (int i = 0; i < 200; i++) line.append(i % 3 == 0 ? "日" : i % 3 == 1 ? "a" : "😀");
            line.append("\033[0m\r\n");
            byte[] output = line.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int[][] sizes = {{174, 47}, {112, 8}, {94, 9}, {80, 24}, {17, 4}, {4, 6}, {5, 8}, {200, 30}};
            for (int screen = 0; screen < 2; screen++) {
                byte[] switchScreen = (screen == 0 ? "\033[?1049h" : "\033[?1049l")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                terminal.write(switchScreen, 0, switchScreen.length);
                for (int i = 0; i < 32; i++) {
                    int steps = 0;
                    while (terminal.compressHistory()) assertTrue("Compression failed to converge", ++steps < 1000);
                    int[] size = sizes[i % sizes.length];
                    terminal.resize(size[0], size[1], 8, 16);
                    terminal.setHistoryRows(2000 - size[1]);
                    int[] state = terminal.state();
                    assertEquals(size[0], state[0]);
                    assertEquals(size[1], state[1]);
                    for (int row = -Math.min(state[7], 10); row < size[1]; row++) terminal.readRow(row);
                    terminal.write(output, 0, output.length);
                    for (int row = 0; row < size[1]; row++) terminal.readRow(row);
                }
            }
        }
    }

    @Test public void closeIsIdempotentAndRejectsReads() {
        NativeTerminal terminal = new NativeTerminal(10, 3, 100, new Listener());
        terminal.close();
        terminal.close();
        assertThrows(IllegalStateException.class, terminal::state);
    }
}
