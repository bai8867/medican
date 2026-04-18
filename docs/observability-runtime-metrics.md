# 运行时指标基线（P2-2 初版）

本仓库后端使用进程内 `RuntimeMetricService`（计数器 + 耗时样本），不依赖 Prometheus。管理员可在具备内容管理权限的会话下查询快照。

## 查询入口

- `GET /api/admin/dashboard/runtime-metrics`（需 `SecurityUtils.requireContentManager()`）
- 响应体为 `ApiResponse`，`data` 含：
  - `counters`：`Map<String, Long>`，各业务计数
  - `avgCostMs`：`Map<String, Long>`，按 key 汇总的平均耗时（毫秒）
  - `maxCostMs`：自进程启动以来各耗时 key 的**单次最大值**（非 P95；见 `RuntimeMetricService`）

**本地怎么看**：浏览器登录管理端后，在开发者工具 Network 中查看上述路径；派生比率与 prompt 体量摘要见 **`GET /api/admin/dashboard/observability-summary`**，字段释义以 **`docs/observability-metrics.md`** 为准。

## 已埋点计数器（按域）

| 前缀 / 键 | 含义 | 主要来源 |
|-----------|------|----------|
| `http.request.*` | 总请求、状态码、业务码、错误分类、按方法与路由模板的耗时 | `RequestObservabilityInterceptor` |
| `error.category.*` | 全局异常分类（业务、参数、校验、JSON、未处理等） | `GlobalExceptionHandler` |
| `error.category.auth.*` | JWT 无效用户、禁用用户 | `JwtAuthFilter` |
| `recommend.request.*` | 推荐请求总量、关闭、空结果 | `RecommendApplicationService` |
| `recommend.feedback.*` | 推荐反馈事件 | `FavoriteHistoryController` |
| `ai.generate.*` | AI 食疗方案：总量、关闭、兜底、上游成功/失败 | `AiTherapyPlanService` |
| `ai.quality.*` | 质量门禁：阻断、样本沉淀 | `AiTherapyPlanQualityGovernance` |
| `ai.diet.*` | AI 药膳（另一入口）总量、关闭、兜底、上游成功/失败 | `AiDietService` |

## 耗时键（`recordCostMs`）

| key | 含义 |
|-----|------|
| `ai.generate` | 单次 AI 食疗方案生成 |
| `ai.diet` | 单次 AI 药膳生成 |
| `recommend.request` | 单次推荐应用调用 |
| `http.request.cost` 及 `http.request.cost.{METHOD}`、`http.request.cost.{METHOD}.{pattern}` | HTTP 层耗时 |

## 后续可演进方向（未在本迭代实现）

- 将同一快照导出到日志（JSON 一行）或推送到外部 TSDB。
- 为 `ai.generate.upstream.failed / recommend.request.empty` 等配置告警阈值与通知渠道。
- 前端构建时长与包体见 CI：`scripts/report-frontend-dist-size.sh`。
