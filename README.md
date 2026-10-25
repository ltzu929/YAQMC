# YAQMC

一个面向 Android、适合自己使用的非官方 QQ 音乐客户端。界面采用 Kotlin / Jetpack Compose，阶段性复用 Rust 核心。

## 当前目标与状态

希望软件轻量、使用舒服，同时降低个人开发的工程与维护负担。当前先完整移植旧 Android 已开放的功能、交互和设置；具体范围与关键决定见[项目规划](docs/zh-CN/project-plan.md)。

基础界面、浏览与账号已有实现和部分验证，播放器、队列、歌词个性化、统计维护与正式交付仍有迁移任务。
实现覆盖与验收结果统一看[迁移对齐清单](docs/zh-CN/native-parity.md)，不以页面存在或构建成功认定迁移完成。

仓库版本为 `0.1.1`，不表示原生版已经正式发布：

- 原生模块为 `:nativeApp`，包名 `org.yaqmc.android.nativeui`，最低 Android 8.0，目前仅构建 ARM64 APK。
- 默认产物为本地调试签名的 benchmark 比较包，可与旧包 `org.yaqmc.android` 共存，两者数据独立。
- 现有发布工作流仍产出旧 React / Capacitor Android 包，原生版的正式签名、升级与数据兼容待落实。

公开安装包见 [Releases](https://github.com/YAQMC/YAQMC/releases)，按具体 Release 核对版本、包名、签名和验证范围。

## 开发入口

日常只维护以下中文文档：

| 文档                                        | 负责什么                                         |
| ------------------------------------------- | ------------------------------------------------ |
| [项目规划](docs/zh-CN/project-plan.md)      | 目标、范围、关键决定、待讨论事项与近期安排       |
| [开发指南](docs/zh-CN/development.md)       | 工程结构、环境、构建运行、必要检查和工作完成标准 |
| [迁移对齐清单](docs/zh-CN/native-parity.md) | 迁移期的旧版能力、原生覆盖、验收状态与关键证据   |

本 README 负责项目介绍、当前阶段和入口。[AGENTS.md](AGENTS.md)只为助手提供简短的阅读指引。
其他现有文档暂时保留，可能描述旧体系，其去留后续再决定；目前不维护成套英文副本。

## 使用边界与许可

YAQMC 与腾讯或 QQ 音乐没有从属或合作关系，不绕过会员、地区、版权或账号权益。
不要公开 Cookie、OAuth code、token、vkey/ekey、签名媒体地址或真实账号资料。

项目采用 [GPL-3.0-or-later](LICENSE)。许可证、第三方声明和对应源码交付资料保持各自职责，见
[第三方许可证](THIRD_PARTY_NOTICES.md)、[对应源代码交付政策](CORRESPONDING_SOURCE_POLICY.md)和[鸣谢](ACKNOWLEDGEMENTS.md)。
