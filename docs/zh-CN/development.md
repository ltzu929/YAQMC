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
Media3 负责系统媒体投影，Rust 负责实际播放与时钟；界面和宿主不另建播放事实源。主题与语言保存在 `native-ui` SharedPreferences。
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
adb shell am instrument -w -r -e notClass org.yaqmc.android.nativeui.NativeProviderSmokeTest org.yaqmc.android.nativeui.test/androidx.test.runner.AndroidJUnitRunner
```

`NativeProviderSmokeTest` 访问真实服务，只做只读检查，执行时单独说明。模拟 Core、在线只读、真实账号写入和系统媒体验收分别记账。
真实写入使用明确授权的测试账号与目标；被忽略的 Rust LIVE 测试可能访问服务或产生声音，不默认运行。

## 每项工作的完成标准

开始前核对规划、对应迁移编号和旧 Android 实际行为，写清目标、范围、假设与可观察的验收条件。
方向未明确时先整理上下文，只询问真正影响方向的缺口；目标和授权清楚时直接推进。普通任务在任务说明中记录即可，不另建文档。

实现保持聚焦，覆盖受影响的正常、空数据、失败、取消、过期响应、权限与保存路径；保留已确认返回行为。
按风险验证，如实说明命令、结果和未覆盖范围。代码、构建通过、模拟响应和真机通过不能互相替代。

完成后只更新受影响的主文档：范围或决定进规划，构建和工作方式进本指南，能力状态及关键验证结论进对齐清单。
文档仅维护中文，应用文案仍遵循已有语言能力。旧参考文档、英文副本暂留，其去留另议；不自动增加报告、进度页、规范文件或新审批步骤。
不提交账号秘密、原始诊断包或无关改动；账号保护、日志脱敏、播放权益与许可证边界继续适用。
