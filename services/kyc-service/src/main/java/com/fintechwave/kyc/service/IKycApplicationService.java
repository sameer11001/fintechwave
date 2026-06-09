package com.fintechwave.kyc.service;

import com.fintechwave.kyc.dto.request.AdminReviewRequest;
import com.fintechwave.kyc.dto.request.SubmitKycRequest;
import com.fintechwave.kyc.dto.response.KycApplicationResponse;
import com.fintechwave.kyc.dto.response.KycDocumentResponse;
import com.fintechwave.kyc.domain.enums.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Core KYC application service contract.
 * All business logic passes through this interface.
 */
public interface IKycApplicationService {

    /**
     * Creates a KYC shell for a newly registered user.
     * Called by the UserRegisteredConsumer — idempotent.
     *
     * @param userId Keycloak user ID from the UserRegistered event
     */
    void createKycShell(UUID userId);

    /**
     * Returns the current KYC application for the calling user.
     *
     * @param userId Keycloak user ID extracted from JWT
     */
    KycApplicationResponse getMyApplication(UUID userId);

    /**
     * Submits the KYC application for admin review.
     * Transitions status from PENDING_SUBMISSION → UNDER_REVIEW.
     * Publishes KYCSubmitted event via outbox.
     *
     * @param userId  Calling user's Keycloak ID
     * @param request Tier being requested
     */
    KycApplicationResponse submitApplication(UUID userId, SubmitKycRequest request);

    /**
     * Uploads a document for the user's current KYC application.
     * Documents are stored in MinIO; only the reference is persisted.
     *
     * @param userId       Calling user's Keycloak ID
     * @param documentType Type of identity document
     * @param file         Uploaded file (JPEG, PNG, PDF)
     * @return Document reference response
     */
    KycDocumentResponse uploadDocument(UUID userId, DocumentType documentType, MultipartFile file);

    /**
     * Returns all documents for the calling user's KYC application.
     * Pre-signed URLs are generated on request.
     *
     * @param userId Calling user's Keycloak ID
     */
    List<KycDocumentResponse> getMyDocuments(UUID userId);

    // ─── Admin operations ─────────────────────────────────────────────────────

    /**
     * Paginated list of applications by status — admin only.
     */
    Page<KycApplicationResponse> listApplications(String status, Pageable pageable);

    /**
     * Full application detail for admin review, including documents with pre-signed URLs.
     *
     * @param applicationId KYC application ID
     */
    KycApplicationResponse getApplicationById(UUID applicationId);

    /**
     * Admin approves or rejects a KYC application.
     * On APPROVED: transitions to VERIFIED, publishes KYCVerified event.
     * On REJECTED:  transitions to REJECTED, publishes KYCRejected event.
     *
     * COMPLIANCE GATE: KYCVerified is the only trigger for wallet provisioning.
     *
     * @param applicationId KYC application ID
     * @param reviewerId    Admin's Keycloak ID
     * @param request       Decision + optional rejection reason
     */
    KycApplicationResponse reviewApplication(UUID applicationId, UUID reviewerId, AdminReviewRequest request);
}
