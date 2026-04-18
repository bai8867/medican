package com.campus.diet.service.ai;

import com.campus.diet.config.LlmProperties;
import com.campus.diet.entity.Recipe;
import com.campus.diet.service.RuntimeLlmSkillPathResolver;
import com.campus.diet.service.RuntimeMetricService;
import com.campus.diet.service.SystemKvService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 食疗方案生成编排：功能开关、指标、本地/Mock 分支、上游调用与失败兜底；上游 JSON 解析委托
 * {@link AiTherapyPlanLlmInvocation}；质量门禁委托 {@link AiTherapyPlanQualityGovernance}；
 * 功能关闭时的响应体委托 {@link AiTherapyPlanDisabledResponseFactory}。
 */
@Component
public class AiTherapyPlanGenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiTherapyPlanGenerationOrchestrator.class);

    private final AiTherapyPlanDisabledResponseFactory disabledResponseFactory;
    private final SystemKvService systemKvService;
    private final RuntimeMetricService runtimeMetricService;
    private final AiTherapyPlanRecipePoolLoader recipePoolLoader;
    private final LlmProperties llmProperties;
    private final AiTherapyPlanOutputMerger outputMerger;
    private final AiTherapyPlanQualityGovernance qualityGovernance;
    private final AiTherapyPlanLlmInvocation llmInvocation;
    private final RuntimeLlmSkillPathResolver runtimeLlmSkillPathResolver;

    private final boolean mock;
    private final String therapyRouteOverride;

    public AiTherapyPlanGenerationOrchestrator(
            AiTherapyPlanDisabledResponseFactory disabledResponseFactory,
            SystemKvService systemKvService,
            RuntimeMetricService runtimeMetricService,
            AiTherapyPlanRecipePoolLoader recipePoolLoader,
            RuntimeLlmSkillPathResolver runtimeLlmSkillPathResolver,
            LlmProperties llmProperties,
            AiTherapyPlanOutputMerger outputMerger,
            AiTherapyPlanQualityGovernance qualityGovernance,
            AiTherapyPlanLlmInvocation llmInvocation,
            @Value("${campus.ai.therapy-route-override:}") String therapyRouteOverride,
            @Value("${campus.ai.mock:true}") boolean mock) {
        this.disabledResponseFactory = disabledResponseFactory;
        this.systemKvService = systemKvService;
        this.runtimeMetricService = runtimeMetricService;
        this.recipePoolLoader = recipePoolLoader;
        this.runtimeLlmSkillPathResolver = runtimeLlmSkillPathResolver;
        this.llmProperties = llmProperties;
        this.outputMerger = outputMerger;
        this.qualityGovernance = qualityGovernance;
        this.llmInvocation = llmInvocation;
        this.therapyRouteOverride = therapyRouteOverride == null ? "" : therapyRouteOverride;
        this.mock = mock;
    }

    public Map<String, Object> generate(String symptom, String constitutionCode) throws Exception {
        long started = System.currentTimeMillis();
        runtimeMetricService.increment("ai.generate.total");
        String sym = symptom == null ? "" : symptom.trim();
        String constCode = constitutionCode == null ? "" : constitutionCode.trim();
        String constLabel = AiTherapyPlanConstitutionLabels.labelOrRaw(constCode);

        if (!systemKvService.flagOn("ai.generation.enabled", true)) {
            runtimeMetricService.increment("ai.generate.disabled");
            Map<String, Object> off = disabledResponseFactory.build(sym, constLabel);
            runtimeMetricService.recordCostMs("ai.generate", System.currentTimeMillis() - started);
            return off;
        }

        boolean vague = sym.length() > 0 && sym.length() < 4;

        List<Recipe> pool = recipePoolLoader.loadActivePoolOrderedByCollectDesc();
        String upstreamUrl = nz(llmProperties.getUrl()).trim();
        String apiKey = nz(llmProperties.getApiKey()).trim();
        String model = nz(llmProperties.getModel()).trim();
        if (mock || upstreamUrl.isEmpty() || model.isEmpty() || "mock".equalsIgnoreCase(model)) {
            runtimeMetricService.increment("ai.generate.fallback");
            runtimeMetricService.recordCostMs("ai.generate", System.currentTimeMillis() - started);
            return qualityGovernance.apply(
                    sym,
                    constCode,
                    outputMerger.buildLocalPlan(sym, constLabel, pool, vague, true),
                    true,
                    "local_fallback");
        }

        try {
            TherapyPlanRuntimeSkillPaths skillPaths = runtimeLlmSkillPathResolver.resolveTherapyPaths();
            String catalogJson = AiTherapyPlanLlmPromptBuilder.buildCatalogJson(pool);
            String overrideRaw = resolveTherapyRouteOverrideRaw();
            TherapyPlanLlmRoute route = TherapyPlanLlmSkillAssembler.resolveRoute(overrideRaw, vague);
            TherapyPlanSkillAssembly assembly =
                    TherapyPlanLlmSkillAssembler.assemble(catalogJson, route, skillPaths);
            if (route == TherapyPlanLlmRoute.BRIEF_INPUT) {
                runtimeMetricService.increment("ai.generate.therapy.skill_set.brief");
            } else {
                runtimeMetricService.increment("ai.generate.therapy.skill_set.default");
            }
            if (!skillPaths.isBaselinePack()) {
                runtimeMetricService.increment("ai.generate.therapy.skill_pack.non_baseline");
            }
            log.debug(
                    "therapy_plan LLM skill_set_id={} system_sha256={} therapy_skill_prefix={}",
                    assembly.getSkillSetId(),
                    assembly.getSkillContentSha256Hex(),
                    skillPaths.prefix());
            String userMsg = AiTherapyPlanLlmPromptBuilder.buildUserMessage(sym, constLabel, vague);
            Map<String, Object> parsed = llmInvocation.chatCompletionsAndParseJson(
                    assembly.getSystemPrompt(), userMsg, upstreamUrl, apiKey, model);
            runtimeMetricService.increment("ai.generate.upstream.success");
            Map<String, Object> out = outputMerger.mergeAndValidate(parsed, sym, constLabel, pool, vague, false);
            out = qualityGovernance.apply(sym, constCode, out, false, "upstream");
            runtimeMetricService.recordCostMs("ai.generate", System.currentTimeMillis() - started);
            return out;
        } catch (Exception ex) {
            log.warn(
                    "AI食疗方案：大模型调用失败，已使用本地菜谱兜底。url={} model={} hasApiKey={} errClass={} errMsg={}",
                    upstreamUrl,
                    model,
                    !apiKey.isEmpty(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            runtimeMetricService.increment("ai.generate.upstream.failed");
            runtimeMetricService.recordCostMs("ai.generate", System.currentTimeMillis() - started);
            return qualityGovernance.apply(
                    sym,
                    constCode,
                    outputMerger.buildLocalPlan(sym, constLabel, pool, vague, true),
                    true,
                    "upstream_failed_fallback");
        }
    }

    public Map<String, Object> evaluateOnlyForReplay(String symptom, String constitutionCode, Map<String, Object> output) {
        return qualityGovernance.apply(
                symptom == null ? "" : symptom.trim(),
                constitutionCode == null ? "" : constitutionCode.trim(),
                output,
                false,
                "replay");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * system_kv {@code ai.therapy.route.override} 优先于 {@code campus.ai.therapy-route-override} / 环境变量。
     */
    private String resolveTherapyRouteOverrideRaw() {
        String kv = systemKvService.get("ai.therapy.route.override", "");
        if (kv != null && !kv.trim().isEmpty()) {
            return kv.trim();
        }
        if (!therapyRouteOverride.trim().isEmpty()) {
            return therapyRouteOverride.trim();
        }
        return null;
    }
}
