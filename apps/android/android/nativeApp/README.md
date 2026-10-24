# Native Android migration checkpoint

Kotlin/Compose UI calling the existing in-process Rust Core through `CoreManager`.
The APK has no Capacitor dependency or packaged web renderer. The isolated official
OAuth screen still uses the existing restricted WebView.

Package: `org.yaqmc.android.nativeui`. It can coexist with the original app; accounts,
queues, settings and caches are independent. Do not copy credential files between apps.

Build with the repository Android toolchain environment configured:

```powershell
node scripts/build-android-native.mjs
# Use an already built release Core from app/build/generated/jniLibs:
node scripts/build-android-native.mjs --reuse-core
# Development build (not suitable for performance comparisons):
node scripts/build-android-native.mjs --debug --reuse-core
# Compare with a specific existing Core binary (folder contains arm64-v8a/libyaqmc_core.so):
$env:YAQMC_ANDROID_NATIVE_LIB_DIR = '<absolute Core folder>'
node scripts/build-android-native.mjs --reuse-core
```

The default `benchmark` build is minified, resource-shrunk, profileable and locally
signed with the Android debug key. It is a comparison artifact, not a production release.
Output: `release-android/YAQMC-<version>-android-native-benchmark.apk`.

Implemented at this checkpoint: home/discovery, four search types, catalog details,
account favorites/playlists/recent history, statistics, playback/seek/mode/quality,
queue removal, sharing, login, line-synchronized lyrics with translation/romanization,
theme and cache controls. Requests use real Core data; there is no demo catalog.

Not yet at UI parity: word-by-word lyric highlighting, full player / queue controls,
custom background/font/lyric presets, and the existing diagnostic export/update UI.
Real account mutations and OAuth round trips require explicit interactive validation.

Batch 1 foundation work (2026-10-23): Home / Discover / Library / Search ordering,
library-child selection, top search / theme / account shortcuts, system / light / dark
mode, system / Simplified Chinese / English language selection, and per-key native
preference persistence. Cached UI labels resolve at render time so language changes
retain provider content and existing page caches. Current native Back behavior is
preserved. See the [project plan](../../../../docs/project-plan.md) for scope and acceptance.

Batch 1 validation: 12 native and 31 shared-host JVM tests passed; native debug lint
reported 0 errors / 32 warnings. Five device UI tests passed on Redmi K70 / Android 16,
including portrait / landscape navigation, language / theme, preference rereads and
Activity recreation, system bar theme flags, cached label localization, and native Back behavior. They use
the actual Core and preserve the paused playback state. They do not validate account
mutations or extended playback. Test preferences and orientation are restored afterward.

Build `:nativeApp:assembleDebug :nativeApp:assembleDebugAndroidTest`, install both APKs
(use `adb install -r -t` for the test package),
and run `adb shell am instrument -w -r -e notClass org.yaqmc.android.nativeui.NativeProviderSmokeTest org.yaqmc.android.nativeui.test/androidx.test.runner.AndroidJUnitRunner`.
Some ROMs require Allow once for the test helper Activity. The fixture foregrounds
the target app without changing permanent device permissions. Gradle's UTP launcher
was unavailable in this run due to a dependency-download TLS failure; direct ADB
execution passed. This is independent of the original full OAuth / account gates.

Batch 2 browsing and account work (2026-10-24): recommendation quick play and Core
continuation entry points, seven remembered discovery tabs, area cards, debounced
search with IME composition protection, automatic pagination with independent retry,
entity links and metadata, artist catalog tabs, shuffle and batch enqueue, account
membership and recovery feedback, shared favorite state, a writable-playlist picker,
and permission-gated playlist mutations. Request deadlines and obsolete account /
query responses have explicit handling. See the project plan for validation and
remaining live account / continuation gates; code presence is not full acceptance.

`NativeCatalogStateTest` and `NativeBrowseTest` use an injected Core interface for
mutation and failure cases; production still constructs `NativeCore`. They never
write to a live account. `NativeProviderSmokeTest` is a separate read-only live-provider
check for home, discovery, four search kinds and entity details; run it explicitly
with `-e class org.yaqmc.android.nativeui.NativeProviderSmokeTest`. Its result depends
on the current upstream service. Account authorization / real writes remain separate.

Batch 2 validation: 23 native and 31 shared-host JVM tests, plus all 18 device tests,
passed on Redmi K70 / Android 16. Device coverage comprises 8 isolated state tests,
4 isolated browsing UI tests, 5 shell regressions and 1 read-only live-provider test.
Lint reports 0 errors / 32 warnings. The optimized benchmark APK was installed with
`adb install --no-streaming -r`; the helper was removed afterward. Existing ARM64
Rust binaries were reused. Logs, screenshots and `batch2-validation.json` are in
ignored `artifacts/android-native-comparison/`. Live account writes, OAuth round trips,
continuous recommendation refill and active-playback regression remain unverified.

Login follow-up: live phone confirmation reached credential exchange, which returned
`20279` twice; official device management showed 5 logged-in devices. Preserve the
business code and show the device-limit guidance. Mobile login now persists its own
installation device identity instead of generating one per retry. After the user
released device slots, one wait lost its connection, then user-confirmed QQ phone
login succeeded. Credential commit and authenticated UI were verified. Updating to
the optimized benchmark and cold-starting retained the session. WeChat / WebView,
sign-out, long-wait connection recovery and account mutations remain separate gates.
Follow-up validation: 282 Rust tests passed / 8 ignored, 24 native JVM tests passed,
7 targeted device tests passed, and lint reported 0 errors / 32 warnings. Rust was
rebuilt; both APK variants contain the same Core binary. See the plan for build
recovery details and ignored `login-fix-validation.json` for local evidence.

Performance comparisons must use optimized builds on the same device, orientation,
refresh rate, track, account state, cache state and thermal conditions. Measure the
WebView renderer process as well as the original app process. The initial baseline
collected before this checkpoint is not a controlled A/B result.

Device validation on 2026-10-23: installed alongside the original app on a Redmi K70;
verified home loading, embedded recommendation details, playback and progress while
the activity was in the background. Search encountered a QQ Music rate limit.
Both APKs used the same Rust binary for the initial startup/memory/scroll samples.
Accounts, themes and page layouts differed, so these samples remain preliminary;
they do not establish equal-content performance or battery savings.
