package com.fintechwave.core.config;

import com.fintechwave.core.web.GlobalExceptionHandler;
import com.fintechwave.core.web.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CoreWebAutoConfiguration {

    /**
     * Registers the unified platform exception handler.
     * Override by declaring a {@code @RestControllerAdvice} bean in the service.
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * Registers the distributed trace ID propagation filter.
     * Override by declaring your own {@link TraceIdFilter} bean.
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdFilter.class)
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
