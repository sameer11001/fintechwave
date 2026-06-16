package com.fintechwave.ledger.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.ledger.dto.response.WalletResponse;
import com.fintechwave.ledger.query.service.WalletProjectionService;
import com.fintechwave.ledger.service.ILedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/wallets/{userId}")
    @Operation(summary = "Provision a wallet for a user (internal — called after KYCVerified)")
    @PreAuthorize("hasAuthority('SCOPE_internal')")
    public ResponseEntity<ApiResponse<WalletResponse>> provisionWallet(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "JOD") String currency) {

        WalletResponse response = ledgerService.provisionWallet(userId, currency);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}