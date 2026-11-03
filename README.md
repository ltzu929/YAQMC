# YAQMC-Android

基于 [YAQMC](https://github.com/YAQMC/YAQMC) 的非官方 QQ 音乐 Android 客户端，使用 Kotlin / Jetpack Compose 原生界面和 Rust Core，希望把日常听歌做得轻量、顺手。

## 功能与当前阶段

- 浏览推荐、搜索歌曲，查看专辑、歌手、歌单和音乐库。
- QQ 音乐账号登录、会员信息、收藏与歌单操作。
- 播放队列、音质选择、歌词显示，以及主题、背景和歌词布局设置。
- 系统媒体通知、后台播放、听歌统计、缓存管理和诊断分享。

当前进入首个公开预览版的准备阶段，候选版本为 **`0.1.4-beta.1`**，尚未发布。先经过一轮用户测试，再准备正式版；歌词完整自由编排等非关键完善继续迭代。

原生界面与 Rust Core 已整合，Android Studio 可构建签名 APK；现有 CI 包含 JVM、Lint、Rust 和 Android 30 / 35 离线界面回归。已完成的真机使用检查、自动音质提示等已知问题，以及焦点打断、蓝牙和长期后台等发布前检查，统一见[迁移对齐清单](apps/android/android/docs/native-parity.md#公开预览准备)。

## 安装与升级关系

最低支持 Android 8.0，当前提供 ARM64 APK。发布页使用[本仓库 Releases](https://github.com/ltzu929/YAQMC/releases)，公开预览会标为 Pre-release。

应用显示名改为 `YAQMC-Android`，安装身份仍为 `org.yaqmc.android`，沿用本项目既有签名。现有 `0.1.2` / `0.1.3` 用户可通过同签名 APK 覆盖升级，保留登录和本地数据。

上游及其他来源安装包可能使用相同包名；**只有包名相同、签名兼容且 versionCode 满足升级要求时才能覆盖安装**，同包名应用不能作为两个独立应用并装。名称变化不表示可以跨签名更新，当前不提供跨包数据迁移。

当前稳定版本的更新检查只接收正式版；首次试用公开预览需手动下载。预览版检查同一预览通道的新版本，并可升级到后续正式版。

## 从这里了解和管理项目

不熟悉 Android 开发时，可以按下面三个问题阅读，不必先看懂源码：

| 想知道什么                                              | 阅读入口                                                                                         |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| 软件要做成什么样，接下来怎样推进改进？                  | [项目规划：如何通过文档把控开发](apps/android/android/docs/project-plan.md#如何通过文档把控开发) |
| Android Studio 里为什么只有这些目录，每个文件夹做什么？ | [开发指南：先认清两个目录入口](apps/android/android/docs/development.md#先认清两个目录入口)      |
| 一个功能做到哪一步，手机上还需要检查什么？              | [迁移对齐清单：按功能查进展](apps/android/android/docs/native-parity.md#按功能查进展)            |

Android Studio 日常打开的是仓库内的 `apps/android/android` 子工程。在左侧“项目”视图展开 `android [yaqmc-android] → docs`，即可双击阅读上述三份文档。完整仓库还包含 `crates/` Rust 核心与来源资料；目录地图和功能对应的代码入口见开发指南。

## 项目方向

希望软件轻量、使用舒服，同时降低个人开发的工程与维护负担。保留旧 Android 已开放的功能、交互和设置；具体范围、发布准备顺序与关键决定见[项目规划](apps/android/android/docs/project-plan.md)。

仓库已收敛为 Kotlin / Compose 原生 Android 应用与 Rust Core，旧 React / Electron / Capacitor 工程已退出。保留上游版权、许可证和来源记录；实现与验收范围统一看[迁移对齐清单](apps/android/android/docs/native-parity.md)。

## 开发入口

日常只维护以下中文文档：

| 文档                                                       | 负责什么                                         |
| ---------------------------------------------------------- | ------------------------------------------------ |
| [项目规划](apps/android/android/docs/project-plan.md)      | 目标、范围、关键决定、待讨论事项与近期安排       |
| [开发指南](apps/android/android/docs/development.md)       | 工程结构、环境、构建运行、必要检查和工作完成标准 |
| [迁移对齐清单](apps/android/android/docs/native-parity.md) | 迁移期的旧版能力、原生覆盖、验收状态与关键证据   |

本 README 负责项目介绍、当前阶段和入口。[AGENTS.md](AGENTS.md)只为助手提供简短的阅读指引。
旧实现与文档可通过 Git 历史回查；仓库另保留许可证、第三方声明及来源证据，不维护成套英文副本。

## 使用边界与许可

YAQMC-Android 与腾讯或 QQ 音乐没有从属或合作关系，不绕过会员、地区、版权或账号权益。
不要公开 Cookie、OAuth code、token、vkey/ekey、签名媒体地址或真实账号资料。

项目采用 [GPL-3.0-or-later](LICENSE)。许可证、第三方声明和对应源码交付资料保持各自职责，见
[第三方许可证](THIRD_PARTY_NOTICES.md)、[对应源代码交付政策](CORRESPONDING_SOURCE_POLICY.md)和[鸣谢](ACKNOWLEDGEMENTS.md)。
