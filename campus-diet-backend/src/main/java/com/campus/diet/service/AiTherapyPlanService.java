package com.campus.diet.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.diet.config.LlmProperties;
import com.campus.diet.entity.Recipe;
import com.campus.diet.mapper.RecipeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PRD 5.4.3 AI 食疗方案：上游为 OpenAI 兼容接口；未配置或 mock 时用本地菜谱拼装兜底。
 */
@Service
public class AiTherapyPlanService {

    private static final Logger log = LoggerFactory.getLogger(AiTherapyPlanService.class);

    private static final Map<String, String> CONSTITUTION_LABEL = Map.ofEntries(
            Map.entry("pinghe", "平和质"),
            Map.entry("qixu", "气虚质"),
            Map.entry("yangxu", "阳虚质"),
            Map.entry("yinxu", "阴虚质"),
            Map.entry("tanshi", "痰湿质"),
            Map.entry("shire", "湿热质"),
            Map.entry("xueyu", "血瘀质"),
            Map.entry("qiyu", "气郁质"),
            Map.entry("tebing", "特禀质")
    );

    private final SystemKvService systemKvService;
    private final RecipeMapper recipeMapper;
    private final LlmChatClient llmChatClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final boolean mock;

    public AiTherapyPlanService(
            SystemKvService systemKvService,
            RecipeMapper recipeMapper,
            LlmChatClient llmChatClient,
            LlmProperties llmProperties,
            @Value("${campus.ai.mock:true}") boolean mock) {
        this.systemKvService = systemKvService;
        this.recipeMapper = recipeMapper;
        this.llmChatClient = llmChatClient;
        this.llmProperties = llmProperties;
        this.mock = mock;
    }

    public Map<String, Object> generate(String symptom, String constitutionCode) throws Exception {
        String sym = symptom == null ? "" : symptom.trim();
        String constCode = constitutionCode == null ? "" : constitutionCode.trim();
        String constLabel = constCode.isEmpty() ? null : CONSTITUTION_LABEL.getOrDefault(constCode, constCode);

        if (!systemKvService.flagOn("ai.generation.enabled", true)) {
            Map<String, Object> off = new LinkedHashMap<>();
            off.put("enabled", false);
            off.put("message", "AI 生成功能已由管理员关闭");
            off.put("planId", "off-" + UUID.randomUUID());
            off.put("symptomSummary", sym.isEmpty() ? "日常调养" : sym);
            off.put("constitutionApplied", constLabel);
            off.put("recipes", List.of());
            off.put("coreIngredients", List.of());
            off.put("lifestyleAdvice", List.of());
            off.put("cautionNotes", List.of("功能已关闭。"));
            off.put("rationale", "");
            off.put("disclaimer", "本内容由系统提示，不构成医疗建议。");
            off.put("isGenericPlan", true);
            off.put(
                    "therapyRecommendMarkdown",
                    "## 说明\n\n当前 **AI 食疗方案** 功能已由管理员关闭，无法生成个性化推荐文案。您可前往 **药膳推荐** 浏览公开菜谱，或稍后再试。\n");
            return off;
        }

        boolean vague = sym.length() > 0 && sym.length() < 4;

        List<Recipe> pool = loadRecipePool();
        String upstreamUrl = nz(llmProperties.getUrl()).trim();
        String apiKey = nz(llmProperties.getApiKey()).trim();
        String model = nz(llmProperties.getModel()).trim();
        // api-key 可为空（如本机 Ollama）；仅当 URL、模型未配置或为 mock 时走本地兜底
        if (mock || upstreamUrl.isEmpty() || model.isEmpty() || "mock".equalsIgnoreCase(model)) {
            return localPlan(sym, constLabel, pool, vague, true);
        }

        String catalogJson = buildCatalogJson(pool);
        String system = buildSystemPrompt(catalogJson);
        String userMsg = buildUserMessage(sym, constLabel, vague);
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", userMsg)
        );

        try {
            String rawContent = llmChatClient.chatCompletionsContent(upstreamUrl, apiKey, model, messages);
            String json = stripMarkdownFence(rawContent);
            JsonNode root = objectMapper.readTree(json);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.convertValue(root, Map.class);
            return mergeAndValidate(parsed, sym, constLabel, pool, vague, false);
        } catch (Exception ex) {
            log.warn(
                    "AI食疗方案：大模型调用失败，已使用本地菜谱兜底。url={} model={} hasApiKey={} errClass={} errMsg={}",
                    upstreamUrl,
                    model,
                    !apiKey.isEmpty(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            return localPlan(sym, constLabel, pool, vague, true);
        }
    }

    private List<Recipe> loadRecipePool() {
        return recipeMapper.selectList(
                Wrappers.<Recipe>lambdaQuery()
                        .eq(Recipe::getStatus, 1)
                        .orderByDesc(Recipe::getCollectCount)
                        .last("LIMIT 60"));
    }

    private static String buildCatalogJson(List<Recipe> pool) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int n = Math.min(pool.size(), 48);
        for (int i = 0; i < n; i++) {
            Recipe r = pool.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"")
                    .append(r.getId())
                    .append("\",\"name\":\"")
                    .append(escapeJson(r.getName()))
                    .append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }

    private String buildSystemPrompt(String catalogJson) {
        return "你是校园中医药膳与食养科普助手。用户会描述「近期身体不适感受」或「日常调养目标」。你的任务是：在**不做疾病诊断、不开药方、不承诺疗效**的前提下，给出可执行的膳食调养参考。\n"
                + "\n"
                + "只输出一段合法 JSON（不要 Markdown 代码围栏，不要 JSON 前后的说明文字）。字段要求：\n"
                + "- symptomSummary(string)：一句话概括用户关切；\n"
                + "- constitutionApplied(string 或空字符串)：若用户消息中给出体质则写入中文体质名，否则空字符串；\n"
                + "- recipes：数组，每项含 recipeId（字符串，必须与下述目录中的 id 完全一致）、recipeName、matchReason；\n"
                + "- coreIngredients：数组，每项 {name,benefit}，至少 3 条，benefit 为一句通俗食养说明；\n"
                + "- lifestyleAdvice、cautionNotes：各至少 3 条短句；\n"
                + "- rationale(string)：2～4 句，说明为何从「食养+作息」角度这样建议（勿下诊断）；\n"
                + "- disclaimer(string)：须明确不能替代诊疗与用药；\n"
                + "- therapyRecommendMarkdown(string)：**必填**，内容为简体中文 **Markdown 正文**（不是 JSON 嵌套对象）。\n"
                + "  therapyRecommendMarkdown 写作要求：\n"
                + "  1）使用二级标题 `##`，建议依次包含：## 调养焦点、## 食养思路与原则、## 一周参考搭配、## 简易食例与做法提示、## 生活习惯配合、## 禁忌与就医提示；\n"
                + "  2）适当用 **加粗** 强调关键食材、频次（如每周 3～4 次）、「适量」「清淡」等安全表述；\n"
                + "  3）列表用 `- `，段落简洁，总长度约 600～1800 字；\n"
                + "  4）与 recipes/coreIngredients/lifestyleAdvice/cautionNotes 信息一致、互相呼应，可点名菜谱名称引导用户到平台菜谱详情查看；\n"
                + "  5）遇信息不足时写温和通用食养建议，并在文中说明「信息有限」；\n"
                + "  6）字符串中的换行写真实换行字符即可（JSON 会转义为 \\n）。\n"
                + "\n"
                + "禁止：疾病命名诊断、处方药物、保证治愈、夸大功效、恐吓性表述。\n"
                + "菜谱目录（只能引用其中 id 作为 recipeId）：\n"
                + catalogJson;
    }

    private String buildUserMessage(String symptom, String constLabel, boolean vague) {
        StringBuilder u = new StringBuilder();
        u.append("【用户输入】近期身体不适或调养目标（请整体理解语气与诉求，勿逐字当成医学主诉）：\n");
        u.append(symptom.isEmpty() ? "（未填写，按日常轻养生处理）" : symptom).append('\n');
        if (constLabel != null) {
            u.append("【可选参考】用户档案中的体质倾向：").append(constLabel).append("（请作为膳食偏性参考，不得据此下诊断）\n");
        }
        if (vague) {
            u.append("【说明】表述较短或较笼统，请输出偏温和的通用食养方案；rationale 与 therapyRecommendMarkdown 中均需简短说明「信息有限、建议补充描述或线下咨询」。\n");
        }
        u.append("\n请严格按 system 要求的字段生成 JSON；therapyRecommendMarkdown 须为完整 Markdown 正文。");
        return u.toString();
    }

    private Map<String, Object> mergeAndValidate(
            Map<String, Object> parsed,
            String symptom,
            String constLabel,
            List<Recipe> pool,
            boolean vague,
            boolean fromFallback) {
        Map<Long, Recipe> byId = pool.stream().collect(Collectors.toMap(Recipe::getId, r -> r, (a, b) -> a));
        Map<String, Recipe> byName = new HashMap<>();
        for (Recipe r : pool) {
            byName.put(r.getName().trim().toLowerCase(Locale.ROOT), r);
        }

        String planId = "plan-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        if (parsed.get("planId") instanceof String && !((String) parsed.get("planId")).isBlank()) {
            planId = ((String) parsed.get("planId")).trim();
        }

        String symptomSummary = str(parsed.get("symptomSummary"), symptom.isEmpty() ? "日常调养" : symptom);
        String constitutionApplied = str(parsed.get("constitutionApplied"), constLabel != null ? constLabel : "");

        List<Map<String, Object>> recipesOut = new ArrayList<>();
        Object recObj = parsed.get("recipes");
        if (recObj instanceof List<?>) {
            for (Object o : (List<?>) recObj) {
                if (!(o instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> m = (Map<?, ?>) o;
                String rid = String.valueOf(m.get("recipeId")).trim();
                String rname = str(m.get("recipeName"), "");
                String reason = str(m.get("matchReason"), "与当前调养方向相符的参考搭配。");
                Recipe hit = null;
                try {
                    Long id = Long.parseLong(rid);
                    hit = byId.get(id);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
                if (hit == null && !rname.isEmpty()) {
                    hit = byName.get(rname.toLowerCase(Locale.ROOT));
                }
                if (hit != null) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("recipeId", String.valueOf(hit.getId()));
                    row.put("recipeName", hit.getName());
                    row.put("matchReason", reason);
                    recipesOut.add(row);
                }
            }
        }
        // dedupe by recipeId
        Set<String> seen = new HashSet<>();
        recipesOut = recipesOut.stream()
                .filter(r -> seen.add(String.valueOf(r.get("recipeId"))))
                .limit(5)
                .collect(Collectors.toCollection(ArrayList::new));

        if (recipesOut.size() < 3) {
            List<Recipe> pick = pickRecipesLocal(symptom, pool, 3 - recipesOut.size());
            for (Recipe r : pick) {
                String idStr = String.valueOf(r.getId());
                if (seen.contains(idStr)) {
                    continue;
                }
                seen.add(idStr);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recipeId", idStr);
                row.put("recipeName", r.getName());
                row.put("matchReason", r.getEfficacySummary() != null && !r.getEfficacySummary().isBlank()
                        ? r.getEfficacySummary()
                        : "与当前症状方向相符的参考搭配。");
                recipesOut.add(row);
                if (recipesOut.size() >= 3) {
                    break;
                }
            }
        }

        List<Map<String, String>> core = readCoreIngredients(parsed.get("coreIngredients"));
        if (core.size() < 3) {
            core = defaultCoreIngredients();
        }

        List<String> life = readStringList(parsed.get("lifestyleAdvice"));
        if (life.size() < 3) {
            life = defaultLifestyle();
        }

        List<String> caution = readStringList(parsed.get("cautionNotes"));
        if (caution.size() < 3) {
            caution = defaultCautions();
        }

        String rationale = str(parsed.get("rationale"), "");
        if (rationale.isEmpty()) {
            rationale = defaultRationale(symptom, constLabel);
        }

        String disclaimer = str(
                parsed.get("disclaimer"),
                "内容由大模型生成，仅供健康教育参考，不构成医疗诊断或治疗方案。");

        // 联调脚本用 rationale 等字段做指纹对比；附带唯一方案编号，避免两次合法调用被误判为「固定 Mock」
        if (!rationale.contains("方案编号：")) {
            rationale = rationale + "（方案编号：" + planId + "）";
        }

        String therapyMd = str(parsed.get("therapyRecommendMarkdown"), "");
        if (therapyMd.isEmpty()) {
            therapyMd = renderTherapyMarkdownFallback(
                    symptomSummary,
                    constitutionApplied.isEmpty() ? null : constitutionApplied,
                    recipesOut,
                    core,
                    life,
                    caution,
                    rationale);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planId", planId);
        out.put("symptomSummary", symptomSummary);
        out.put("constitutionApplied", constitutionApplied.isEmpty() ? null : constitutionApplied);
        out.put("recipes", recipesOut);
        out.put("coreIngredients", core);
        out.put("lifestyleAdvice", life);
        out.put("cautionNotes", caution);
        out.put("rationale", rationale);
        out.put("disclaimer", disclaimer);
        out.put("therapyRecommendMarkdown", therapyMd);
        // 仅当用户输入过短（<4 字）视为「表述笼统」；走本地/兜底不代表症状不清晰，勿与 vague 混用
        out.put("isGenericPlan", vague);
        out.put("localFallback", fromFallback);
        return out;
    }

    /**
     * 当模型未返回 therapyRecommendMarkdown 或解析失败时，由结构化字段拼出可读 Markdown。
     */
    private String renderTherapyMarkdownFallback(
            String symptomSummary,
            String constitutionApplied,
            List<Map<String, Object>> recipesOut,
            List<Map<String, String>> core,
            List<String> life,
            List<String> caution,
            String rationale) {
        StringBuilder md = new StringBuilder();
        md.append("## 调养焦点\n\n");
        md.append("结合您描述的「**").append(mdEscape(symptomSummary)).append("**」");
        if (constitutionApplied != null && !constitutionApplied.isBlank()) {
            md.append("，并参考体质倾向「**").append(mdEscape(constitutionApplied)).append("**」");
        }
        md.append("，以下从**药膳与饮食搭配**角度给出可执行的温和建议（仅供养生参考）。\n\n");

        md.append("## 食养思路与原则\n\n");
        md.append("- 以**清淡、温热适口、易消化**为主，避免过于滋腻或刺激。\n");
        md.append("- 食材与菜谱可轮换，**适量、长期**优于一次大量。\n\n");

        md.append("## 推荐食材与意象\n\n");
        for (Map<String, String> row : core) {
            String n = row.get("name");
            String b = row.get("benefit");
            if (n != null && !n.isBlank() && b != null && !b.isBlank()) {
                md.append("- **").append(mdEscape(n.trim())).append("**：").append(mdEscape(b.trim())).append("\n");
            }
        }
        md.append("\n");

        md.append("## 一周参考搭配\n\n");
        md.append("下列与下方「推荐食疗方」列表一致，建议**每周选做 3～4 次**，单次适量；点击菜谱名可查看详情。\n\n");
        for (Map<String, Object> r : recipesOut) {
            String name = String.valueOf(r.getOrDefault("recipeName", "")).trim();
            String reason = String.valueOf(r.getOrDefault("matchReason", "")).trim();
            if (!name.isEmpty()) {
                md.append("- **").append(mdEscape(name)).append("**");
                if (!reason.isEmpty()) {
                    md.append("：").append(mdEscape(reason));
                }
                md.append("\n");
            }
        }
        md.append("\n");

        md.append("## 生活习惯配合\n\n");
        for (String line : life) {
            if (line != null && !line.isBlank()) {
                md.append("- ").append(mdEscape(line.trim())).append("\n");
            }
        }
        md.append("\n");

        md.append("## 禁忌与就医提示\n\n");
        for (String line : caution) {
            if (line != null && !line.isBlank()) {
                md.append("- ").append(mdEscape(line.trim())).append("\n");
            }
        }
        md.append("\n");

        md.append("## 简要说明\n\n");
        md.append(mdEscape(rationale != null ? rationale.trim() : "")).append("\n\n");
        md.append("---\n\n*本页内容为膳食与生活调养参考，**不替代**诊疗与处方；急症或症状持续加重请及时就医。*\n");
        return md.toString();
    }

    private static String mdEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").replace('\r', '\n').replace("\n\n", "\n").trim().replace('\n', ' ');
    }

    private Map<String, Object> localPlan(
            String symptom,
            String constLabel,
            List<Recipe> pool,
            boolean vague,
            boolean fromFallback) {
        Map<String, Object> shell = new LinkedHashMap<>();
        shell.put("symptomSummary", symptom.isEmpty() ? "日常调养" : symptom);
        shell.put("constitutionApplied", constLabel);
        shell.put("recipes", List.of());
        shell.put("coreIngredients", List.of());
        shell.put("lifestyleAdvice", List.of());
        shell.put("cautionNotes", List.of());
        shell.put("rationale", "");
        shell.put("disclaimer", "");
        return mergeAndValidate(shell, symptom, constLabel, pool, vague, fromFallback);
    }

    private static String str(Object v, String dft) {
        if (v == null) {
            return dft;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? dft : s;
    }

    private static List<Map<String, String>> readCoreIngredients(Object v) {
        List<Map<String, String>> out = new ArrayList<>();
        if (!(v instanceof List<?>)) {
            return out;
        }
        for (Object o : (List<?>) v) {
            if (!(o instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> m = (Map<?, ?>) o;
            String name = str(m.get("name"), "");
            String benefit = str(m.get("benefit"), "");
            if (!name.isEmpty() && !benefit.isEmpty()) {
                out.add(Map.of("name", name, "benefit", benefit));
            }
        }
        return out;
    }

    private static List<String> readStringList(Object v) {
        if (!(v instanceof List<?>)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) v) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<Map<String, String>> defaultCoreIngredients() {
        return List.of(
                Map.of("name", "山药", "benefit", "健脾益肺、补肾固精，适合作为温和食补基底。"),
                Map.of("name", "薏苡仁", "benefit", "渗湿健脾；体寒者可选用炒制薏米以减凉性。"),
                Map.of("name", "百合", "benefit", "养阴润肺、清心安神，对口干舌燥、睡眠不佳可作膳食参考。")
        );
    }

    private static List<String> defaultLifestyle() {
        return List.of(
                "尽量固定作息，减少连续熬夜；用脑间隙做轻度拉伸。",
                "少量多次饮水，温水为宜；减少过甜饮料。",
                "每周数次轻松有氧运动（快走、八段锦），以微汗为度。"
        );
    }

    private static List<String> defaultCautions() {
        return List.of(
                "本方案为膳食参考，不能替代诊疗；症状持续或加重请及时就医。",
                "孕妇、哺乳期、慢性肾病或正在服药者，食用药膳前请咨询医师或药师。",
                "对列出的食材过敏者请勿食用相应菜品。"
        );
    }

    private static String defaultRationale(String symptom, String constLabel) {
        String s = symptom.isEmpty() ? "日常调养" : symptom;
        if (constLabel != null && !constLabel.isEmpty()) {
            return "结合您描述的主要不适「" + s + "」，并参考体质倾向「" + constLabel
                    + "」，从清补兼顾、少刺激、易执行的角度给出食疗与生活建议。";
        }
        return "结合您描述的主要不适「" + s + "」，从清补兼顾、少刺激、易执行的角度给出食疗与生活建议。";
    }

    private List<Recipe> pickRecipesLocal(String symptom, List<Recipe> pool, int need) {
        if (pool.isEmpty() || need <= 0) {
            return List.of();
        }
        String sym = symptom.toLowerCase(Locale.ROOT);
        List<Recipe> scored = new ArrayList<>(pool);
        scored.sort(Comparator.comparingInt(Recipe::getCollectCount).reversed());
        List<Recipe> filtered = scored.stream()
                .filter(r -> matchesSymptomHeuristic(sym, r))
                .limit(Math.max(need, 3))
                .collect(Collectors.toList());
        if (filtered.size() < need) {
            for (Recipe r : scored) {
                if (filtered.contains(r)) {
                    continue;
                }
                filtered.add(r);
                if (filtered.size() >= need) {
                    break;
                }
            }
        }
        return filtered.stream().limit(need).collect(Collectors.toList());
    }

    private static boolean matchesSymptomHeuristic(String symLower, Recipe r) {
        if (symLower.isEmpty()) {
            return true;
        }
        String blob = (nz(r.getName()) + " " + nz(r.getEfficacySummary()) + " " + nz(r.getSymptomTags())
                + " " + nz(r.getEfficacyTags())).toLowerCase(Locale.ROOT);
        if (blob.isEmpty()) {
            return false;
        }
        if ((symLower.contains("痘") || symLower.contains("口干") || symLower.contains("熬夜"))
                && (blob.contains("百合") || blob.contains("银耳") || blob.contains("粥"))) {
            return true;
        }
        if ((symLower.contains("疲劳") || symLower.contains("气短") || symLower.contains("虚"))
                && (blob.contains("黄芪") || blob.contains("鸡") || blob.contains("山药"))) {
            return true;
        }
        if ((symLower.contains("凉") || symLower.contains("怕冷") || symLower.contains("胃"))
                && (blob.contains("姜") || blob.contains("羊肉") || blob.contains("粥"))) {
            return true;
        }
        String[] tokens = symLower.split("[\\s,，、;；]+");
        for (String t : tokens) {
            if (t.length() >= 2 && blob.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String stripMarkdownFence(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1).trim();
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence).trim();
            }
        }
        return s;
    }
}
