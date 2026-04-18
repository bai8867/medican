# 后端最小可观测指标（P2-2）

**Owner**：后端负责人（单人项目可为本人）。本文档与进程内 **`RuntimeMetricService`** + **`RequestObservabilityInterceptor`** 对齐；**非** Prometheus 格式，适合先接管理端或定时探针。

## 1. 管理端接口

| 接口 | 说明 |
|------|------|
| `GET /api/admin/dashboard/runtime-metrics` | 原始快照：`counters`、`avgCostMs`、**`maxCostMs`**（进程内单次耗时上界）。需内容管理权限。 |
| `GET /api/admin/dashboard/observability-summary` | 在快照上派生的比率与关键延迟字段，便于最小看板绑定。 |

## 2. 核心指标（5 + 1 项）

### （1）HTTP 分类错误率

- **含义**：带 `http.request.error.*` 分类的「非 none」请求占比（与 `RequestObservabilityInterceptor.afterCompletion` 一致）。
- **计数器**：`http.request.total`；`http.request.error.server|client|auth|validation`；成功观测仍记 `http.request.error.none`（命名历史原因，**不计入**分子）。
- **摘要字段**：`observability-summary` → `http.error_rate_classified` = `(server+client+auth+validation) / http.request.total`。

### （2）HTTP 延迟（均值与进程内峰值）

- **含义**：全链路请求耗时；**`maxCostMs` 为自进程启动以来该 key 的单次最大值**，不是滑动窗口 P95。真 P95 需 Micrometer/Prometheus 直方图后续替换。
- **键**：`recordCostMs("http.request.cost", costMs)`（及按 method、pattern 的细分键，见拦截器）。
- **摘要字段**：`http.avg_cost_ms`、`http.max_cost_ms`（对应 `http.request.cost`）。

### （3）推荐 Feed「有结果页」占比

- **含义**：推荐列表分页在**启用推荐**且候选非空路径下，返回的当前页是否有至少一条菜谱。
- **计数器**：`recommend.feed.page_nonempty`、`recommend.feed.page_empty`（在 `RecommendApplicationService.scoredRecommendFeedPage` 末尾）。
- **摘要字段**：`recommend.feed.nonempty_ratio` = `nonempty / (nonempty + empty)`。

### （4）AI 食疗上游成功率（条件样本）

- **含义**：在**实际发起上游 JSON 解析路径**时，成功 vs 失败（不含未走上游的本地/Mock）。
- **计数器**：`ai.generate.upstream.success`、`ai.generate.upstream.failed`（编排器内）。
- **摘要字段**：`ai.therapy.upstream_ok_ratio`。

### （5）AI 膳食上游成功率（条件样本）

- **计数器**：`ai.diet.upstream.success`、`ai.diet.upstream.failed`（`AiDietService`）。
- **摘要字段**：`ai.diet.upstream_ok_ratio`。

### （6）LLM prompt 体量占位（不裁剪）

- **含义**：在实际发起 **Chat Completions** 前，对当次请求的 `messages` 中各条 **`content`** 做 **Java `String.length()`（UTF-16 代码单元）** 求和，写入进程内计数器；**不**改写、**不**截断 prompt。用于粗看上下文膨胀与回归对比；**不是**上游 tokenizer 的真实 token，**不可**用于计费或硬阈值门禁。
- **计数器（原始快照 `runtime-metrics`）**：
  - 食疗：`ai.generate.therapy.prompt_budget.observed`（次数）、`ai.generate.therapy.prompt_budget.chars_total`（上述长度之和的累加，见 `AiTherapyPlanLlmInvocation`）。
  - 膳食：`ai.diet.prompt_budget.observed`、`ai.diet.prompt_budget.chars_total`（见 `AiDietService`）。
- **debug 日志**：`approx_tokens = ceil(chars/4)` 级别粗算，仅排障；实现见 **`LlmPromptBudgetHooks`**。
- **摘要字段（`observability-summary`）**：`AdminDashboardController.buildObservabilitySummary` 派生：
  - `ai.therapy.prompt_budget.observed`、`ai.therapy.prompt_budget.chars_total`、`ai.therapy.prompt_budget.avg_utf16_content_units`（`chars_total / observed`，无样本时为 `null`）。
  - `ai.diet.prompt_budget.observed`、`ai.diet.prompt_budget.chars_total`、`ai.diet.prompt_budget.avg_utf16_content_units`。

## 3. 其它已有计数（摘录）

- 推荐：`recommend.request.total|disabled|empty`；耗时 `recommend.request`（`avg`/`max`）。
- 食疗生成：`ai.generate.total|disabled|fallback|…`；`ai.generate` 耗时。
- 膳食：`ai.diet.total|disabled|fallback|…`；`ai.diet` 耗时。
- JWT：`jwt.auth.*`（见 `JwtAuthFilter`）。

## 4. 最小看板落地顺序

1. 定时拉取 **`/api/admin/dashboard/observability-summary`**（需登录/管理权限，按现有安全模型走）。**可执行步骤与脚本**：见 **`docs/observability-dashboard-playbook.md`**（含 `scripts/Fetch-ObservabilitySummary.ps1` 与 curl 示例）。
2. 将上述 **5 项核心业务指标** 与可选的第 2 节（6）**prompt 体量**绑定图表或表格；阈值告警先用人肉 + 日志。
3. 需要真 **P95/P99** 时：引入 Micrometer + Prometheus（或云厂商 APM），再逐步下线进程内 `maxCostMs` 的粗判依赖。

## 5. 与路径2清单的对应

- **P2-2**：指标定义与埋点命名以本文档为准；扩展新指标时同步更新 **`AdminDashboardController.buildObservabilitySummary`** 的派生字段，避免「有计数无解释」（**`prompt_budget.*` 已与第 2 节（6）对齐**）。
