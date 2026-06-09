package com.fintechwave.kyc.api;

import com.fintechwave.kyc.dto.request.AdminReviewRequest;
import com.fintechwave.kyc.dto.response.KycApplicationResponse;
import com.fintechwave.kyc.service.IKycApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin KYC management controller.
 * All endpoints require ROLE_ADMIN (enforced at filter chain + method
 * security).
 */
@RestController
@RequestMapping("/api/v1/admin/kyc")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminKycController {

    private final IKycApplicationService kycService;

    /**
     * GET /api/v1/admin/kyc/applications?status=UNDER_REVIEW&page=0&size=20
     * Paginated list of KYC applications filtered by status.
     */
    @GetMapping("/applications")
    public ResponseEntity<Page<KycApplicationResponse>> listApplications(
            @RequestParam(value = "status", required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(kycService.listApplications(status, pageable));
    }

    /**
     * GET /api/v1/admin/kyc/applications/{applicationId}
     * Full application detail including document references.
     */
    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<KycApplicationResponse> getApplication(
            @PathVariable("applicationId") UUID applicationId) {
        return ResponseEntity.ok(kycService.getApplicationById(applicationId));
    }

    /**
     * POST /api/v1/admin/kyc/applications/{applicationId}/review
     * Approve or reject a KYC application.
     * COMPLIANCE GATE: Approval triggers KYCVerified event → wallet provisioning.
     */
    @PostMapping("/applications/{applicationId}/review")
    public ResponseEntity<KycApplicationResponse> reviewApplication(
            @PathVariable("applicationId") UUID applicationId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid AdminReviewRequest request) {
        UUID reviewerId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(kycService.reviewApplication(applicationId, reviewerId, request));
    }
}
