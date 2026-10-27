package com.termux.app;

import android.app.Application;
import android.app.Notification;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import com.termux.shared.termux.shell.TermuxShellManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = TermuxServiceWakeLockTest.TestApplication.class)
public class TermuxServiceWakeLockTest {
    public static class TestApplication extends Application {
        boolean wifiUnavailable;
        boolean wifiDenied;

        @Override
        public Object getSystemService(String name) {
            if (Context.WIFI_SERVICE.equals(name)) {
                if (wifiDenied) throw new SecurityException("Wi-Fi service access denied");
                if (wifiUnavailable) return null;
            }
            return super.getSystemService(name);
        }
    }

    private TermuxService service;
    private TestApplication application;

    @Before
    public void setUp() {
        application = (TestApplication) RuntimeEnvironment.getApplication();
        service = Robolectric.buildService(TermuxService.class).get();
        ReflectionHelpers.setField(service, "mShellManager", new TermuxShellManager(application));
        PowerManager power = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
        Shadows.shadowOf(power).setIgnoringBatteryOptimizations(service.getPackageName(), true);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.callInstanceMethod(service, "actionReleaseWakeLock", ClassParameter.from(boolean.class, false));
    }

    @Test
    public void repeatedAcquisitionDoesNotRequireRepeatedRelease() {
        acquire();
        PowerManager.WakeLock cpu = cpuLock();
        WifiManager.WifiLock wifi = wifiLock();
        acquire();
        assertSame(cpu, cpuLock());
        assertSame(wifi, wifiLock());
        assertTrue(cpu.isHeld());
        assertTrue(wifi.isHeld());
        assertFalse(Shadows.shadowOf(cpu).isReferenceCounted());
        ReflectionHelpers.callInstanceMethod(service, "actionReleaseWakeLock", ClassParameter.from(boolean.class, false));
        assertFalse(cpu.isHeld());
        assertFalse(wifi.isHeld());
    }

    @Test
    public void acquisitionRecoversLocksWhoseObjectsExistButAreNotHeld() {
        acquire();
        PowerManager.WakeLock cpu = cpuLock();
        WifiManager.WifiLock wifi = wifiLock();
        cpu.release();
        wifi.release();
        acquire();
        assertSame(cpu, cpuLock());
        assertSame(wifi, wifiLock());
        assertTrue(cpu.isHeld());
        assertTrue(wifi.isHeld());
    }

    @Test
    public void unavailableWifiDoesNotAbortCpuLockAndCanBeRetried() {
        application.wifiUnavailable = true;
        acquire();
        PowerManager.WakeLock cpu = cpuLock();
        assertTrue(cpu.isHeld());
        assertNull(wifiLock());
        Notification notification = ReflectionHelpers.callInstanceMethod(service, "buildNotification");
        assertTrue(notification.extras.getString(Notification.EXTRA_TEXT).contains("Wi-Fi lock unavailable"));
        application.wifiUnavailable = false;
        acquire();
        assertSame(cpu, cpuLock());
        assertTrue(wifiLock().isHeld());
        assertEquals(1, Shadows.shadowOf(cpu).getTimesHeld());
    }

    @Test
    public void deniedWifiDoesNotStopSessionsOrDropCpuLock() {
        application.wifiDenied = true;
        acquire();
        assertTrue(cpuLock().isHeld());
        assertNull(wifiLock());
        assertFalse(service.wantsToStop());
        service.onUnbind(null);
        assertTrue(cpuLock().isHeld());
    }

    @Test
    public void releaseToleratesLocksThatAreAlreadyUnheld() {
        acquire();
        PowerManager.WakeLock cpu = cpuLock();
        WifiManager.WifiLock wifi = wifiLock();
        cpu.release();
        ReflectionHelpers.callInstanceMethod(service, "actionReleaseWakeLock", ClassParameter.from(boolean.class, false));
        assertFalse(wifi.isHeld());
        assertNull(cpuLock());
        assertNull(wifiLock());
    }

    private void acquire() {
        ReflectionHelpers.callInstanceMethod(service, "actionAcquireWakeLock");
    }

    private PowerManager.WakeLock cpuLock() {
        return ReflectionHelpers.getField(service, "mWakeLock");
    }

    private WifiManager.WifiLock wifiLock() {
        return ReflectionHelpers.getField(service, "mWifiLock");
    }
}
