# Medican 架构优化升级设计（并行双轨）

## 1. 背景与目标

项目当前已形成可运行的前后端一体化业务闭环，但在持续交付与长期可维护性上存在结构性瓶颈：

- 核心模块出现“大文件 + 多职责”趋势，修改与回归成本持续上升。
- 质量护栏不完整，架构演进时的稳定性保障不足。
- 前后端接口契约尚未系统化，联动改造时容易出现隐性破坏。
- 性能治理处于局部优化阶段，缺少纳入迭代节奏的持续治理机制。

本次设计的目标是采用“并行双轨”方式，在同一迭代内同时推进：

1. 质量护栏建设（CI、测试、门禁）。
2. 架构拆分与边界重建（后端分层、前端模块化）。
3. 关键路径性能优化（首屏与核心接口）。

## 2. 范围与约束

### 2.1 范围

- 覆盖 `campus-diet-backend` 与 `tcm-diet-frontend` 两个子系统。
- 允许前后端联动改造。
- 允许小幅接口调整，并同步前端适配。

### 2.2 约束

- 禁止破坏主业务链路：登录、推荐、收藏/浏览、AI 方案、后台管理。
- 接口调整优先“向后兼容”，无法兼容时必须提供迁移与回退方案。
- 每次拆分必须伴随最小测试补齐，禁止“纯重构无验证”。

## 3. 方案对比与选择

### 方案 A：稳定优先（先护栏后拆分）

- 优点：回归风险低、工程稳定性提升快。
- 缺点：架构收益释放慢，重构压力后移。

### 方案 B：结构优先（先拆分后护栏）

- 优点：边界重建见效快，代码结构改善明显。
- 缺点：短期回归风险高、问题定位成本高。

### 方案 C：并行双轨（本次选择）

- 优点：稳定性、结构质量、性能收益同步提升。
- 缺点：对任务拆解与节奏控制要求更高。

选择方案 C，核心原因是项目已进入“可运行但需升级”的阶段，继续单轨推进会拉长治理周期并增加后续技术债利息。

## 4. 目标架构

### 4.1 后端目标分层

从当前“Controller -> 大 Service -> Mapper”演进为：

- API 层：参数校验、鉴权、响应编排。
- Application 层：业务用例编排与事务边界。
- Domain 层：推荐、AI、周历等领域规则。
- Infrastructure 层：数据库、外部 LLM、监控、缓存等实现细节。

原则：业务规则下沉到 Domain，Application 只负责“流程”，Infrastructure 不反向污染 Domain。

### 4.2 前端目标分层

从“页面内直接混合状态、请求、展示逻辑”演进为：

- Views：页面壳与布局。
- Components：可复用 UI 组件。
- Composables / UseCases：页面业务编排与状态转换。
- API Clients：纯 HTTP 访问，不承载业务规则。
- Domain：前端模型、转换器、字段兼容策略。

原则：页面只关注渲染，不直接承载复杂业务流程。

## 5. 文件级拆分蓝图（第一阶段）

### 5.1 后端

- `service/RecipeRecommendService.java` 拆分为：
  - `application/recommend/RecommendApplicationService.java`
  - `domain/recommend/RecommendScoringDomainService.java`
  - `domain/recommend/RecommendFilterDomainService.java`
  - `infrastructure/recommend/RecommendQueryRepository.java`

- `service/AiTherapyPlanService.java` 拆分为：
  - `application/therapy/TherapyPlanApplicationService.java`
  - `domain/therapy/TherapyPromptBuilder.java`
  - `domain/therapy/TherapyResultNormalizer.java`
  - `infrastructure/llm/OpenAiCompatibleClient.java`

- `service/CampusWeeklyCalendarService.java` 拆分为：
  - `application/calendar/WeeklyCalendarApplicationService.java`
  - `domain/calendar/WeeklyCalendarAssembler.java`
  - `domain/calendar/WeeklyCalendarValidationService.java`

### 5.2 前端

- `views/Recommend.vue` 拆分为：
  - `views/recommend/RecommendPage.vue`
  - `components/recommend/RecommendFilterPanel.vue`
  - `components/recommend/RecommendFeedList.vue`
  - `composables/useRecommendPage.js`

- `views/admin/AdminRecipe.vue` 与 `api/adminRecipe.js` 拆分为：
  - `views/admin/recipe/AdminRecipePage.vue`
  - `components/admin/recipe/*`
  - `api/clients/adminRecipeClient.js`
  - `domain/adminRecipe/mapper.js`

- `api/request.js` 演进为：
  - `api/http/client.js`
  - `api/http/interceptors.js`
  - `api/http/gateways/*.js`

## 6. 接口契约升级策略

### 6.1 调整边界

- 允许新增字段与错误结构标准化。
- 避免删除已有字段；必须删除时走兼容窗口与灰度开关。

### 6.2 统一响应壳（渐进启用）

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "uuid",
  "timestamp": 1713225600000
}
```

### 6.3 前端兼容读取

- 客户端优先读取新字段。
- 新字段缺失时回退旧字段。
- 保留旧字段读取至少一个稳定迭代。

## 7. 并行双轨实施节奏（4 周）

### 第 1 周：护栏与基线

- 建立最小 CI：安装依赖、构建、前后端测试、失败阻断。
- 建立高风险路径冒烟：登录、推荐、AI、后台关键接口。
- 完成架构拆分 RFC（本设计）并冻结首批改造范围。

验收标准：

- 主分支每次提交可自动执行并得到明确通过/失败结果。
- 关键链路至少具备可重复的自动化验证命令。

### 第 2 周：后端拆分首批 + 接口契约骨架

- 拆分推荐与 AI 核心用例的 Application/Domain/Infrastructure 边界。
- 引入统一响应壳的兼容实现（不强制一次性全量切换）。
- 补齐对应单测与集成测试最小集。

验收标准：

- 至少 2 个核心服务完成拆分并可通过回归测试。
- 接口变更清单与兼容矩阵可追溯。

### 第 3 周：前端拆分首批 + 性能治理

- 完成推荐页与后台菜谱页首批拆分。
- 引入页面级懒加载与关键组件按需加载。
- API Client 层与页面编排层分离，减少页面耦合。

验收标准：

- 首屏加载指标（体积/耗时）较基线可量化改善。
- 页面拆分后功能行为与既有用例一致。

### 第 4 周：联调稳定 + 灰度切换

- 进行全链路回归与异常场景验证。
- 清理兼容分支中的临时逻辑，保留必要回退开关。
- 输出下一阶段拆分清单与风险回收计划。

验收标准：

- 核心链路通过回归，发布具备可执行回滚路径。
- 第一阶段架构治理目标（护栏+拆分+性能）全部达标。

## 8. 测试与验证策略

- 后端：`mvn test` + 关键服务专项测试。
- 前端：`npm run lint`、`npm run test`、`npm run build`、必要的 `npm run test:e2e`。
- 集成：跨端关键导航与核心接口契约回归。

重点校验：

- 推荐结果可用性（筛选、排序、分页）。
- AI 结果稳定性（上游失败时本地兜底）。
- 权限边界正确性（用户、食堂管理、管理员）。

## 9. 风险与缓解

- 风险：双轨推进导致任务过载。  
  缓解：每周限定“护栏 1 项 + 拆分 1-2 项 + 性能 1 项”。

- 风险：接口小改动引发前端回归。  
  缓解：契约变更清单 + 兼容读取 + 灰度开关。

- 风险：拆分后职责漂移。  
  缓解：以“Application 编排、Domain 规则、Infra 实现”做代码评审门禁。

## 10. 第一批落地任务（执行入口）

1. 建立前后端 CI 门禁最小闭环。
2. 拆分 `RecipeRecommendService` 的评分与过滤能力。
3. 拆分 `Recommend.vue` 为页面壳 + 过滤面板 + 推荐流列表。
4. 为拆分模块补齐最小自动化验证。
5. 提交基线性能对比数据（改造前后）。

---

本设计用于“并行双轨”第一阶段。后续迭代按同样节奏滚动推进，直到核心巨型模块完成边界重建。
