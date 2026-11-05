# YAQMC-Android

**轻量版 QQ 音乐，专注日常听歌。**

基于 [YAQMC](https://github.com/YAQMC/YAQMC) 的非官方 Android 客户端，使用 Kotlin / Jetpack Compose 原生界面和 Rust Core。以 QQ 音乐账号、会员曲库和歌单为核心，计划通过 B 站收藏夹补充翻唱、现场等版本。

## 功能

- QQ 音乐账号登录、会员信息、收藏和歌单管理。
- 搜索歌曲，浏览推荐、专辑、歌手和音乐库。
- 播放队列、会员权益范围内的音质选择、歌词与外观设置。
- 后台播放、系统媒体控制、缓存管理和听歌统计。

**B 站收藏音频尚未接入。** 后续将支持读取用户选定的收藏夹，播放其中翻唱、现场等视频的音频，与 QQ 音乐曲库互补。

## 下载与安装

前往 [Releases](https://github.com/ltzu929/YAQMC/releases) 下载 APK。目前发布为 `0.1.4-beta.1` 公开预览版，支持 Android 8.0 及以上的 ARM64 设备。



## 从源码构建

克隆完整仓库，在 Android Studio 中打开 `apps/android/android`。按[开发指南](apps/android/android/docs/development.md)配置 JDK、Android SDK / NDK 和 Rust 后，运行 `:nativeApp:assembleDebug` 即可生成可安装、包含完整 Rust Core 的开发版。

开发版使用独立包名和本机调试签名，无需维护者的私有密钥。构建命令、依赖版本和测试方法见开发指南。

## 反馈

使用问题和建议请提交到 [Issues](https://github.com/ltzu929/YAQMC/issues)。请附上应用版本、系统版本和复现步骤，日志中不要包含账号凭据或私人信息。

## 来源与许可

本项目基于 YAQMC 开发，与腾讯或 QQ 音乐没有从属或合作关系，歌曲访问和音质取决于账号权益及原平台限制。

项目采用 [GPL-3.0-or-later](LICENSE)。上游及第三方来源见[鸣谢](ACKNOWLEDGEMENTS.md)、[第三方声明](THIRD_PARTY_NOTICES.md)和[对应源码说明](CORRESPONDING_SOURCE_POLICY.md)。
