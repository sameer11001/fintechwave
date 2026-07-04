package com.fintechwave.reporting.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.reporting.domain.search.TransactionDocument;
import com.fintechwave.reporting.domain.search.UserDocument;
import com.fintechwave.reporting.dto.DashboardSummaryResponse;
import com.fintechwave.reporting.dto.HeatmapResponse;
import com.fintechwave.reporting.dto.KycSummaryResponse;
import com.fintechwave.reporting.repository.search.TransactionSearchRepository;
import com.fintechwave.reporting.repository.search.UserSearchRepository;
import com.fintechwave.reporting.service.DashboardReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final TransactionSearchRepository txSearchRepo;
    private final UserSearchRepository userSearchRepo;
    private final DashboardReportService dashboardReportService;

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionDocument>>> getTransactionHistory(
            @RequestParam String userId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                txSearchRepo.findBySenderIdOrReceiverId(userId, userId, pageable)));
    }

    @GetMapping("/transactions/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionDocument>>> getTransactionsByStatus(
            @RequestParam String status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                txSearchRepo.findByStatus(status, pageable)));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserDocument>>> getUsers(
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                userSearchRepo.findAll(pageable)));
    }

    @GetMapping("/users/kyc")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserDocument>>> getUsersByKycTier(
            @RequestParam String tier,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                userSearchRepo.findByKycTier(tier, pageable)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardReportService.getDashboardSummary()));
    }

    @GetMapping("/activity/heatmap")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<HeatmapResponse>> getActivityHeatmap() throws Exception {
        return ResponseEntity.ok(ApiResponse.success(dashboardReportService.getActivityHeatmap()));
    }

    @GetMapping("/kyc/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<KycSummaryResponse>> getKycSummary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardReportService.getKycSummary()));
    }

}
