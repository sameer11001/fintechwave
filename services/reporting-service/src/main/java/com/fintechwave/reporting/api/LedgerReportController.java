package com.fintechwave.reporting.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.reporting.dto.NetFlowResponse;
import com.fintechwave.reporting.dto.ReconciliationStatusResponse;
import com.fintechwave.reporting.dto.RevenueTrendResponse;
import com.fintechwave.reporting.dto.TrialBalanceResponse;
import com.fintechwave.reporting.dto.WalletDistributionResponse;
import com.fintechwave.reporting.service.LedgerReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/ledger")
@RequiredArgsConstructor
public class LedgerReportController {

    private final LedgerReportService ledgerReportService;

    @GetMapping("/revenue/trend")
    public ResponseEntity<ApiResponse<RevenueTrendResponse>> getRevenueTrend(
            @RequestParam(defaultValue = "1M") String period) {
        return ResponseEntity.ok(ApiResponse.success(ledgerReportService.getRevenueTrend(period)));
    }

    @GetMapping("/wallets/distribution")
    public ResponseEntity<ApiResponse<WalletDistributionResponse>> getWalletDistribution() {
        return ResponseEntity.ok(ApiResponse.success(ledgerReportService.getWalletDistribution()));
    }

    @GetMapping("/netflow")
    public ResponseEntity<ApiResponse<NetFlowResponse>> getNetFlow(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success(ledgerReportService.getNetFlow(days)));
    }

    @GetMapping("/reconciliation-status")
    public ResponseEntity<ApiResponse<ReconciliationStatusResponse>> getReconciliationStatus() {
        return ResponseEntity.ok(ApiResponse.success(ledgerReportService.getReconciliationStatus()));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<ApiResponse<TrialBalanceResponse>> getTrialBalance(
            @RequestParam(defaultValue = "1M") String period) {
        return ResponseEntity.ok(ApiResponse.success(ledgerReportService.getTrialBalance(period)));
    }
}
