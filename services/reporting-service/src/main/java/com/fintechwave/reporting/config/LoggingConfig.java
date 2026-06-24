package com.fintechwave.reporting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration(proxyBeanMethods = false)
public class LoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter logFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain filterChain) throws jakarta.servlet.ServletException, java.io.IOException {
                String path = request.getRequestURI();
                if (path != null && path.contains("/actuator/health")) {
                    filterChain.doFilter(request, response);
                    if (response.getStatus() >= 400) {
                        logger.warn("Health check failed with status: " + response.getStatus());
                    }
                    return;
                }
                super.doFilterInternal(request, response, filterChain);
            }
        };
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(10000);
        filter.setIncludeHeaders(false);
        filter.setAfterMessagePrefix("REQUEST DATA: ");
        return filter;
    }
}
