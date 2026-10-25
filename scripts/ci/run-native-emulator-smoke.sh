#!/usr/bin/env bash
# CI-only emulator smoke test: fake Core and local UI; no QQ login or provider writes.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

output="artifacts/android-emulator"
mkdir -p "$output"
apk="apps/android/android/nativeApp/build/outputs/apk/debug/nativeApp-debug.apk"
test_apk="apps/android/android/nativeApp/build/outputs/apk/androidTest/debug/nativeApp-debug-androidTest.apk"
test_package="org.yaqmc.android.nativeui.test/androidx.test.runner.AndroidJUnitRunner"
test_classes="org.yaqmc.android.nativeui.NativePlaybackTest,org.yaqmc.android.nativeui.NativePersonalizationUiTest"

adb wait-for-device
adb install --no-streaming -r "$apk"
adb install --no-streaming -r -t "$test_apk"

set +e
adb shell am instrument -w -r -e class "$test_classes" "$test_package" 2>&1 | tee "$output/instrumentation.txt"
test_status=${PIPESTATUS[0]}
set -e

# Keep local, bounded failure evidence; do not collect or upload provider/account data.
adb logcat -d -v brief -t 1500 > "$output/logcat-tail.txt" 2>/dev/null || true
adb pull /sdcard/Android/data/org.yaqmc.android.nativeui/files "$output/screenshots" >/dev/null 2>&1 || true

if [ "$test_status" -ne 0 ] || ! grep -Eq '^OK \\([0-9]+ tests?\\)' "$output/instrumentation.txt" || grep -Eq 'FAILURES!!!|INSTRUMENTATION_RESULT: shortMsg=' "$output/instrumentation.txt"; then
  echo "Native emulator instrumentation did not pass" >&2
  exit 1
fi
echo "Native emulator smoke tests passed"
