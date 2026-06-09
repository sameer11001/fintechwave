package com.fintechwave.gateway.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ResponseTraceFilter implements GlobalFilter {

    private final FilterUtility filterUtility;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            String correlationId = filterUtility.getCorrelationId(exchange.getRequest().getHeaders());

            if (correlationId != null) {
                log.debug("[ResponseTraceFilter] Echoing correlation ID {} back to client", correlationId);
                exchange.getResponse()
                        .getHeaders()
                        .add(FilterUtility.CORRELATION_ID, correlationId);
            }
        }));
    }
}
