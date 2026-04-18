# HTTP API 契约最小规范（路径2 / P1-4）

本文档定义前后端共用的**最小**约定，用于降低字段漂移与兼容风险。细化到具体 DTO 时以 Java `*Dto` / Controller 与前端 `src/api` 为准。

## 1. 传输与格式

- **编码**：UTF-8；请求/响应 `Content-Type: application/json`（文件上传除外）。
- **时间**：ISO-8601 字符串或 Unix 毫秒，**同一资源域内保持一致**；新建接口优先 ISO-8601（含时区或明确为 `Asia/Shanghai` 约定）。
- **布尔**：JSON `true` / `false`，避免 `1`/`0` 混用（历史接口除外）。

## 2. URL 与命名

- **路径前缀**：业务接口统一 `/api/...`；Actuator `/actuator/...` 单独约定，不混入业务前缀。
- **路径风格**：小写 + 连字符 `kebab-case` 或复数资源名；**同一控制器内保持一种风格**，避免同资源多拼法。
- **查询参数**：蛇形 `snake_case`（与 Spring `@RequestParam` 常见写法及现有前端对齐），例如 `page_size`、`constitution_code`。

## 3. 统一响应包

成功与业务失败均使用后端 `ApiResponse` 形态（以代码为准）：

**例外**：运维探活 **`GET /api/health`** 返回裸 JSON `{"status":"ok"}`，**不**使用 `code`/`msg`/`data` 包络（见 `HealthController`）。契约测试：`HealthControllerApiContractTest`。

- `code`：业务码；`0` 或约定值表示成功（与现有前端拦截器一致）。
- `msg`：人类可读说明。
- `data`：载荷；无数据时可为 `null` 或省略（以具体接口文档为准，**新接口应固定一种**）。

分页列表推荐放在 `data` 内：

- `records`：当前页项数组。
- `total`：总条数（若成本过高可文档说明为近似值，**不得静默改语义**）。
- `page` / `page_size` / `has_more`：与 `PageResult` 及现有推荐流对齐。

## 4. 错误与兼容

- **HTTP 状态**：4xx/5xx 与业务 `code` 组合以现有 `GlobalExceptionHandler` 为准；前端 axios 拦截器已依赖该行为，**新增错误类型须补充拦截器或文档**。
- **可选字段**：新增字段须**向后兼容**；删除或改类型须版本化或灰度说明。
- **枚举与编码**：体质、季节等使用**稳定英文编码**（如 `qixu`、`spring`），展示文案由前端或字典接口负责。

## 5. 核心流引用（实现即文档）

| 领域     | 后端入口（示例） | 前端调用（示例） |
|----------|------------------|------------------|
| 推荐卡片 | `CampusSceneController` 推荐 feed 相关 GET | `src/api` 下校园/场景模块 |
| 周历     | 校园周历相关 Controller | `CampusWeeklyCalendar.vue` 使用的 API 模块 |
| 药膳详情 | `RecipePublicController` `GET /api/recipes/{id}` | `RecipeDetail.vue` / `src/api/recipe.ts` |
| 用户资料 | `UserController` `GET/PUT /api/user/profile`、`PUT /api/user/preferences` 等 | `profile.ts` / `userSettings.ts` |
| 认证登录 | `AuthController` `POST /api/auth/login`、`POST /api/auth/register` | `src/api/auth.ts` |
| AI 食疗 / 药膳 AI | `AiDietController` `POST /api/ai/generate`、`POST /api/ai/diet-plan`、`POST /api/ai/feedback` | `src/api/ai.ts` |

变更上述接口时：**同时**更新前端调用处、相关 mock（若有）与本节表格。

**OpenAPI 片段（可选）**：机器可读的最小路径与 `ApiResponse` 包络见同目录 [`api-contract-openapi-snippet.yaml`](./api-contract-openapi-snippet.yaml)（非全量 spec，随核心流迭代补充）。

**可执行契约样例（后端）**：

- `CampusWeeklyCalendarControllerApiContractTest`：校园用户侧周历相关 `GET` 的 `ApiResponse` 与 `data` 形状。
- `FavoriteHistoryControllerApiContractTest`：`/api/user/favorites`、`/api/user/history` 的鉴权与分页/列表包络。
- `SceneTherapyControllerApiContractTest`：`/api/scenes` 列表与 `/api/scenes/{id}/recipes` 的 `ApiResponse` 与 `SceneSolutionDto` 字段。
- `FeedbackApiControllerApiContractTest`：`POST /api/feedback` 校验失败、匿名与登录态下的 `data.ok` 与 `submit` 入参。
- `CampusSceneControllerApiContractTest`：`/api/campus/recipes/recommend-feed`、`/api/campus/scenes`、`/api/campus/scenes/recipes` 的 `ApiResponse` 与分页字段。
- `HealthControllerApiContractTest`：`/health`、`/api/health` 探活形态。
- `UserControllerApiContractTest`：`GET /api/user/profile`、`PUT /api/user/preferences` 的 `ApiResponse` 与关键 `data` 字段。
- `AuthControllerApiContractTest`：`POST /api/auth/login`、`POST /api/auth/register` 的 `ApiResponse` 与 `data.token` / `data.user` 形状。
- `RecipePublicControllerApiContractTest`：`GET /api/recipes/{id}` 成功载荷关键字段、登录态下 `data.favorited`、非法 id 时 `400` + `ApiResponse`。
- `AiDietControllerApiContractTest`：`POST /api/ai/diet-plan`、`POST /api/ai/generate`、`POST /api/ai/feedback` 的 `ApiResponse` 与关键 `data` 字段。
- `AdminCampusWeeklyCalendarControllerApiContractTest`：`GET/PUT /api/admin/campus-weekly-calendar` 的鉴权、`weekMonday` 非法、`days` / `mealsTemplate` 分支与 `saveAdmin` 入参契约。
- `AdminUserControllerApiContractTest`：`GET /api/admin/users` 分页 `PageResult`、`GET /api/admin/users/{id}` 详情 `data` 形状及 401/403/404。
- `AdminRecipeControllerApiContractTest`：`GET /api/admin/recipes` 分页、`GET /api/admin/recipes/{id}` 详情、删除链路的 `ApiResponse` 与关键 `jsonPath`。
- `AdminRecipeControllerPatchStatusWebMvcTest`：`PATCH /api/admin/recipes/{id}/status` 使用独立 `SliceApplication`（`@SpringBootTest` + `@AutoConfigureMockMvc`，不拉起 `CampusDietApplication`）；`RecipeMapper` 通过显式 `MapperFactoryBean` 注册并在内存 H2 上走真实 `lambdaUpdate`（避免 `@MapperScan(basePackageClasses=RecipeMapper)` 扫全包 + `@MockBean` 替换 `RecipeMapper` 导致 MP 无 lambda 列缓存）；`SceneRecipeMapper` / `RuntimeMetricService` 仍 `@MockBean`。
- `AdminDashboardControllerApiContractTest`：`/api/admin/dashboard/overview` 与 `observability-summary` 的鉴权及聚合字段契约。
- `AdminSystemControllerApiContractTest`：`/api/admin/system/flags` 与 `PUT` 开关、`PUT /kv` 的 `ApiResponse` 与 `upsert` 调用。
- `AdminIngredientControllerApiContractTest`：`GET` 分页、`POST` 创建返回实体、`DELETE` 与鉴权。
- `AdminAiQualityControllerApiContractTest`：管理端 AI 质量相关接口的 `ApiResponse` 与关键字段（见测试内注释）。

变更上述 JSON 形状时请同步更新对应测试与运维探活断言。

## 6. PR 自检（摘要）

见仓库 [`.github/pull_request_template.md`](../.github/pull_request_template.md) 中的 API 与类型检查项。
