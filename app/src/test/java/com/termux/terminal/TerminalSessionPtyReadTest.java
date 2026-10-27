package com.termux.terminal;

import android.app.Application;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.FileDescriptor;
import java.io.InterruptedIOException;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class,
    shadows = TerminalSessionPtyReadTest.PtyReadShadow.class)
public class TerminalSessionPtyReadTest {
    @Implements(Os.class)
    public static class PtyReadShadow {
        static int calls;
        static int errno;
        static InterruptedIOException interruption;

        @Implementation
        protected static int read(FileDescriptor descriptor, byte[] buffer, int offset, int count)
            throws ErrnoException, InterruptedIOException {
            calls++;
            if (calls == 1 && errno != 0) throw new ErrnoException("read", errno);
            if (calls == 1 && interruption != null) {
                buffer[offset] = 42;
                throw interruption;
            }
            buffer[offset] = 7;
            return 1;
        }
    }

    @Before
    public void resetRead() {
        PtyReadShadow.calls = 0;
        PtyReadShadow.errno = 0;
        PtyReadShadow.interruption = null;
    }

    @Test
    public void retriesInterruptedSyscallBeforeDeliveringFreshOutput() throws Exception {
        PtyReadShadow.errno = OsConstants.EINTR;
        byte[] buffer = new byte[4];
        assertEquals(1, TerminalSession.readFromPty(new FileDescriptor(), buffer));
        assertEquals(2, PtyReadShadow.calls);
        assertEquals(7, buffer[0]);
    }

    @Test
    public void retriesInterruptedIoWithoutTransferredBytes() throws Exception {
        PtyReadShadow.interruption = new InterruptedIOException("Interrupted read");
        byte[] buffer = new byte[4];
        assertEquals(1, TerminalSession.readFromPty(new FileDescriptor(), buffer));
        assertEquals(2, PtyReadShadow.calls);
        assertEquals(7, buffer[0]);
    }

    @Test
    public void preservesBytesTransferredBeforeInterruption() throws Exception {
        PtyReadShadow.interruption = new InterruptedIOException("Partial read");
        PtyReadShadow.interruption.bytesTransferred = 1;
        byte[] buffer = new byte[4];
        assertEquals(1, TerminalSession.readFromPty(new FileDescriptor(), buffer));
        assertEquals(1, PtyReadShadow.calls);
        assertEquals(42, buffer[0]);
    }

    @Test
    public void lastSlaveClosingIsEndOfOutput() throws Exception {
        PtyReadShadow.errno = OsConstants.EIO;
        assertEquals(-1, TerminalSession.readFromPty(new FileDescriptor(), new byte[4]));
        assertEquals(1, PtyReadShadow.calls);
    }

    @Test
    public void nonTransientReadErrorsAreNotSilentlyRetried() {
        PtyReadShadow.errno = OsConstants.EBADF;
        ErrnoException error = assertThrows(ErrnoException.class,
            () -> TerminalSession.readFromPty(new FileDescriptor(), new byte[4]));
        assertEquals(OsConstants.EBADF, error.errno);
        assertEquals(1, PtyReadShadow.calls);
    }
}
