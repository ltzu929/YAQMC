# 开发指南

方向、范围与关键决定看[项目规划](project-plan.md)，能力覆盖和验收看[迁移对齐清单](native-parity.md)。本文维护实际开发所需的结构、命令与工作方式。

## 工程结构与当前边界

| 位置                             | 用途                                                                        |
| -------------------------------- | --------------------------------------------------------------------------- |
| `apps/android/android/nativeApp` | 当前 Kotlin / Compose 原生界面与测试                                        |
| `apps/android/android/app`       | 旧 Capacitor Android 客户端；原生模块仍复用其中部分宿主源码、资源与混淆配置 |
| `crates/yaqmc-android`           | Rust JNI 桥，与宿主 `CoreManager` 通信                                      |
| `crates/yaqmc-core`              | 播放、队列、推荐会话、持久化等共享能力                                      |
| `crates/yaqmc-provider-qqmusic`  | QQ 音乐提供器适配及精确固定的 `qqmusic-api` 依赖                            |
| `src`、`apps/desktop`            | 保留的旧 React / Electron 体系，按需核对旧行为                              |

当前调用链为 Compose → `NativeViewModel` / repository → `NativeCore` → `CoreManager` → JNI → Rust Core。
Media3 负责系统媒体投影，Rust 负责实际播放与时钟；界面和宿主不另建播放事实源。主题、语言、歌词与外观设置保存在 `native-ui` SharedPreferences，图片背景使用应用私有副本。
原生 APK 不打包旧 Web 界面，但 Gradle settings 仍依赖 npm 安装的 Capacitor 模块。

## 环境与构建

需要 Node.js **26.7.0** 与其自带 npm、Rust **1.88.0 或更高版本**、JDK **21**、Android SDK / Build Tools **36**、NDK **28.2.13676358** 和 cargo-ndk **4.1.2**。
Windows 使用 MSVC；Gradle 使用仓库 wrapper。Node 版本以 `.node-version` 和 `package.json` 为准；先检查 `node --version`。
可用 `YAQMC_JAVA_HOME` 指定 JDK，`ANDROID_SDK_ROOT` / `ANDROID_HOME` 指定 SDK。

以下命令在仓库根目录执行：

```powershell
npm ci
# 重编 ARM64 release Rust Core，构建优化比较包，并运行原生 JVM 单元测试
node scripts/build-android-native.mjs
# 开发包
node scripts/build-android-native.mjs --debug
```

默认输出 `release-android/YAQMC-<version>-android-native-benchmark.apk`，使用 Android debug key；benchmark 与 debug 均仅 ARM64。
包名为 `org.yaqmc.android.nativeui`，可与旧包共存。正式签名、旧包升级与数据兼容待落实；不要跨包复制凭据。
本地安装使用 `adb install -r '<APK 路径>'`，遇到设备确认时由用户处理。

只改 Kotlin 时可以复用兼容的 Core：

```powershell
# 此目录需包含 arm64-v8a/libyaqmc_core.so
$env:YAQMC_ANDROID_NATIVE_LIB_DIR = '<已构建的 JNI 输出目录>'
node scripts/build-android-native.mjs --reuse-core
```

未指定目录时，`--reuse-core` 查找旧 `app/build/generated/jniLibs`，不会自动使用上次 `nativeApp` 输出。
修改 Rust 后应重编或明确使用本次产物；同一 Gradle 工程避免重叠构建。
`npm run android:build:debug` / `android:build:release` 面向旧 `:app`，`npm run dev` 面向旧 React 假数据界面，均不能替代原生构建与验收。

## GitHub Actions 原生安装包与模拟器

[Android Native APK](../../.github/workflows/android-native-ci.yml) 是 **Kotlin / Compose 原生版** 的 CI；旧 `CI` / `Electron Release` 绿灯不能代替它。工作流在涉及 Android / Rust / 构建脚本的 Pull Request、`main` 更新及手动触发时运行：

1. `android-native`：安装 Node 26.7.0、JDK 21、Rust 1.88.0、SDK 36 / NDK 28.2.13676358 / cargo-ndk 4.1.2，`npm ci`，构建 ARM64 Rust Core 和优化 benchmark APK，运行原生及共享宿主 JVM 测试、Lint；核对 APK 的签名、包名、ARM64 JNI 库和 SHA-256。
2. `android-emulator`：在 PR 或手动执行且 ARM64 job 成功后，独立构建 x86_64 Rust Core 和 Debug APK，在 Android 15 / API 35 模拟器上运行指定的离线 Compose UI 测试：`NativePlaybackTest` 与 `NativePersonalizationUiTest`，使用模拟数据，不进行线上登录 / 账号写入。测试失败时保留 instrument 输出与有界日志，成功时也尽力提取屏幕截图；这并非真实播放器 / QQ 接口验收。普通 `main` push 不运行模拟器，以减少重复计算。

在 GitHub 仓库的 **Actions → Android Native APK → 具体运行记录 → Summary → Artifacts** 下载：

- `YAQMC-native-benchmark-arm64-<commit>`：优化、可侧载的 `YAQMC-<version>-android-native-benchmark.apk` 与 `SHA256SUMS-native.txt`；成功产出时保留 **14 天**。
- `YAQMC-native-test-reports-<commit>`、`YAQMC-native-emulator-evidence-<commit>`：JVM / Lint 报告、模拟器测试记录与可获取的截图；有报告时保留 **7 天**。

Android 原生包名是 `org.yaqmc.android.nativeui`，可与旧版 `org.yaqmc.android` 共存。这是 CI 使用临时 **debug keystore** 签名的测试包，**并非正式签名发行包**；不同 CI 构建不保证能够覆盖安装。签名冲突时应先备份测试版数据，再仅卸载 `nativeui` 包。别把此测试包当成已解决正式升级 / 数据兼容（T06）的依据。

**本地复现：** 常规 ARM64 使用前述 `node scripts/build-android-native.mjs`；模拟器专用 x86_64 使用 `YAQMC_ANDROID_NATIVE_ABI=x86_64` 与 `YAQMC_ANDROID_NATIVE_LIB_DIR=<包含 x86_64/libyaqmc_core.so 的目录>`，先通过 `cargo ndk -P 26 -t x86_64 -o <目录> build --release --locked -p yaqmc-android` 构建 JNI，再运行 `node scripts/run-android-gradle.mjs :nativeApp:assembleDebug :nativeApp:assembleDebugAndroidTest`。环境变量仅作用于本次进程，不要用于 ARM64 发布 APK。模拟器运行的测试范围见 [run-native-emulator-smoke.sh](../../scripts/ci/run-native-emulator-smoke.sh)。

## 跨对话接手与任务交付

新对话先阅读仓库根目录 `AGENTS.md`，再核对[项目规划](project-plan.md)和[迁移对齐清单](native-parity.md)。以 **当前 `main` 分支和未合并 PR** 为准，不能假设上次提到的任务已经合并、构建成功或真机通过。

开发顺序固定为：确认迁移编号和旧版行为 → 独立分支修改源码及针对性测试 → PR 检查 `Android Native APK` 结果与 Artifacts → 必要时读取失败日志并修复 → 提供 APK 供用户真机验收 → 更新本指南（环境或工作方式）、项目规划（范围/决定）或迁移清单（实现与验证结论）。未经授权不合并 PR、不发布正式包、不操作真实账号写入；不用“构建成功”代替“设备上正常”。下次继续时优先检查尚未通过的 CI 和未合并 PR，避免重复创建任务。

## 必要检查

按改动影响选择检查，不要求每次修改都跑完整矩阵：

| 改动                 | 检查                                                                                |
| -------------------- | ----------------------------------------------------------------------------------- |
| 文档、入口与模板     | `npm run docs:check`；对改动文件执行 Prettier 检查                                  |
| 文档检查脚本         | 对应 `node --test`、ESLint 与文档检查                                               |
| 原生界面、状态、偏好 | 相关 JVM 单元测试、lint、构建及对应真机场景                                         |
| Rust / JNI / 协议    | 受影响 crate 的 fmt / clippy / test、契约与原生包回归；共享影响扩大时检查 workspace |
| 账号、系统媒体、交付 | 对应真机 / 真实账号验证；签名、升级和数据兼容分别检查                               |

```powershell
node scripts/run-android-gradle.mjs :nativeApp:testDebugUnitTest :app:testDebugUnitTest :nativeApp:lintDebug
node scripts/run-android-gradle.mjs :nativeApp:assembleDebug :nativeApp:assembleDebugAndroidTest
```

设备测试先安装主包与测试包，测试包使用 `adb install -r -t '<测试 APK 路径>'`。隔离和壳层回归可排除在线提供器测试：

```powershell
adb shell am instrument -w -r -e notClass org.yaqmc.android.nativeui.NativeProviderSmokeTest,org.yaqmc.android.nativeui.NativePlaybackLiveTest org.yaqmc.android.nativeui.test/androidx.test.runner.AndroidJUnitRunner
```

`NativeProviderSmokeTest` 访问真实服务，只做只读检查，执行时单独说明。模拟 Core、在线只读、真实账号写入和系统媒体验收分别记账。
`NativePlaybackLiveTest` 会短暂播放并修改本地队列、播放设置和听歌记录；需显式添加 `-e livePlayback true` 并单独运行该类。
系统媒体 / 焦点检查使用 Core 本地测试音频，在线播放、推荐补歌和公开分享目标另行调用真实提供器；回执分别标明音源与结果。
测试结束尝试恢复原队列内容、播放位置与设置，恢复结果单独记录；这不等于真实账号收藏、歌单写入或蓝牙 / 耳机验收。
测试 APK 中的独立音频焦点辅助 Activity 仅用于模拟其他应用的临时打断，不打包进主应用。
真实写入使用明确授权的测试账号与目标；被忽略的 Rust LIVE 测试可能访问服务或产生声音，不默认运行。

## 每项工作的完成标准

开始前核对规划、对应迁移编号和旧 Android 实际行为，写清目标、范围、假设与可观察的验收条件。
方向未明确时先整理上下文，只询问真正影响方向的缺口；目标和授权清楚时直接推进。普通任务在任务说明中记录即可，不另建文档。

实现保持聚焦，覆盖受影响的正常、空数据、失败、取消、过期响应、权限与保存路径；保留已确认返回行为。
按风险验证，如实说明命令、结果和未覆盖范围。代码、构建通过、模拟响应和真机通过不能互相替代。

完成后只更新受影响的主文档：范围或决定进规划，构建和工作方式进本指南，能力状态及关键验证结论进对齐清单。
文档仅维护中文，应用文案仍遵循已有语言能力。旧参考文档、英文副本暂留，其去留另议；不自动增加报告、进度页、规范文件或新审批步骤。
不提交账号秘密、原始诊断包或无关改动；账号保护、日志脱敏、播放权益与许可证边界继续适用。
