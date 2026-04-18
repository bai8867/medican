package com.campus.diet.config;

import com.campus.diet.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Servlet 过滤器链中 order 数值越小越先执行。CORS 必须早于 JWT：预检 OPTIONS 与带鉴权失败体的响应都需先补上 CORS 头。
     *
     * @see Ordered#HIGHEST_PRECEDENCE
     */
    static final int FILTER_ORDER_CORS = Ordered.HIGHEST_PRECEDENCE;

    static final int FILTER_ORDER_JWT = Ordered.HIGHEST_PRECEDENCE + 10;

    private final RequestObservabilityInterceptor requestObservabilityInterceptor;
    private final String allowedOriginPatterns;

    public WebConfig(
            RequestObservabilityInterceptor requestObservabilityInterceptor,
            @Value("${campus.cors.allowed-origin-patterns:http://localhost:11999,http://127.0.0.1:11999,http://localhost:5173,http://127.0.0.1:5173}")
                    String allowedOriginPatterns) {
        this.requestObservabilityInterceptor = requestObservabilityInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(FILTER_ORDER_JWT);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        for (String raw : allowedOriginPatterns.split(",")) {
            String pattern = raw == null ? "" : raw.trim();
            if (!pattern.isEmpty()) {
                config.addAllowedOriginPattern(pattern);
            }
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.setAllowCredentials(true);
        config.addExposedHeader("Authorization");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new CorsFilter(source));
        bean.addUrlPatterns("/*");
        bean.setOrder(FILTER_ORDER_CORS);
        return bean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestObservabilityInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health");
    }
}
