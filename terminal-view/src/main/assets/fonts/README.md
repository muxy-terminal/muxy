# Terminal fonts

The default terminal uses JetBrains Mono for Latin text and symbols. Noto Sans CJK regional subsets supply Korean, Japanese, and Simplified Chinese text. Hangul uses KR, kana uses JP, and shared Han characters follow the device language. Other device languages use KR for shared Han characters. Emoji and unsupported glyphs retain Android's system fallback.

The renderer keeps JetBrains Mono's cell width and fits CJK glyphs to their terminal cell count. Row metrics include the CJK fonts. A user-provided `~/.termux/font.ttf` replaces this default selection without modifying the file.

All four font binaries are unmodified upstream files licensed under SIL Open Font License 1.1. The accompanying license files are included in the APK. Trailing whitespace was removed from the JetBrains license text.

| Font | Upstream version | SHA-256 |
| --- | --- | --- |
| `JetBrainsMono-Regular.ttf` | JetBrains Mono 2.304 | `a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f` |
| `NotoSansKR-Regular.otf` | Noto Sans CJK 2.004, KR subset | `69975a0ac8472717870aefeab0a4d52739308d90856b9955313b2ad5e0148d68` |
| `NotoSansJP-Regular.otf` | Noto Sans CJK 2.004, JP subset | `dff723ba59d57d136764a04b9b2d03205544f7cd785a711442d6d2d085ac5073` |
| `NotoSansSC-Regular.otf` | Noto Sans CJK 2.004, SC subset | `faa6c9df652116dde789d351359f3d7e5d2285a2b2a1f04a2d7244df706d5ea9` |

Sources:

- [JetBrains Mono, commit cd5227bd1f61dff3bbd6c814ceaf7ffd95e947d9](https://github.com/JetBrains/JetBrainsMono/tree/cd5227bd1f61dff3bbd6c814ceaf7ffd95e947d9/fonts/ttf). Copyright 2020 The JetBrains Mono Project Authors.
- [Noto Sans CJK, commit 523d033d6cb47f4a80c58a35753646f5c3608a78](https://github.com/notofonts/noto-cjk/tree/523d033d6cb47f4a80c58a35753646f5c3608a78/Sans/SubsetOTF). Copyright 2014-2021 Adobe.
