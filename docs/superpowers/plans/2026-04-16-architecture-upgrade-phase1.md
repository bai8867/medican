# Architecture Upgrade Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不破坏现有业务链路的前提下，完成第一批并行双轨改造：CI 门禁增强、后端推荐核心拆分、前端推荐页拆分，并补齐最小自动化验证。

**Architecture:** 采用“护栏 + 拆分 + 性能”并行推进。后端将 `RecipeRecommendService` 的评分与过滤规则下沉到领域服务；前端将 `Recommend.vue` 从“巨型页面”拆为页面壳、组件与 composable；同时保证 CI 对前后端检查可稳定执行并阻断失败。

**Tech Stack:** GitHub Actions, Spring Boot 2.7, JUnit 5, Vue 3, Vite, Element Plus, Node test runner

---

### Task 1: Harden CI Guardrails

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `tcm-diet-frontend/package.json`
- Test: 本地命令验证（frontend + backend）

- [ ] **Step 1: 为前端增加 CI 专用脚本（失败即退出）**

```json
{
  "scripts": {
    "test:ci": "npm run lint && npm run typecheck && npm run test && npm run build"
  }
}
```

- [ ] **Step 2: 为后端增加 CI 参数，避免噪音并稳定输出**

```yaml
- name: Run tests
  run: mvn -B -ntp -Dstyle.color=always test
```

- [ ] **Step 3: 在 CI 中统一调用前端聚合脚本**

```yaml
- name: Frontend quality gate
  run: npm run test:ci
```

- [ ] **Step 4: 增加并发控制避免同分支重复工作流抢占资源**

```yaml
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
```

- [ ] **Step 5: 本地执行前端校验**

Run: `npm --prefix tcm-diet-frontend run test:ci`  
Expected: `lint/typecheck/test/build` 全部通过，退出码 0。

- [ ] **Step 6: 本地执行后端校验**

Run: `mvn -B -ntp -f campus-diet-backend/pom.xml test`  
Expected: `BUILD SUCCESS`。

- [ ] **Step 7: Commit**

```bash
git add .github/workflows/ci.yml tcm-diet-frontend/package.json
git commit -m "chore: harden ci gates for frontend and backend"
```

---

### Task 2: Extract Backend Recommend Domain Services

**Files:**
- Create: `campus-diet-backend/src/main/java/com/campus/diet/domain/recommend/RecommendFilterDomainService.java`
- Create: `campus-diet-backend/src/main/java/com/campus/diet/domain/recommend/RecommendScoringDomainService.java`
- Create: `campus-diet-backend/src/main/java/com/campus/diet/domain/recommend/RecommendReasonBuilder.java`
- Modify: `campus-diet-backend/src/main/java/com/campus/diet/service/RecipeRecommendService.java`
- Test: `campus-diet-backend/src/test/java/com/campus/diet/service/RecipeRecommendServiceTest.java`

- [ ] **Step 1: 先写失败测试，锁定“过滤 + 评分 + 推荐理由”行为**

```java
@Test
void should_build_reason_with_personalized_hybrid_hint() {
    RecommendReasonBuilder builder = new RecommendReasonBuilder();
    String reason = builder.build(0.80, 0.72, 0.30, true, true);
    assertTrue(reason.contains("体质与时令"));
    assertTrue(reason.contains("收藏热度高"));
    assertTrue(reason.contains("行为偏好加权"));
}
```

- [ ] **Step 2: 运行该测试并确认失败**

Run: `mvn -f campus-diet-backend/pom.xml -Dtest=RecipeRecommendServiceTest#should_build_reason_with_personalized_hybrid_hint test`  
Expected: FAIL（类/方法尚不存在或断言失败）。

- [ ] **Step 3: 创建推荐理由构建器并实现可测试逻辑**

```java
public class RecommendReasonBuilder {
    public String build(double matchScore, double popularityScore, double behaviorBoost, boolean personalized, boolean useHybrid) {
        StringBuilder sb = new StringBuilder();
        if (personalized) {
            sb.append(matchScore >= 0.75 ? "体质与时令匹配度高；" : "体质与时令整体匹配；");
        } else {
            sb.append("当前按时令与热度排序；");
        }
        if (popularityScore >= 0.7) sb.append("收藏热度高。");
        else if (popularityScore >= 0.4) sb.append("近期热度稳定。");
        else sb.append("适合作为补充调养。");
        if (useHybrid && behaviorBoost > 0.01) sb.append("结合你的近期行为偏好加权。");
        return sb.toString();
    }
}
```

- [ ] **Step 4: 创建过滤与评分领域服务（保持现有算法不变）**

```java
public class RecommendFilterDomainService {
    public List<Recipe> applySceneAndKeywordFilter(List<Recipe> source, String sceneTag, String keyword) {
        // 提取自 RecipeRecommendService 的过滤逻辑，不改变语义
        return source;
    }
}
```

```java
public class RecommendScoringDomainService {
    public double score(double matchWeight, double popularityWeight, double behaviorWeight,
                        double match, double popularity, double behaviorBoost, boolean useHybrid) {
        double base = matchWeight * match + popularityWeight * popularity;
        return base + (useHybrid ? behaviorWeight * behaviorBoost : 0.0);
    }
}
```

- [ ] **Step 5: 在 `RecipeRecommendService` 中改为委托新领域服务**

```java
double totalScore = scoringDomainService.score(
        matchWeight, popularityWeight, behaviorWeight, match, pop, behaviorBoost, useHybrid);
String reason = reasonBuilder.build(match, pop, behaviorBoost, personalized, useHybrid);
scored.add(new ScoredRecipe(r, totalScore, reason));
```

- [ ] **Step 6: 运行后端相关测试验证拆分未改变行为**

Run: `mvn -B -ntp -f campus-diet-backend/pom.xml test`  
Expected: `BUILD SUCCESS` 且推荐相关测试通过。

- [ ] **Step 7: Commit**

```bash
git add campus-diet-backend/src/main/java/com/campus/diet/domain/recommend \
        campus-diet-backend/src/main/java/com/campus/diet/service/RecipeRecommendService.java \
        campus-diet-backend/src/test/java/com/campus/diet/service/RecipeRecommendServiceTest.java
git commit -m "refactor: extract recommend filtering and scoring domain services"
```

---

### Task 3: Split Recommend View into Page + Composable + Components

**Files:**
- Create: `tcm-diet-frontend/src/views/recommend/RecommendPage.vue`
- Create: `tcm-diet-frontend/src/components/recommend/RecommendFilterPanel.vue`
- Create: `tcm-diet-frontend/src/components/recommend/RecommendFeedList.vue`
- Create: `tcm-diet-frontend/src/composables/useRecommendPage.js`
- Modify: `tcm-diet-frontend/src/views/Recommend.vue`（作为兼容入口或路由重定向壳）
- Test: `tcm-diet-frontend/src/composables/useRecommendFeedFilters.test.js`（必要时补用例）

- [ ] **Step 1: 先写一个失败测试，锁定筛选面板变更会触发 reload**

```js
test('changing effect filter triggers reload callback', () => {
  let calls = 0
  const onReload = () => { calls += 1 }
  const panel = createRecommendFilterPanelModel({ onReload })
  panel.setEffectFilter('补气')
  assert.equal(calls, 1)
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm --prefix tcm-diet-frontend run test -- src/composables/useRecommendFeedFilters.test.js`  
Expected: FAIL（模型函数尚未抽离或测试未满足）。

- [ ] **Step 3: 抽离 `useRecommendPage` 承载状态与副作用**

```js
export function useRecommendPage(deps) {
  const effectFilter = ref(loadEfficacyPreference())
  const constitutionFilter = ref(loadConstitutionFilterPreference())
  const sortBy = ref(loadSortPreference())
  const recipePool = ref([])
  async function reload() {
    // 从原 Recommend.vue 迁移的 loadPool 主流程
  }
  return { effectFilter, constitutionFilter, sortBy, recipePool, reload }
}
```

- [ ] **Step 4: 抽离筛选面板组件，只负责输入与事件抛出**

```vue
<template>
  <div class="toolbar page-card">
    <!-- 功效、体质、排序、搜索输入 -->
  </div>
</template>
<script setup>
defineProps({ effectFilter: String, constitutionFilter: String, sortBy: String })
const emit = defineEmits(['update:effectFilter', 'update:constitutionFilter', 'update:sortBy', 'search'])
</script>
```

- [ ] **Step 5: 抽离推荐流展示组件，统一接收 `visibleSlice` 与事件回调**

```vue
<template>
  <div class="masonry">
    <template v-for="(item, idx) in visibleSlice" :key="item.kind === 'ai' ? item.id : item.recipe.id + '-' + idx">
      <RecipeCard v-if="item.kind === 'recipe'" :recipe="item.recipe" @patch-collect="$emit('patchCollect', $event)" />
      <button v-else type="button" class="ai-tile page-card" @click="$emit('goAi')">...</button>
    </template>
  </div>
</template>
```

- [ ] **Step 6: 用 `RecommendPage.vue` 作为页面壳拼装 composable + 子组件**

```vue
<script setup>
import { useRecommendPage } from '@/composables/useRecommendPage'
import RecommendFilterPanel from '@/components/recommend/RecommendFilterPanel.vue'
import RecommendFeedList from '@/components/recommend/RecommendFeedList.vue'
const vm = useRecommendPage()
</script>
```

- [ ] **Step 7: 将原 `views/Recommend.vue` 改为兼容壳并复用新页面**

```vue
<template>
  <RecommendPage />
</template>
<script setup>
import RecommendPage from './recommend/RecommendPage.vue'
</script>
```

- [ ] **Step 8: 运行前端验证**

Run: `npm --prefix tcm-diet-frontend run test:ci`  
Expected: 前端 lint/test/build 全部通过。

- [ ] **Step 9: Commit**

```bash
git add tcm-diet-frontend/src/views/Recommend.vue \
        tcm-diet-frontend/src/views/recommend/RecommendPage.vue \
        tcm-diet-frontend/src/components/recommend/RecommendFilterPanel.vue \
        tcm-diet-frontend/src/components/recommend/RecommendFeedList.vue \
        tcm-diet-frontend/src/composables/useRecommendPage.js
git commit -m "refactor: split recommend page into composable and focused components"
```

---

### Task 4: Phase-1 Verification and Risk Gates

**Files:**
- Modify: `docs/superpowers/specs/2026-04-16-architecture-upgrade-design.md`（记录实际偏差）
- Create: `docs/superpowers/specs/2026-04-16-architecture-upgrade-phase1-verification.md`
- Test: 前后端全量命令 + 关键链路冒烟

- [ ] **Step 1: 记录实际改造与设计偏差**

```md
## Deviation Log
- Planned: full extract of weekly calendar service
- Actual: deferred to phase 2 to keep blast radius controlled
```

- [ ] **Step 2: 形成阶段验收文档（命令、结果、风险）**

```md
## Verification Commands
- npm --prefix tcm-diet-frontend run test:ci
- mvn -B -ntp -f campus-diet-backend/pom.xml test
```

- [ ] **Step 3: 执行全量验证**

Run: `npm --prefix tcm-diet-frontend run test:ci && mvn -B -ntp -f campus-diet-backend/pom.xml test`  
Expected: 两端命令全部通过，退出码 0。

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-04-16-architecture-upgrade-design.md \
        docs/superpowers/specs/2026-04-16-architecture-upgrade-phase1-verification.md
git commit -m "docs: capture phase-1 architecture upgrade verification"
```

---

## Rollback Plan

- 后端拆分保留单一入口 `RecipeRecommendService`，如异常可回退为原内联逻辑。
- 前端路由仍保持 `Recommend.vue` 入口，必要时将其切回旧实现。
- CI 配置若误伤可通过单文件回滚 `.github/workflows/ci.yml` 恢复。

## Definition of Done

- CI 能稳定阻断前后端质量失败。
- 推荐服务评分/过滤逻辑完成域服务拆分，行为不变。
- 推荐页完成页面壳 + composable + 组件拆分，交互不退化。
- 验证文档明确记录命令、结果、剩余风险。
