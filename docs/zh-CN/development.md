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

原生构建脚本在复制 APK 到 `release-android/` 前执行安装包校验：使用 SDK 36 的 `aapt` 核对包名 `org.yaqmc.android.nativeui`、版本号与 ARM64 ABI，检查 APK 中实际包含 `lib/arm64-v8a/libyaqmc_core.so`，再用 `apksigner` 校验签名并输出证书 SHA-256。任一检查失败即停止交付，不把构建成功当作 APK 已通过交付核对。校验器的纯逻辑测试位于 `scripts/ci/native-apk-check.test.mjs`，运行 `node --test scripts/ci/native-apk-check.test.mjs`；此检查不替代设备安装、真实播放及签名连续性检查。

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

## Android Studio 本地工作流

2026-10-27 起，本地 Codex 将 Android Studio 纳入日常开发与审查。网页端继续通过分支 / PR 交付；本地使用 IDE 阅读 Kotlin / Compose、断点调试、查看 Logcat，以及管理模拟器和连接设备。界面问题按需使用 Layout Inspector；卡顿、内存与播放后台问题按需使用 Profiler，并按改动保留必要的测试和真机验收。

打开的是 `apps/android/android` Gradle 工程，运行模块选择 `nativeApp`、日常调试选择 `debug`，优化比较使用 `benchmark`。旧 `app` 仍作为共享宿主保留。SDK、NDK、Gradle wrapper 和 JDK 沿用上节版本，不因 IDE 的升级提示自动调整。

先在终端加载本机已有的 Node 26.7.0、Cargo、JDK 21、SDK 36 和 Gradle 缓存环境，再从仓库根目录启动（可执行文件路径按本机安装位置填写）：

```powershell
$env:YAQMC_JAVA_HOME = '<JDK 21 目录>'
$env:ANDROID_SDK_ROOT = '<现有 SDK 36 目录>'
npm run android:studio -- 'D:\android\bin\studio64.exe'
```

首次使用前先通过原生构建脚本生成 Core。启动脚本默认读取 `nativeApp/build/generated/jniLibs`，也可用 `YAQMC_ANDROID_NATIVE_LIB_DIR` 指定已核对的兼容目录；每次启动将所选 ABI 的 Core 复制到被忽略的 `.gradle/yaqmc-studio/jniLibs`，供 IDE 使用，避免 Clean Project 删除运行所需的 JNI 库。修改 Rust 后先重新构建并重新运行启动命令，更新该副本。脚本向 IDE 注入 SDK / NDK、Rust TLS Maven 仓库、Core 目录、版本和源码提交信息，并在被忽略的 `local.properties`、`.gradle/config.properties` 中保存 SDK 与 JDK 路径；首次创建 `.idea/gradle.xml` 时选择 `GRADLE_LOCAL_JAVA_HOME`，已有 IDE 设置不覆盖。附加 `--prepare-only` 可只准备本地路径和 Core 副本而不启动 IDE。

Gradle JDK 选择 **JDK 21**（`GRADLE_LOCAL_JAVA_HOME` 或上述 `JAVA_HOME`），IDE 自身继续使用随附 JBR；两者用途见 [Android 官方 JDK 说明](https://developer.android.com/build/jdks)。本机新装 Studio 的 JBR 为 Java 25，不直接用于本工程 Gradle。IDE 的 SDK Location 应指向已有 SDK 36，避免另建一套环境。

启动前退出已有 Android Studio 进程，使新进程接收完整环境；切换任务分支或更换 Core / ABI / 离线测试变量后也重新启动，刷新源码标记和环境。IDE 同步或构建与命令行 Gradle 任务错开执行。IDE Run 用于开发调试，需要交付 APK 时继续使用 `build-android-native.mjs`，保留本机 debug keystore 与原有签名配置；以脚本测试、Lint、构建及对应设备检查作为交付依据。

## 跨对话接手与任务交付

2026-10-26 确认采用：**网页端 GPT 修改文件、提交代码及创建 PR，本地 Codex 拉取审查，按改动在本机验证和构建。** Android 验证与安装包交付由本机完成，使用已有本机签名配置。仓库原有通用 CI 的结果可作为补充证据。

当前通用 CI 仍检查保留的旧 React / Electron 工程，包括 Windows / Linux 桌面构建，以及共享 Rust 质量和契约检查。这些任务服务于迁移期间的回归，产品方向仍为 Android；它们不代替原生 Android 的测试、Lint、APK 构建和设备验收。CI 向 Android 收敛的调整另行处理。

用户已确认当前使用的网页端具备提交代码和创建 PR 的能力，本项目按此能力通过独立分支 / PR 交付源码及相关测试。网页端无需为这一分工搭建 Android 编译或签名环境。

新对话先阅读仓库根目录 `AGENTS.md`，再核对[项目规划](project-plan.md)和[迁移对齐清单](native-parity.md)。以 **当前 `main`、开发分支、未合并 PR 和本地改动** 为准，不能假设上次提到的任务已经合并、构建成功或真机通过。

`main` 保存已审查且经用户确认合入的改动。后续每项任务从最新 `main` 创建独立开发分支；网页端与本地 Codex 围绕同一个任务分支协作，通过 PR 交付。

网页端开工提示需明确仓库、分支或提交，并要求先阅读上述入口与主文档；不能假定普通聊天会自动加载 `AGENTS.md`。先确认迁移编号与旧版行为，再在独立分支修改源码及相关测试、提交代码并创建 PR。交付时提供基准提交、分支及 PR 链接、改动目标和未验证项；如未执行编译或测试，应明确说明。

本地 Codex 先检查工作区并拉取远端分支，审查实际差异，再按本指南的必要检查选择测试、Lint、构建及真机场景。需要安装包时，使用前述原生构建入口和已有本机签名；当前 debug / benchmark 使用本机 Android debug keystore，需保留同一密钥。正式签名、旧包升级和数据兼容仍按 T06 单独验收。

审查问题、验证结果与剩余真机场景直接在当前任务或 PR 中说明；只更新受影响的主文档。文件修改、本地检查、构建与真机验收分别记录实际结果。未经授权不合并 PR、不发布正式包、不卸载应用、不迁移数据或操作真实账号写入。

任务合并后先同步本地 `main`，确认 PR 已合并且最终文件内容已纳入，再清理对应本地与远端分支；squash 合并按 PR 状态和实际内容核对，不只看提交祖先关系。未合并分支与未跟踪文件先核对归属，补齐到对应任务分支后提交，不能作为过期改动直接删除。

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

上述默认运行器会启动真实应用。仅验证离线播放器 UI 与个性化时，先设置 `$env:YAQMC_ANDROID_OFFLINE_UI_TESTS = '1'` 构建并安装测试 APK，再使用专用 `NativeOfflineTestRunner`。该运行器使用普通 `Application` 和轻量 `ComponentActivity`，每项测试前后断言真实 Core 未初始化；恢复其他设备测试前需移除该变量并重新构建、安装测试 APK：

```powershell
adb shell am instrument -w -r -e class org.yaqmc.android.nativeui.NativePlaybackTest,org.yaqmc.android.nativeui.NativePersonalizationUiTest,org.yaqmc.android.nativeui.NativeMaintenanceUiTest org.yaqmc.android.nativeui.test/org.yaqmc.android.nativeui.NativeOfflineTestRunner
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
文档仅维护中文，应用文案仍遵循已有语言能力。旧参考文档、英文副本暂留，其去留另议；不自动增加报告、进度页、规范文件或新审批步骤。
不提交账号秘密、原始诊断包或无关改动；账号保护、日志脱敏、播放权益与许可证边界继续适用。
