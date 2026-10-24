# Native Android project plan

> [简体中文](zh-CN/project-plan.md) | **English**

Updated: 2026-10-24. This document distinguishes confirmed direction, provisional decisions, and open questions.

## Confirmed direction

- Build a third-party QQ Music client because the official application feels bloated.
- The core purpose is listening to music. The current phase must fully port the old Android client's features, interactions, and settings.
- Target Android and reduce the complexity of development, builds, and maintenance.
- Use a native Android UI, currently Kotlin / Jetpack Compose.
- Record scope, architecture decisions, and acceptance criteria in documentation.

The Android-only direction does not authorize immediate removal of desktop code, the old client, or release workflows.
Their retirement still needs planning.

## Product scope: fully port the old Android client first

The user clarified that the native client omits too much and must first fully port the original application.
The earlier interpretation of listening-only as permission to simplify existing functionality has been corrected.

- Use capabilities actually exposed by the old Android client as the baseline, covering pages, actions, settings,
  state feedback, and delivery.
- Include existing lyrics, appearance, account, statistics, diagnostics, and update capabilities. Do not downgrade
  them based on assumed usage frequency.
- Reuse Rust and implement the native UI and Android interactions in Kotlin / Compose.
- Preserve information hierarchy, interaction intent, and configurability. Review visual details with each module;
  native adaptation must not silently omit capabilities.
- Reduce stack and engineering overhead. Discuss feature simplification after the complete port.

The [native parity ledger](native-parity.md) records scope. Missing items are migration work, organized into the six
confirmed batches below. List desktop capabilities disabled in the old Android host separately. Package identity
and compatibility with existing account / local data still require a migration decision.

## D001: provisionally retain the Rust Core

Status: **provisionally adopted; open to review**. Decision date: 2026-10-23.

Use Kotlin / Compose with the existing Rust Core through JNI. The user prefers an entirely Android-native stack
in principle, but provisionally retains Rust because a rewrite would be substantial and the recent tests did not
appear to show a large difference.

Evidence limit: the existing comparison used native and Web UIs with the same Rust binary. It does not establish
performance, battery, or maintenance equivalence between Kotlin / Rust and an all-Kotlin implementation.
See the [native module checkpoint](../apps/android/android/nativeApp/README.md) for recorded device observations and limits.

Constraints:

- Direct new UI work to the native Android module.
- Reuse valuable Rust capabilities and match exposed old Android features; adapt implementation structure to native needs.
- Assign new responsibilities by ownership and implementation / maintenance cost. Avoid duplicate business state.
- Simplifying the build remains open work. A native UI does not mean the repository is independent of Node,
  Capacitor, or the old project configuration.

Review this choice when reproducible JNI / Rust maintenance, build, or performance problems emerge, or when a
specific benefit justifies the Kotlin rewrite cost. No full rewrite or all-Kotlin benchmark implementation is
required to adopt this provisional decision.

## Development constraints

Associate each porting task with parity ledger IDs. Check user entry points, interactions, settings, state, and
evidence separately. Existing Rust code, a matching page name, or a passing build alone does not establish parity.

1. Define the goal, scope, and observable acceptance criteria before implementation. A task description is enough
   for small changes; a separate document is unnecessary.
2. Distinguish confirmed requirements, suggestions, and open questions. Open questions do not authorize implementation.
3. Record reasons for technology or scope changes before affected implementation. Proceed directly when the goal
   and authorization are clear.
4. Keep changes focused. Explain the problem and maintenance cost when adding a dependency or abstraction.
5. Validate according to risk. Distinguish automated checks, builds, device tests, and real-account validation;
   identify checks that were not run.
6. Update affected documentation. Describe planned behavior, implemented behavior, and verified behavior separately.

Existing credential protection, log redaction, playback entitlement, and license constraints continue to apply.

## Confirmed migration batches

The user agreed to these six batches. Preserve the full-port scope and update this plan when reordering work.
Each batch follows old-behavior review, native implementation, targeted checks, and device acceptance.
Shared capabilities still require acceptance in the native package.

| Batch | Scope                                                                                                                               | Parity IDs and boundaries                                                                                  |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 1     | Foundation and application shell: navigation, top shortcuts, color mode, language, preference persistence foundation                | N01, N02, O01, O05; T05 persistence foundation; preserve native Back behavior as specified below           |
| 2     | Browsing and accounts: home, Discover, search, entity details, login, membership, library, playlist actions                         | N03, H01–H02, D01–D02, S01–S04, C01–C05, A01–A07; player / queue links under N03 finish in batch 3         |
| 3     | Player and queue: controls, quality feedback, recommendation continuation, reordering, play-next, sharing, system media integration | P01–P06, I01–I02; integrate H02 playback / replenishment and N03 player / queue links                      |
| 4     | Lyrics and personalization: word effects, layouts, preset editor, palettes, backgrounds, fonts                                      | L01–L05, O02–O04; persistence and compatibility for these settings under T05                               |
| 5     | Statistics and maintenance: listening statistics, cache, diagnostics, issue reporting, updates, About                               | T01–T04; connect associated preferences to T05                                                             |
| 6     | Delivery and replacement: builds, signing, upgrades, data compatibility, complete regression                                        | T06 and T05 legacy-data compatibility; review every applicable item and determine replacement arrangements |

Track cross-batch IDs at subtask level; completing one part does not complete the entire ID.
Use the current native build entry point to validate earlier batches. Final delivery arrangements belong to batch 6.

## Batch 1 tasks and acceptance criteria

This batch covers the application shell and basic settings. Page contents belong to batch 2; custom palettes,
backgrounds, fonts, and lyric layouts belong to batch 4. Do not mark those capabilities complete in batch 1.

| Task  | Scope                                     | Observable acceptance criteria                                                                                                                                                                                                                                           |
| ----- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| B1-01 | Navigation and library entry points (N01) | Phone destinations follow old Android order: Home, Discover, Library, Search. Wide-screen destinations and selection correspond; favorites, playlists, recent history, and statistics belong to Library. Pages remain reachable and unobscured by navigation.            |
| B1-02 | Top shortcuts (N02)                       | Search, theme toggle, and account avatar are reachable. The account shortcut reaches the appropriate settings location. Guest / signed-in identity uses existing account state; membership details belong to a later batch.                                              |
| B1-03 | Color mode (O01)                          | System, light, and dark are selectable. The shortcut switches from the resolved mode to the opposite explicit mode and saves it. Settings and shortcuts agree; system mode responds to system theme changes.                                                             |
| B1-04 | Language (O05)                            | System, Simplified Chinese, and English are selectable. Existing native static text, action feedback, and accessibility labels follow the preference. Server content and music metadata remain in their original language. Later UI additions include both translations. |
| B1-05 | Persistence foundation (part of T05)      | Theme and language survive UI recreation and application restart. Missing / invalid values have defined defaults. Adding settings for later modules preserves other stored values. Preserve the existing native theme preference.                                        |
| B1-06 | Back behavior and shell regression (N01)  | Preserve the current native Back implementation. Verify root navigation, detail pages, settings, player, and queue return paths. Theme / language and orientation changes do not unexpectedly issue playback commands.                                                   |

### Confirmed difference: preserve current native Back behavior

The user's final instruction is to retain the current native behavior. The source baseline is:

- Selecting a root destination clears the native page back stack; opening a regular page records its source.
- System Back outside Home first tries the stack and falls back to Home when empty. Home uses Android's default Back handling.
- The top Back button on non-root pages first tries the stack and falls back to Home when empty.

See Back handling in [NativeApp](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeApp.kt)
and `navigate` / `back` in [NativeViewModel](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeViewModel.kt).
This defines the source behavior being retained; batch 1 validation is recorded below. Do not introduce Back rules
merely to match the old client.

Implementation order: review entry points and text; establish preference reads / writes for theme and language;
align navigation and top shortcuts; validate. Choose class structure, persistence, and navigation libraries when
implementing; this plan does not prescribe new dependencies.

Completion records include task results, parity IDs, check commands / results, device / screen conditions, and
comparison evidence. Automated checks focus on Back decisions, color-mode resolution, locale fallback, and
preference persistence. Record builds and device checks separately. Device coverage includes phone and wide layouts,
all three theme and language choices, restart persistence, and existing playback paths. Unperformed checks stay
pending. Original-package upgrades and cross-application account / data migration remain decisions for batch 6.

### Batch 1 implementation record (2026-10-23)

Native implementations for B1-01 through B1-05 are connected. B1-06 retains the existing Back handlers and stack.
Phone and wide layouts share destination order, and library children retain Library selection. Top shortcuts expose
search, theme toggle, and account avatar. Basic settings use the existing `native-ui` SharedPreferences, retain the
theme key, and add a language key; per-key writes preserve unrelated settings. Android English / Chinese resources
cover static text, and cached pages keep resource references. Changing language neither clears page caches nor
issues playback requests. Server metadata and server error messages remain unchanged. Full shared OAuth host
interaction and text acceptance remain under A01 in batch 2.
Color mode also updates status / navigation bar icon appearance, with light / dark flags checked on device.

Device testing exposed the fourth rail destination being clipped in phone landscape. Rail items now respect the
available height so all four remain visible. Batch 1 code and the following checks are complete; batches 2–6 retain
their original scope.

| Check                     | Result and evidence boundary                                                                                                                                                                                                                                                            |
| ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Native unit tests         | `:nativeApp:testDebugUnitTest`: 12 passed, comprising 7 existing tests and 5 preference / root-selection tests                                                                                                                                                                          |
| Shared host unit tests    | `:app:testDebugUnitTest`: 31 passed                                                                                                                                                                                                                                                     |
| Static analysis           | `:nativeApp:lintDebug`: 0 errors, 32 warnings; warnings have not been presented as fixed                                                                                                                                                                                                |
| Device UI tests           | Redmi K70 / Android 16 / 1440 × 3200: 5 `NativeShellTest` cases passed, covering portrait navigation / library selection, all four landscape destinations, three language / theme choices, shortcuts, recreation / preference rereads, cached label localization, and native Back paths |
| Playback state regression | Theme / language and recreation checks preserve current track, queue, and playing flag. Playback was paused; this does not establish full active-playback acceptance or battery behavior.                                                                                               |
| Build                     | `node scripts/build-android-native.mjs --reuse-core` produces an optimized benchmark APK using the existing ARM64 Rust library. Rust was not rebuilt. This remains a locally debug-signed comparison artifact.                                                                          |

Build `:nativeApp:assembleDebug` and `:nativeApp:assembleDebugAndroidTest`, install both APKs (use `adb install -r -t`
for the test package), then run
`adb shell am instrument -w -r org.yaqmc.android.nativeui.test/androidx.test.runner.AndroidJUnitRunner`.
Gradle `connectedDebugAndroidTest` could not download a UTP dependency because of a TLS failure, so the same tests
were launched directly with ADB. Device prompts for test helper Activities used Allow once; no permanent permissions
were changed. The fixture includes foreground launching for ROMs that block background test launches. Each test
restores theme / language and orientation. Tests do not like tracks, edit playlists, sign out, or authorize login.

The optimized native package was updated in place and its cold-start UI checked; the test helper package was removed.
Local logs, UI-test / cold-start screenshots, and `batch1-validation.json` are in Git-ignored `artifacts/android-native-comparison/batch1-*`.
Reproducible test sources are in the native module's `src/test/` and `src/androidTest/`. Separate tablet hardware,
automatic system theme changes, recovery after process reclamation, authenticated avatars, and real account flows
have not each received device coverage. Carry their validation into subsequent batches; do not describe these
checks as complete-port acceptance.

## Batch 2 tasks and acceptance criteria

Port the old Android browsing and account behavior while preserving batch 1 navigation and preferences.
Add both English and Chinese resources for new UI text.

| Task  | Scope                                     | Observable acceptance                                                                                                                                                                                                                        |
| ----- | ----------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| B2-01 | Home, discovery, areas (H01–H02, D01–D02) | Dedicated recommendation entries with open / immediate play; Guess and radar start Core continuation; data-driven discovery tabs remember selection; old display-only cards remain display-only; area playlists and artists are reachable    |
| B2-02 | Search (S01–S04)                          | Four kinds, 280 ms debounce, IME composition guard, submit dismisses keyboard, clear and initial focus; blank search shows recommended playlists; automatic pagination has separate loading / retry and rejects obsolete results             |
| B2-03 | Details (N03, C01–C05)                    | Song / artist / album links and dedicated metadata; artist top / all songs / all albums with paging; sequential / shuffle playback and batch enqueue; account playlist ownership and permissions                                             |
| B2-04 | Account and library (A01–A04)             | Sign-in / cancellation / sign-out / refresh and restoration feedback; tier, membership, expiry and additional benefits; login / reauthorization / stale / partial / pagination states; shared favorite state and pending / capability guards |
| B2-05 | Playlist operations (A05–A07)             | Writable playlist picker; permission-gated rename / delete / add or remove current song / collection; pending guards, destructive confirmations and explicit unconfirmed outcomes                                                            |
| B2-06 | Reliability and validation                | Deadlines end loading and permit retry; obsolete requests cannot replace newer pages; separately record unit tests, builds, lint, device checks, live account writes and continuation refill coverage                                        |

H02 refill and playback integration, plus N03 player / queue entry points, remain in batch 3.
Live account write acceptance requires an identified test account and target; development authorization
does not imply changing the existing account library.

### Batch 2 implementation record (2026-10-24)

`NativeCatalog` and `NativeCatalogRepository` reuse existing Core methods. Home adds separate open /
quick-play actions and Guess / radar continuation startup. Discovery exposes seven data-driven remembered
tabs while keeping old MV / podcast cards display-only. Search adds 280 ms debounce, IME composition
protection, submit / keyboard dismissal, clear, initial focus and blank-query recommendations. Pagination
has independent pending / retry state, preserves loaded results and cached page numbers, and rejects obsolete responses.

Details add entity links, dedicated metadata, artist top / all songs / all albums, sequential / shuffle play
and batch enqueue. Account UI adds membership / secondary benefits and refresh / recovery feedback.
Favorites share a projection, isolate account revisions and protect confirmed mutations from older reads.
The playlist picker filters writable owned playlists; edit / collection actions use capabilities, destructive
confirmations and explicit unconfirmed results. Failures appear inside editing / selection dialogs as well.
Request deadlines become recoverable failures while navigation cancellation remains cancellation.
Production still constructs `NativeCore`; fake Core responses are confined to tests.

Device testing found that separate popup windows restored the system locale, producing Chinese song
menus on English pages. `NativePopups` restores the in-app locale inside menus and dialogs. The shared
OAuth host accepts an optional restricted language parameter; the old client retains its existing behavior
when absent. Native Back behavior and batch 1 preferences remain unchanged.

| Check                  | Result and evidence boundary                                                                                                                                                                                                                                                   |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| JVM tests              | 23 native and 31 shared-host tests passed, including models, paging merge, protocol parameters, deadlines / cancellation, permissions and title validation                                                                                                                     |
| Lint                   | `:nativeApp:lintDebug`: 0 errors / 32 warnings                                                                                                                                                                                                                                 |
| Device tests           | Redmi K70 / Android 16 / 1440 × 3200: all 18 tests passed through direct ADB instrumentation, comprising 8 state tests, 4 browsing UI tests, 5 shell regressions and 1 live-provider check                                                                                     |
| Isolated coverage      | State / browsing tests inject a fake Core for obsolete query / account responses, pagination retry, favorite races, writable-playlist filtering, duplicate-write guards, rejection feedback, IME composition, popup locale and membership presentation; no live account writes |
| Live provider          | Home returned 5 sections, discovery 7 tabs; song / artist / album / playlist searches each returned 30 results; album returned 12 tracks, artist top / songs / albums returned 40 / 30 / 30; read-only evidence, not account or playback acceptance                            |
| Build and installation | Debug, test and optimized benchmark APKs built successfully using the existing ARM64 Rust library without rebuilding Rust; optimized native package updated successfully and test helper removed; locally debug-signed comparison artifact                                     |

This run used `adb install --no-streaming -r`, adding `-t` for the helper, after the device canceled the initial
streaming install. Test-helper prompts used Allow once. Tests restore theme / language and orientation;
shell regressions preserve the existing paused track, queue and playback state without validating continuous playback.
Logs, screenshots, public provider counts and `batch2-validation.json` are in ignored `artifacts/android-native-comparison/batch2-*`.

Live account authorization / sign-out / restoration and real favorite / playlist writes require separate
acceptance. This work has not changed the existing account library. Recommendation refill and player /
queue integration remain in batch 3; lyrics / customization, maintenance and delivery remain in batches 4–6.

### A01 login failure follow-up (2026-10-24)

Real phone authorization reaches official QQ Music and receives confirmation, but two credential exchanges
returned business code `20279` (login device limit). The official device-management page showed 5 logged-in
devices. The implementation discarded that code and showed a generic protocol error. Preserve optional
`upstreamCode` in failure snapshots and show actionable native feedback for `20279`, retaining compatibility
with older snapshots without classifying a business failure as refusal or successful authentication.

Mobile authorization also generated a fresh random device identity on every attempt. Persist the installation's
own virtual device in provider settings, reuse its client for retries, restore identity after recreation and save
updated device information after QR preparation. Storage failures stop new requests instead of silently rotating
identity; logout and cache clearing retain this setting. This cannot automatically release an existing server
device limit; removing old devices remains the user's decision.

Validation: 282 provider Rust unit tests passed / 8 ignored; 24 native unit tests passed; lint reported
0 errors / 32 warnings. Seven targeted device tests passed: device-limit feedback, one live-provider check
and five shell regressions. ARM64 Rust was rebuilt; debug / benchmark APKs contain the same Core SHA-256.
An initial Windows temporary-directory write failed; project-local temporary storage resolved it. Overlapping
Gradle runs later corrupted Kotlin incremental caches; cleaning those build caches and rebuilding serially
with incremental compilation disabled succeeded. Final builds and installation passed; failed attempts are not passes.

After the user manually released device slots, the first wait for confirmation lost its connection. The next
user-confirmed attempt logged in successfully: credential exchange completed, logs recorded
`credential_committed=true`, and native UI showed authenticated status. Updating to the optimized benchmark
package and cold-starting retained that status, validating this session's restoration. This covers one QQ phone
authorization and cold start; WeChat / WebView authorization, sign-out, connection recovery during longer waits
and real favorite / playlist writes remain separate gates. The helper was removed; the old app and account
library were preserved. Sanitized stages and `login-fix-validation.json` are in local ignored artifacts.

## Topics still to detail

The [native parity ledger](native-parity.md) is the migration baseline. The earlier [listening review](listening-review.md)
remains a reliability review; its simplification direction has been corrected.

These topics determine implementation order and acceptance details without reducing the confirmed full-port scope:

1. Later batches: detail tasks and acceptance criteria for batches 3–6 while preserving the complete migration scope.
2. Experience parity: review old interactions, visual intent, configurability, and native adaptation, then performance criteria.
3. Architecture: Android / Rust responsibilities, data ownership, and build entry points.
4. Migration: priorities, old-project retirement, account / local-data compatibility, and release approach.

Split out architecture decisions and development acceptance documents when needed.

## Documentation entry points

This plan records the new direction. Existing [architecture](architecture.md), [development](development.md), and
[feature roadmap](roadmap.md) documents mainly describe the previous multiplatform system. Their platform targets
and feature lists need platform-specific interpretation. Exposed old Android capabilities are the port baseline;
desktop-only features and multiplatform delivery targets are listed separately.

The root [AGENTS.md](../AGENTS.md) provides a pre-development reading entry point. Saving that entry point does not
verify automatic loading in a fresh session or enforcement by CI.
