# 开发指南

本文是 YAQMC-Android 从需求分析、实现、测试、体验验收到提交和发布的总体规范，也提供实际构建与检查入口。

## 文档职责

| 资料                                                  | 维护内容                                                 |
| ----------------------------------------------------- | -------------------------------------------------------- |
| [README](../README.md)                                | 面向使用者的定位、真机截图、功能亮点、下载安装与反馈入口 |
| 本开发指南                                            | 开发全流程、实现原则、工程结构、工具、验证与发布要求     |
| 开发计划（仅本机）                                    | 开发方向、当前重点、Bug、功能需求、技术维护及待讨论事项  |
| [Releases](https://github.com/ltzu929/YAQMC/releases) | 各个已发布版本的变化和安装包                             |
| 技术参考与来源资料                                    | 特定模块的说明、协议研究、许可证、第三方声明与来源证据   |

日常主文档使用中文。同一事实在负责它的位置维护，其他位置引用；版本和依赖以工程配置、锁文件为准，实现以当前源码为准。公开文档保持独立可用，链接只指向仓库内公开资料或公开网页。

## 开发与交付流程

本项目按个人开发组织工作。用户把握目标、体验和重要取舍；开发者负责方案判断、实现、代码自查与必要验证。常规工程决定主动完成，需要讨论的方向性选择集中说明理由。

| 阶段       | 必要工作                                         | 完成条件                                               |
| ---------- | ------------------------------------------------ | ------------------------------------------------------ |
| 需求分析   | 理解实际场景、现象、期望、范围和需保留的体验     | 能说明本次解决什么，以及怎样观察是否达成               |
| 方案判断   | 核对当前实现、数据归属和影响，提出推荐方案       | 方向与关键取舍已明确，常规实现和检查由开发者安排       |
| 修改代码   | 聚焦目标，复用现有能力，保护已有工作和数据       | 本次行为已实现，相关状态与交互一致                     |
| 开发自查   | 阅读实际差异，检查主路径及失败、取消、重试、恢复 | 发现的问题已处理，范围外事项已记入计划                 |
| 功能测试   | 按影响选择静态检查、单测、模拟器及真机验证       | 必要检查取得最终结果，并说明覆盖范围                   |
| 用户验收   | 展示实际效果，按需交付候选包及体验步骤           | 用户确认目标和体验达成；明显界面调整遵循下文的截图顺序 |
| 整理交付   | 同步受影响的文档、待办和交付说明                 | 改动、检查结果、产物与剩余事项清楚可查                 |
| 提交与发布 | 审核通过后提交和推送，按授权发布应用版本         | 提交、推送或发布操作成功，向用户交付对应标识和链接     |

小修复可以在当前聊天中简短完成上述步骤；分析和验证深度随改动影响调整。需要长期维护的后续事项进入开发计划。

### 需求与方案

开始前检查当前分支是否为主分支、提交和未提交修改，保留已有工作；阅读任务涉及的源码、配置和现有结论。修复 Bug 时先确定受影响版本、触发步骤和实际表现，必要时用日志、测试或设备操作定位原因。

开发者先提炼目标、边界和可观察的验收表现，再判断方案。方向明确后直接推进；涉及功能范围、明显体验差异、数据迁移或长期维护成本的选择，先解释推荐方案与取舍并讨论清楚。发现新问题时区分本轮处理与后续待办。

日常文档和明确的小修复直接在 `main` 修改。较大功能、跨层调整或实验性重构是否使用临时分支，由开发者判断并说明理由；当前默认不安排 PR 或他人审查流程。

### 实现与代码自查

- 保留现有用户能力、操作意图、可配置性和数据延续；功能删减与明显交互变化先对齐。
- 采用 Android 常见交互，统一同类入口、控件、反馈和返回行为。基本可用性由开发者主动把关。
- 优先复用现有组件与责任源头，明确状态和数据归属。新增依赖、持久化数据、跨层接口或抽象应服务当前具体问题。
- 检查保存、取消、重复操作、请求过期、失败重试和恢复；页面反馈与实际执行结果一致，账号切换后旧请求和数据正确失效。
- 产品文案从使用者角度表达。需要处理的问题提供简短提示与明确动作，技术详情放在诊断等适当入口，**一定避免在主流程堆放防御性说明！**
- 界面看实际渲染，跨层功能检查相关链路。修复共同原因时检查同类入口，范围外的问题记入计划。

完成实现后阅读核对范围、代码行为、资源文案与实际体验。用户负责验收目标和体验，代码检查由开发者承担。

### 体验验收与候选包

明显的界面调整先完成本地实现、必要检查，并向用户展示应用实际运行截图；截图确认后，再生成候选安装包。构建用于本地检查的 debug 或测试 APK 属于开发过程，供用户覆盖更新的签名候选包遵循此顺序。

界面检查按影响覆盖常规尺寸、小屏、横屏、深浅主题、较大字号与控件可达性；涉及手势和动画时检查实际操作和过渡过程。README 使用真机运行截图。

需要手机体验时给出版本、安装包位置、具体操作和期望结果；使用手机测试前说明预计占用时间、辅助应用和临时设备设置。短测试集中完成，长期后台等观察结合正常使用安排，临时设置按约定恢复。

从 `0.1.6` 起使用 `top.ltzu.yaqmc` 和既有个人签名，作为新应用安装，不迁移旧版登录和本地数据；此后常规更新沿用该包名和签名，通过覆盖安装保留数据。真实账号写入、卸载应用、更换应用身份和数据迁移另需明确授权。

候选包可来自本轮未提交工作区，须记录基础提交及改动快照，标清候选用途；正式发布从审核后提交的干净源码重建。候选验收后的功能调整补充相关验证和验收。

### 整理、审核与提交

交付说明集中回答四件事：改了什么、检查结果、还剩什么、需要用户体验什么。分别报告文件修改、本地检查、构建、设备验收及提交推送的实际状态。

同步受影响的文档和开发计划后交给用户审核。用户确认本轮交付通过，再整理提交并推送 GitHub；应用版本发布按明确授权执行，已有授权继续有效。

文档交付到审核、提交和推送即可；需要发布安装包时继续按[版本与发布](#版本与发布)准备。发布完成后提供链接与版本，发布后的检查由用户手动负责。

## 文档与计划维护

- 功能、安装或界面展示变化时更新 README；开发流程、环境、命令或工程边界变化时更新本指南。
- 新发现的 Bug、功能需求和技术维护事项进入开发计划，记录问题或目标、优先级、状态和验收表现；Bug 另补受影响版本与复现步骤。
- 计划分为开发方向、当前重点、待办和待讨论事项。已确认事项使用“待开发、进行中、待验收”；想法先讨论，再进入待办。
- 审核通过并完成交付后移出待办，保留对应提交或 Release 的变化记录；部分完成时保留剩余内容和已有结果。
- 历史材料标明适用日期、版本和用途，退出日常工作入口。必要的日志、截图和验收证据在本机归档，当前规范与计划保持简明。
- 许可证、第三方声明和来源证据独立维护。历史审计快照按其原范围解释，当前依赖变化时核对实际来源和交付材料。

## 构建与运行

构建与测试可直接执行官方命令；需要工程索引、引用分析、依赖源码、语义重构、交互调试或界面检查时，再按需使用 Android Studio 及其可用的 MCP 接口。IDE、命令行和可选脚本复用同一套 Gradle、Cargo 与 SDK 配置及构建任务。

在 `apps/android` 目录执行：

```bash
./gradlew :nativeApp:assembleDebug
```

Windows PowerShell 使用 `./gradlew.bat`。输出为 `nativeApp/build/outputs/apk/debug/nativeApp-debug.apk`，包名 `top.ltzu.yaqmc.debug`，使用 Android SDK 的本机调试签名，可以与发布版并装。

Gradle 从根目录 `package.json` 读取版本，从 Git 读取源码提交，并自动调用 cargo-ndk 构建、打包当前 ABI 的 Rust Core。只改 Kotlin 时复用未变化的 Core；执行 Clean 后会重新编译。Studio 构建不依赖 npm，也无需手动复制 `.so`。

使用 Android Studio 时，可使用共享运行配置：

| 配置               | 用途                                 |
| ------------------ | ------------------------------------ |
| 运行与调试         | `debug` 变体，安装并启动当前应用模块 |
| 检查 JVM 与 Lint   | 运行 JVM 单元测试与 Debug Lint       |
| 模拟器离线界面测试 | 在 x86_64 模拟器执行隔离的界面回归   |
| 打包 Release APK   | 使用维护者既有签名生成正式 APK       |

需要查看接近发布版的优化结果时可执行 `:nativeApp:assembleBenchmark`，使用相同源码与 release 优化配置、本机调试签名和独立包名 `top.ltzu.yaqmc.benchmark`。输出在 `nativeApp/build/outputs/apk/benchmark/`，无需发行者私钥。

正式版 `top.ltzu.yaqmc` 的构建会检查既有个人签名指纹，以保证新包名版本之间可以覆盖升级。维护者按 [keystore.properties.example](../apps/android/keystore.properties.example)配置被忽略的 `keystore.properties`，执行 `:nativeApp:assembleRelease`。自行构建与测试使用 debug 或 benchmark 即可；独立发行 fork 时应配置自己的应用身份、签名与相应校验。

从不含 `.git` 的源码归档构建时，增加 `-Pyaqmc.buildCommit=<归档对应的完整提交 SHA>`，或设置 `YAQMC_BUILD_COMMIT`。提交号为 40 位小写十六进制；普通 Git 克隆无需设置。

可选的 `npm run android:build` 会调用同一 Gradle 入口，完成 JVM 检查和 APK 校验后复制到 `release-android/`。`yaqmc.nativeLibDir` / `YAQMC_ANDROID_NATIVE_LIB_DIR` 可显式复用已编译 Core，使用时需保证 ABI 与源码版本匹配。

## 工程结构

| 目录                            | 内容                                      |
| ------------------------------- | ----------------------------------------- |
| `apps/android/nativeApp`        | Kotlin / Compose 界面、Android 宿主和测试 |
| `crates/yaqmc-android`          | Rust JNI 桥接                             |
| `crates/yaqmc-core`             | 实际播放、队列、推荐会话与持久化          |
| `crates/yaqmc-provider-api`     | 音乐提供器的数据结构与接口                |
| `crates/yaqmc-provider-qqmusic` | QQ 音乐接入                               |
| `crates/yaqmc-protocol`         | 通信协议和 JVM 测试使用的契约样本         |
| `assets`                        | 品牌资源                                  |
| `scripts`、`.github`            | 可选构建工具和自动检查                    |
| `docs`                          | 协议、许可证与来源资料                    |

Android Kotlin 源码位于 `nativeApp/src/main/java/org/yaqmc/android/`：

- `nativeui/`：Compose 页面、状态和偏好；`NativeApp` 组织导航，`NativeViewModel` 协调请求。
- `core/`：Core 生命周期、JNI 宿主、登录协调与凭据保存。
- `media/`：系统媒体会话、通知、音频焦点与后台服务。
- `update/`：版本检查；`OAuthActivity` 承接授权登录。

Kotlin 界面经 JNI 调用 Rust Core。Rust 维护实际播放状态与时钟，Media3 负责 Android 系统媒体会话，避免两侧分别维护播放事实。语言、主题、歌词和外观偏好保存在 `native-ui` SharedPreferences，图片背景使用应用私有副本。

## 检查与测试

按实际改动选择必要检查：

| 改动                         | 检查重点                                                                      |
| ---------------------------- | ----------------------------------------------------------------------------- |
| 文档                         | 文档链接、格式、内容一致性和公开范围                                          |
| Kotlin 状态或业务逻辑        | 相关 JVM 测试、Lint 和构建，按影响补界面或链路检查                            |
| Compose 界面或交互           | 相关模型及界面回归，查看实际截图并操作受影响入口                              |
| Rust、提供器或 JNI           | 相关 Rust 测试、格式与 Clippy；契约变化核对样本及两端调用，必要时验证真实服务 |
| 播放、账号、持久化或系统集成 | 覆盖受影响链路与设备场景，例如冷启动、焦点、蓝牙、后台和覆盖升级              |
| 版本、签名或打包             | 版本映射、构建、APK 身份及原生库校验，核对源码与产物一致性                    |

回归用例优先覆盖修复的问题和关键行为。复用同一源码、配置与有效范围下已有的检查；新增改动、失败或未解决疑点再补查。异步任务读取最终日志、状态或产物，超时或不完整输出记录为未完成。发布准备还应取得对应提交的 CI 最终结果。

Android 单元测试和静态检查，在 Gradle 工程目录运行：

```bash
./gradlew :nativeApp:testDebugUnitTest :nativeApp:lintDebug
```

Rust 和仓库检查，在仓库根目录运行：

```bash
cargo fmt --all -- --check
cargo clippy -p yaqmc-core -p yaqmc-protocol -p yaqmc-provider-api -p yaqmc-provider-qqmusic --all-targets --locked -- -D warnings
cargo test -p yaqmc-core -p yaqmc-protocol -p yaqmc-provider-api -p yaqmc-provider-qqmusic --all-targets --locked
npm ci
npm test
npm run contracts:check
npm run docs:check
npm run format:check
npm run provenance:enforce
```

辅助检查首次使用前运行 `npm ci`，后续按锁文件变化更新依赖。Linux 的 Rust 音频测试需要 ALSA 开发库和可用音频输出，CI 的依赖与虚拟输出配置见 [core-verify.yml](../.github/workflows/core-verify.yml)。Android 自动检查配置见 [android-native-verify.yml](../.github/workflows/android-native-verify.yml)。

### 模拟器与真实服务

离线界面回归在 API 30 / 35 的 x86_64 模拟器运行。启动目标模拟器后使用 Studio 的“模拟器离线界面测试”，或在 Gradle 工程目录执行：

```bash
./gradlew :nativeApp:connectedDebugAndroidTest -Pyaqmc.offlineUiTests=1 -Pyaqmc.nativeAbi=x86_64 -Pandroid.testInstrumentationRunnerArguments.class=org.yaqmc.android.nativeui.NativePlaybackTest,org.yaqmc.android.nativeui.NativePersonalizationUiTest,org.yaqmc.android.nativeui.NativeMaintenanceUiTest,org.yaqmc.android.nativeui.NativeCatalogStateTest,org.yaqmc.android.nativeui.NativeExperienceUiTest
```

该配置使用 `NativeOfflineTestRunner`，不初始化真实 Core。CI 的离线 APK 不包含 Rust Core，只能测试界面，不能日常听歌。真实 x86_64 播放另需安装 Rust target `x86_64-linux-android`，使用 `-Pyaqmc.nativeAbi=x86_64` 构建正常变体。

恢复 ARM64 正常构建前移除 `yaqmc.offlineUiTests`、`yaqmc.nativeAbi` 及对应的 `YAQMC_ANDROID_OFFLINE_UI_TESTS`、`YAQMC_ANDROID_NATIVE_ABI`、`YAQMC_ANDROID_NATIVE_LIB_DIR` 环境配置，再重新构建。

`NativeProviderSmokeTest` 访问真实在线服务；`NativePlaybackLiveTest` 需显式传入 `livePlayback=true`，会播放音频并暂时调整本地队列与设置。真实账号、蓝牙、焦点打断、长期后台和覆盖升级分别在对应设备上检查，隔离界面回归不覆盖这些行为。

日志、布局和性能问题按所需信息选择 Android SDK / ADB 命令或 Studio 的 Logcat、Layout Inspector、Profiler。`artifacts/`、构建报告、诊断包和本机测试记录不提交。

## 版本与发布

### 发布材料

| 材料       | 内容与去向                                                          |
| ---------- | ------------------------------------------------------------------- |
| 安装包     | 使用本版确认的包名、签名且版本正确的 APK，作为用户下载附件          |
| 发布说明   | 本版新增功能、修复内容、必要的安装说明和反馈入口，写在 Release 页面 |
| 源码与记录 | 对应源码提交、许可证资料和本次构建记录，已有材料按变化更新          |

用户下载页的附件只放 APK。安装包和构建记录保存在 `release-android/`、`artifacts/`，已有有效检查直接复用。

### 发布步骤

1. **准备版本。** 明确本次发布范围和通道，完成体验验收与必要检查；同步 `package.json`、`package-lock.json` 及受影响的公开说明。版本格式为 `x.y.z` 或 `x.y.z-alpha.n` / `beta.n` / `rc.n`，同一基础版本按 `alpha < beta < rc < 正式版` 递增。核对工程生成的 versionCode 高于之前交付的安装包，标签使用 `v<version>`。
2. **审核并固定源码。** 用户审核通过后提交、推送已审内容；读取对应提交的 CI 最终结果。未解决的相关检查失败应先修复，新增功能改动回到实现和验收环节。
3. **构建正式安装包。** 从该干净提交执行签名 release 构建，保留版本、完整提交号和构建记录。按本版约定核对包名和签名，常规更新保持连续；签名缺失或不匹配时修正配置后再交付。
4. **整理产物。** 复用构建脚本的版本、包名和签名校验结果，保存 APK、对应源码提交号和构建记录；源码与许可资料按本次变化更新。
5. **整理用户发布页。** 标签指向已核对的提交，写好简短发布说明并准备 APK。预发布版本使用 Pre-release，正式版按授权设置发布状态；应用发布须有明确授权。
6. **执行发布并交接。** 推送版本标签，上传 APK 并完成 Release 发布，向用户交付版本、发布链接及本机材料位置。发布后的检查由用户手动完成。

应用内更新与反馈指向本仓库。稳定版默认不订阅预览；预览版可接收同通道更新及后续正式版，规则见 [AndroidUpdateChecker](../apps/android/nativeApp/src/main/java/org/yaqmc/android/update/AndroidUpdateChecker.kt)。

## 按需参考

- [QQ 音乐提供器](zh-CN/qqmusic-provider.md)：当前接入结构、账号与音源边界、源码入口。
- [对应源码交付政策](../CORRESPONDING_SOURCE_POLICY.md)、[第三方声明](../THIRD_PARTY_NOTICES.md)：源码和许可材料。
- [来源台账说明](release/provenance.md)：来源证据与历史审计范围。
