# AI 问答优化方向：在大模型调用中集成 Skill

## 1. 文档目的与适用范围

本文档描述**当前 AI 问答 / 生成链路**的演进方向，重点回应一种产品与技术倾向：**把与 AI 相关的 Skill（可复用、可版本化的能力说明与流程约束）显式接入大模型调用**，而不是把所有规则长期堆在单一巨型 `system` 字符串里。

**适用范围**（与仓库现状对齐）：

- 后端通过 `LlmChatClient` 以 **OpenAI 兼容 Chat Completions** 调用上游（见 `campus-diet-backend` 中 `LlmChatClient`）。
- 典型场景包括 **AI 食疗方案**（`AiTherapyPlanService` + `AiTherapyPlanLlmPromptBuilder`）与 **AI 膳食相关生成**（`AiDietService` 等），当前多为 **system + user 两轮文本**，返回内容再经 JSON 解析与质量治理。

本文档**不**要求与 Cursor / `AGENTS.md` 的 `openskills` 运行时一一对应；而是借用「Skill」这一概念，描述**应用侧**如何组织提示词与编排逻辑。

---

## 2. 现状简述

| 维度 | 现状 |
|------|------|
| 调用形态 | 单次 `chatCompletionsContent`，消息体为 `messages` 列表 |
| 提示词 | 食疗 / 膳食均为 **classpath 多段 Skill**（`llm-skills/runtime-llm/...`），装配器线性拼接；共享合规见 `shared/core-compliance@1.txt`；食疗 part-A 已细拆为 `core.identity@1` / `output.schema-therapy-json@1` / `output.markdown-therapy-body@1`（可选 `locale.zh-cn@1`），无 identity 时回退单文件 `system-prompt-part-a.txt`；膳食正文为 `core.identity@1` + `output.json-only@1`，无 identity 时回退锚点路径单文件 |
| prompt 体量观测 | 上游调用前记录 **UTF-16 `content` 长度** 的进程内计数（**不裁剪** prompt）；见 `LlmPromptBudgetHooks` 与 `RuntimeMetricService` 键名 §7 |
| 可扩展性 | 仍靠新增/改资源文件与装配顺序演进；**远程热更新**未做，运营可通过 `system_kv` 与 YAML 切换路径（见 M2） |
| 与「技能」的关系 | 仓库根目录已有面向**人类+编码智能体**的 Skill 体系（如 `.claude/skills` / Cursor skills），与**线上用户请求的 LLM 调用**尚未形成统一抽象 |

---

## 3. 核心问题（为何要 Skill 化）

1. **职责混杂**：医学/食养合规、输出 JSON schema、Markdown 文风、菜谱目录约束等挤在同一 prompt，不利于分工评审与 A/B。
2. **复用困难**：「安全红线」「JSON -only」「语气与长度」等在多个 AI 接口（食疗、膳食、未来客服问答）中重复或漂移。
3. **演进不可观测**：缺少「本次请求加载了哪些 Skill 片段 / 版本」的日志与配置面，排障与合规审计成本高。
4. **与模型能力不匹配**：部分上游已支持更丰富的协议（如 tool、structured output）；即便暂不用 tool，**逻辑上**也可先 Skill 化，为后续升级留接口。

---

## 4. 优化总目标

- **目标 A**：将 LLM 输入拆为**稳定内核**（身份与安全）+ **可组合 Skill 块**（格式、领域、工具使用说明等），按场景装配。
- **目标 B**：Skill **可版本化、可配置**（例如按 `system_kv`、环境或功能开关选择 Skill 集），并在日志/指标中记录 `skillSetId` 与版本哈希（不涉及用户隐私原文）。
- **目标 C**：与现有 **质量治理、兜底、指标**（如 `RuntimeMetricService`、`applyQualityGovernance`）衔接，避免「prompt 变长但治理逻辑脱节」。

---

## 5. Skill 定义建议（应用侧语义）

建议将 **Skill** 定义为一条**有元数据的文本单元**（存文件、DB 或配置中心均可），至少包含：

| 字段 | 说明 |
|------|------|
| `id` | 稳定标识，如 `tcm.safety-boundary`、`output.json-therapy-plan` |
| `version` | 语义化或整数版本，便于回滚与对比实验 |
| `priority` / `order` | 拼装到 system 时的顺序（安全类通常最先） |
| `content` | 注入模型的正文（自然语言 + 必要时少量结构化说明） |
| `appliesTo` | 场景标签：`therapy_plan`、`diet_qa`、`general` 等 |
| `tokenBudgetHint` | 可选，供裁剪或选用短版 Skill |

**与仓库内「智能体 Skill」的关系**：可约定**命名空间**，例如 `runtime-llm.*` 表示线上推理专用，`dev-agent.*` 表示仅开发流程使用，避免混用。

---

## 6. 实现路径对比（供选型）

### 方案 A：Classpath 多段 Skill + 线性装配（M1 基线，已落地）

**做法**：食疗 part-A 由 **`core.identity@1.txt`**、**`output.schema-therapy-json@1.txt`**、**`output.markdown-therapy-body@1.txt`**（可选 **`locale.zh-cn@1.txt`**）拼装；若自定义前缀下缺少 `core.identity@1.txt`，则回退单文件 **`system-prompt-part-a.txt`**（兼容旧 M2 包）。其后为 **`shared/core-compliance@1.txt`**（安全/合规红线，与膳食共用）、**`context.recipe-catalog@1.txt`**（菜谱目录引用规则前缀；无则回退 **`system-prompt-part-b.txt`**）及代码注入的 **`catalogJson`**；**`RuntimeLlmSkillLoader`**（含 **`loadOptionalUtf8`**）负责读取；**`TherapyPlanLlmSkillAssembler`** 输出 **`TherapyPlanSkillAssembly`**（`skillSetId`、内容 SHA256、`appliedSkillRefs`）。

| 优点 | 缺点 |
|------|------|
| diff 可读、单测可断言资源存在与关键子串 | 仍是「启动时一次性加载」，未做远程热更新 |
| 不依赖 DB / 配置中心即可上线 | 多场景复用需再抽公共片段或共享文件 |

**适用**：满足 **目标 A**；与方案 B 组合使用（见下）。

---

### 方案 B：场景路由 + 多 Skill 动态子集（已落地于真实上游调用）

**做法**：**`TherapyPlanLlmRoute`** 在默认与「短输入」间切换：当用户主诉少于 4 字（且未强制 `default`）时，在 **part-A 文本块末尾**与共享合规块之间插入 **`skill-input-vague-symptom@1.txt`**，再拼接 **`context.recipe-catalog@1.txt`**（无则 **`system-prompt-part-b.txt`**）与 **`catalogJson`**。路由覆盖顺序：**`system_kv` 键 `ai.therapy.route.override`** 优先，其次 **`AI_THERAPY_ROUTE_OVERRIDE`** / **`campus.ai.therapy-route-override`**（`application.yml`）。**`AiTherapyPlanGenerationOrchestrator`** 在调用 `LlmChatClient` 前完成装配，并打指标 **`ai.generate.therapy.skill_set.default` / `brief`**，非基线资源包时 **`ai.generate.therapy.skill_pack.non_baseline`**；**debug** 日志含 `skill_set_id`、`system_sha256`、`therapy_skill_prefix`（不含用户原文）。多轮调用、更细意图分类仍属后续扩展。

| 优点 | 缺点 |
|------|------|
| 控制 token 与针对性更强 | 路由错误会带来体验问题，需要监控与 fallback |
| 便于 A/B 与灰度 | 多轮增加延迟与费用 |

**适用**：你已倾向「AI 相关 skill 进入大模型调用」，且希望**可运营、可灰度**。

---

### 方案 C：协议层升级（Tool / JSON Schema / 结构化输出）

**做法**：扩展 `LlmChatClient` 支持 `tools`、`tool_choice` 或厂商 structured output；将「查菜谱」「查用户体质」等做成真正工具，Skill 中只写**何时调用、如何汇总**。

| 优点 | 缺点 |
|------|------|
| 减少幻觉、事实可追溯 | 实现与联调成本高；依赖上游能力 |
| 与复杂问答形态更匹配 | 运维与超时、重试策略更复杂 |

**适用**：问答范围扩大、与数据库/检索强绑定的阶段。

**方案 C 落地门槛（仓库当前不实现 Tool/结构化输出代码）**：在出现**明确、可验收**的业务场景后再启动，例如同时满足：**(1)** 用户问题必须依赖**实时库/检索**才能回答且纯 prompt 幻觉成本不可接受；**(2)** 上游 API 已稳定支持 `tools` 或 structured output 且与本项目 **`LlmChatClient`** 契约对齐；**(3)** 已具备 tool 调用的超时、熔断、指标与回退路径设计。在此之前仍以 **A + B + classpath Skill** 演进为主。

---

**选型建议**：当前食疗链路已 **A + B（子集）** 并行；在路由与指标稳定后，按业务需要局部引入 **C**（例如仅对「需检索」的子场景开放 tool）。

---

## 7. 与当前代码的映射关系（落地时改哪里）

- **提示构造（食疗）**：**`TherapyPlanLlmSkillAssembler.assemble`** + **`TherapyPlanLlmRoute`** + **`TherapyPlanRuntimeSkillPaths`**（含 part-A、`contextRecipeCatalog()` / **`context.recipe-catalog@1.txt`** 与 **`partB()`** 回退）；共享合规 **`RuntimeLlmSharedSkillResources.CORE_COMPLIANCE`**。**`AiTherapyPlanLlmPromptBuilder.buildSystemPrompt(String)`** 仍为便捷封装，**固定 `DEFAULT` 路由**（单测/工具用）；线上编排见 **`AiTherapyPlanGenerationOrchestrator`**。
- **提示构造（膳食）**：**`DietPlanLlmSkillAssembler`**：共享合规 + 同目录 **`core.identity@1.txt`** + **`output.json-only@1.txt`**（与旧版单行 system **字符串一致**，中间无额外换行）；**`campus.ai.runtime-llm.diet-system-prompt-resource`** 仍为 **锚点 classpath**（取其父目录解析片段；无 `core.identity@1.txt` 时 **`loadRequiredUtf8(锚点)`** 整文件作 body，兼容旧包）。**`AiDietLlmPromptBuilder`** 委托装配器。
- **路径与覆盖（M2）**：**`RuntimeLlmSkillPathProperties`** 绑定 **`campus.ai.runtime-llm.*`**（环境变量 **`AI_THERAPY_SKILL_PREFIX`**、**`AI_DIET_SYSTEM_PROMPT_RESOURCE`**）；**`RuntimeLlmSkillPathResolver`** 读取 **`system_kv`**：**`ai.runtime-llm.therapy-resource-prefix`**、**`ai.runtime-llm.diet-system-prompt-resource`**，非空则优先生效。
- **Skill 根目录**：食疗 **`llm-skills/runtime-llm/therapy-plan/`**；共享 **`llm-skills/runtime-llm/shared/`**；膳食 **`llm-skills/runtime-llm/diet-plan/`**。常量 **`AiTherapyPlanLlmPromptBuilder.THERAPY_PLAN_SKILL_SET_ID`** 与 **`TherapyPlanLlmRoute.DEFAULT#skillSetId()`** 对齐（指标/路由语义）。
- **调用入口**：**`AiTherapyPlanService.generate`** → **`AiTherapyPlanGenerationOrchestrator`**；**`AiDietService`** 在进入 **`llmChatClient.chatCompletionsContent`** 前组装 `messages`。
- **观测**：**`RuntimeMetricService`** 计数 **`ai.generate.therapy.skill_set.*`**、**`ai.generate.therapy.skill_pack.non_baseline`**、**`ai.diet.skill_set.default` / `custom`**；**prompt 体量（占位，不裁剪）**：每次上游请求前 **`ai.generate.therapy.prompt_budget.observed`** + **`ai.generate.therapy.prompt_budget.chars_total`**（**`AiTherapyPlanLlmInvocation`**），膳食 **`ai.diet.prompt_budget.observed`** + **`ai.diet.prompt_budget.chars_total`**（**`AiDietService`**）；**debug** 日志含 `approx_tokens`（`chars/4` 粗算）。管理端摘要 **`GET /api/admin/dashboard/observability-summary`** 派生 **`ai.*.prompt_budget.avg_utf16_content_units`**（见 **`docs/observability-metrics.md`**）。日志注意脱敏。
- **测试**：**`TherapyPlanLlmSkillAssemblerTest`**、**`DietPlanLlmSkillAssemblerTest`**、**`RuntimeLlmSkillPathResolverTest`**、**`AiTherapyPlanServiceTest`**（含 brief 与路由覆盖）、**`LlmPromptBudgetHooksTest`**、**`AdminDashboardObservabilitySummaryTest`** 等。

---

## 8. 建议的 Skill 清单（示例，可按优先级分批）

1. **`core.identity`**：助手身份与领域（校园中医药膳科普）——食疗见 **`core.identity@1.txt`**；膳食见 **`diet-plan/core.identity@1.txt`**。
2. **`compliance.medical-disclaimer`**：禁止诊断/处方/疗效承诺；就医提示（与现有 **`shared/core-compliance@1.txt`** 策略对齐，可再拆细粒度）。
3. **`output.schema-therapy-json`**：JSON 字段约束——食疗见 **`output.schema-therapy-json@1.txt`**。
4. **`output.markdown-therapy-body`**：`therapyRecommendMarkdown` 结构与篇幅——食疗见 **`output.markdown-therapy-body@1.txt`**。
5. **`context.recipe-catalog`**：仅含「如何引用 catalog 中的 id」规则——已实现为 **`therapy-plan/context.recipe-catalog@1.txt`**；**具体 catalog 仍由代码注入**；无该文件时回退 **`system-prompt-part-b.txt`**（旧 M2 包）。
6. **`input.vague-symptom`**：短输入时的温和通用策略（**`skill-input-vague-symptom@1.txt`**，与 `TherapyPlanLlmRoute` 联动）。
7. **`locale.zh-cn`**：简体中文补充说明——食疗见 **`locale.zh-cn@1.txt`**（可选文件）。
8. **膳食 JSON-only**：**`diet-plan/output.json-only@1.txt`**（与 identity 拼接为膳食 system 正文）。

---

## 9. 里程碑（建议）

| 阶段 | 产出 | 验收要点 |
|------|------|----------|
| M1 | Skill 模型与注册表 + 1 个场景接入（如仅食疗方案） | **已落地**：食疗 part-A 多段 + **`system-prompt-part-b`** + **`skill-input-vague-symptom@1`** + **`shared/core-compliance@1`** + `RuntimeLlmSkillLoader` + `TherapyPlanLlmSkillAssembler` + `TherapyPlanLlmRoute`；编排器内可观测。**后续**：远程版本切换、更细注册表。 |
| M2 | 管理配置或环境变量切换 Skill 版本 | **已落地（classpath）**：`campus.ai.runtime-llm` + 环境变量；**`system_kv`**：`ai.runtime-llm.therapy-resource-prefix`、`ai.runtime-llm.diet-system-prompt-resource` 覆盖 YAML。指标 **`ai.generate.therapy.skill_pack.non_baseline`**、**`ai.diet.skill_set.custom`** 可区分非默认包。**后续**：对象存储/配置中心热拉取。 |
| M3 | 第二场景（如 `AiDietService`）复用公共 Skill | **已落地**：膳食与食疗共用 **`shared/core-compliance@1`**；膳食正文拆为 **`core.identity@1`** + **`output.json-only@1`**（锚点路径回退旧单文件）；食疗菜谱目录前缀拆为 **`context.recipe-catalog@1`**（无则回退 **`system-prompt-part-b`**）。**后续**：短版 Skill、合规再拆细等。 |
| M4（可选） | Tool / 检索与 Skill 文档联动 | 限定场景试点，完整可观测与熔断 |

---

## 10. 风险与注意事项

- **上下文膨胀**：Skill 过多会挤占用户输入与 catalog；需 **token 预算** 与「短版 Skill」策略。
- **一致性**：JSON 与 Markdown 两段输出仍需与现有 `mergeAndValidate` / 治理逻辑一致，避免「Skill 写了字段、代码校验不认」。
- **合规**：安全与免责声明类 Skill 变更应走**评审与版本记录**，不宜仅热修字符串。
- **多上游差异**：若同时支持 Ollama / 云 API，需验证合并后的 `system` 长度与特殊字符转义。

---

## 11. 小结

当前 AI 链路已具备 **稳定调用客户端与质量治理**，食疗侧 **方案 A + B** 已在 **真实上游调用路径** 上装配完成，**M2（YAML/环境变量 + system_kv）** 与 **M3（共享合规 + 膳食 JSON-only 拆分）** 已接入代码；**prompt 体量**已做进程内计数与摘要派生（**不裁剪** prompt，见 §7）；**`context.recipe-catalog`** 已从 part-b 拆为独立 Skill。下一步高性价比方向：§8 余项（如合规再拆细）、观测稳定后的 **短版 Skill / 真 token 预算策略**；**方案 C** 仅在满足 §6「方案 C 落地门槛」后立项实现。

---

## 12. 排障最短路径（现象 → 配置/指标 → 日志键）

| 现象 / HTTP 业务码 | 优先检查 | 进程内指标（管理端 `runtime-metrics` / `observability-summary`） | 日志 / 代码锚点 |
|--------------------|----------|------------------------------------------------------------------|----------------|
| 前端 **4031**（`ErrorCodes.ACCOUNT_DISABLED`） | 账号是否被禁用、是否需重新登录 | `error.category.biz.4031`、`error.category.auth.disabled` | `JwtAuthFilter`、`GlobalExceptionHandler` |
| 食疗生成走兜底 / 用户感知「模板回复」 | `LLM_URL` / `LLM_HTTP_*`、`LLM_API_KEY`、`LLM_TIMEOUT_MS`；上游可达性 | `ai.generate.upstream.failed` ↑、`ai.therapy.upstream_ok_ratio` ↓（摘要） | `AiTherapyPlanService` / 编排器 **debug**：`skill_set_id`、`system_sha256`（**不含用户原文**） |
| 膳食生成失败或关闭 | 管理端系统开关 `ai_generate_enabled`；同上 LLM 环境 | `ai.diet.disabled`、`ai.diet.upstream.failed`、`ai.diet.upstream_ok_ratio` | `AiDietService` |
| 怀疑 **Skill 文件未加载 / 路径错** | `campus.ai.runtime-llm.*`、**`system_kv`**：`ai.runtime-llm.therapy-resource-prefix`、`ai.runtime-llm.diet-system-prompt-resource` | `ai.generate.therapy.skill_pack.non_baseline`、`ai.diet.skill_set.custom` | `RuntimeLlmSkillLoader`、`RuntimeLlmSkillPathResolver`；加载失败会抛/记录在装配路径 |
| 路由与预期不符（短输入仍走默认等） | **`system_kv`**：`ai.therapy.route.override`；环境 **`AI_THERAPY_ROUTE_OVERRIDE`** / `campus.ai.therapy-route-override` | `ai.generate.therapy.skill_set.default` vs `brief` | `TherapyPlanLlmRoute`、`AiTherapyPlanGenerationOrchestrator` |
| **Prompt 变长 / 费用异常感** | 是否误配非基线 Skill 包、catalog 是否过大 | `ai.generate.therapy.prompt_budget.*`、`ai.diet.prompt_budget.*`；摘要 `*.avg_utf16_content_units` | **debug**：`therapy_plan` / `diet_plan` 行内 `utf16_content_units`、`approx_tokens`（`LlmPromptBudgetHooks`，**不裁剪**） |

更完整的键名与 HTTP 观测见 **`docs/observability-metrics.md`**；原始快照字段见 **`docs/observability-runtime-metrics.md`**。

---

*文档版本：2026-04-16 修订；2026-04-16 二次修订（膳食 identity+json-only、食疗 part-A 细拆、`prompt_budget` 指标与 observability-summary 派生字段，与 `campus-diet-backend` 对齐）· 可与《大模型调用调试说明》及路径2清单中的 AI 相关项一并维护。*
