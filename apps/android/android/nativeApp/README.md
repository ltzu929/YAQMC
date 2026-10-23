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

Not yet at UI parity: word-by-word lyric highlighting, account playlist editing,
custom background/font/lyric presets, and the existing diagnostic export/update UI.
Real account mutations and OAuth round trips require explicit interactive validation.

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
