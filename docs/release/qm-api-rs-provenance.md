# qm-api-rs 来源审计记录

> 适用范围：2026-09-06 对下述固定提交的来源审计。证据保留原范围，当前依赖以 [manifest](../../crates/yaqmc-provider-qqmusic/Cargo.toml) 和 [Cargo.lock](../../Cargo.lock) 为准，发布要求见[对应源码交付政策](../../CORRESPONDING_SOURCE_POLICY.md)。

Recorded decision: **PASS** at reviewed revision
`7d0f6e18b1d1d89a06cc5964e9c057acb0926ea5`.

Repository: `https://github.com/YAQMC/qm-api-rs`. The crate declares
`GPL-3.0-or-later`; YAQMC links it unconditionally and distributes matching
corresponding source for the exact pin.

The crate records an independent Rust implementation. Its QMC implementation
maps behavioral adaptations from `gongjiehong/QMCDecode` at revision
`aea76301a08678100ec677cb61a8458bc75662ec`; the applicable MIT notice and
source-to-target mappings are present in the crate and mirrored in
[THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md). `L-1124/QQMusicApi` is
recorded as a protocol and API-shape reference, not incorporated source.
Former port and extracted-client source claims were removed before this pin.

The 2026-09-06 review retains the previously reviewed credential, transport,
MQTT, parser, pagination, recommendation, canonical public-song-link, daily
recommendation discovery, and CI hardening baseline. The new delta introduces
typed mobile QR terminal outcomes, non-sensitive progress observation, strict
active-topic binding for MQTT login events, and regression tests for terminal
classification and stale-topic rejection. It does not modify `src/qmc.rs`; the
existing QMC source mappings and blob evidence therefore remain applicable.

Immutable evidence and mappings are recorded in
[provenance-ledger.json](provenance-ledger.json), including the crate license,
history, source revisions, file mappings, notices, and reviewed blob hashes.
For a release, compare the resolved dependency revision with the Cargo pin and the
reviewed revision above. The [provider readiness record](provider-readiness.json)
retains the historical cutover evidence.

A build or live verification result does not substitute for provenance or
corresponding-source delivery. Any dependency-pin change requires a new review
and updated immutable evidence.
