# Complete native Android parity ledger

> [简体中文](zh-CN/native-parity.md) | **English**

Date: 2026-10-24. Baseline: the current old Android client (React / Capacitor) versus Kotlin / Compose `nativeApp`.
The initial inventory used source, Android capability gates, and existing home screenshots without rerunning the app.
Batches 1 and 2 have since added implementation and checks; see the [project plan](project-plan.md) for results and
coverage limits. No real-account mutations were performed.

## Scope and status

Fully port exposed old Android features, interactions, settings, and feedback first, retaining Rust.
The current native client has basic playback and generic pages but substantial missing actions and configuration.
Listening-only purpose does not authorize dropping old features.

“Code present” means implementation exists, “partial” means incomplete user capability, and “missing” means the
native UI or integration is absent. None implies device acceptance. Use IDs for future work and refine details.

## Navigation, home, discovery, search

| ID  | Old Android baseline                                                                       | Native status / difference                                                                                                                              |
| --- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| N01 | Phone / tablet navigation and library hub                                                  | Code present: batch 1 aligns destination order, wide navigation, and library-child selection; native Back retained; see project plan for device results |
| N02 | Top search, quick theme switch, account avatar                                             | Code present: batch 1 adds top search, theme toggle, and account avatar; see project plan for device results                                            |
| N03 | Song / artist / album links across lists, player, and queue                                | Partial: batch 2 adds song / artist / album links in lists and details; dedicated player / queue entry points remain in batch 3                         |
| H01 | Dedicated recommendation cards, immediate play, metadata                                   | Code present: dedicated recommendation labels, counts, immediate play and separate open actions; see batch 2 validation record                          |
| H02 | Guess / radar Core recommendation sessions                                                 | Partial: Guess / radar entries call continuation_start; refill and playback integration remain in batch 3                                               |
| D01 | Remembered discovery tabs: featured, charts, new songs / albums, categories, MVs, podcasts | Code present: seven data-driven remembered tabs including featured / MVs / podcasts; old display-only cards stay display-only                           |
| D02 | Area cards with navigation / quick play                                                    | Code present: area playlist / artist navigation, quick play and grid presentation                                                                       |
| S01 | Four search kinds                                                                          | Code present: four kinds and separate cached results; live provider and UI results are recorded separately in batch 2                                   |
| S02 | Android debounce, IME composition, submit / clear, initial focus                           | Code present: 280 ms debounce, composition guard, submit dismisses keyboard, clear and initial focus; see device coverage                               |
| S03 | Automatic pagination, separate pagination state / retry                                    | Code present: automatic paging, separate pending / failure / retry, deduplication, page restoration and obsolete-request guards                         |
| S04 | Recommended playlists on empty search                                                      | Code present: blank search loads recommended playlists from home data with open / play actions                                                          |

Sources: [Sidebar](../src/components/Sidebar.tsx), [TopBar](../src/components/TopBar.tsx),
[HomePage](../src/pages/HomePage.tsx), [ExplorePage](../src/pages/ExplorePage.tsx),
[AreaPage](../src/pages/AreaPage.tsx), [SearchPage](../src/pages/SearchPage.tsx).
Native implementation: [NativeApp](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeApp.kt),
[NativeModels](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeModels.kt),
[NativeViewModel](../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeViewModel.kt).

## Details, account, library

| ID  | Old Android baseline                                                                 | Native status / difference                                                                                                                                                                                                                                                                                   |
| --- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| C01 | Song metadata, entity links, actions                                                 | Code present: song metadata, track number / duration / quality, entity links and track action menu                                                                                                                                                                                                           |
| C02 | Album release year, genre, artist, totals, shuffle, batch enqueue                    | Code present: year / genre / artist / counts / duration, sequential / shuffle play and batch enqueue                                                                                                                                                                                                         |
| C03 | Artist top / all songs / all albums tabs and pagination                              | Code present: top / all songs / all albums tabs and provider_artist_catalog pagination                                                                                                                                                                                                                       |
| C04 | Playlist owner, updated date, totals, ownership / capabilities                       | Code present: owner / update / total / loaded duration, ownership and editability; live account data pending acceptance                                                                                                                                                                                      |
| C05 | Playlist shuffle and batch enqueue                                                   | Code present: sequential / shuffle play and batch enqueue of loaded tracks, preserving old account-list scope                                                                                                                                                                                                |
| A01 | QQ / WeChat, official-app authorization, QR, cancel, sign-out, restore               | Code present / shared host: feedback, QR refresh, reopen official app, account refresh / restoration retry and OAuth locale; one live QQ phone login and benchmark cold-start restoration verified; device-limit feedback / stable identity added; WeChat / WebView, sign-out and long-wait recovery pending |
| A02 | Profile, identity, membership tier / status / expiry / benefits                      | Code present: profile / masked identity, tier / membership / expiry and secondary benefits; fixture UI checked, live account pending                                                                                                                                                                         |
| A03 | Library sign-in / reauthorization guidance, stale / partial results, pagination      | Code present: login / reauthorization / unsupported / stale / partial / pagination failure and retry; account revision changes clear old data                                                                                                                                                                |
| A04 | Synchronized favorites, pending / capability states, feedback                        | Code present: shared favorites, pending / rights guards and reconciled / rejected / unknown feedback; fixture concurrency checks, live writes pending                                                                                                                                                        |
| A05 | Add song to writable playlist picker                                                 | Code present: writable owned-playlist picker with paging / error / stale feedback and add requests; live writes pending                                                                                                                                                                                      |
| A06 | Capability-based rename / delete, add / remove current song, confirmation / feedback | Code present: permission-gated rename / delete / add or remove current song, confirmations and outcomes; live writes pending                                                                                                                                                                                 |
| A07 | Collect / uncollect playlist                                                         | Code present: permission-gated collect / uncollect and projected results; live writes pending                                                                                                                                                                                                                |

Sources: [SongPage](../src/pages/SongPage.tsx), [AlbumPage](../src/pages/AlbumPage.tsx),
[ArtistPage](../src/pages/ArtistPage.tsx), [PlaylistPage](../src/pages/PlaylistPage.tsx),
[LibraryPage](../src/pages/LibraryPage.tsx), [AddToPlaylistPicker](../src/components/AddToPlaylistPicker.tsx),
[account runtime](../src/application/account-runtime.ts), [settings](../src/pages/SettingsPage.tsx).
Native equivalents are concentrated in the three `Native*` files above.

Both implementations have underlying create-playlist methods, but this review found no independent old product UI
creation entry point. An API alone does not establish old UI functionality.

## Playback, queue, sharing

| ID  | Old Android baseline                                                             | Native status / difference                                                                                   |
| --- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| P01 | Transport, seek, sequential / shuffle / repeat one                               | Code present; verify behavior and persistence                                                                |
| P02 | Mini-player previous and favorite / share actions                                | Partial: play / pause, next, queue only                                                                      |
| P03 | Requested / resolved quality, capabilities, unavailable options, fallback reason | Partial: static choices and resolved quality without equivalent explanations / gating                        |
| P04 | Queue play-next, reorder, entry operations                                       | Partial: index-based play and entry removal; no play-next / reorder                                          |
| P05 | Current / upcoming queue and actual Core shuffle traversal                       | Partial: raw list without upcoming traversal projection                                                      |
| P06 | Loading / buffering / error / unavailable feedback                               | Partial: error text and messages, mini player primarily playing boolean                                      |
| I01 | System media, lock screen, headset / Bluetooth, focus, background, deep links    | Shared host / pending acceptance; do not count existing backend as a rewrite, or skip native-package testing |
| I02 | Public link, YAQMC deep link, plain-text system sharing                          | Partial: one combined public-link share, absent from full player                                             |

Sources: [PlayerBar](../src/components/PlayerBar.tsx), [TrackList](../src/components/TrackList.tsx),
[QueuePanel](../src/components/QueuePanel.tsx), [SongShareActions](../src/components/SongShareActions.tsx),
[Android playback service](../apps/android/android/app/src/main/java/org/yaqmc/android/media/PlaybackService.kt).
Loaded-tracks-only play-all also exists in old account lists; validate each page against its old behavior rather
than universally classifying this as a new regression. Volume tools hidden in old compact Android layouts are
not automatically native-phone omissions.

## Lyrics, appearance, language

| ID  | Old Android baseline                                                                     | Native status / difference                                                                                                               |
| --- | ---------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| L01 | Synchronized lines, translation / romanization, lyric seeking                            | Code present; validate scrolling and track changes                                                                                       |
| L02 | Word highlighting, fill / jump effects                                                   | Missing: word timing parsed but only line emphasis rendered                                                                              |
| L03 | Secondary-line auto / show / hide and user timing offset                                 | Missing settings; only document offset applied                                                                                           |
| L04 | Font scale / weight / spacing, spring / scale / blur, hide passed lines, fade width      | Missing settings and effects                                                                                                             |
| L05 | Built-in layouts / presets, custom copy / edit / reset, composer / preview               | Missing: fixed narrow / wide arrangement only                                                                                            |
| O01 | System / light / dark                                                                    | Code present: three modes, toggle from resolved appearance, theme and language saved independently                                       |
| O02 | Palette, custom primary / secondary, preview, reset                                      | Missing: hardcoded native colors                                                                                                         |
| O03 | Default / artwork / color / imported-image background, fit, influence, opacity, material | Missing import / settings / rendering                                                                                                    |
| O04 | App interface font scale / family                                                        | Missing; system font scaling is not equivalent                                                                                           |
| O05 | System / Chinese / English language                                                      | Code present: system / Chinese / English selection and static text resources; cached labels update while provider content stays original |

Sources: [LyricsPanel](../src/components/LyricsPanel.tsx), [lyrics scene](../src/components/lyrics-scene/LyricsScene.tsx),
[LyricsPresetEditor](../src/components/LyricsPresetEditor.tsx), [preferences](../src/application/preferences.ts),
[settings](../src/pages/SettingsPage.tsx). Native counterparts are `NowPlaying`, `LyricList`, and `Settings` in `NativeApp`.
Port equivalent native capabilities and interaction intent; the AMLL React / DOM implementation is not itself a Compose implementation.

## Statistics, maintenance, delivery

| ID  | Old Android baseline                                                                                  | Native status / difference                                                                                                                           |
| --- | ----------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| T01 | Four date ranges, trends, song / artist / album totals, quality / provider breakdown, confirmed clear | Partial: fixed 30-day summary / top-song cards only                                                                                                  |
| T02 | Cache size, media / artwork counts, metadata / lyric entries, clear                                   | Partial: rounded MB total and clear only                                                                                                             |
| T03 | Log level, console forwarding, clear logs, redacted diagnostic sharing, issue reporter                | Missing native UI; some shared host / Core capabilities exist                                                                                        |
| T04 | Update checks / state / Release link, version / platform / audio information                          | Missing native update and about UI despite shared checker                                                                                            |
| T05 | Preference persistence and upgrade compatibility                                                      | Partial: batch 1 persists theme and language and retains native theme key; other module settings and cross-package legacy upgrades remain later work |
| T06 | Signed release, package update, data continuity, release workflow                                     | Incomplete: separate nativeui package, default benchmark build, workflow still produces old app                                                      |

Sources: [StatisticsPage](../src/pages/StatisticsPage.tsx), [settings](../src/pages/SettingsPage.tsx),
[Android bridge](../packages/yaqmc-client/src/bridges/android.ts),
[native Gradle](../apps/android/android/nativeApp/build.gradle.kts),
[native build script](../scripts/build-android-native.mjs), [release workflow](../.github/workflows/electron-release.yml).

## Old Android platform boundaries

The [Android capability table](../packages/yaqmc-client/src/bridge.ts), layouts, and client gates establish:

- Desktop lyric windows / island, desktop window / tray / global shortcuts, plugins, and local HTTP API were disabled.
- General file export and statistics JSON / CSV export are gated by `fileExport = false`. Buttons in shared source
  do not establish Android functionality.
- Diagnostic bundles are an exception: settings explicitly enable Android export / native sharing, making T03 a real gap.
- Image import is supported by `fileImport = true`, so O03 is in scope.

These distinguish actual port omissions from platform limits. Extending beyond old Android needs separate scope.

## Work and completion criteria

The [project plan](project-plan.md) confirms the batch order: foundation / shell, browsing / accounts, player / queue,
lyrics / personalization, statistics / maintenance, delivery / replacement. Include reliability fixes with each group.
Later groups remain part of the complete port.

N01 Back behavior is a user-confirmed difference: preserve the current native implementation, with its source baseline
recorded in the project plan. That decision alone does not complete other navigation differences; batch 1 Back-path
device results are recorded separately in the plan.

For each ID verify the old entry point, equivalent native action / state, persisted settings, failure / cancellation
feedback, automated checks, and device evidence. Track real-account mutation and upgrade / data acceptance separately;
only record passes for actually executed checks. Complete and accept every applicable item, or obtain explicit user
agreement to a difference, before calling the port complete or replacing the old client.

Existing home screenshots show layout, top entry point, and mini-player differences. Accounts, themes, and content
differ, so they support layout observations only. They were viewed in the local Git-ignored
`artifacts/android-native-comparison/` directory during the initial inventory. New batch 1 UI-test screenshots use
`batch1-*` filenames; their validation scope is recorded separately from the old-client comparison baseline.
This ledger does not infer a completion percentage from item counts or count shared Rust functionality as rewrite work.
