# 校园药膳前端 UI 美化报告

**日期**：2026-04-16  
**范围**：`tcm-diet-frontend`（Vue 3 + Vite 6 + Element Plus + Vant）  
**方法**：结合仓库既有设计令牌与 `frontend-design` 技能中的「现有系统优先（Module C）」原则，在不大改信息架构的前提下提升层次、动效一致性与无障碍表现；并参考公开资料中 Element Plus 与 CSS 变量的主题实践。

---

## 1. 使用的方法与依据

| 来源 | 用途 |
|------|------|
| **Skill：`frontend-design`（compound-engineering）** | 以「检测现有设计系统 → 在既有变量与组件库上扩展」为主；避免与品牌绿/米纸色冲突的「模板化 AI 审美」；强调焦点态、`prefers-reduced-motion` 与少量有目的的动效。 |
| **网络检索** | 检索「Vue 3 + Element Plus + CSS 变量主题」的常见做法：在 `:root` 覆盖 `--el-color-primary` 系列、按需配合暗色变量文件、注意样式加载顺序等，用于与当前工程（已在 `global.css` 对齐 Element 主色）交叉验证，而非引入新的构建期 SCSS 主题链。 |
| **MCP** | 当前会话未配置可用的浏览器截图类 MCP；视觉验证以 **`npm run build` 通过** 与代码层面的 litmus 自检为主（见第 4 节）。 |

---

## 2. 设计取向（Visual thesis）

在已有「草本绿 + 纸色底 + 宋黑混排」的 TCM 校园语境下，本次强化为：

- **材质**：主背景由纯色改为极轻的径向高光 + 纵向渐变，营造纸张/展台感，但不抢内容。  
- **层次**：登录/门户卡片与移动端底栏增加轻量阴影与半透明底，提升浮层感。  
- **动效**：统一使用 `--ease-out` 与 `--duration-*`，与 `prefers-reduced-motion` 全局降级配合。  
- **无障碍**：全局 `::selection`、`:focus-visible` 环、卡片 `:focus-within` 与收藏按钮/文字链的可见焦点。

---

## 3. 变更文件与要点

| 文件 | 改动摘要 |
|------|-----------|
| `tcm-diet-frontend/src/style/variables.css` | 新增动效时长、缓动曲线、`--focus-ring-*`、行高语义变量，供全局与组件复用。 |
| `tcm-diet-frontend/src/style/global.css` | `body` 分层背景 + `background-attachment: fixed`；`::selection`；`prefers-reduced-motion`；通用 `:focus-visible`；`ui-card` / `.btn` 过渡改用新令牌。 |
| `tcm-diet-frontend/src/style/main.css` | `.page-title` 使用宋体系、字距与行高令牌，与侧栏/卡片标题气质一致。 |
| `tcm-diet-frontend/src/style/admin.css` | `.admin-login-card`：更柔和的纸白混合底、主色微量掺入的边框、双层阴影、`backdrop-filter`（不支持时仍可退化）。 |
| `tcm-diet-frontend/src/components/common/NavSidebar.vue` | 品牌标题字体统一为 `var(--font-serif)`、颜色改 `var(--color-primary-dark)`；菜单项过渡与 `:active` 微位移；补充 `focus-visible`。 |
| `tcm-diet-frontend/src/components/common/RecipeCard.vue` | 标题宋体+字重；占位图渐变改由品牌/表面色 `color-mix` 生成；悬停与 `:focus-within` 同步抬升与描边色；收藏色用 `--color-warning`；收藏与「不感兴趣」的焦点环。 |
| `tcm-diet-frontend/src/components/campus/CampusMobileTabbar.vue` | 窄屏下 Tabbar 顶边线、半透明底、模糊与饱和度、激活项字重。 |
| `tcm-diet-frontend/index.html` | `theme-color` 与 `color-scheme: light`，改善移动端浏览器 UI 与 PWA 边缘场景下的色调一致性。 |

未引入新依赖、未更换 UI 框架，**Element Plus 主色链仍在 `global.css` 的 `:root` 中维护**，与网络检索建议的「CSS 变量运行时覆盖」路径一致。

---

## 4. 验证

已执行：

```bash
cd tcm-diet-frontend
npm run build
```

**结果**：Vite 生产构建成功（exit code 0）。

说明：`npm run lint` 在扫描整个 `src`（含大量 `.ts`）时因当前 ESLint 配置/parser 与 `package.json` 中脚本范围不一致会出现解析错误，属既有问题；本次改动文件为 `.css` / `.vue` / `index.html`，与上述解析错误无直接关联。

---

## 5. 风险与后续建议

1. **`backdrop-filter` 与 `color-mix`**：在极少数旧版 WebView 上可能降级为无模糊/无混合色，布局不受影响。  
2. **`body` 固定背景附件**：极个别移动浏览器在 `fixed` 背景上存在性能差异；若后续发现滚动掉帧，可改为仅在 `@media (min-width: …)` 上启用 `background-attachment: fixed`。  
3. **全局 `:focus-visible`**：可能与第三方组件内部焦点样式叠加；若个别表单项出现「双边框感」，可在该组件局部用更精确选择器收窄全局规则。  
4. **视觉回归**：建议在真实设备（尤其 Android Chrome + 校园弱网）上打开首页、推荐流、登录门户各一页做一次肉眼验收。

---

## 6. 结论

本次美化在**不推翻现有配色与排版骨架**的前提下，完成了**背景层次、组件表面、移动端底栏、焦点与动效令牌**的系统化提升，并与 Element Plus 的 CSS 变量主题思路及项目内已有 `--el-color-primary*` 配置保持一致。详细代码以仓库 diff 为准。
