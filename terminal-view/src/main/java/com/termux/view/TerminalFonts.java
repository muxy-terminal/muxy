package com.termux.view;

import android.content.res.AssetManager;
import android.graphics.Typeface;

import java.util.Locale;

final class TerminalFonts {
    final Typeface monospace;
    final Typeface korean;
    final Typeface japanese;
    final Typeface chinese;
    private final Typeface han;

    TerminalFonts(AssetManager assets, Locale locale) {
        monospace = Typeface.createFromAsset(assets, "fonts/JetBrainsMono-Regular.ttf");
        korean = Typeface.createFromAsset(assets, "fonts/NotoSansKR-Regular.otf");
        japanese = Typeface.createFromAsset(assets, "fonts/NotoSansJP-Regular.otf");
        chinese = Typeface.createFromAsset(assets, "fonts/NotoSansSC-Regular.otf");
        String language = locale.getLanguage();
        han = "ja".equals(language) ? japanese : "zh".equals(language) ? chinese : korean;
    }

    Typeface forCodePoint(int codePoint) {
        if (codePoint >= 0x1100 && codePoint <= 0x11ff
                || codePoint >= 0x3130 && codePoint <= 0x318f
                || codePoint >= 0xa960 && codePoint <= 0xa97f
                || codePoint >= 0xac00 && codePoint <= 0xd7ff) return korean;
        if (codePoint >= 0x3040 && codePoint <= 0x30ff
                || codePoint >= 0x31f0 && codePoint <= 0x31ff
                || codePoint >= 0xff66 && codePoint <= 0xff9f
                || codePoint >= 0x1aff0 && codePoint <= 0x1b16f) return japanese;
        if (codePoint >= 0x3100 && codePoint <= 0x312f
                || codePoint >= 0x31a0 && codePoint <= 0x31bf) return chinese;
        // Shared Han characters use the device language, with Korean as the default.
        if (codePoint >= 0x2e80 && codePoint <= 0x303f
                || codePoint >= 0x31c0 && codePoint <= 0x9fff
                || codePoint >= 0xf900 && codePoint <= 0xfaff
                || codePoint >= 0xfe10 && codePoint <= 0xfe1f
                || codePoint >= 0xfe30 && codePoint <= 0xfe4f
                || codePoint >= 0xff01 && codePoint <= 0xff60
                || codePoint >= 0xffe0 && codePoint <= 0xffe6
                || codePoint >= 0x20000 && codePoint <= 0x323af) return han;
        return monospace;
    }
}
