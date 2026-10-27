package com.termux.view;

import android.app.Application;
import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Typeface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.Locale;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalFontsTest {
    private AssetManager assets;
    private TerminalFonts fonts;

    @Before
    public void setUp() {
        assets = RuntimeEnvironment.getApplication().getAssets();
        fonts = new TerminalFonts(assets, Locale.KOREAN);
    }

    @Test
    public void jetBrainsMonoKeepsLatinCharactersInEqualWidthCells() {
        Paint paint = new Paint();
        paint.setTypeface(fonts.monospace);
        paint.setTextSize(24);
        assertTrue(paint.hasGlyph("A"));
        assertEquals(paint.measureText("X"), paint.measureText("i"), 0.01f);
        assertEquals(paint.measureText("X"), paint.measureText("W"), 0.01f);
        assertEquals(paint.measureText("X"), paint.measureText(" "), 0.01f);
    }

    @Test
    public void scriptsSelectTheirBundledRegionalFonts() {
        assertSame(fonts.monospace, fonts.forCodePoint('A'));
        assertSame(fonts.korean, fonts.forCodePoint('한'));
        assertSame(fonts.korean, fonts.forCodePoint(0x1100));
        assertSame(fonts.korean, fonts.forCodePoint(0x3131));
        assertSame(fonts.japanese, fonts.forCodePoint('あ'));
        assertSame(fonts.japanese, fonts.forCodePoint('カ'));
        assertSame(fonts.japanese, fonts.forCodePoint(0xff76));
        assertSame(fonts.chinese, fonts.forCodePoint(0x3105));
        assertSame(fonts.monospace, fonts.forCodePoint(0x1f600));
    }

    @Test
    public void sharedHanCharactersFollowTheDeviceLanguage() {
        assertSame(fonts.korean, fonts.forCodePoint('中'));
        TerminalFonts japanese = new TerminalFonts(assets, Locale.JAPANESE);
        assertSame(japanese.japanese, japanese.forCodePoint('日'));
        TerminalFonts chinese = new TerminalFonts(assets, Locale.SIMPLIFIED_CHINESE);
        assertSame(chinese.chinese, chinese.forCodePoint('中'));
        TerminalFonts english = new TerminalFonts(assets, Locale.ENGLISH);
        assertSame(english.korean, english.forCodePoint('中'));
    }

    @Test
    public void regionalFontsContainRepresentativeGlyphs() {
        Paint paint = new Paint();
        paint.setTextSize(24);
        for (String text : new String[]{"한", "あ", "カ", "中"}) {
            paint.setTypeface(fonts.forCodePoint(text.codePointAt(0)));
            assertTrue(text, paint.hasGlyph(text));
            assertTrue(text, paint.measureText(text) > 0);
        }
    }

    @Test
    public void rendererMeasuresClustersWithTheSelectedFont() {
        TerminalRenderer renderer = new TerminalRenderer(24, fonts.monospace, fonts);
        for (String text : new String[]{"A", "한", "あ", "中"}) {
            float width = ReflectionHelpers.callInstanceMethod(renderer, "measureCluster",
                ClassParameter.from(char[].class, text.toCharArray()), ClassParameter.from(int.class, 0),
                ClassParameter.from(int.class, text.length()), ClassParameter.from(long.class, 0L));
            Paint paint = ReflectionHelpers.getField(renderer, "mTextPaint");
            assertSame(fonts.forCodePoint(text.codePointAt(0)), paint.getTypeface());
            assertTrue(width > 0);
        }
    }

    @Test
    public void lineMetricsIncludeEveryCjkFontWithoutChangingCellWidth() {
        TerminalRenderer renderer = new TerminalRenderer(24, fonts.monospace, fonts);
        Paint paint = new Paint();
        paint.setTextSize(24);
        paint.setTypeface(fonts.monospace);
        assertEquals(paint.measureText("X"), renderer.getFontWidth(), 0.01f);
        for (Typeface typeface : new Typeface[]{fonts.monospace, fonts.korean, fonts.japanese, fonts.chinese}) {
            paint.setTypeface(typeface);
            assertTrue(renderer.getFontLineSpacing() >= Math.ceil(paint.getFontSpacing()));
        }
    }

    @Test
    public void zoomPreservesTheDefaultFontSelection() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTextSize(24);
        TerminalFonts defaults = view.mRenderer.mFonts;
        assertNotNull(defaults);
        for (int size : new int[]{26, 18, 32, 22, 40, 24}) {
            view.setTextSize(size);
            assertSame(defaults, view.mRenderer.mFonts);
            assertSame(defaults.monospace, view.mRenderer.mTypeface);
        }
    }

    @Test
    public void customFontsRemainSelectedUntilTheDefaultIsRestored() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTextSize(24);
        view.setTypeface(Typeface.SERIF);
        view.setTextSize(26);
        assertSame(Typeface.SERIF, view.mRenderer.mTypeface);
        assertNull(view.mRenderer.mFonts);
        view.setDefaultTypeface();
        assertNotNull(view.mRenderer.mFonts);
        assertSame(view.mRenderer.mFonts.monospace, view.mRenderer.mTypeface);
    }
}
