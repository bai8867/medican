## 摘要

<!-- 简述本 PR 解决的问题或实现的能力 -->

## 变更类型

- [ ] 修复缺陷
- [ ] 新功能
- [ ] 重构 / 工程化
- [ ] 文档或配置

## 自检清单

### API 契约（涉及 HTTP / DTO / 分页 / 错误码时勾选并说明）

- [ ] 已阅读 `docs/api-contract.md`，本次变更符合命名与 `ApiResponse` 约定
- [ ] 已同步更新前端 `src/api` 或相关调用（若接口形状变化）
- [ ] 可选字段 / 兼容：无破坏性变更，或已在描述中说明迁移方式

### 前端 UI 栈（涉及新页面或新组件库依赖时）

- [ ] 已阅读 `docs/ui-stack-strategy.md`，未引入第三套 UI 库；Element / Vant 选型与场景一致

### 验证

- [ ] 本地已执行相关命令（例如 `npm run lint`、`npm run typecheck`、`.\mvnw.cmd test`），结果附于评论或 CI 已通过

## 关联

<!-- Issue / 文档 / 路径2 清单条目等 -->
