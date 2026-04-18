# 项目现状诊断与优化建议（pro）

> 生成日期：2026-04-16  
> 范围：`tcm-diet-frontend` + `campus-diet-backend` + 仓库治理与 CI  
> 说明：本文是对仓库当前形态的综合判断，与 `docs/C-1-未完成事项.md`、`路径2-未完成项清单.md`、`项目综合分析.md`、`AI问答与Skill集成优化方向.md` 等**互补**：侧重「问题归纳 + 行动优先级」，细节验收仍以各专题文档为准。

---

## 1. 当前项目画像（简要）

- **产品形态**：校园食养 Web（Vue 3 + Vite 前端，Spring Boot 2.7 / Java 11 后端），含推荐、收藏/浏览、AI 食疗方案、后台运营与系统开关。
- **工程成熟度**：已具备较完整 README、AGENTS 约定、本地脚本（JDK/Maven/MySQL/前后端启动）、**GitHub Actions CI**（前端 lint/typecheck/test/build + 体积门禁；后端 `mvnw test` + 生产安全基线脚本），后端近期出现大量**领域拆分与 AI Skill 化**相关新增代码与单测。
- **工作区信号（来自 git 状态快照）**：存在**大量未跟踪文件**与广泛修改并行（文档、后端 AI 管线、schema、测试、`.github` 等），说明处于**高密度迭代期**，若不及时「分批落盘」，review 与回滚成本会快速上升。

---

## 2. 主要问题归纳

### 2.1 仓库卫生与协作摩擦（高影响、低技术难度）

| 现象 | 风险 |
|------|------|
| 根目录存在 **`uv.exe`** 等大体积/工具型文件（且易被误提交） | 仓库膨胀、clone 变慢、误传二进制 |
| `.agents/`、`AGENTS.md`、多份「分析/清单/指南」**并行新增** | 信息源分散，后续自己回看时不知道以哪份为准 |
| 单次变更跨域过大（前端+后端+文档+CI 同批） | PR 难读、 bisect 困难、回滚粒度粗 |

### 2.2 文档与事实一致性（中影响）

- `项目综合分析.md` 等文内部分描述（例如后端监听地址）可能与当前 `application.yml` / `AGENTS.md` **存在漂移**；长期会误导联调与环境排障。
- 「路径 2」「C-1」等多条路线文档**并存**：好处是记录决策过程；代价是需要一个**极短的索引**（例如 README 一节「当前主路线」）避免执行时分叉。

### 2.3 前端工程债（用户可感知 + CI 已部分门禁）

- 生产构建 **chunk 体积**、**拆包/懒加载**、以及 `request` 与 store/router 的**静态依赖环**等问题，在 `docs/C-1-未完成事项.md` 中已有明确条目；CI 已对 **build 耗时**与 **dist 总体积**设 guard，后续优化需与门禁数字**联动调整**，避免「优化了但 CI 红」或「CI 绿但体验未改善」。
- **Element Plus + Vant 双栈**：策略与渐进治理已在 `docs/ui-stack-strategy.md` 等落地，剩余主要是**按域收敛**与新增准入执行。

### 2.4 后端与 AI 子系统复杂度（中长期）

- AI 侧已从「单块 system prompt」演进到 **classpath 多段 Skill + 路由装配 + 指标/质量治理**，能力变强的同时带来：
  - **编排与观测**成本（skill 集版本、哈希、路由错误对体验的影响）
  - **测试数据与资源文件**同步维护成本
- **Spring Boot 2.7** 仍在官方支持周期末端语境下：非紧急，但应纳入「升级或冻结策略」避免被动应对 CVE。

### 2.5 测试与发布信任链（中影响）

- 过滤器、CORS、真实装配顺序等 **集成级**覆盖仍相对薄（C-1 文档已点出）。
- **安全基线**：自动化脚本与单测已接 CI；**生产进程级演练**仍依赖人工留档（`docs/security-baseline-checklist.md` 与 drill 目录已有指引），属于「知道做了」但易拖延的环节。

### 2.6 CI 覆盖边界

- 当前 CI 已包含 **轻量 E2E**（`npm run test:e2e:ci`：build 后 preview + `e2e/ci-gate.spec.js`）；**全链路** Playwright（登录/推荐等）仍主要依赖本地 `npm run test:e2e` 与后端可用性。

---

## 3. 优化建议（按优先级）

### P0：先让「仓库状态」与「主路线」可控

1. **清理与忽略规则**  
   - 明确 `uv.exe`、本地数据库目录、IDE/缓存等是否应进 `.gitignore` 或迁出仓库根目录。  
   - 目标：工作区 `git status` 以「可提交的业务变更」为主，而不是工具与大二进制。

2. **确立单一「当前迭代主线」索引**  
   - 在 `README.md` 增加很小一节（5～10 行）：当前默认路线（例如 C-1 / 路径 2）、指向 `docs/` 内权威清单的链接。  
   - 其它分析型 md 保留为历史/专题，不必删，但避免互相重复长篇结论。

3. **分批提交与 PR 粒度**  
   - 建议按「后端 AI / 后端非 AI / 前端 / 文档与 CI」拆 PR，即使单人维护，也有利于未来自己复盘。

### P1：工程体验与性能（与 CI 门禁对齐）

1. **前端构建与体积**  
   - 落实 `manualChunks`、路由级懒加载、对大依赖的按需引入；每次调整后对照 `ci-artifacts` 中的 metrics 或本地脚本输出，**同步更新** CI 中的 guard 阈值（避免拍脑袋收紧）。

2. **`request.js` 解耦**  
   - 按 C-1 建议消除静态环依赖（动态 import 与登出/禁用跳转策略统一），降低打包器警告与意外整包拖拽。

3. **类型与 API 层**  
   - 继续将高频 `src/api` 从 JS 迁 TS 或收紧 shim，配合 `typecheck:report/count` 做每周收敛指标（路径 2 文档已描述工具链）。

### P2：质量、安全与 AI 治理能力

1. **集成测试补齐（高风险路径优先）**  
   - 用户禁用、系统 KV、周历发布、管理端写操作等：MockMvc + 安全过滤器链或轻量集成测试择一推进。

2. **安全基线人工演练闭环**  
   - 按清单完成一次 prod 配置故意错误启动 → 阻断确认 → 恢复，并把证据记入 `docs/security-baseline-drill-logs/`。

3. **AI Skill 体系**  
   - 在已有 `skill_set_id` / SHA256 / 路由指标基础上，增加「排障手册」级最短路径：常见失败码 → 建议检查的配置键 → 相关日志键名（仍避免记录用户原文）。  
   - 中长期评估 **结构化输出 / tools**（见 `AI问答与Skill集成优化方向.md` 方案 C）是否与上游能力匹配。

4. **依赖升级策略**  
   - Spring Boot 3.x / Java 17 迁移与 **javax → jakarta** 影响面大，适合单独立项；短期可对 2.7 线做 **依赖 CVE 监控 + 小版本补丁**。

### P3：可选增强

- CI 增加定时或手动的 **`npm audit`**（registry 限制时按文档切换官方源）。  
- 对关键学生链路增加 **E2E 冒烟子集**（夜间或手动 workflow），控制时长与 flake。  
- 观测：若已用 `RuntimeMetricService` 等，可在 `docs/observability-*.md` 中补「本地如何看计数」三行说明，降低自己三个月后回来看的成本。

---

## 4. 方案对比（何时选哪条路）

| 方案 | 适用 | 优点 | 缺点 |
|------|------|------|------|
| **小步合并 + 文档索引**（推荐默认） | 当前高密度改动期 | 回滚安全、认知负担低 | 需要自律拆 PR |
| **大而全一次合并** | 赶演示或单人分支长期不推 | 短期省事 | review/回滚困难，CI 失败难定位 |
| **先清仓库再开发** | status 已「看不清」时 | 后续心态与工具链更清爽 | 需半天整理与 .gitignore 决策 |

若你更关注「尽快对外演示」，可短期选大而全，但建议演示后立刻回到小步合并。

---

## 5. 建议的近期执行顺序（1～2 周可落地）

1. `.gitignore` / 根目录工具文件整理 + `README` 增加「主路线索引」。  
2. 前端构建体积与 `request` 解耦（与 CI 阈值联动）。  
3. 安全基线 **1 次**人工演练留档。  
4. 选 1～2 个高风险 Controller 补集成/MockMvc 测试。  
5. AI 侧：补最短排障小节（可放在已有 `AI问答与Skill集成优化方向.md` 末尾或 `docs/` 独立一页）。

---

## 6. 与现有文档的关系

| 文档 | 本文如何使用 |
|------|----------------|
| `docs/C-1-未完成事项.md` | B 批次前端、测试缺口、audit、仓库卫生等**仍有效**，本文 P0～P1 与之对齐 |
| `路径2-未完成项清单.md` | CI、typecheck、mvnw、安全基线等**进度已更新**；本文强调「远程 CI 连续绿灯」「人工演练」类**无法由智能体代劳**的验收 |
| `项目综合分析.md` | 业务与架构全景；建议定期校对与代码默认配置是否一致 |
| `AI问答与Skill集成优化方向.md` | AI 演进专项；本文不重复实现细节，只强调**观测与排障**配套 |

---

## 7. 成功标准（你可用来判断是否「优化到位」）

- `git status` 清爽；无意外大文件进版本库。  
- README 能回答「我现在该看哪份清单、默认怎么跑 CI 等价命令」。  
- 前端 CI 中 build/dist 门禁与真实优化趋势一致，而非形式主义。  
- 安全基线至少有**一次**可核验的演练记录。  
- AI 相关故障平均定位时间下降（靠 skill 元数据日志 + 文档短路径）。

---

---

## 8. 落实记录（非手动项，2026-04-16）

下列条目已在仓库内**直接落盘**（仍须你人工完成的：**安全基线演练留档**、**分批 commit/PR**、**生产密钥与真实 DB 口令**等不在此列）：

| 原建议（见上文 §3） | 落地方式 |
|---------------------|----------|
| P0 `.gitignore` / 根目录工具 | 去除 **`/uv.exe`** 重复规则；增加 **`.agents/plugins/cache/`** 忽略，降低误提交缓存体积 |
| P0 README「主路线索引」 | **已存在**（见 `README.md` 首节表格）；**常用命令** 含 **audit:ci**、**runtime-metrics**、**`Frontend supply chain audit`** 工作流说明与 `docs/observability-metrics.md` 链接；后端快速启动节含与 **`application.yml`** 键名对齐的 **环境变量速查** |
| P1 前端拆包 | 在 **`vite.config.js`** 增加 **`vendor-axios`** chunk（与既有 `vendor-*` 并列）；路由懒加载在 **`router/index.js`** 已全覆盖，未改行为 |
| P1 `request` 与路由/store | **C-1「回调注入」已落地**：`src/api/http/interceptors.ts` + `accountDisabledNavigator`，http 层不静态依赖 Pinia/Router |
| P2 文档与事实一致 | **`项目综合分析.md`** §3.1：后端监听描述已与 **`application.yml`**（默认 `127.0.0.1`、`SERVER_ADDRESS` 覆盖）对齐 |
| P3 CI `npm audit` | **`.github/workflows/ci.yml`**：推送/PR 时 **`npm audit --audit-level=critical`**；**`package.json`** 脚本 **`audit:ci`** |
| P3 定时/手动 audit | **`.github/workflows/frontend-supply-chain-audit.yml`**：`schedule`（每周一 UTC 06:00）+ **`workflow_dispatch`**，仅 **`npm ci` + audit**，`permissions: contents: read` |
| P3 观测文档三行 | **`docs/observability-runtime-metrics.md`** 含 **`maxCostMs`** 与本地 Network 查看路径；**`README.md`** 指向 **`docs/observability-metrics.md`** |
| P2 AI 排障手册级短路径 | **`AI问答与Skill集成优化方向.md`** **§12**（现象→配置→指标→代码锚点） |
| P2 集成测 / 过滤器装配 | **`AdminDashboardControllerApiContractTest`** 已覆盖 **`/runtime-metrics`**、**`/observability-summary`** 与角色门禁；**`WebConfig`** 将 **CORS / JWT** 均改为 **`FilterRegistrationBean`** 并显式 **`Ordered`**（CORS 先于 JWT），**`WebConfigFilterOrderContractTest`** 锁定顺序数值；**`CorsJwtPreflightMockMvcTest`** 在 **MockMvc** 上按生产顺序挂载 **CorsFilter → JwtAuthFilter**，对 **`OPTIONS /api/health`** 断言 **`Access-Control-Allow-Origin`** / **`Access-Control-Allow-Credentials`**（无 `Authorization` 预检）；**`docs/C-1-未完成事项.md`** 已同步：与嵌入式 Tomcat 全链仍有环境差异，**全容器栈 OPTIONS** 为低优先级增强 |

**仍为人工或流程项（本文不代为完成）**：`docs/security-baseline-drill-logs/` 演练、按域拆 PR、`xlsx` 替换决策、全链路 Playwright 定期跑通、Spring Boot 3 单独立项；**嵌入式 Tomcat 全链 OPTIONS**（与 MockMvc 切片相比边际收益小）按需补做。

---

*文档结束。*
