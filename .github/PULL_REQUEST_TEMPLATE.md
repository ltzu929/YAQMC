## 变更目的

<!-- 说明问题、用户影响和为什么需要现在修改。关联 Issue：Closes #... -->

## 实现与边界

<!-- 说明关键实现、没有改变的边界、平台差异和 trade-off。 -->

## 验证证据

- [ ] `npm run docs:check`
- [ ] `npm run format:check`
- [ ] 受影响的原生 JVM 测试、Lint 与 APK 构建（注明实际执行范围）
- [ ] `cargo fmt --all -- --check`
- [ ] 受影响 Rust crate 的 clippy / test 与协议契约检查
- [ ] 行为变化有回归测试；界面变化有截图/无障碍检查（如适用）

## 安全与隐私

- [ ] 未提交 Cookie、OAuth code、token、vkey/ekey、签名 URL、真实账号资料或原始诊断包
- [ ] 未扩大 OAuth、JNI 或文件分享权限
- [ ] 未实现会员、地区、版权、DRM 或 VMP 绕过

## 文档与发布影响

- [ ] 受影响的中文主文档已更新，或说明无需修改的理由；应用文案遵循已有语言能力
- [ ] 列出迁移、兼容性、回滚和发布说明影响
