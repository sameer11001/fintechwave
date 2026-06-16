package com.fintechwave.transaction.api;

import com.fintechwave.transaction.dto.request.CashInRequest;
import com.fintechwave.transaction.dto.request.CashOutRequest;
import com.fintechwave.transaction.dto.request.InitiateTransferRequest;
import com.fintechwave.transaction.dto.response.TransactionResponse;
import com.fintechwave.transaction.query.service.TransactionProjectionService;
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
    private final TransactionProjectionService queryService;

    @PostMapping("/p2p")
    public ResponseEntity<TransactionResponse> initiateP2P(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid InitiateTransferRequest request) {
        UUID senderId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.initiateP2PTransfer(senderId, request));
    }

    @PostMapping("/cash-in")
    public ResponseEntity<TransactionResponse> initiateCashIn(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid CashInRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.initiateCashIn(userId, request));
    }

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
        return ResponseEntity.ok(queryService.getUserTransactions(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID callerId = UUID.fromString(jwt.getSubject());

        // TODO: Implement proper authorization checks
        // In a real CQRS system, you might want to enforce that callerId is either
        // sender or receiver
        // The projection service can fetch by id, and then controller can verify
        // ownership,
        // or the projection service can do it. For now, fetch by ID.

        TransactionResponse response = queryService.getTransactionById(id);

        if (!response.senderId().equals(callerId) && !response.receiverId().equals(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(response);
    }
}
