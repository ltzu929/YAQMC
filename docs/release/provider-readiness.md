# 提供器切换记录（历史）

> 适用范围：2026-09-06 的上游提供器切换、固定依赖与维护者豁免记录。原结论和证据继续保留，当前 Android 发布按[开发指南](../development.md#版本与发布)执行。

Historical decision: **READY (MAINTAINER SOAK WAIVER)** for the `qmapi` backend at
exact `qm-api-rs` revision
`7d0f6e18b1d1d89a06cc5964e9c057acb0926ea5`.

The historical machine-readable record is [provider-readiness.json](provider-readiness.json).
当时使用的提供器准备校验命令已退出当前工程。现有来源台账检查见[台账说明](provenance.md)，当前依赖提交从 Cargo manifest 与锁文件核对。

## Verified boundary

- `qm-api-rs` is the production implementation for clear-vkey retrieval,
  lyrics and QRC processing, and QMC decryption.
- The provider uses the `qmapi` credential envelope for production account
  operations. OAuth staging, mutation reconciliation, entitlement decisions,
  transport policy, caching, artwork mapping, and wire DTO mapping remain
  YAQMC responsibilities.
- Retired in-tree fallback implementations are guarded from production source
  and dependency graphs by the readiness checker.
- The legacy session slot remains only as a bounded compatibility and rollback
  input; it is not the primary production credential.

## Evidence decision

Authenticated provider verification covered login/session resolution, clear
and encrypted playback sources, lyrics, seek continuity, account reads, and
favorite mutation reconciliation before cutover. The crate provenance record
is maintained separately in
[qm-api-rs-provenance.md](qm-api-rs-provenance.md).

The previous exact-pin waiver applied only to revision
`827233cb799bede84ee5033ec16450dc1d5e2587`; it is not carried forward. The
current pin retains the typed Guess and Radar continuation requests, validated
canonical public-song-link helper, and credential-scoped daily recommendation
discovery. It adds typed mobile QR terminal outcomes and non-sensitive progress
events, and binds MQTT business events to the active QR topic without changing
the QMC implementation. Its Rust, provider-boundary, and Android compile checks
passed before the release request. On 2026-09-06, maintainer Mai-xiyu explicitly
requested the release and thereby authorized skipping the new pin's three-day
soak. This waiver does not assert that the soak or a production-account LIVE run
passed.
