package com.campus.diet.service.ai;

import java.util.List;
import java.util.Map;

/**
 * LLM 请求体量观测：仅用于日志与进程内指标，**不**裁剪或改写 prompt。
 */
public final class LlmPromptBudgetHooks {

    private LlmPromptBudgetHooks() {
    }

    /** 对 {@code messages} 中各条 {@code content} 的 Java {@link String#length()} 求和（UTF-16 代码单元，与常见 tokenizer 近似相关）。 */
    public static int sumMessageUtf16Units(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Map<String, String> m : messages) {
            if (m == null) {
                continue;
            }
            String c = m.get("content");
            if (c != null) {
                n += c.length();
            }
        }
        return n;
    }

    /**
     * 粗算 token 上界（chars/4），便于与无 tokenizer 时的看板对齐；不可用于计费或硬裁剪。
     */
    public static int approxTokensHeuristic(int utf16Units) {
        if (utf16Units <= 0) {
            return 0;
        }
        return (utf16Units + 3) / 4;
    }
}
