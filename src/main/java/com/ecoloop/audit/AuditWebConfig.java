package com.ecoloop.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Registers {@link AuditLoggingInterceptor} for all handled requests except static assets,
 * actuator, dev, and swagger/openapi endpoints (keeps request logs focused on real traffic).
 */
@Configuration
public class AuditWebConfig implements WebMvcConfigurer {

    private final AuditLoggingInterceptor interceptor;

    public AuditWebConfig(AuditLoggingInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.ico",
                        "/dev/**",
                        "/actuator/**",
                        "/api/dev/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/swagger-resources/**"
                );
    }
}
