# 开发指南

方向、范围与关键决定看[项目规划](project-plan.md)，能力覆盖和验收看[迁移对齐清单](native-parity.md)。本文维护实际开发所需的结构、命令与工作方式。

## 工程结构与当前边界

| 位置                                                             | 用途                                                         |
| ---------------------------------------------------------------- | ------------------------------------------------------------ |
| `apps/android/android/nativeApp`                                 | 当前 Kotlin / Compose 原生界面与测试                         |
| `apps/android/android/nativeApp/src/main/java/org/yaqmc/android` | 登录、凭据、JNI 宿主、媒体服务与更新；与原生界面同属一个模块 |
| `crates/yaqmc-android`                                           | Rust JNI 桥，与宿主 `CoreManager` 通信                       |
| `crates/yaqmc-core`                                              | 播放、队列、推荐会话、持久化等共享能力                       |
| `crates/yaqmc-provider-qqmusic`                                  | QQ 音乐提供器适配及精确固定的 `qqmusic-api` 依赖             |
| `crates/yaqmc-protocol/fixtures`                                 | Rust 协议生成的契约样本，供原生 JVM 测试使用                 |

工作目录按以下职责保留：`apps/` 与 `crates/` 是源码，`scripts/` 与 `.github/` 是构建和检查，`docs/` 是当前中文主文档及必要的来源资料，`assets/` 保留品牌资源。`target/`、`node_modules/`、Gradle `build/` 是可重建缓存；`artifacts/` 保存本机验证记录，`release-android/` 只保留当前候选包，均不提交。旧包和对应说明在本机 `artifacts/android-native-comparison/pre-cleanup-packages/` 留档。机器本地 `.codex/`、SDK 路径和签名配置保持私有。

当前调用链为 Compose → `NativeViewModel` / repository → `NativeCore` → `CoreManager` → JNI → Rust Core。
Media3 负责系统媒体投影，Rust 负责实际播放与时钟；界面和宿主不另建播放事实源。主题、语言、歌词与外观设置保存在 `native-ui` SharedPreferences，图片背景使用应用私有副本。
Gradle 仅包含 `:nativeApp`，不依赖 npm、Capacitor 或旧 Web 界面。Node 用于构建与检查脚本，npm 仅安装文档格式化工具。Rust 核心保留 Android 使用的能力与协议兼容响应，已删除桌面插件运行时、本地 HTTP API、桌面系统媒体实现及独立进程入口。

## 环境与构建

需要 Rust **1.88.0 或更高版本**、JDK **21**、Android SDK / Build Tools **36**、NDK **28.2.13676358** 和 cargo-ndk **4.1.2**。Windows 使用 MSVC；Gradle 使用仓库 wrapper。Node.js **26.7.0** 与其自带 npm 用于可选批处理、脚本测试和文档格式化，Studio 打包不依赖 Node。

Gradle 是统一构建入口：从仓库 `package.json` 读取版本并计算 versionCode，从 Git 读取源码提交；`:nativeApp:buildRustCore` 调用已有 cargo-ndk 编译当前 ABI 的 Rust Core，并作为 Android `preBuild` 依赖。Gradle 跟踪 Rust 输入和输出，只改 Kotlin 时复用未变化的 Core，Clean Project 后自动补建；不再由启动脚本注入版本或复制 Core。

本机配置位于 Gradle 工程 `apps/android/android`，均保持私有：

- `local.properties` 保存 SDK 的 `sdk.dir`、Rust TLS 的 `yaqmc.rustlsVerifierMavenDir`，以及需要时的 `yaqmc.cargoHome`、`yaqmc.rustupHome`；Windows 路径使用正斜杠，冒号写成 `\:`。这些路径配置一次后，直接从桌面打开 Studio 也能读取。
- `.gradle/config.properties` 的 `java.home` 指向 JDK 21，Studio Gradle JDK 选 `GRADLE_LOCAL_JAVA_HOME`。IDE 自身继续使用随附 JBR；两者用途见 [Android 官方 JDK 说明](https://developer.android.com/build/jdks)。
- `keystore.properties` 使用标准 Android 签名字段 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，引用既有密钥；可参照同目录 `keystore.properties.example`。真实文件被 Git 忽略，禁止重新生成密钥代替旧密钥。Gradle 同步时即可读取签名，不依赖启动 IDE 的终端。此方式见 [Android 官方签名说明](https://developer.android.com/studio/publish/app-signing#secure-key)。

Gradle 在 release 构建时检查 ARM64 Core 和旧版签名指纹，缺失或不符即停止，不退回调试签名。环境变量 `ANDROID_RELEASE_*` 仍可为 CI 或批处理提供签名，本机文件优先。SDK / Cargo 路径也可通过原有环境变量提供。

显式复用已构建 Core 时可指定 `yaqmc.nativeLibDir` / `YAQMC_ANDROID_NATIVE_LIB_DIR`；此模式跳过 Rust 编译，调用者应在 Rust 改动后更新该目录。日常 IDE 构建使用 Gradle 默认目录，无需设置此项。模拟器及离线验证继续按“必要检查”使用独立 ABI 与运行器。

常规包沿用 `org.yaqmc.android`、旧签名、原应用数据目录和 Android Keystore，见项目规划 D004。之前的 `org.yaqmc.android.nativeui` 为独立比较包，其数据不跨包复制。`.debug` / `.benchmark` 只供显式开发验证，使用独立包名和调试签名。

命令行也可直接在 Gradle 工程运行 `./gradlew.bat :nativeApp:assembleRelease`。原有 `npm run android:build` 作为可选批处理保留，调用同一 Gradle 构建后运行 JVM 测试、用 SDK 的 `aapt` / `apksigner` 检查 APK，再复制到 `release-android/YAQMC-<version>-android-arm64-v8a.apk`；`--reuse-core` 仅供显式复用。安装使用 `adb install -r '<APK 路径>'`，设备确认由用户处理。签名、构建和真实账号 / 播放验收分别记录。

## Android Studio 本地工作流

本地开发、调试和打包优先使用 Android Studio 及其 MCP，遵循项目规划 D005。打开 `apps/android/android` Gradle 工程，SDK Location 指向已有 SDK 36，Gradle JDK 选择 JDK 21，然后 Sync Project。路径与签名按上节配置，不要求通过脚本启动 Studio。

生成手机覆盖升级包：

1. 在顶部运行配置选择 **打包 Release APK**，点击绿色运行按钮；该配置只执行 Gradle `:nativeApp:assembleRelease`，无需连接手机。
2. 等待 Build 窗口显示 `BUILD SUCCESSFUL`。APK 位于 `nativeApp/build/outputs/apk/release/nativeApp-release.apk`，从左侧 Project 视图或文件管理器打开；Studio 的构建通知也可定位产物。
3. 也可在 Build Variants 把 `nativeApp` 切到 `release`，使用 Build 菜单的 Generate APKs。已有签名由 Gradle 自动使用；如使用 Generate Signed Bundle / APK 向导，仍选择原有密钥。

日常使用共享配置 **运行与调试**，指向当前 `nativeApp` 模块：先在 Build Variants 选择 `debug`，再选择模拟器或设备，点击 Run 安装启动，或点击 Debug 使用断点、单步与变量检查。该配置自动显示 Logcat，保留应用存储和现有日志；旧本机配置 `app` 指向已经删除的模块，应在 Run → Edit Configurations 中移除，已有 `nativeApp` 配置可继续使用。

完整本地工作流按问题选择以下入口：

| 工作                 | 优先使用的现成能力                                                                              | 检查结果                        |
| -------------------- | ----------------------------------------------------------------------------------------------- | ------------------------------- |
| 工程同步与依赖       | Studio Sync Project、SDK Manager、Gradle JDK；MCP 模块 / 依赖查询                               | 同步完成、模块和依赖可解析      |
| 阅读、修改与重构     | IDE 导航、检查与重构；MCP 的符号、调用关系、问题检查和 rename refactoring                       | 实际改动、受影响调用和检查结果  |
| 模拟器与安装运行     | Device Manager、Running Devices、运行与调试配置                                                 | 目标设备上的实际启动与操作      |
| 断点调试             | Debug、Attach Debugger、断点、单步和变量窗口；跨 JNI 问题按需使用原生调试器                     | 异常位置、调用栈和相关变量      |
| 日志与崩溃           | View → Tool Windows → Logcat，选择目标设备 / 进程；调试包过滤 `package:org.yaqmc.android.debug` | 复现时段的日志与异常栈          |
| JVM 与静态检查       | 共享配置 **检查 JVM 与 Lint**；单项测试也可从代码旁运行                                         | Tests 窗口、JUnit 和 Lint 报告  |
| 离线界面回归         | 启动目标 x86_64 模拟器，执行 **模拟器离线界面测试**                                             | Android 测试结果与实际画面      |
| 状态栏、遮挡与布局   | Running Devices 的实际画面与 Layout Inspector，检查运行中的 debug 应用                          | 截图、控件边界和 Compose 层级   |
| 卡顿、内存与后台播放 | Profiler 与系统跟踪；性能比较选 `benchmark`，分配 / 堆分析选 `debug`                            | 与具体操作对应的采样或 trace    |
| 打包与升级           | 打包 Release APK、Build 输出、Analyze APK、SDK 签名工具                                         | 产物、身份 / 签名及对应升级结果 |

日志、布局与性能入口分别参考 [Logcat](https://developer.android.com/studio/debug/logcat)、[Layout Inspector](https://developer.android.com/studio/debug/layout-inspector) 和 [Profiler](https://developer.android.com/studio/profile)。当前 Manifest 已启用 `profileable`；这表示允许采样，实际性能结论仍需针对具体问题录制。配置完成不等于这些项目都已实际检查过。

同一工程避免 IDE 与命令行 Gradle 重叠构建。SDK、NDK、Gradle wrapper 不因 IDE 的升级提示自动调整。

本地 Codex 优先通过 Studio MCP 检查工程、代码问题和构建结果，执行现有运行配置。2026-10-30 核对的 MCP 接口提供工程 / 依赖、语义查找、重构、检查、构建和运行配置；没有单独的断点控制、Logcat、Layout Inspector 或 Profiler 接口。这些功能使用 IDE 本身，需要自动化补充时使用现成 `adb` / SDK 工具，或请用户完成具体操作，不因 MCP 缺口另造工具。网页端继续按分支 / PR 交付，验证按改动保留必要的测试与真机场景。

已有 `npm run android:studio -- '<Studio executable>' --prepare-only` 只作为可选的首次路径准备工具，写入被忽略的 SDK、JDK、Cargo 与 TLS Maven 路径；不再要求预先编译 Core，也不传入固定版本或源码提交。配置完成后可直接打开 IDE。

保留脚本的范围：Android 启动 / 构建 / 检查脚本是可选批处理；文档格式化使用已有 Prettier，Rust 检查使用 Cargo；契约样本生成、来源台账和日志脱敏检查保留仓库专用规则，这些工作不由 Studio 的通用检查替代。常规更新使用现有签名，`new-android-release-key.ps1` 不属于更新工作流。新增脚本前先确认 IDE、Gradle、Cargo、SDK 或现有工具确实未覆盖所需能力。

## 跨对话接手与任务交付

2026-10-26 确认采用：**网页端 GPT 修改文件、提交代码及创建 PR，本地 Codex 拉取审查，按改动在本机验证和构建。** Android 验证与安装包交付由本机完成，使用已有本机签名配置。仓库原有通用 CI 的结果可作为补充证据。

CI 收敛为原生 Android JVM、Lint、离线 APK / 模拟器验证，以及 Rust 核心、协议、脚本和文档检查。旧 Electron / WebView 发布与网站工作流已移除；正式发布流程尚未启用。

用户已确认当前使用的网页端具备提交代码和创建 PR 的能力，本项目按此能力通过独立分支 / PR 交付源码及相关测试。网页端无需为这一分工搭建 Android 编译或签名环境。

新对话先阅读仓库根目录 `AGENTS.md`，再核对[项目规划](project-plan.md)和[迁移对齐清单](native-parity.md)。以 **当前 `main`、开发分支、未合并 PR 和本地改动** 为准，不能假设上次提到的任务已经合并、构建成功或真机通过。

`main` 保存已审查且经用户确认合入的改动。后续每项任务从最新 `main` 创建独立开发分支；网页端与本地 Codex 围绕同一个任务分支协作，通过 PR 交付。

网页端开工提示需明确仓库、分支或提交，并要求先阅读上述入口与主文档；不能假定普通聊天会自动加载 `AGENTS.md`。先确认迁移编号与旧版行为，再在独立分支修改源码及相关测试、提交代码并创建 PR。交付时提供基准提交、分支及 PR 链接、改动目标和未验证项；如未执行编译或测试，应明确说明。

本地 Codex 先检查工作区并拉取远端分支，审查实际差异，再按本指南的必要检查选择测试、Lint、构建及真机场景。需要安装包时优先使用 Studio 的 release 构建和既有签名；debug / benchmark 使用本机 Android debug keystore。签名、旧包升级和数据兼容仍按 T06 分别验收。

审查问题、验证结果与剩余真机场景直接在当前任务或 PR 中说明；只更新受影响的主文档。文件修改、本地检查、构建与真机验收分别记录实际结果。未经授权不合并 PR、不发布正式包、不卸载应用、不迁移数据或操作真实账号写入。

任务合并后先同步本地 `main`，确认 PR 已合并且最终文件内容已纳入，再清理对应本地与远端分支；squash 合并按 PR 状态和实际内容核对，不只看提交祖先关系。未合并分支与未跟踪文件先核对归属，补齐到对应任务分支后提交，不能作为过期改动直接删除。

## 必要检查

按改动影响选择检查，不要求每次修改都跑完整矩阵：

| 改动                 | 检查                                                                                |
| -------------------- | ----------------------------------------------------------------------------------- |
| 文档、入口与模板     | `npm run docs:check`；对改动文件执行 Prettier 检查                                  |
| 文档检查脚本         | 对应 `node --test`与文档检查                                                        |
| 原生界面、状态、偏好 | 相关 JVM 单元测试、lint、构建及对应真机场景                                         |
| Rust / JNI / 协议    | 受影响 crate 的 fmt / clippy / test、契约与原生包回归；共享影响扩大时检查 workspace |
| 账号、系统媒体、交付 | 对应真机 / 真实账号验证；签名、升级和数据兼容分别检查                               |

优先在 Studio 运行 **检查 JVM 与 Lint**，它直接执行 `:nativeApp:testDebugUnitTest` 和 `:nativeApp:lintDebug`，不需要设备。需要单独构建时，在 Gradle 面板选择 `assembleDebug` / `assembleDebugAndroidTest`。命令行等价入口是在 Gradle 工程执行 `./gradlew.bat` 后接相同任务；原 Node 包装器仅保留作批处理。

离线界面回归使用 **模拟器离线界面测试**：先通过 Device Manager 启动目标 x86_64 模拟器，再运行该配置。它通过标准 Gradle 项目参数设置离线运行器和 ABI，并仅选择 `NativePlaybackTest`、`NativePersonalizationUiTest`、`NativeMaintenanceUiTest` 三个既有套件；Gradle 完成构建、测试包安装、执行和报告，不需要手动逐个安装 APK。`connectedDebugAndroidTest` 使用已连接设备，执行前仅保留目标模拟器。参数只在这个配置中生效；恢复运行 / 调试时正常重建 debug 包。

`NativeOfflineTestRunner` 使用普通 `Application` 和轻量 `ComponentActivity`，每项测试前后断言真实 Core 未初始化。它只验证离线界面，不能用于真实播放或账号功能验收。Gradle 的测试任务、筛选与报告位置见 [Android 官方测试说明](https://developer.android.com/studio/test/command-line)。

以下 `adb` 入口保留作定向诊断或 CI 使用，不作为日常 IDE 操作的前置步骤。手动安装测试包时使用 `adb install -r -t '<测试 APK 路径>'`。隔离和壳层回归可排除在线提供器测试：

```powershell
adb shell am instrument -w -r -e notClass org.yaqmc.android.nativeui.NativeProviderSmokeTest,org.yaqmc.android.nativeui.NativePlaybackLiveTest org.yaqmc.android.debug.test/androidx.test.runner.AndroidJUnitRunner
```

上述默认运行器会启动真实应用。手动执行离线套件时，构建阶段需设置 `-Pyaqmc.offlineUiTests=1 -Pyaqmc.nativeAbi=x86_64`（或对应原有环境变量），再使用专用运行器；恢复其他设备测试前移除这些参数并重新构建、安装测试 APK：

```powershell
adb shell am instrument -w -r -e class org.yaqmc.android.nativeui.NativePlaybackTest,org.yaqmc.android.nativeui.NativePersonalizationUiTest,org.yaqmc.android.nativeui.NativeMaintenanceUiTest org.yaqmc.android.debug.test/org.yaqmc.android.nativeui.NativeOfflineTestRunner
```

本地 x86_64 模拟器设置 `YAQMC_ANDROID_NATIVE_ABI=x86_64`。纯离线测试可将 `YAQMC_ANDROID_NATIVE_LIB_DIR` 指向事先创建的独立空目录，并使用上述离线运行器；这种 APK 不包含 Rust Core，不能用于真实播放。真实 Core 验证需提供匹配 ABI 的 JNI 库；恢复常规 ARM64 构建前移除模拟器及离线测试环境变量，并重新构建 APK。

离线套件的执行范围与已有结果见[迁移对齐清单](native-parity.md)的验证摘要，不能扩展为真实播放、账号或真机验收。

`.github/workflows/android-native-verify.yml` 对原生相关 PR 执行 JVM、Lint、APK 构建及 API 30 / 35 模拟器检查。CI 使用 `NativeOfflineTestRunner`，运行 `NativePlaybackTest`、`NativePersonalizationUiTest` 与 `NativeMaintenanceUiTest`，不初始化真实 Core。维护套件注入统计、缓存、诊断及更新结果，覆盖清除确认、失败保留、请求互斥、问题草稿恢复与 FileProvider 附件读取；不会清除真实听歌记录或提交 GitHub 问题。工作流上传的 `yaqmc-native-offline-test-apks-*` 仅供离线测试，不包含 Rust Core，常规启动和真实播放请使用本机构建入口生成的完整包。

`NativeProviderSmokeTest` 访问真实服务，只做只读检查，执行时单独说明。模拟 Core、在线只读、真实账号写入和系统媒体验收分别记账。
`NativePlaybackLiveTest` 会短暂播放并修改本地队列、播放设置和听歌记录；需显式添加 `-e livePlayback true` 并单独运行该类。
系统媒体 / 焦点检查使用 Core 本地测试音频，在线播放、推荐补歌和公开分享目标另行调用真实提供器；回执分别标明音源与结果。
测试结束尝试恢复原队列内容、播放位置与设置，恢复结果单独记录；这不等于真实账号收藏、歌单写入或蓝牙 / 耳机验收。
测试 APK 中的独立音频焦点辅助 Activity 仅用于模拟其他应用的临时打断，不打包进主应用。
真实写入使用明确授权的测试账号与目标；被忽略的 Rust LIVE 测试可能访问服务或产生声音，不默认运行。

## 开发与审查约定

本节落实[项目规划](project-plan.md) D003，适用于网页端实现与本地审查。普通任务在聊天或 PR 中简要说明即可，不新增报告或审批步骤。

### 开发前：明确体验与实现边界

用户可以用日常语言描述问题。本地 Codex 结合旧 Android 行为、当前实现和迁移范围，整理推荐方案与可观察的验收表现；普通小修复直接推进。只有影响功能范围、明显体验差异或长期维护成本的取舍才需要讨论，不要求用户选择技术细节。

交给网页端的任务说明包括仓库与基准分支、要解决的问题、本次范围、应复用的现有能力及验收表现，沿用上文分支 / PR 交付流程。实现中发现需要显著扩大范围或改变既定架构时，先说明原因、替代方案和推荐选择。

### 实现与审查：按改动选择检查

| 适用改动                 | 开发要求                                                                                                     | 审查方式                                                                                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- |
| 新增或调整功能           | 用用户要完成的操作定义结果，先核对现有能力；保留已确认行为与本次边界                                         | 对照原始问题走通相关操作，确认没有只增加入口却缺少实际能力                                                                                   |
| 用户可见界面             | 优先复用同类组件，遵循平台惯例；同组控件的表达、尺寸、对齐和反馈一致；常用操作直接可达，图标提供可访问性说明 | 本地查看实际运行页面并操作，检查理解成本、步骤、主次关系与相关等待 / 空数据 / 失败状态；明显可用性问题主动修正                               |
| 面向用户的文案           | 说明发生了什么和可采取的行动；技术细节放详情或诊断入口；保持已有语言支持                                     | 检查页面、设置、错误提示和更新说明，避免内部枚举、字段名或未经解释的开发术语直接面向用户；例如媒体控制组采用统一图标表达，不随意混用文字按钮 |
| 依赖、状态与跨层接口     | 先检查已有能力；说明新增结构解决的当前问题及维护代价；明确状态归属，不重复保存同一业务事实                   | 检查是否存在更直接的实现，是否因重复状态产生同步补丁；不要求无关重构，也不以减少行数作为唯一标准                                             |
| 保存、异步请求与数据操作 | 明确保存 / 取消 / 重试语义，失败反馈与实际结果一致，处理相关的过期响应和生命周期变化                         | 按影响验证取消不生效、保存后重启保留、失败不假成功等场景；跨层修改检查实际调用链路                                                           |
| 缺陷修复与维护           | 查找同类原因，优先修复共同组件或责任源头，并更新受影响的测试和主文档                                         | 检查同类路径是否仍有问题；超出本次范围的缺口明确记录，不为修复单点问题自动扩大重构                                                           |

网页端交付前按上述要求自查；本地审查同时评估目标是否达成、交互是否合理、实现是否过度复杂，再执行相关测试和设备验证。界面审查使用模拟器或真机的实际运行结果，按需借助 Android Studio 调试；仅检查源码或测试通过不能代替界面审查。暂未查看运行效果时明确保留该项待检查，不将其计为体验验收完成。

### 交付：让用户能判断结果

面向用户优先说明改了什么、实际验证了什么、还剩什么，以及需要体验的少数关键操作。技术命令与详细证据按需提供，不要求用户理解日志才能判断完成情况。检查范围依照“必要检查”选择；小改动保持简短，不机械填写整张表或运行全部测试。

## 每项工作的完成标准

开始前核对规划、对应迁移编号和旧 Android 实际行为，写清目标、范围、假设与可观察的验收条件。
方向未明确时先整理上下文，只询问真正影响方向的缺口；目标和授权清楚时直接推进。普通任务在任务说明中记录即可，不另建文档。

实现保持聚焦，覆盖受影响的正常、空数据、失败、取消、过期响应、权限与保存路径；保留已确认返回行为。
按风险验证，如实说明命令、结果和未覆盖范围。代码、构建通过、模拟响应和真机通过不能互相替代。

完成后只更新受影响的主文档：范围或决定进规划，构建和工作方式进本指南，能力状态及关键验证结论进对齐清单。
文档仅维护中文，应用文案仍遵循已有语言能力。旧参考文档和英文副本已清理；历史行为通过清单中的固定提交链接回查。许可、第三方归属与来源证据单独保留。
不提交账号秘密、原始诊断包或无关改动；账号保护、日志脱敏、播放权益与许可证边界继续适用。
