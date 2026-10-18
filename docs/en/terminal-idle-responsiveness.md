# Terminal responsiveness after idle or PTY backpressure

## Confirmed blocking path

The UI thread previously wrote terminal input through a blocking 4 KiB `ByteQueue`. If the child stopped reading its PTY, the writer thread could block in the kernel while the UI thread waited for queue space. Paste, IME input, or terminal-generated responses could then stop event dispatch for the app. A circular wait was also possible if the child was waiting for output to be consumed by the same blocked UI thread.

The process-pause regression reproduces the UI-thread wait before the correction. This is a concrete backpressure defect, not proof that every reported long-idle freeze has the same cause. The attached emulators did not contain an existing ANR trace establishing the user's physical-device failure.

## Correction

`TerminalInputQueue` replaces only the UI-to-PTY queue. The PTY-to-emulator queue and native main-thread ownership remain unchanged.

- The producer copies complete writes into a fixed 1 MiB ring without waiting for the child.
- The writer thread drains accepted bytes in order. Caller-owned mutable byte arrays are not retained.
- A write which cannot fit is rejected in full. Accepted pending input is retained. The main app shows an error; terminal-library clients receive `onTerminalInputError`, which logs by default.
- Repeated rejections are reported once until a write succeeds. A closed writer rejects new input instead of leaving producers waiting forever.
- Write failures and unexpected writer interruption are reported instead of swallowed. Closing/disposal ends the queue and wakes its consumer.

The bound is deliberate: an unresponsive child cannot grow the app's memory without limit. An input operation exceeding the bound must be retried after the child resumes or split by the caller. The main app does not silently truncate it. Queue close during explicit disposal/process exit does not try to replay input into another process.

No compression policy, Ghostty pin, terminal history limit, or native synchronization has been changed. A host probe filled 50,000 history lines, completed incremental compression, read the history back, and resumed output without hanging. This does not establish that compression is fault-free on every Android device.

## Verification

- A paused PTY process group plus a delayed child reader exercises a 128 KiB write. The UI call must return within 1.5 seconds. After the child resumes, the capture must match every submitted byte. API 34 and 35 pass. API 21 skips this specific raw-PTY test because its system image lacks `stty`.
- A write beyond the 1 MiB bound is rejected once; a later ordinary command still reaches the same PTY. This passes on API 21, 34, and 35.
- An Android test produces 3,000 history lines, backgrounds the activity across the idle-compression interval, returns to the same process, checks retained history, and submits UTF-8 input. API 21, 34, and 35 pass.
- Host regressions cover native compression convergence, history restoration/resumed output, queue copying, wraparound, atomic overflow rejection, range validation, closure, and consumer wakeup.
- Debug and release host suites pass 165 emulator, 51 View, and ten app tests per build type. Release app build and terminal-view lint pass.
- Android connection/View/pointer suites complete 69 scenarios without failures on API 21 x86, API 34 x86_64, and API 35 x86_64. API 21 skips 13 scenarios; API 34/35 skip optional live input. Some newer-API assertions return early on older Android.

Physical-device Doze, OEM background freezing, long-lived SSH network timeouts, and hours-long inactivity have not been reproduced. If those paths still freeze, capture the process state and main-thread/native stack before restarting the app. These checks do not claim to fix Android suspending a child process or a remote connection closing.

Float and other pinned consumers receive this library change only after their muxy revision is updated and rebuilt. This correction has not been released yet.
