package com.fintechwave.ledger.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.ledger.dto.response.WalletResponse;
import com.fintechwave.ledger.query.service.WalletProjectionService;
import com.fintechwave.ledger.service.ILedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger", description = "Wallet balance and account management")
public class LedgerController {

    private final ILedgerService ledgerService;
    private final WalletProjectionService queryService;

    @GetMapping("/wallets/{userId}")
    @Operation(summary = "Get wallet balance for a user")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletBalance(@PathVariable UUID userId) {
        WalletResponse response = queryService.getWalletResponse(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Force authoritative MongoDB reconciliation check")
    public ResponseEntity<ApiResponse<String>> reconcile() {
        ledgerService.reconcile();
        return ResponseEntity
                .ok(ApiResponse.success("Reconciliation PASSED. Asset Float matches User Liabilities perfectly."));
    }

    @PostMapping("/simulate-divergence")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Simulate a ledger balance divergence for testing")
    public ResponseEntity<ApiResponse<String>> simulateDivergence() {
        ledgerService.simulateDivergence();
        return ResponseEntity.ok(ApiResponse
                .success("Divergence simulated. An artificial $2,000 credit was added to the Platform Float."));
    }

}