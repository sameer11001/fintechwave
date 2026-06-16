package com.fintechwave.reporting.api;

import com.fintechwave.core.web.ApiResponse;
import com.fintechwave.reporting.domain.search.TransactionDocument;
import com.fintechwave.reporting.domain.search.UserDocument;
import com.fintechwave.reporting.repository.search.TransactionSearchRepository;
import com.fintechwave.reporting.repository.search.UserSearchRepository;
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

        @GetMapping("/users/kyc")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Page<UserDocument>>> getUsersByKycTier(
                        @RequestParam String tier,
                        @PageableDefault(size = 20) Pageable pageable) {

                return ResponseEntity.ok(ApiResponse.success(
                                userSearchRepo.findByKycTier(tier, pageable)));
        }

}
