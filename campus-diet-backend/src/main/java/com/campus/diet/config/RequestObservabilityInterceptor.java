package com.campus.diet.config;

import com.campus.diet.service.RuntimeMetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

@Component
public class RequestObservabilityInterceptor implements HandlerInterceptor {

    public static final String ATTR_STARTED_AT = "obs.startedAt";
    public static final String ATTR_BIZ_CODE = "obs.bizCode";

    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityInterceptor.class);

    private final RuntimeMetricService runtimeMetricService;

    public RequestObservabilityInterceptor(RuntimeMetricService runtimeMetricService) {
        this.runtimeMetricService = runtimeMetricService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_STARTED_AT, System.currentTimeMillis());
        runtimeMetricService.increment("http.request.total");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long startedAt = readLong(request.getAttribute(ATTR_STARTED_AT), System.currentTimeMillis());
        long costMs = Math.max(0L, System.currentTimeMillis() - startedAt);

        String method = safeToken(request.getMethod(), "UNKNOWN");
        String pattern = resolvePathPattern(request);
        int status = response.getStatus();
        int bizCode = readInt(request.getAttribute(ATTR_BIZ_CODE), status);
        String errorCategory = classifyErrorCategory(status, bizCode, ex);

        runtimeMetricService.recordCostMs("http.request.cost", costMs);
        runtimeMetricService.recordCostMs("http.request.cost." + method, costMs);
        runtimeMetricService.recordCostMs("http.request.cost." + method + "." + pattern, costMs);
        runtimeMetricService.increment("http.request.status." + normalizeStatus(status));
        runtimeMetricService.increment("http.request.biz." + normalizeStatus(bizCode));
        runtimeMetricService.increment("http.request.error." + errorCategory);

        log.info(
                "request_observed method={} pattern={} status={} bizCode={} costMs={} category={}",
                method,
                pattern,
                status,
                bizCode,
                costMs,
                errorCategory);
    }

    private static String classifyErrorCategory(int status, int bizCode, Exception ex) {
        if (ex != null || status >= 500 || bizCode >= 500) {
            return "server";
        }
        if (status >= 400 || bizCode >= 400) {
            if (bizCode == 401 || bizCode == 4031) {
                return "auth";
            }
            if (bizCode == 400) {
                return "validation";
            }
            return "client";
        }
        return "none";
    }

    private static String resolvePathPattern(HttpServletRequest request) {
        Object attr = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (attr instanceof String && !((String) attr).isBlank()) {
            return normalizePath((String) attr);
        }
        return normalizePath(request.getRequestURI());
    }

    private static String normalizePath(String rawPath) {
        String clean = rawPath == null ? "unknown" : rawPath.trim();
        if (clean.isEmpty()) {
            return "unknown";
        }
        return clean.replaceAll("[^a-zA-Z0-9/_{}-]", "_")
                .replace('/', '.')
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "")
                .toLowerCase(Locale.ROOT);
    }

    private static long readLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return fallback;
    }

    private static int readInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return fallback;
    }

    private static String safeToken(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeStatus(int status) {
        if (status <= 0) {
            return "unknown";
        }
        return String.valueOf(status);
    }
}
