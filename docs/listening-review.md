# Native Android listening flow review

> [简体中文](zh-CN/listening-review.md) | **English**

Date: 2026-10-23. Scope: the Kotlin / Compose client, shared Android host, and Rust playback interfaces.
This review inspected source, existing test reports, and device records. It did not change functionality,
rerun the application, or operate accounts. The user subsequently clarified that the full old client must be ported.
The [parity ledger](native-parity.md) now defines migration scope; this earlier review remains reliability evidence.

## Assessment and existing flow

The basic listening flow is implemented, with substantial feature, interaction, and settings parity work remaining.
Include reliability and acceptance evidence in each migration module.
Implemented code and previously observed device behavior are separate evidence levels.

| Step                  | Implemented                                                                                                             | Evidence and gaps                                                                                                                                    |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Find music            | Home, discovery, four search types, details, pagination                                                                 | Home and embedded playlist observed; search hit upstream rate limiting, so successful search-to-play still needs validation                          |
| Start playback        | Select a track and replace the queue, play all, add to queue                                                            | Playback and advancing position observed; play all uses currently loaded tracks                                                                      |
| Control playback      | Mini and full players, pause / resume, next / previous, seek, sequential / shuffle / repeat one, quality, queue removal | Implemented; complete native-device validation and failure recovery remain open                                                                      |
| Keep listening        | Rust playback state, Media3 session, background service, audio focus, noisy-output pause                                | Short background playback observed; prolonged screen-off playback, Bluetooth, interruptions, network changes, and process recreation need validation |
| Return to music       | Login, favorites, account playlists, recent history                                                                     | Implemented; native login round trips, session restoration, and real writes require interactive validation                                           |
| Supporting experience | Line lyrics, translation / romanization, theme, cache controls, sharing, statistics                                     | Word highlighting and old settings remain migration work                                                                                             |

## Proposed priorities

### Failure recovery and clear status

Source-derived risk: `NativeCore.invoke` has a 30-second `withTimeout`, while page loading and general actions
rethrow `CancellationException`. A timeout follows that cancellation path, bypassing the ordinary error branch
that clears page loading or displays an action failure. This was inferred from control flow; no timeout was
injected or reproduced on a device during this review.

Proposed acceptance: a nonresponding request leaves loading and offers retry after timeout; ordinary navigation
cancellation does not produce a stale error.
Sources: [NativeCore.kt](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeCore.kt),
[NativeViewModel.kt](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeViewModel.kt).

The model receives playback `phase`, but the mini player primarily uses the playing boolean and does not show
loading or buffering. Playback error text is in the full player. Provide enough status and recovery actions
at the current entry point to distinguish waiting, paused, and failed playback.
Source: [NativeApp.kt](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeApp.kt).

### Validate continued listening

Proposed scenarios: successful search-to-play, natural track changes, seek, prolonged locked-screen listening,
headset / Bluetooth controls, audio-focus interruption and recovery, network interruption / changes, and queue /
account state after reopening. These are unverified scenarios, not established failures.

Existing focus and media-state policy unit tests do not validate the complete system and device paths.
Sources: [PlaybackService.kt](../apps/android/android/app/src/main/java/org/yaqmc/android/media/PlaybackService.kt),
[CorePlayer.kt](../apps/android/android/app/src/main/java/org/yaqmc/android/media/CorePlayer.kt).

### Discuss continuous playback scope

- Confirmed integration gap: native recommendations use `player_play_tracks` and never start `continuation_start`.
  The Rust ordinary-play handler ends an existing recommendation session. Full migration must restore the old guess /
  radar continuation behavior. Ordinary queued tracks can still play consecutively.
- Current behavior: play all submits loaded tracks without fetching the remainder of a paginated list. Discuss its
  intended playback scope before assuming that every list should be loaded in full.

Sources: [NativeModels.kt](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeModels.kt),
[NativeApp.kt](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeApp.kt),
[NativeViewModel.kt](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeViewModel.kt),
[Rust command handling](../crates/yaqmc-core/src/server/methods.rs).

### Simplify engineering and delivery

The native module still imports Kotlin sources, resources, and ProGuard configuration from the old app. Gradle
configuration requires installed Capacitor directories and the build entry uses Node. The existing release
workflow builds the old Android app rather than `nativeApp`. These are engineering / delivery dependencies;
they do not establish that the native APK contains a Web renderer.

After defining release and migration scope, separate the native build, shared host responsibilities, and release
acceptance. Rewriting working Rust functionality is unnecessary solely to simplify the project.
Sources: [native build configuration](../apps/android/android/nativeApp/build.gradle.kts),
[Gradle settings](../apps/android/android/settings.gradle.kts),
[native build script](../scripts/build-android-native.mjs),
[release workflow](../.github/workflows/electron-release.yml).

## Evidence limits

- Existing JUnit XML reports show seven native model / protocol tests and 31 shared host tests with zero failures.
  They were inspected, not rerun.
- Previous Redmi K70 observations covered home, embedded playlists, advancing playback, and short background playback.
  Search encountered rate limiting.
- Preliminary native startup / memory observations improved, while short CPU samples did not improve in the same
  direction. Accounts, content, themes, and caches differed. No prolonged battery test, matched-track playback
  comparison, or all-Kotlin comparison establishes battery savings.
- The native package has independent accounts, queue, and cache; it is not yet a direct upgrade replacement.
- Automated coverage focuses on models, protocol, and host policies. The native module has no `src/androidTest`
  scenario test sources yet.

See the [native checkpoint](../apps/android/android/nativeApp/README.md) for device observations and limits.
Local raw comparison artifacts are in the Git-ignored `artifacts/android-native-comparison/` directory and are
not required files for a public checkout.

Discuss migration batches against the complete parity ledger and include reliability / recovery in their acceptance.
This review does not remove existing features or settle a new home / navigation design.
