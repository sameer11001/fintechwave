package com.fintechwave.kyc.api;

import com.fintechwave.kyc.domain.enums.DocumentType;
import com.fintechwave.kyc.dto.request.SubmitKycRequest;
import com.fintechwave.kyc.dto.response.KycApplicationResponse;
import com.fintechwave.kyc.dto.response.KycDocumentResponse;
import com.fintechwave.kyc.query.service.KycProjectionService;
import com.fintechwave.kyc.service.IKycApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Slf4j
public class KycController {

    private final IKycApplicationService kycService;
    private final KycProjectionService queryService;

    @GetMapping("/me")
    public ResponseEntity<KycApplicationResponse> getMyApplication(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(queryService.getMyApplication(userId));
    }

    /**
     * POST /api/v1/kyc/submit
     * Submits the KYC application for review.
     * User must have uploaded at least one document before submitting.
     */
    @PostMapping("/submit")
    public ResponseEntity<KycApplicationResponse> submitApplication(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid SubmitKycRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(kycService.submitApplication(userId, request));
    }

    @PostMapping(value = "/documents/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KycDocumentResponse> uploadDocument(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("documentType") DocumentType documentType,
            @RequestParam("file") MultipartFile file) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(kycService.uploadDocument(userId, documentType, file));
    }

    /**
     * GET /api/v1/kyc/documents
     * Returns all documents for the calling user with pre-signed MinIO URLs (15-min
     * TTL).
     */
    @GetMapping("/documents")
    public ResponseEntity<List<KycDocumentResponse>> getMyDocuments(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(kycService.getMyDocuments(userId));
    }
}
