# 可观测性最小看板落地（P2-2）

**Owner**：后端负责人（单人项目可为本人）。**目标**：在不引入 Prometheus 的前提下，用已有 **`GET /api/admin/dashboard/observability-summary`** 做定时拉取、人工看板或自建告警的「可执行」闭环。

指标语义与字段名以 **`docs/observability-metrics.md`** 为准；本文只写**操作**与**告警分层建议**。

## 1. 前置条件

- 后端已启动，且可从运行探针的机器访问（默认本机 **`http://127.0.0.1:11888`**）。
- 具备**内容管理权限**角色的账号 JWT（与现有 `SecurityUtils.requireContentManager()` 一致）。
- 将 JWT 写入环境变量 **`MEDICAN_ADMIN_JWT`**（或 **`TCM_ADMIN_JWT`**），供脚本读取。

## 2. 一键拉取（PowerShell）

在仓库根目录：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Fetch-ObservabilitySummary.ps1 `
  -BaseUrl "http://127.0.0.1:11888" `
  -OutFile ".\ci-artifacts\observability\summary-latest.json"
```

- 成功：**exit 0**，标准输出为完整 `ApiResponse` JSON（含 `code` 与 `data` 摘要字段）。
- 缺 Token：**exit 2**。
- HTTP 非 200 或业务 `code`≠200：**exit 1**。
- 可选阈值门禁（`MaxHttpErrorRateClassified` / `MinRecommendNonemptyRatio` 超阈）：**exit 5**（见脚本 `.SYNOPSIS`）。

阈值门禁示例（任务计划程序或 CI 中作为「软 SLO」探针）：

```powershell
.\scripts\Fetch-ObservabilitySummary.ps1 -MaxHttpErrorRateClassified 0.15 -MinRecommendNonemptyRatio 0.3
```

等价的 curl（Linux / Git Bash）：

```bash
curl -sS -H "Authorization: Bearer $MEDICAN_ADMIN_JWT" \
  "http://127.0.0.1:11888/api/admin/dashboard/observability-summary" | jq .
```

## 3. 最小「看板」形态（三选一）

| 形态 | 做法 | 适用 |
|------|------|------|
| A. 定时任务 + 文件 | Windows 任务计划程序 / cron 调上述脚本，保留 `summary-*.json` | 单人项目、零依赖 |
| B. 表格 | 将 `data` 中 `http.error_rate_classified`、`recommend.feed.nonempty_ratio`、`ai.*_upstream_ok_ratio` 粘到表格，按日期列对比 | 周回顾 |
| C. 外部 HTTP 监控 | 用支持 Bearer 的探针（Uptime、自建小服务）周期 GET 同一 URL，只断言 **HTTP 200 + body 含 `"code":200`** | 粗粒度存活 |

## 4. 告警分层（建议阈值，非硬编码在仓库）

以下为**人工或外部系统**参考阈值；与进程内 `maxCostMs` 的粗粒度一致，真 P95 需后续 Micrometer（见 **`observability-metrics.md` 第 4 节**）。

| 优先级 | 字段 | 建议关注 |
|--------|------|-----------|
| P0 | `http.error_rate_classified` | 持续 > 0.05 且 `http.request.total` 已较大时排查 |
| P1 | `http.max_cost_ms` | 突然高于近 7 日基线 3 倍以上 |
| P1 | `recommend.feed.nonempty_ratio` | 在推荐已启用场景下长期 < 0.3（需结合业务是否允许空页） |
| P2 | `ai.therapy.upstream_ok_ratio` / `ai.diet.upstream_ok_ratio` | 有上游样本时 < 0.85 持续多窗口 |

## 5. 与路径2清单的对应

- **P2-2**：本文 + **`Fetch-ObservabilitySummary.ps1`** 覆盖「最小看板与告警策略」的**可自动化**部分；**仍须人工**：接入商业 APM/Grafana、短信/钉钉路由与一次故障演练。
