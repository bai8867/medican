package com.campus.diet.common;

import com.campus.diet.config.RequestObservabilityInterceptor;
import com.campus.diet.service.RuntimeMetricService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void handleBiz_shouldMarkBizCodeAndCounter() {
        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(runtimeMetricService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/recipes/1");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBiz(new BizException(404, "药膳不存在"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getCode());
        assertEquals(404, request.getAttribute(RequestObservabilityInterceptor.ATTR_BIZ_CODE));
        assertEquals(1L, counter(runtimeMetricService, "error.category.biz"));
        assertEquals(1L, counter(runtimeMetricService, "error.category.biz.404"));
    }

    @Test
    void handleBiz_when409_shouldReturnConflict() {
        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(runtimeMetricService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/ingredients");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBiz(new BizException(409, "食材名称已存在"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getCode());
        assertEquals(409, request.getAttribute(RequestObservabilityInterceptor.ATTR_BIZ_CODE));
        assertEquals(1L, counter(runtimeMetricService, "error.category.biz.409"));
    }

    @Test
    void handleOther_shouldRecordUnhandledCounterAndMark500() {
        RuntimeMetricService runtimeMetricService = new RuntimeMetricService();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(runtimeMetricService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/ai/generate");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleOther(new IllegalStateException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getCode());
        assertEquals(500, request.getAttribute(RequestObservabilityInterceptor.ATTR_BIZ_CODE));
        assertEquals(1L, counter(runtimeMetricService, "error.category.unhandled"));
    }

    private static long counter(RuntimeMetricService runtimeMetricService, String key) {
        Object counters = runtimeMetricService.snapshot().get("counters");
        if (!(counters instanceof Map<?, ?>)) {
            return 0L;
        }
        Object value = ((Map<?, ?>) counters).get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
