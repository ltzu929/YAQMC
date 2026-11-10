# 来源台账说明

本文解释 [provenance-ledger.json](provenance-ledger.json) 的用途与覆盖范围。它保留既有来源审计快照、固定依赖叠加记录及历史 Provider Component 实现记录；各项结论按台账中的版本、提交和证据范围解释。

## 当前检查入口

在仓库根目录执行：

```bash
npm run provenance:enforce
```

[校验器](../../scripts/validate-provenance-ledger.mjs)核对台账结构、不可变源码引用、许可或授权证据、目标映射、贡献者权利和资源来源等记录。台账中的既有通过结论及机器校验各有其覆盖范围，新增源码、资源或依赖仍按本次变化核对。

## 资料职责

- 台账保存源码来源、固定提交、映射与证据；人工核对引用是否支持相应记录。
- [许可同意记录](../../LICENSING_CONSENT.md)与机器记录保存相关授权及适用范围。
- [qm-api-rs 来源记录](qm-api-rs-provenance.md)说明已审计固定依赖；修改依赖提交时核对新增差异与对应证据。
- [第三方声明](../../THIRD_PARTY_NOTICES.md)维护适用归属和声明；历史组件按原适用范围保留。
- 每次 Android 二进制发布的实际依赖、精确源码、许可证、哈希和构建身份按[对应源码交付政策](../../CORRESPONDING_SOURCE_POLICY.md)归档。

既有台账和授权记录保留其历史事实，当前开发与发布流程统一见[开发指南](../development.md)。
