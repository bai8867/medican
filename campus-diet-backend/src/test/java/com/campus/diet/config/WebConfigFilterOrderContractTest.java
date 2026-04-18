package com.campus.diet.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 与 {@link WebConfig} 中 {@code FilterRegistrationBean#setOrder} 约定对齐：CORS 先于 JWT，避免预检与错误响应缺头。
 */
class WebConfigFilterOrderContractTest {

    @Test
    void corsFilterOrder_isBeforeJwtFilterOrder() {
        assertThat(WebConfig.FILTER_ORDER_CORS).isLessThan(WebConfig.FILTER_ORDER_JWT);
    }
}
