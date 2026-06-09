package com.fintechwave.reporting.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.reporting.domain.entity.*;
import com.fintechwave.reporting.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporting API — all endpoints ADMIN-only or user-scoped.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final TransactionSummaryRepository txSummaryRepo;
    private final DailyVolumeRepository dailyVolumeRepo;
    private final BalanceSnapshotRepository balanceSnapshotRepo;
    private final KycStatusSummaryRepository kycStatusRepo;
    private final FailedTxRateRepository failedTxRateRepo;

    /**
     * Paginated transaction history for a user.
     * GET /api/v1/reports/transactions?userId=&page=&size=
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionSummary>>> getTransactionHistory(
            @RequestParam UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                txSummaryRepo.findByUserIdOrderByOccurredAtDesc(userId, pageable)));
    }

    /**
     * Daily volume aggregate for admin dashboard.
     * GET /api/v1/reports/daily-volume?from=&to=
     */
    @GetMapping("/daily-volume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DailyVolume>>> getDailyVolume(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.success(
                dailyVolumeRepo.findByReportDateBetweenOrderByReportDateDesc(from, to)));
    }

    /**
     * Balance history for a user.
     * GET /api/v1/reports/balance-history?userId=&page=&size=
     */
    @GetMapping("/balance-history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<BalanceSnapshot>>> getBalanceHistory(
            @RequestParam UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                balanceSnapshotRepo.findByUserIdOrderBySnapshotAtDesc(userId, pageable)));
    }

    /**
     * KYC pipeline overview — paginated by status.
     * GET /api/v1/reports/kyc-status?status=PENDING&page=&size=
     */
    @GetMapping("/kyc-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<KycStatusSummary>>> getKycStatus(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                kycStatusRepo.findByKycStatusOrderByUpdatedAtDesc(status, pageable)));
    }

    /**
     * Daily failed transaction rate — for alerting dashboards.
     * GET /api/v1/reports/failed-tx-rate?from=&to=
     */
    @GetMapping("/failed-tx-rate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<FailedTxRate>>> getFailedTxRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.success(
                failedTxRateRepo.findByReportDateBetweenOrderByReportDateDesc(from, to)));
    }
}
