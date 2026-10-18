# muxy addons 0.119.10

Coordinated source-built `muxy.6` releases. API, Float, Tasker, and Widget pin muxy `c13c4999bf928d2a61feaae738dc72837f391f3a`. Styling and Boot retain standalone runtime behavior with updated version codes.

## PTY responsiveness

Main muxy and Float no longer wait for space in a child-input queue on the UI thread. Accepted input is copied into a bounded 1 MiB ring and delivered in order. An entire new write is rejected with an error if it cannot fit. Writer errors and interruption are surfaced, and closing the writer wakes its consumer.

Float displays input errors through its existing UI. The overlay/PTY tests pause the child process group, submit 128 KiB, verify UI responsiveness, resume the child, and compare every delivered byte. Hidden-overlay idle tests preserve the shell PID, history, and subsequent UTF-8 input. Teardown detaches the rendered emulator before native disposal so pending redraws do not access a closed terminal.

Ghostty source, compression policy, native thread ownership, and history limits are unchanged. The fix addresses a reproduced backpressure path, not every possible physical-device idle freeze. Existing CJK, deletion, mouse/keyboard routing, images, color, and selection paths are retained.

| App | Version | Minimum Android |
| --- | --- | --- |
| Termux:API | 0.53.0+muxy.6 | 7.0 |
| Termux:Styling | 0.32.1+muxy.6 | 5.0 |
| Termux:Boot | 0.8.1+muxy.6 | 5.0 |
| Termux:Float | 0.17.0+muxy.6 | 7.0 |
| Termux:Tasker | 0.9.0+muxy.6 | 5.0 |
| Termux:Widget | 0.15.0+muxy.6 | 7.0 |

## Upgrade

Install over earlier muxy-signed builds without uninstalling. Keep the main app's existing bootstrap variant. Package identities, shared UID, permissions, minimum SDK requirements, and private signer are unchanged:

```text
756768af514d3fa0d1c214d54dabd7b0cd088a1723a22f520b90e8e8545262ce
```

Differently signed upstream, F-Droid, and Play APKs cannot join muxy's UID. Every fork publishes its APK. The combined distribution includes all six APKs, exact source commits, corresponding source for the forks and pinned muxy, GPLv3 text, provenance, and checksums. Verify downloads with `sha256sum -c ADDON-SHA256SUMS`.

## Verification

- Clean pinned-source builds pass APK checksum, certificate, package, SDK, shared UID, and native 16 KiB alignment checks. Float contains the native terminal for all four ABIs.
- All six update alongside signed main muxy on API 35 and share its UID. Styling, Boot, and Tasker also update on API 21.
- Float's nine-scenario overlay/PTY suite completes without failures on API 35, with optional live typing skipped. It includes idle recovery, stalled-child input, CJK, deletion, mouse/keyboard visibility, colors, and disposal.
- Shared host tests pass 165 emulator, 51 View, and ten main-app tests in debug and release.
- Main Android connection/View/pointer suites complete 69 scenarios on API 21/34/35 without failures. API 21 skips 13 scenarios; API 34/35 skip optional live typing. Some newer-API assertions return early on older Android.
- Signed main upgrades pass shell/rotation, extra-key editing, and keyboard-toolbar behavior on API 21/35. The API 35 signed app also passes upstream touch/keyboard routing.

Physical-device Doze, OEM freezing, SSH/network timeouts, hours-long inactivity, real reboot jobs, automation-host execution, and launcher widget behavior remain outside this verification matrix. No claim is made that every long-idle failure is fixed.
