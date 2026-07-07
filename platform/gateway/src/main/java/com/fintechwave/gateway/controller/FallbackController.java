package com.fintechwave.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/iam")
    public Mono<ResponseEntity<Map<String, String>>> iamFallback() {
        return createFallbackResponse("IAM Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/kyc")
    public Mono<ResponseEntity<Map<String, String>>> kycFallback() {
        return createFallbackResponse("KYC Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/ledger")
    public Mono<ResponseEntity<Map<String, String>>> ledgerFallback() {
        return createFallbackResponse("Ledger Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/transactions")
    public Mono<ResponseEntity<Map<String, String>>> transactionsFallback() {
        return createFallbackResponse("Transaction Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/fraud")
    public Mono<ResponseEntity<Map<String, String>>> fraudFallback() {
        return createFallbackResponse("Fraud Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/notifications")
    public Mono<ResponseEntity<Map<String, String>>> notificationsFallback() {
        return createFallbackResponse("Notification Service is currently unavailable. Please try again later.");
    }

    @RequestMapping("/reports")
    public Mono<ResponseEntity<Map<String, String>>> reportsFallback() {
        return createFallbackResponse("Reporting Service is currently unavailable. Please try again later.");
    }

    private Mono<ResponseEntity<Map<String, String>>> createFallbackResponse(String message) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Service Unavailable", "message", message)));
    }
}
