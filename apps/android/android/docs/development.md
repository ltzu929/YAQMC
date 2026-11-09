# 开发指南

## 获取源码与环境

```bash
git clone https://github.com/ltzu929/YAQMC.git
cd YAQMC
```

Android Studio 打开仓库中的 `apps/android/android`，完整源码仍需保留在仓库目录中，Gradle 会读取根目录的 Rust 工程和版本文件。

| 工具                      | 版本                           |
| ------------------------- | ------------------------------ |
| JDK                       | 21                             |
| Android SDK / Build Tools | 36 / 36.0.0                    |
| Android NDK               | 28.2.13676358                  |
| Rust                      | 1.88.0 或更高版本              |
| cargo-ndk                 | 4.1.2                          |
| Node.js                   | 26.7.0，仅辅助脚本和格式化需要 |

通过 Android Studio 的 SDK Manager 安装 SDK、Build Tools 和 NDK；Windows 安装 Rust 时选用 MSVC 工具链及对应 C++ 构建工具。Gradle 使用仓库自带 wrapper。

在仓库根目录准备 Rust 依赖：

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk --version 4.1.2 --locked
cargo fetch --locked
```

`qqmusic-api` 由 Cargo 从公开的 `YAQMC/qm-api-rs` 仓库获取，精确提交已固定在 manifest 和 `Cargo.lock`，无需额外克隆同级仓库。

## 首次配置

在 `apps/android/android/local.properties` 设置本机路径：

```properties
sdk.dir=/path/to/android-sdk
yaqmc.rustlsVerifierMavenDir=/path/to/rustls-platform-verifier-android-0.1.1/maven
```

`rustls-platform-verifier-android` 由上面的 Cargo 命令下载，所需目录是该包源码内的 `maven/`。默认 Cargo 缓存位于用户目录的 `.cargo/registry/src/<registry>/`；设置过 `CARGO_HOME` 时使用相应目录。也可在仓库根目录运行现有辅助命令，输出实际路径：

```bash
node --input-type=module -e 'import {resolveRustlsVerifierMaven} from "./scripts/android-toolchain.mjs"; console.log(resolveRustlsVerifierMaven())'
```

Windows 的 properties 路径使用正斜杠，盘符冒号写作 `\:`。Cargo / Rustup 不在 Studio 的 PATH 中时，可在同一文件指定 `yaqmc.cargoHome` 和 `yaqmc.rustupHome`，分别指向本机 Cargo、Rustup 目录。

在 Android Studio 的 Gradle 设置中选择 JDK 21，然后执行 **Sync Project with Gradle Files**。IDE 可以使用随附 JBR，Gradle 编译使用 JDK 21。

`local.properties`、签名文件和 IDE 缓存均不提交；源码、锁文件、Gradle wrapper、共享运行配置和签名示例保留在仓库中。

## 构建与运行

源码可在熟悉的编辑器中修改，构建与测试可直接执行官方命令；需要工程索引、引用分析、依赖源码、语义重构、交互调试或界面检查时，再按需使用 Android Studio 及其可用的 MCP 接口。IDE、命令行和可选脚本复用同一套 Gradle、Cargo 与 SDK 配置及构建任务。

在 `apps/android/android` 目录执行：

```bash
./gradlew :nativeApp:assembleDebug
```

Windows PowerShell 使用 `./gradlew.bat`。输出为 `nativeApp/build/outputs/apk/debug/nativeApp-debug.apk`，包名 `org.yaqmc.android.debug`，使用 Android SDK 的本机调试签名，可以与发布版并装。

Gradle 从根目录 `package.json` 读取版本，从 Git 读取源码提交，并自动调用 cargo-ndk 构建、打包当前 ABI 的 Rust Core。只改 Kotlin 时复用未变化的 Core；执行 Clean 后会重新编译。Studio 构建不依赖 npm，也无需手动复制 `.so`。

日常开发可使用共享运行配置：

| 配置               | 用途                                 |
| ------------------ | ------------------------------------ |
| 运行与调试         | `debug` 变体，安装并启动当前应用模块 |
| 检查 JVM 与 Lint   | 运行 JVM 单元测试与 Debug Lint       |
| 模拟器离线界面测试 | 在 x86_64 模拟器执行隔离的界面回归   |
| 打包 Release APK   | 使用维护者既有签名生成正式 APK       |

需要查看接近发布版的优化结果时可执行 `:nativeApp:assembleBenchmark`，使用相同源码与 release 优化配置、本机调试签名和独立包名 `org.yaqmc.android.benchmark`。输出在 `nativeApp/build/outputs/apk/benchmark/`，无需发行者私钥。

正式版 `org.yaqmc.android` 的构建会检查既有签名指纹，以保证原用户能够覆盖升级。维护者按 [keystore.properties.example](../keystore.properties.example)配置被忽略的 `keystore.properties`，执行 `:nativeApp:assembleRelease`。自行构建与测试使用 debug 或 benchmark 即可；独立发行 fork 时应配置自己的应用身份、签名与相应校验。

从不含 `.git` 的源码归档构建时，增加 `-Pyaqmc.buildCommit=<归档对应的完整提交 SHA>`，或设置 `YAQMC_BUILD_COMMIT`。提交号为 40 位小写十六进制；普通 Git 克隆无需设置。

可选的 `npm run android:build` 会调用同一 Gradle 入口，完成 JVM 检查和 APK 校验后复制到 `release-android/`。`yaqmc.nativeLibDir` / `YAQMC_ANDROID_NATIVE_LIB_DIR` 可显式复用已编译 Core，使用时需保证 ABI 与源码版本匹配。

## 工程结构

| 目录                             | 内容                                      |
| -------------------------------- | ----------------------------------------- |
| `apps/android/android/nativeApp` | Kotlin / Compose 界面、Android 宿主和测试 |
| `crates/yaqmc-android`           | Rust JNI 桥接                             |
| `crates/yaqmc-core`              | 实际播放、队列、推荐会话与持久化          |
| `crates/yaqmc-provider-api`      | 音乐提供器的数据结构与接口                |
| `crates/yaqmc-provider-qqmusic`  | QQ 音乐接入                               |
| `crates/yaqmc-protocol`          | 通信协议和 JVM 测试使用的契约样本         |
| `assets`                         | 品牌资源                                  |
| `scripts`、`.github`             | 可选构建工具和自动检查                    |
| `docs`                           | 协议、许可证与来源资料                    |

Android Kotlin 源码位于 `nativeApp/src/main/java/org/yaqmc/android/`：

- `nativeui/`：Compose 页面、状态和偏好；`NativeApp` 组织导航，`NativeViewModel` 协调请求。
- `core/`：Core 生命周期、JNI 宿主、登录协调与凭据保存。
- `media/`：系统媒体会话、通知、音频焦点与后台服务。
- `update/`：版本检查；`OAuthActivity` 承接授权登录。

Kotlin 界面经 JNI 调用 Rust Core。Rust 维护实际播放状态与时钟，Media3 负责 Android 系统媒体会话，避免两侧分别维护播放事实。语言、主题、歌词和外观偏好保存在 `native-ui` SharedPreferences，图片背景使用应用私有副本。

## 检查与测试

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

Linux 的 Rust 音频测试需要 ALSA 开发库和可用音频输出，CI 的依赖与虚拟输出配置见 [core-verify.yml](../../../../.github/workflows/core-verify.yml)。按改动选择相关检查；文档修改运行文档与格式检查即可。同一源码和配置下已有的有效检查结果可复用，新增改动、失败或未解决疑点需要时再补查。异步任务以最终日志、状态或产物确认结果，超时或不完整输出不计为通过。

### 模拟器与真实服务

离线界面回归在 API 30 / 35 的 x86_64 模拟器运行。启动目标模拟器后使用 Studio 的“模拟器离线界面测试”，或在 Gradle 工程目录执行：

```bash
./gradlew :nativeApp:connectedDebugAndroidTest -Pyaqmc.offlineUiTests=1 -Pyaqmc.nativeAbi=x86_64 -Pandroid.testInstrumentationRunnerArguments.class=org.yaqmc.android.nativeui.NativePlaybackTest,org.yaqmc.android.nativeui.NativePersonalizationUiTest,org.yaqmc.android.nativeui.NativeMaintenanceUiTest,org.yaqmc.android.nativeui.NativeCatalogStateTest,org.yaqmc.android.nativeui.NativeExperienceUiTest
```

该配置使用 `NativeOfflineTestRunner`，不初始化真实 Core。CI 的离线 APK 不包含 Rust Core，只能测试界面，不能日常听歌。真实 x86_64 播放另需安装 Rust target `x86_64-linux-android`，使用 `-Pyaqmc.nativeAbi=x86_64` 构建正常变体。

恢复 ARM64 正常构建前移除 `yaqmc.offlineUiTests`、`yaqmc.nativeAbi` 及对应的 `YAQMC_ANDROID_OFFLINE_UI_TESTS`、`YAQMC_ANDROID_NATIVE_ABI`、`YAQMC_ANDROID_NATIVE_LIB_DIR` 环境配置，再重新构建。

`NativeProviderSmokeTest` 访问真实在线服务；`NativePlaybackLiveTest` 需显式传入 `livePlayback=true`，会播放音频并暂时调整本地队列与设置。真实账号、蓝牙、焦点打断、长期后台和覆盖升级分别在对应设备上检查，隔离界面回归不覆盖这些行为。

日志、布局和性能问题按所需信息选择 Android SDK / ADB 命令或 Studio 的 Logcat、Layout Inspector、Profiler。`artifacts/`、构建报告、诊断包和本机测试记录不提交。

## 公开预览发布流程

1. 同步 `package.json`、`package-lock.json` 中的版本，完成相关检查，从干净提交构建签名 APK。版本后缀按 `alpha < beta < rc < 正式版` 递增，标签使用 `v<version>`。
2. 核对包名、版本、ABI、签名及包内 `YAQMC_BUILD_COMMIT`；已有 `verifyNativeApk` 检查和 SDK 的 `aapt` / `apksigner` 可用于校验。正式包保持既有签名，以支持覆盖升级。
3. 按[对应源码交付政策](../../../../CORRESPONDING_SOURCE_POLICY.md)准备精确依赖源码、许可证、来源清单及 SHA-256，核对源码归档与 APK 对应同一提交。
4. 在 [Releases](https://github.com/ltzu929/YAQMC/releases)发布 APK。预发布版本标记 Pre-release；说明聚焦可用功能、安装要求和反馈方式，源码取得方式按对应源码政策提供。

应用内更新与反馈指向本仓库。稳定版默认不订阅预览；预览版可接收同通道更新及后续正式版。构建产物和本机归档保存在被忽略的 `release-android/`、`artifacts/`，不用提交到源码仓库。
