# 后端分层命名与依赖方向（路径2 / P1-3）

本文档固定当前仓库的后端分层约定，便于继续拆分服务与避免循环依赖。

## 包职责

| 层级 | 包路径（示例） | 职责 |
|------|------------------|------|
| 接入层 | `controller`, `controller.admin` | HTTP 映射、参数校验、调用应用服务或门面服务；不写业务规则与持久化细节。 |
| 应用层 | `application.*` | 用例编排、事务边界（若需要）、跨聚合协调；可依赖 domain 与 infrastructure 的接口抽象。 |
| 领域层 | `domain.*` | 纯领域规则与策略（无 Spring Web 类型）；不依赖 controller。 |
| 基础设施 | `infrastructure.*`, `mapper`, `entity` | 持久化、外部系统适配；实现查询仓储等。 |
| 服务门面 | `service`, `service.ai` | 对外部控制器暴露的编排与集成；复杂 LLM 流放在 `service.ai` 子包，由薄门面（如 `AiTherapyPlanService`）委托 orchestrator / builder。 |

## 依赖方向（必须）

```
controller → service / application → domain
                ↓
         infrastructure / mapper
```

- **禁止**：`domain` → `service`；`domain` → `controller`；`infrastructure` → `controller`。
- **推荐**：长流程从 `service` 抽到 `service.ai` 下的 `*Orchestrator`、`*Builder`、`*Merger` 等，由同一用例门面注入调用，共享类型放在 `dto` 或领域侧值对象。

## 与现有代码的对应关系

- **周历富化**：`CampusWeeklyCalendarService`（门面）→ `CampusWeeklyCalendarRecipeEnricher`（协作类，仍在 service 侧时可接受；若继续升域可迁入 `application` 或 `domain` 并保留接口）。
- **AI 食疗**：`AiTherapyPlanService`（薄门面）→ `service.ai.AiTherapyPlanGenerationOrchestrator` 及 `AiTherapyPlanLlmPromptBuilder` 等；新增能力优先落在 `service.ai`，避免单类膨胀。

## 变更原则

新增模块时先判定属于「HTTP 边界」「用例编排」「领域规则」还是「IO 适配」，再选包；若出现跨层调用，优先通过提取接口或上移用例来解开，而不是在 controller 中堆业务分支。
