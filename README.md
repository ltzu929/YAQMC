# YAQMC-Android

<img src="assets/yaqmc-logo.png" alt="YAQMC-Android 图标" width="96" align="right">

**轻量版 QQ 音乐，专注日常听歌。**

继续使用自己的 QQ 音乐账号、会员曲库和歌单，在简洁的 Android 界面里浏览、播放和看歌词。

基于 [YAQMC](https://github.com/YAQMC/YAQMC) 开发的非官方客户端，使用 Kotlin / Jetpack Compose 原生界面。

**[下载安卓版](https://github.com/ltzu929/YAQMC/releases/latest)** · [版本记录](https://github.com/ltzu929/YAQMC/releases) · [问题反馈](https://github.com/ltzu929/YAQMC/issues)

## 界面预览

真机截图稍后补充。

## 核心亮点

- **自己的音乐库**：登录 QQ 音乐账号，查看会员信息、收藏和歌单，选择账号权益内的音质。
- **直接的浏览与播放**：搜索歌曲、歌手、专辑和歌单，浏览推荐与音乐库，切换入口保留搜索条件和列表位置。
- **独立播放器**：封面与歌词左右滑动切换，播放队列支持下一首播放、移除和拖动排序。
- **歌词与外观**：逐字歌词、翻译与罗马音显示，支持歌词预设、配色和背景设置。
- **日常听歌所需**：后台播放、系统媒体控制、缓存管理和听歌统计。

## 下载与安装

在[最新正式版](https://github.com/ltzu929/YAQMC/releases/latest)下载 `.apk` 并安装。希望体验预览版本时，可在[全部版本](https://github.com/ltzu929/YAQMC/releases)中选择标记为 Pre-release 的版本。

- 系统：Android 8.0 及以上。
- 架构：ARM64（`arm64-v8a`）。
- 更新：本项目发行的后续版本支持覆盖安装，保留登录和本地数据。
- 版本：发布日期、变化和安装包见对应 Release 页面。

## 使用与反馈

首次使用可从账号入口登录 QQ 音乐，再在媒体库查看收藏和歌单；也可以直接搜索、浏览推荐并播放歌曲。

**歌曲与音质。** 可访问曲目和音质取决于账号会员、地区及原平台版权要求，已有 QQ 音乐会员权益继续由原账号提供。

**覆盖安装。** 本项目的更新沿用包名和签名；其他发行者的同包名安装包需要核对签名是否匹配。

使用问题和建议请提交到 [Issues](https://github.com/ltzu929/YAQMC/issues)，附上应用版本、设备与系统版本、复现步骤和实际结果。截图与日志请移除账号凭据和私人信息。

## 后续方向

计划通过 B 站选定收藏夹补充 QQ 音乐没有的翻唱、现场等版本，只播放视频中的音频。这项功能目前仍在规划中。

## 开发

界面采用 Kotlin / Jetpack Compose，底层复用 Rust Core。完整的开发流程、工程结构、构建、测试与发布规范见[开发指南](docs/development.md)。

在已有开发环境中，用 VS Code 打开仓库根目录，在 `apps/android/android` 执行 Gradle 的 `:nativeApp:assembleDebug` 任务即可构建开发版。

开发版使用独立包名和本机调试签名，包含完整 Rust Core，无需维护者的私有密钥。

## 来源与许可

本项目基于 YAQMC 开发，与腾讯或 QQ 音乐没有从属或合作关系，歌曲访问和音质取决于账号权益及原平台限制。

项目采用 [GPL-3.0-or-later](LICENSE)。上游及第三方来源见[鸣谢](ACKNOWLEDGEMENTS.md)、[第三方声明](THIRD_PARTY_NOTICES.md)和[对应源码说明](CORRESPONDING_SOURCE_POLICY.md)。
