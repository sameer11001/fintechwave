package com.fintechwave.security;

import com.fintechwave.security.config.JwtProperties;
import com.fintechwave.security.filter.JwtAuthFilter;
import com.fintechwave.security.jwt.JwtProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@AutoConfiguration
@ConditionalOnClass(UsernamePasswordAuthenticationFilter.class)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtProvider jwtProvider(JwtProperties jwtProperties) {
        return new JwtProvider(jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthFilter jwtAuthFilter(JwtProvider jwtProvider) {
        return new JwtAuthFilter(jwtProvider);
    }
}
