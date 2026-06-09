package com.fintechwave.transaction.api;

import com.fintechwave.transaction.dto.request.CashInRequest;
import com.fintechwave.transaction.dto.request.CashOutRequest;
import com.fintechwave.transaction.dto.request.InitiateTransferRequest;
import com.fintechwave.transaction.dto.response.TransactionResponse;
import com.fintechwave.transaction.service.ITransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final ITransactionService transactionService;

    /**
     * POST /api/v1/transactions/p2p
     * Initiates a wallet-to-wallet P2P transfer.
     */
    @PostMapping("/p2p")
    public ResponseEntity<TransactionResponse> initiateP2P(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid InitiateTransferRequest request) {
        UUID senderId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.initiateP2PTransfer(senderId, request));
    }

    /**
     * POST /api/v1/transactions/cash-in
     * Creates a Stripe Payment Intent for card funding.
     * The client must confirm the returned paymentIntentId using Stripe.js.
     */
    @PostMapping("/cash-in")
    public ResponseEntity<TransactionResponse> initiateCashIn(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CashInRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.initiateCashIn(userId, request));
    }

    /**
     * POST /api/v1/transactions/cash-out
     * Initiates a Stripe Instant Payout to the user's saved card.
     */
    @PostMapping("/cash-out")
    public ResponseEntity<TransactionResponse> initiateCashOut(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CashOutRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.initiateCashOut(userId, request));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(transactionService.getMyTransactions(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(transactionService.getTransactionById(id, callerId));
    }
}
