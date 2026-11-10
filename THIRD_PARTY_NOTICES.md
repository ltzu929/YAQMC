# Third-party notices

## 当前原生 Android 版本

当前应用使用 Kotlin / Compose 原生界面与 Rust Core。各版本的对应源码资料维护实际依赖清单、精确版本、来源、适用许可证和保留的声明，见[对应源码交付政策](CORRESPONDING_SOURCE_POLICY.md)。历史 Web / 桌面组件的声明在下文保留，不表示它们仍包含在当前 APK 中。

- Rust 依赖以发布提交的 `Cargo.lock` 为准，随源码保留各包的许可证和版权声明；`qqmusic-api` 精确固定到下文记录的 `qm-api-rs` 提交。
- Android 运行时的 AndroidX、Kotlin、协程、Guava 及 JetBrains 注解依赖采用 Apache-2.0；精确坐标、POM（含父 POM）、源码和适用声明随依赖清单交付。
- 播放页的八个 `ic_player_*.xml` 矢量图标来自 [Google Material Icons](https://github.com/google/material-design-icons)，Copyright Google Inc.，采用 Apache-2.0；仅将官方 SVG 路径转换为 Android VectorDrawable，各文件内保留原始来源链接，[许可证正文](apps/android/nativeApp/src/main/assets/licenses/material-icons-Apache-2.0.txt)随 APK 保留。
- `androidx.graphics:graphics-path:1.0.1` 包含 C++ / JNI 实现，除 sources JAR 外，源码附件另保留[官方发布记录](https://developer.android.com/jetpack/androidx/releases/graphics#graphics-path-1.0.1)所指提交 `8a05a22af450d589ef911d772a001a49dcb05b71` 的完整模块源码，适用 Apache-2.0。
- `rustls:rustls-platform-verifier:0.1.1` 的 AAR 随 `rustls-platform-verifier-android` crate 提供，适用 MIT 或 Apache-2.0。源码附件另包含[上游 0.1.1 版本提交](https://github.com/rustls/rustls-platform-verifier/commit/da3d9c36f48fb9f8f97e94f132fdab67ab0fc75b)中的 Android 工程及两份许可证正文。
- Guava 的 `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` 是不含类文件的冲突占位 JAR；保留其空归档、POM 与父 POM 许可说明。

YAQMC's production QMC/mflac decryptor delegates to the pinned `qm-api-rs`
revision `7d0f6e18b1d1d89a06cc5964e9c057acb0926ea5`, which independently
adapts cipher behavior from the MIT-licensed project below. Upstream files were
not vendored. Protocol-only research references, including GPL/LGPL and
unlicensed repositories, are recorded in
[docs/qqmusic-provider.md](docs/qqmusic-provider.md) and are not reproduced
here.

## Apple Music-like Lyrics（历史 Web 客户端）

Source: <https://github.com/amll-dev/applemusic-like-lyrics>

Packages: `@applemusic-like-lyrics/core` `0.5.2` and `@applemusic-like-lyrics/react` `0.5.2`

License: GNU Affero General Public License version 3 only (`AGPL-3.0-only`). These official packages were used by
the retired desktop lyric renderer without vendoring or local modification. The current native Android app does not depend on them. The package license text and corresponding
source are available from the upstream project; distributors of YAQMC must satisfy the GPLv3/AGPLv3 obligations
described in [docs/lyrics.md](https://github.com/ltzu929/YAQMC/blob/b0b5acf9d91ebb5e677ee6a95b38318faa558e15/docs/lyrics.md#amll-decision).

`mzj3920/qqmusic-decrypt`, AynaLivePlayer/miaosic, and official QQ Music Electron ASAR contents are **not**
in-tree YAQMC sources. The `mzj3920` and ASAR claims were removed from the current pinned `qm-api-rs` revision;
miaosic was protocol corroboration only.

## Rust Provider Component examples（历史插件示例）

The optional, unsigned API v3 example packages formerly under `examples/plugins/packages/` contain WebAssembly Components
built from the checked-in Rust sources and lockfiles. They are not built into the YAQMC desktop executable. The
complete platform example directly uses:

| Package       | Version   | Upstream                                          | License declared by the crate                      |
| ------------- | --------- | ------------------------------------------------- | -------------------------------------------------- |
| `dlmalloc`    | `0.2.11`  | <https://github.com/alexcrichton/dlmalloc-rs>     | MIT or Apache-2.0                                  |
| `serde_json`  | `1.0.145` | <https://github.com/serde-rs/json>                | MIT or Apache-2.0                                  |
| `wit-bindgen` | `0.46.0`  | <https://github.com/bytecodealliance/wit-bindgen> | Apache-2.0 with LLVM exception, Apache-2.0, or MIT |

The example `Cargo.lock` files are the authoritative transitive resolutions. Source distributions retain those
lockfiles and the project GPL notice; distributors should preserve the upstream license choices applicable to the
exact resolved crates.

## QMCDecode

Source: <https://github.com/gongjiehong/QMCDecode>
Revision: `aea76301a08678100ec677cb61a8458bc75662ec`

| Upstream file / range                                           | Pinned `qm-api-rs` target                                           | Transformation                                                                                                                              |
| --------------------------------------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `QMCDecode/QMCipher.swift` `QMMapCipher.getMask` / `rotate`     | `src/qmc.rs` `Qmc2Map::decrypt_in_place`                            | Independent Rust rewrite of the non-circular `(key << rot) \| (key >> rot)` mask. The key is indexed with `key.len()` rather than `& 0xFF`. |
| `QMCDecode/QMCipher.swift` `QMRC4Cipher`                        | `src/qmc.rs` `Qmc2Rc4` / `decrypt_in_place`                         | Independent Rust rewrite of the 128-byte first segment and 5,120-byte segmented RC4 stream.                                                 |
| `QMCDecode/QMCKeyDecoder.swift` and `QMCDecode/TeaCipher.swift` | `src/qmc.rs` `derive_key`, `simple_make_key`, `decrypt_tencent_tea` | Independent Rust rewrite of ekey TEA unwrapping after any EncV2 outer wrapper is removed.                                                   |

The YAQMC file `crates/yaqmc-provider-qqmusic/src/qmc.rs` now only validates
and redacts the media key before selecting
`crates/yaqmc-provider-qqmusic/src/qmapi/qmc.rs`'s `QmapiQmcDecryptor`. That
adapter delegates Map, segmented-RC4, and ekey processing to the pinned symbols
above; the former duplicate in-tree cipher and TEA implementation was retired
when the production provider cut over to the pinned dependency.

MIT License

Copyright (c) 2019 程序猿老龚 gjh.me

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
