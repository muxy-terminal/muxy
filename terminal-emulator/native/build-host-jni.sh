#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module="$(cd "$here/.." && pwd)"
java_home="${1:?Java home is required}"
output="$module/build/native/host/jni"
mkdir -p "$output"
temporary="$(mktemp "$output/libmuxy_terminal.XXXXXX.so")"
pty_test="$(mktemp "$output/termux-test.XXXXXX")"
trap 'rm -f "$temporary" "$pty_test"' EXIT
cc -D_GNU_SOURCE -std=c11 -Wall -Wextra -Werror \
    -I "$java_home/include" -I "$java_home/include/linux" \
    "$module/src/test/jni/termux_test.c" -o "$pty_test"
"$pty_test"
bash "$here/build.sh" host
cc -shared -fPIC -std=c11 -Wall -Wextra -Werror \
    -I "$java_home/include" -I "$java_home/include/linux" \
    -I "$module/build/native/ghostty/include" "$module/src/main/jni/muxy_terminal.c" \
    -L "$module/build/native/host/lib" -lghostty-vt \
    -Wl,-rpath,'$ORIGIN/../lib' -o "$temporary"
mv "$temporary" "$output/libmuxy_terminal.so"
rm -f "$pty_test"
trap - EXIT
