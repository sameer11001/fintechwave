package com.fintechwave.fraud.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.fraud.dto.FraudDecisionResponse;
import com.fintechwave.fraud.service.IFraudService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only endpoint for inspecting fraud decisions.
 */
@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
public class FraudController {

    private final IFraudService fraudService;

    /**
     * Returns paginated fraud decisions for a given user (ADMIN only).
     *
     * GET /api/v1/fraud/decisions?userId=&page=&size=
     */
    @GetMapping("/decisions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<FraudDecisionResponse>>> getUserDecisions(
            @RequestParam UUID userId,
            @PageableDefault(size = 20, sort = "decidedAt") Pageable pageable) {

        Page<FraudDecisionResponse> decisions = fraudService.getUserDecisions(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(decisions));
    }
}
