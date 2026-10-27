package com.termux.app;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class SessionDrawerTest {
    private TermuxActivity activity;
    private View root;
    private DrawerLayout drawer;
    private View sessionsDrawer;
    private ImageButton sessionsButton;
    private View toolbar;
    private ViewPager pager;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        activity.setContentView(R.layout.activity_termux);
        activity.mTerminalView = activity.findViewById(R.id.terminal_view);
        root = activity.findViewById(R.id.activity_termux_root_view);
        drawer = activity.getDrawer();
        sessionsDrawer = activity.findViewById(R.id.left_drawer);
        sessionsButton = activity.findViewById(R.id.session_drawer_button);
        toolbar = activity.findViewById(R.id.terminal_toolbar);
        pager = activity.getTerminalToolbarViewPager();
        ReflectionHelpers.callInstanceMethod(activity, "setSessionDrawerButtonView");
    }

    @Test
    public void hiddenExtraKeysKeepAnAccessibleButtonOutsideTheTerminal() {
        measureLayout(360, 640);

        assertEquals(View.GONE, pager.getVisibility());
        assertEquals(View.VISIBLE, sessionsButton.getVisibility());
        assertTrue(sessionsButton.isFocusable());
        assertTrue(sessionsButton.isClickable());
        assertEquals(activity.getText(R.string.action_open_sessions), sessionsButton.getContentDescription());
        assertEquals(activity.getText(R.string.action_open_sessions), sessionsButton.getTooltipText());
        assertEquals(activity.getString(R.string.title_sessions), drawer.getDrawerTitle(GravityCompat.START));
        assertEquals(dp(48), sessionsButton.getWidth());
        assertEquals(dp(48), sessionsButton.getHeight());
        assertEquals(dp(48), toolbar.getHeight());
        assertEquals(toolbar.getTop(), drawer.getBottom());
        assertEquals(drawer.getHeight(), activity.mTerminalView.getHeight());
    }

    @Test
    public void sessionsButtonSharesTheExistingTwoRowToolbarHeight() {
        setPagerHeight(75);
        measureLayout(360, 640);

        assertEquals(dp(75), toolbar.getHeight());
        assertEquals(dp(75), pager.getHeight());
        assertEquals(sessionsButton.getRight(), pager.getLeft());
        assertEquals(toolbar.getWidth(), pager.getRight());
        assertEquals(toolbar.getTop(), drawer.getBottom());
    }

    @Test
    public void singleRowAndEmptyCustomLayoutsRetainOnlyTheMinimumActionHeight() {
        for (float height : new float[] {37.5f, 0}) {
            setPagerHeight(height);
            measureLayout(640, 320);

            assertEquals(dp(48), toolbar.getHeight());
            assertEquals(dp(height), pager.getHeight());
            assertEquals(View.VISIBLE, sessionsButton.getVisibility());
            assertEquals(toolbar.getTop(), drawer.getBottom());
        }
    }

    @Test
    public void buttonTogglesTheDrawerWithoutDependingOnExtraKeys() {
        assertTrue(sessionsButton.performClick());
        assertTrue(drawer.isDrawerOpen(sessionsDrawer));
        assertTrue(sessionsButton.performClick());
        assertFalse(drawer.isDrawerOpen(sessionsDrawer));
        assertEquals(View.GONE, pager.getVisibility());
    }

    @Test
    public void anInitiallyOpenDrawerHasTheMatchingAccessibleActionAfterLayout() {
        drawer.openDrawer(sessionsDrawer, false);
        measureLayout(360, 640);

        assertEquals(activity.getText(R.string.action_close_sessions), sessionsButton.getContentDescription());
        assertEquals(activity.getText(R.string.action_close_sessions), sessionsButton.getTooltipText());
    }

    @Test
    public void buttonDoesNotBypassTheTextSelectionLock() {
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, sessionsDrawer);
        sessionsButton.performClick();
        assertFalse(drawer.isDrawerOpen(sessionsDrawer));

        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, sessionsDrawer);
        sessionsButton.performClick();
        assertTrue(drawer.isDrawerOpen(sessionsDrawer));
    }

    @Test
    public void drawerChangesFromOtherEntryPointsUpdateTheButtonAndRestoreTerminalFocus() {
        measureLayout(360, 640);
        drawer.openDrawer(sessionsDrawer, false);
        assertEquals(activity.getText(R.string.action_close_sessions), sessionsButton.getContentDescription());
        assertEquals(activity.getText(R.string.action_close_sessions), sessionsButton.getTooltipText());
        assertTrue(activity.findViewById(R.id.settings_button).requestFocus());

        drawer.closeDrawer(sessionsDrawer, false);
        assertEquals(activity.getText(R.string.action_open_sessions), sessionsButton.getContentDescription());
        assertTrue(activity.mTerminalView.hasFocus());
    }

    private void setPagerHeight(float height) {
        pager.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = pager.getLayoutParams();
        params.height = dp(height);
        pager.setLayoutParams(params);
    }

    private void measureLayout(int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(dp(width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
