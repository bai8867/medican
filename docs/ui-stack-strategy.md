# 前端 UI 双栈策略（路径2 / P1-2）

当前依赖 **Element Plus** 与 **Vant** 两套组件库。本策略约束新增代码的选型，避免三栈扩散。

## 1. 主栈与场景边界

| 栈            | 定位 | 默认使用场景 |
|---------------|------|----------------|
| **Element Plus** | 桌面端、管理后台、复杂表单与表格 | `src/views/admin/**`、系统设置中带 `el-` 的页面、需 `ElMessage`/`ElMessageBox` 的全局反馈（与现有 `main.js` 全局注册一致） |
| **Vant**         | 移动优先、拇指操作、底部导航 | 门户入口 `EntrySelect`、`AccountDisabled`、`CampusLogin`、校园端设置子页（`src/views/settings/**`）、`CampusWeeklyCalendar`、`CampusMobileTabbar`、场景 `scene/detail` 等已采用 Vant 的页面 |

## 2. 允许例外

- **消息反馈**：在非 Vant 页面使用 `ElMessage` 为当前基线，**不强制**改为 Vant `showToast`（避免大范围重写）；**新增纯移动页**优先 `showToast` / `showDialog`。
- **渐进迁移**：同一业务域内「整页」迁移，不在单文件内混用两套表单组件（例如同一表单同时 `el-form` 与 `van-field`）。

## 3. 新增组件准入（Code Review）

1. **先选域**：该路由是否已属 admin / 系统设置 → Element；是否属校园 H5 设置/周历 → Vant。
2. **禁止**：在新页面中仅为「省事」引入第三套 UI 库。
3. **复用**：优先使用 `src/components` 已有封装；若需新组件，命名与目录应反映域（如 `campus/`、`admin/`）。

## 4. 迁移顺序（建议）

1. 保持 admin 全栈 Element Plus 不变。
2. 校园用户高频路径以 Vant 为主（周历、设置、底部栏已对齐）。
3. ~~`CampusLogin`、`EntrySelect`、`AccountDisabled`、`Constitution`（体质采集）、`NotFound`（404）、`AIPlanDetail`（AI 方案详情）、`AIGenerate`（AI 食疗生成）~~ 已整页迁移为 Vant；其它仍用 Element 的校园页：**整页迁移**时再替换，避免半页混排。

## 5. 与清单验收对齐

- 书面策略：本文档。
- 高频页治理示例：`CampusWeeklyCalendar` + 设置子页走 Vant；管理端走 Element Plus —— 作为**分域**参考实现。
