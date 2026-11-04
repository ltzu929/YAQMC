# 对应源码交付政策

YAQMC-Android 沿用 YAQMC 的 `GPL-3.0-or-later` 许可证。公开预览与正式版均须按 [GPLv3 第 6 节](LICENSE)向二进制接收者提供完整对应源码，并在发布页说明取得方式。源码交付与二进制使用同一版本和提交。

## 当前原生 Android 版本

用户下载页仅保留 APK，发布说明聚焦功能、安装要求与反馈入口。下列源码、许可与校验资料在仓库及工程归档中维护，不逐项作为用户下载附件或铺入发布说明。

源码工程归档应包含：

- 最终发布提交的 Kotlin / Compose 界面、Android 宿主、Rust workspace、资源、协议样本，以及构建和检查所需的脚本。
- 该提交的 `Cargo.toml` / `Cargo.lock`、Gradle 配置与 wrapper、版本文件、构建说明、签名配置示例、许可证、版权声明和来源记录。不交付私有签名文件、账号数据或本机路径配置。
- 实际链接的 `qm-api-rs` 精确版本源码。目前 `qqmusic-api` 固定为 `7d0f6e18b1d1d89a06cc5964e9c057acb0926ea5`，发布时同时核对提供器 manifest 和 `Cargo.lock`。
- 按实际 Rust / Maven 解析结果整理的依赖清单、适用许可证与声明，以及需要交付的依赖对应源码；保留精确版本、来源与构建所需修改。不能只列直接依赖，也不能以一个上游仓库链接代替应交付的源码。

当前 APK 不包含 Electron、React、Capacitor、AMLL 或旧 Provider Component 示例，不为当前原生版要求这些已退出组件的源码。

从最终干净提交导出项目源码，单独备齐外部依赖。GitHub 自动生成的仓库源码 ZIP 不包含 `qm-api-rs` 等外部依赖；不能单独把它作为完整对应源码交付。复用已有 Core 二进制时，也必须能确定并提供构建它的精确源码。

Rust 可使用 Cargo 自带的 `vendor --locked --versioned-dirs` 收集精确依赖，保留生成的来源替换配置与各包校验文件。Android 依赖按实际 `releaseRuntimeClasspath` 收集 sources JAR、POM（必要时含父 POM）与适用声明；预编译 AAR / JAR 或 JNI `.so` 的首选源码不在 sources JAR 中时另行补齐，空占位归档则核对并记录其不含类文件。归档中的依赖清单不得包含本机缓存绝对路径。

源码构建说明须说明 SDK / NDK / JDK / Cargo 版本、依赖使用方式、签名配置示例与构建命令。不含 `.git` 时，通过 `yaqmc.buildCommit` 或 `YAQMC_BUILD_COMMIT` 提供清单中的完整提交 SHA；构建无需取得发行者私有签名密钥，默认 debug 包使用独立身份。

## 发布清单与核对

每次发布在工程归档中保留 `CORRESPONDING-SOURCE-MANIFEST.json`，记录：

- 应用名称、版本、versionCode、Git 标签与完整提交 SHA。
- APK 的包名、ABI、签名证书 SHA-256、文件名与 SHA-256。
- 每个源码 / 依赖归档的文件名、来源、精确版本或提交和 SHA-256；包含使用的许可证与构建说明。
- `docs/release/provenance-ledger.json` 的 SHA-256、引用的来源证据，以及 `node scripts/validate-provenance-ledger.mjs --enforce` 的实际执行结果。

发布前重新读取归档，核对其中的版本、锁文件、许可证、构建入口与清单哈希。源码应与 APK 相符，缺少应交付的源码时不发布二进制。

来源台账保留历史审计快照；校验通过表示台账规则通过，不自动涵盖后续新增代码或依赖。当前原生 Android 的依赖汇总和源码归档仍需按上述要求完成。源码交付也不替代缺失的版权许可或第三方归属核对。

## 历史二进制

包含 `@applemusic-like-lyrics/core` / `react` `0.5.2` 的历史 Web / 桌面二进制，仍需保留对应的 `AGPL-3.0-only` 声明和首选源码。该版本对应上游提交 `fd7ec2d597daa2a66a37ca5f3214d6757ec17cfa`；历史插件示例按各自锁文件与许可证处理。

旧版发布工作流及 `scripts/ci/corresponding-source.mjs` 已退出，不能作为当前发布已完成源码归档的依据。历史说明可从 [Git 固定提交](https://github.com/ltzu929/YAQMC/blob/b0b5acf9d91ebb5e677ee6a95b38318faa558e15/CORRESPONDING_SOURCE_POLICY.md)回查；当前流程见[开发指南](apps/android/android/docs/development.md#公开预览发布流程)。
