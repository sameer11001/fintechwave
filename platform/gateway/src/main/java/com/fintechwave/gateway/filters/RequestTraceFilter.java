package com.fintechwave.gateway.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RequestTraceFilter implements GlobalFilter {

    private final FilterUtility filterUtility;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
        String correlationId = filterUtility.getCorrelationId(requestHeaders);

        if (correlationId == null) {
            correlationId = generateCorrelationId();
            log.debug("[RequestTraceFilter] Generated new correlation ID: {}", correlationId);
            exchange = filterUtility.setCorrelationId(exchange, correlationId);
        } else {
            log.debug("[RequestTraceFilter] Forwarding with existing correlation ID: {}", correlationId);
        }

        return chain.filter(exchange);
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
