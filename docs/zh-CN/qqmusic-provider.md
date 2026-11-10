# QQ 音乐提供器

本文说明当前原生 Android 客户端的接入结构和维护边界。工程入口与全流程检查见[开发指南](../development.md)，依赖的精确版本以 [Cargo manifest](../../crates/yaqmc-provider-qqmusic/Cargo.toml) 和 [Cargo.lock](../../Cargo.lock) 为准。

## 实现结构

| 层次         | 职责与入口                                                                                                                                                                                                                                             |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Android 界面 | `NativeViewModel` 协调页面和操作，使用规范化模型展示搜索、详情、账号与音乐库                                                                                                                                                                           |
| Android 宿主 | [CoreManager](../../apps/android/nativeApp/src/main/java/org/yaqmc/android/core/CoreManager.kt) 管理 Core 与凭据回调；[OAuthActivity](../../apps/android/nativeApp/src/main/java/org/yaqmc/android/OAuthActivity.kt) 承接授权页面                      |
| JNI 与 Core  | [yaqmc-android](../../crates/yaqmc-android/src/lib.rs) 桥接调用；Rust Core 管理实际播放、队列、推荐会话与持久化                                                                                                                                        |
| 提供器接口   | [yaqmc-provider-api](../../crates/yaqmc-provider-api/src/lib.rs) 定义账号、目录、音源、歌词等边界                                                                                                                                                      |
| QQ 音乐实现  | [adapter.rs](../../crates/yaqmc-provider-qqmusic/src/adapter.rs) 适配接口，[qqmusic.rs](../../crates/yaqmc-provider-qqmusic/src/qqmusic.rs) 协调提供器业务，[qmapi](../../crates/yaqmc-provider-qqmusic/src/qmapi/mod.rs) 接入固定版本的 `qqmusic-api` |

目录搜索和详情、清晰音源、歌词获取及解密、QMC 解密、部分凭据和原始账号写入由固定依赖承担；YAQMC 保留 OAuth / 手机授权编排、权益与音源选择、加密音源请求、写入结果协调、缓存和页面数据映射。具体分工随实际源码维护。

## 账号与授权

Android 提供手机 QQ 授权和 QQ / 微信 OAuth 相关入口。手机授权由宿主处理官方 App 跳转与二维码流程；网页授权使用隔离的 Android `WebView`，回调、外部跳转与目标包按授权规则校验。当前宿主不再使用 Electron `BrowserWindow`。

Android 凭据通过 [CredentialStore](../../apps/android/nativeApp/src/main/java/org/yaqmc/android/core/CredentialStore.kt) 与 [CredentialVault](../../apps/android/nativeApp/src/main/java/org/yaqmc/android/core/CredentialVault.kt) 加密保存，Rust 提供器管理会话恢复、续期和请求作用域。账号更换或退出时使旧账号请求和缓存结果失效；界面使用账号信息投影。

新增账号链路时核对登录成功、拒绝、取消、超时、重新授权、冷启动恢复和续期失败等状态；真实账号写入按明确授权执行，结果与界面状态保持一致。

## 目录、播放与歌词

实体使用带提供器与类型的规范身份；原生引用中的必要字段在边界内传递。公开目录信息、账号权限、音源是否存在和实际播放结果分别处理。

音质候选按账号权益和实际返回结果解析，播放器显示实际音质。加密音源密钥用于原生解码路径，媒体缓存保存密文；请求、解密和可用性选择入口见 [qqmusic.rs](../../crates/yaqmc-provider-qqmusic/src/qqmusic.rs)、[QMC 适配](../../crates/yaqmc-provider-qqmusic/src/qmapi/qmc.rs)和[歌词适配](../../crates/yaqmc-provider-qqmusic/src/qmapi/lyric.rs)。

歌词规范化后提供逐行、逐字时间和翻译 / 罗马音。界面的时间偏移、定位和跟随行为由相关模型与 Compose 实现共同检查。

## 缓存与接口变化

元数据、实体、歌词和账号数据使用不同缓存规则，时限与键版本见源码。账号数据按会话隔离，认证错误按实际结果反馈。

上游响应变化时修改提供器边界、解析器和脱敏样本，保留明确的失败与重试行为；规范化接口变化同时核对协议样本、JNI 和 Kotlin 调用。确定性测试使用脱敏样本，真实服务及设备操作的覆盖范围分别记录，检查入口统一使用开发指南。

## 历史研究与来源

[历史协议研究账本](../qqmusic-provider.md)保留 2026-08 的端点观察、请求字段、固定提交和研究来源；[Windows 官方客户端互操作记录](qqmusic-official-interoperability.md)保留当时桌面版本的观察。两者按各自日期和平台使用，当前 Android 行为从上述实现入口核对。

实际复用的来源与许可见[第三方声明](../../THIRD_PARTY_NOTICES.md)、[qm-api-rs 来源记录](../release/qm-api-rs-provenance.md)和[来源台账说明](../release/provenance.md)，致谢见[鸣谢](../../ACKNOWLEDGEMENTS.md)。
