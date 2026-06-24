package com.fintechwave.core.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Slf4j
public class KafkaMdcEnrichmentAspect {

    /**
     * Wraps every @KafkaListener method to:
     * 1. Generate a correlation_id for the processing session.
     * 2. Always clean up MDC after the method exits.
     */
    @Around("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public Object enrichMdcForKafkaConsumer(ProceedingJoinPoint pjp) throws Throwable {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlation_id", correlationId);
        try {
            return pjp.proceed();
        } finally {
            MDC.clear();
        }
    }
}
