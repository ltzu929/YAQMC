# 原生 Android 听歌流程梳理

> **简体中文** | [English](../listening-review.md)

日期：2026-10-23。范围：现有 Kotlin / Compose 客户端、复用的 Android 宿主与 Rust 播放接口。
本轮阅读代码、核对既有测试报告与设备记录，没有修改功能、重新运行应用或执行账号操作。
本报告是首轮听歌路径检查；用户随后明确先完整移植旧版。迁移范围以[完整移植对齐清单](native-parity.md)为准，
本报告保留为可靠性与证据补充，以下顺序不构成完整移植排期。

## 总体判断

基本听歌路径已有实现；完整移植仍有大量功能、交互与设置待补齐，可靠性问题也应纳入相应模块验收。
“已实现”表示存在代码路径；“已有设备记录”表示先前有限场景的观察，不等于本轮完整验收。

## 现有听歌路径

| 环节             | 代码中已有能力                                                                                | 验证范围与缺口                                                                         |
| ---------------- | --------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| 找到歌           | 首页推荐、发现、歌曲 / 歌手 / 专辑 / 歌单搜索、详情、加载更多                                 | 已有首页与内嵌推荐歌单设备记录；搜索当时遭遇上游限流，成功搜索到播放的完整流程仍需验证 |
| 开始播放         | 点歌曲替换队列并从所选歌曲开始、播放全部、加入队列                                            | 已有实际播放与进度前进记录；“播放全部”只使用页面当前已加载歌曲                         |
| 控制播放         | 底部播放器、完整播放页、暂停 / 继续、上下首、拖动进度、顺序 / 随机 / 单曲循环、音质、队列移除 | 有实现；上述控制和失败恢复缺少原生版完整设备验收记录                                   |
| 持续收听         | Rust 队列与播放状态、Media3 媒体会话、后台服务、音频焦点、耳机断开暂停                        | 已有短时后台播放记录；长时间锁屏、蓝牙操作、焦点打断恢复、网络切换与进程重建仍需验证   |
| 再次找到常听内容 | 登录、收藏、账号歌单、最近播放入口                                                            | 有实现；原生版登录往返、账号恢复及真实写入仍待交互验证                                 |
| 辅助体验         | 同步逐行歌词、翻译 / 罗马音、主题、缓存设置、分享、统计                                       | 逐字歌词及旧版配置待迁移，详见完整对齐清单                                             |

## 具体缺口与建议顺序

### 先处理：失败时能够恢复

**代码风险：请求超时可能留下持续加载状态。** `NativeCore.invoke` 使用 30 秒 `withTimeout`，
页面加载和通用动作却把 `CancellationException` 直接重新抛出。
超时属于该取消路径，页面因而没有执行普通错误分支的 `loading = false`，通用动作也不会给出失败提示。
该结果由控制流推导，本轮没有注入超时或真机复现；需要与正常导航取消区分处理。

建议验收：模拟无响应，超时后退出加载并提供重试；切换页面时，旧请求不弹出无关报错。
依据：[NativeCore.kt](../../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeCore.kt)、
[NativeViewModel.kt](../../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeViewModel.kt)。

**代码缺口：播放中的等待与失败反馈不完整。** 模型已接收 `phase`，底部播放器主要按 `playing`
切换播放 / 暂停图标，没有呈现加载或缓冲；播放错误文字放在完整播放页。
建议让用户在当前入口就能判断正在等待、已暂停还是失败，并有明确的恢复动作。
依据：[NativeApp.kt](../../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeApp.kt)。

### 同批优先验证：持续听歌是否可靠

建议建立一组围绕真实使用的验收场景：成功搜索到播放、多首自然切换、拖动进度、锁屏连续收听、
耳机 / 蓝牙操作、音频焦点打断恢复、网络中断与切换、退出再打开后的队列和账号状态。
这些是待验证场景，不能直接写成已有故障。

Android 宿主已有焦点与媒体状态策略单元测试，但策略测试不能替代系统和设备上的完整路径。
依据：[PlaybackService.kt](../../apps/android/android/app/src/main/java/org/yaqmc/android/media/PlaybackService.kt)、
[CorePlayer.kt](../../apps/android/android/app/src/main/java/org/yaqmc/android/media/CorePlayer.kt)。

### 然后讨论：连续播放的边界

- **已确认的接入缺口**：原生版推荐歌曲走普通 `player_play_tracks`，未调用 `continuation_start`。
  Rust 的普通播放处理会结束已有推荐会话，因此推荐入口目前不会建立旧版的推荐续播。
  完整移植应恢复“猜你喜欢 / 雷达”原有续播行为；普通队列中的歌曲连续播放不受此接入缺口影响。
- **当前行为需明确**：“播放全部”把已加载歌曲提交给队列，没有按完整歌单继续拉取未加载页面。
  在支持分页的列表中，播放范围与完整列表可能不同。需要讨论加载和播放范围，而非直接默认全量加载。

依据：[NativeModels.kt](../../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeModels.kt)、
[NativeApp.kt](../../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeApp.kt)、
[NativeViewModel.kt](../../apps/android/android/nativeApp/src/main/java/org/yaqmc/android/nativeui/NativeViewModel.kt)、
[Rust 命令处理](../../crates/yaqmc-core/src/server/methods.rs)。

### 工程整理：降低后续维护成本

原生模块仍从旧 `app` 目录引用 Kotlin 源码、资源和混淆配置；Gradle 配置仍要求 Capacitor 安装目录，
构建入口仍使用 Node 脚本。现有发布工作流构建的是旧 Android 应用，尚未切换到 `nativeApp`。
这些是工程依赖和交付缺口，不能据此推断原生 APK 内打包了 Web 界面。

建议在明确发布与迁移范围后，整理原生版独立构建、共享宿主边界和发布验收；无需为精简而重写已有 Rust 能力。
依据：[原生模块构建配置](../../apps/android/android/nativeApp/build.gradle.kts)、
[Gradle 工程配置](../../apps/android/android/settings.gradle.kts)、
[原生构建脚本](../../scripts/build-android-native.mjs)、
[发布工作流](../../.github/workflows/electron-release.yml)。

## 证据与限制

- 本轮核对了已有 JUnit XML 报告：原生模型 / 协议测试 7 项、共享宿主测试 31 项，报告均为零失败；本轮未重跑。
- 既有 Redmi K70 记录确认首页、内嵌歌单、播放进度和短时后台播放；搜索遭遇限流。
- 既有性能样本中，原生界面启动和内存有改善观察，短时 CPU 样本未同向改善；账号、内容、主题和缓存不同。
  未有长时间耗电、同曲播放或全 Kotlin 对照结果，不能据此承诺省电。
- 原生版包名与旧版不同，账号、队列和缓存独立；尚不能当作旧版直接升级替换。

设备记录和性能限制见[原生模块检查点](../../apps/android/android/nativeApp/README.md)。
本地原始比较记录在被 Git 忽略的 `artifacts/android-native-comparison/`，不作为公开检出的必备文件。
自动化覆盖目前以模型、协议及宿主策略为主，原生模块尚无 `src/androidTest` 场景测试源码。

讨论建议：按完整对齐清单安排迁移批次，并将“稳定听下去、失败能恢复”列入每批验收。
本轮没有删除旧功能，也没有确定新的首页或导航设计。
