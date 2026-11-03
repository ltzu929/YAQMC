# YAQMC

一个面向 Android、适合自己使用的非官方 QQ 音乐客户端。界面采用 Kotlin / Jetpack Compose，阶段性复用 Rust 核心。

## 从这里了解和管理项目

不熟悉 Android 开发时，可以按下面三个问题阅读，不必先看懂源码：

| 想知道什么                                              | 阅读入口                                                                                         |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| 软件要做成什么样，接下来怎样推进改进？                  | [项目规划：如何通过文档把控开发](apps/android/android/docs/project-plan.md#如何通过文档把控开发) |
| Android Studio 里为什么只有这些目录，每个文件夹做什么？ | [开发指南：先认清两个目录入口](apps/android/android/docs/development.md#先认清两个目录入口)      |
| 一个功能做到哪一步，手机上还需要检查什么？              | [迁移对齐清单：按功能查进展](apps/android/android/docs/native-parity.md#按功能查进展)            |

Android Studio 日常打开的是仓库内的 `apps/android/android` 子工程。在左侧“项目”视图展开 `android [yaqmc-android] → docs`，即可双击阅读上述三份文档。完整仓库还包含 `crates/` Rust 核心与来源资料；目录地图和功能对应的代码入口见开发指南。

## 当前目标与状态

希望软件轻量、使用舒服，同时降低个人开发的工程与维护负担。当前先完整移植旧 Android 已开放的功能、交互和设置；具体范围与关键决定见[项目规划](apps/android/android/docs/project-plan.md)。

仓库已收敛为 Kotlin / Compose 原生 Android 应用与 Rust 核心，旧 React / Electron / Capacitor 工程已退出。各模块已有实现，完整设备回归与正式交付仍需按清单完成。
实现覆盖与验收结果统一看[迁移对齐清单](apps/android/android/docs/native-parity.md)，不以页面存在或构建成功认定迁移完成。

仓库版本为 `0.1.3`，用户已确认本轮使用结果并同意收尾，尚未正式发布：

- 原生模块为 `:nativeApp`，常规安装包沿用 `org.yaqmc.android` 与旧版签名，最低 Android 8.0，目前交付 ARM64 APK。
- 默认生成覆盖升级包，保留同包应用的登录和本地数据；后续修复与新功能沿用这一交付方式，见[项目规划 D004](apps/android/android/docs/project-plan.md#d004常规安装包保持覆盖升级)。
- 调试与性能比较包只在显式指定时生成，使用独立开发包名；正式发布工作流尚未启用。

当前原生安装包按[开发指南](apps/android/android/docs/development.md)在本机通过 Android Studio 的“打包 Release APK”配置生成，尚未正式发布。
2026-10-31，用户安装 `0.1.2` 后确认本轮反馈问题基本修复并通过审核，详细结果见[迁移对齐清单](apps/android/android/docs/native-parity.md#2026-10-31-用户安装验收)。
2026-11-03，用户确认 `0.1.3` 息屏续播、音乐通知、返回桌面后的超级岛及音质提示改善，本版本按确认范围收尾；自动音质正常选档时仍显示不可用提示，留待下一轮调整，详见迁移对齐清单文末。

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

YAQMC 与腾讯或 QQ 音乐没有从属或合作关系，不绕过会员、地区、版权或账号权益。
不要公开 Cookie、OAuth code、token、vkey/ekey、签名媒体地址或真实账号资料。

项目采用 [GPL-3.0-or-later](LICENSE)。许可证、第三方声明和对应源码交付资料保持各自职责，见
[第三方许可证](THIRD_PARTY_NOTICES.md)、[对应源代码交付政策](CORRESPONDING_SOURCE_POLICY.md)和[鸣谢](ACKNOWLEDGEMENTS.md)。
