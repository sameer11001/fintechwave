package com.fintechwave.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@SpringBootApplication
public class GatewayApplication {

        public static void main(String[] args) {
                SpringApplication.run(GatewayApplication.class, args);
        }

        // ─── Route Configuration ───────────────────────────────────────────────────

        @Bean
        public RouteLocator fintechwaveRouteConfig(RouteLocatorBuilder builder) {
                return builder.routes()

                                // ── IAM Service (user registration, auth, profile) ─────────────────
                                .route("user-service", p -> p
                                                .path("/api/v1/users/**", "/api/v1/auth/**", "/api/v1/internal/**")
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString())
                                                                .circuitBreaker(config -> config
                                                                                .setName("iamCircuitBreaker")
                                                                                .setFallbackUri("forward:/fallback/iam")))
                                                .uri("http://user-service:8081"))

                                // ── KYC Service (document upload, verification, state machine) ─────
                                .route("kyc-service", p -> p
                                                .path("/api/v1/kyc/**", "/api/v1/admin/kyc/**")
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString()))
                                                .uri("http://kyc-service:8082"))

                                // ── Ledger Service (double-entry bookkeeping, balance queries) ─────
                                .route("ledger-service-read", p -> p
                                                .path("/api/v1/ledger/**", "/api/v1/accounts/**")
                                                .and()
                                                .method(HttpMethod.GET)
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString())
                                                                .retry(retryConfig -> retryConfig
                                                                                .setRetries(3)
                                                                                .setMethods(HttpMethod.GET)
                                                                                .setBackoff(Duration.ofMillis(100),
                                                                                                Duration.ofMillis(1000),
                                                                                                2, true)))
                                                .uri("http://ledger-service:8083"))

                                .route("ledger-service-write", p -> p
                                                .path("/api/v1/ledger/**", "/api/v1/accounts/**")
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString())
                                                                .circuitBreaker(config -> config
                                                                                .setName("ledgerCircuitBreaker")
                                                                                .setFallbackUri("forward:/fallback/ledger")))
                                                .uri("http://ledger-service:8083"))

                                // ── Transaction Service (initiate transfer, Stripe, state machine) ─
                                .route("transaction-service", p -> p
                                                .path("/api/v1/transactions/**", "/api/v1/webhooks/**")
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString())
                                                                .requestRateLimiter(config -> config
                                                                                .setRateLimiter(redisRateLimiter())
                                                                                .setKeyResolver(userKeyResolver()))
                                                                .circuitBreaker(config -> config
                                                                                .setName("transactionCircuitBreaker")
                                                                                .setFallbackUri("forward:/fallback/transactions")))
                                                .uri("http://transaction-service:8084"))

                                // ── Fraud Service (admin/internal — rate limited + circuit breaker) ─
                                .route("fraud-service", p -> p
                                                .path("/api/v1/fraud/**")
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString())
                                                                .circuitBreaker(config -> config
                                                                                .setName("fraudCircuitBreaker")
                                                                                .setFallbackUri("forward:/fallback/fraud")))
                                                .uri("http://fraud-service:8085"))

                                // ── Notification Service ───────────────────────────────────────────
                                .route("notification-service", p -> p
                                                .path("/api/v1/notifications/**")
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString())
                                                                .circuitBreaker(config -> config
                                                                                .setName("notificationCircuitBreaker")
                                                                                .setFallbackUri("forward:/fallback/notifications")))
                                                .uri("http://notification-service:8086"))

                                // ── Reporting Service ──────────────────────────────────────────────
                                .route("reporting-service", p -> p
                                                .path("/api/v1/reports/**")
                                                .filters(f -> f
                                                                .addResponseHeader("X-Response-Time",
                                                                                LocalDateTime.now().toString())
                                                                .retry(retryConfig -> retryConfig
                                                                                .setRetries(3)
                                                                                .setMethods(HttpMethod.GET)
                                                                                .setBackoff(Duration.ofMillis(100),
                                                                                                Duration.ofMillis(1000),
                                                                                                2, true))
                                                                .circuitBreaker(config -> config
                                                                                .setName("reportingCircuitBreaker")
                                                                                .setFallbackUri("forward:/fallback/reports")))
                                                .uri("http://reporting-service:8087"))
                                .build();
        }

        @Bean
        public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
                return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                                .timeLimiterConfig(TimeLimiterConfig.custom()
                                                .timeoutDuration(Duration.ofSeconds(10))
                                                .build())
                                .build());
        }

        @Bean
        public RedisRateLimiter redisRateLimiter() {
                return new RedisRateLimiter(10, 20, 1);
        }

        @Bean
        public KeyResolver userKeyResolver() {
                return exchange -> Mono.justOrEmpty(
                                exchange.getRequest().getHeaders().getFirst("X-User-Id"))
                                .defaultIfEmpty("anonymous");
        }
}
