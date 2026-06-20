package com.fintechwave.kyc.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.kyc.domain.entity.*;
import com.fintechwave.kyc.domain.enums.DocumentType;
import com.fintechwave.kyc.domain.enums.KycStatus;
import com.fintechwave.kyc.domain.enums.KycTier;
import com.fintechwave.kyc.dto.request.AdminReviewRequest;
import com.fintechwave.kyc.dto.request.SubmitKycRequest;
import com.fintechwave.kyc.dto.response.KycApplicationResponse;
import com.fintechwave.kyc.dto.response.KycDocumentResponse;
import com.fintechwave.kyc.exception.InvalidKycStateTransitionException;
import com.fintechwave.kyc.exception.KycApplicationNotFoundException;
import com.fintechwave.kyc.repository.*;
import com.fintechwave.kyc.service.IKycApplicationService;
import com.fintechwave.kyc.storage.IDocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fintechwave.events.GenericDomainEvent;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class KycApplicationServiceImpl implements IKycApplicationService {

    private final KycApplicationRepository applicationRepository;
    private final KycDocumentRepository documentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final IDocumentStorageService storageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void createKycShell(UUID userId) {
        if (applicationRepository.existsByUserId(userId)) {
            log.info("KYC shell already exists for userId={} — idempotent skip", userId);
            return;
        }

        KycApplication application = KycApplication.builder()
                .userId(userId)
                .status(KycStatus.PENDING_SUBMISSION)
                .currentTier(KycTier.TIER_0)
                .requestedTier(KycTier.TIER_1)
                .build();
        applicationRepository.save(application);

        publishOutboxEvent(application.getId(), "KYC_APPLICATION", "KYC_CREATED", 1,
                Map.of("userId", userId.toString(),
                        "status", KycStatus.PENDING_SUBMISSION.name(),
                        "currentTier", KycTier.TIER_0.name(),
                        "requestedTier", KycTier.TIER_1.name()));

        log.info("KYC shell created: applicationId={}", application.getId());
    }

    @Override
    public KycApplicationResponse getMyApplication(UUID userId) {
        return KycApplicationResponse.from(
                findByUserId(userId));
    }

    @Override
    @Transactional
    public KycApplicationResponse submitApplication(UUID userId, SubmitKycRequest request) {
        KycApplication app = findByUserId(userId);

        if (app.getStatus() != KycStatus.PENDING_SUBMISSION && app.getStatus() != KycStatus.REJECTED) {
            throw new InvalidKycStateTransitionException(
                    "Application is already in status=" + app.getStatus() + " and cannot be resubmitted");
        }

        app.setStatus(KycStatus.UNDER_REVIEW);
        app.setRequestedTier(request.requestedTier());
        app.setRejectionReason(null);
        applicationRepository.save(app);

        publishOutboxEvent(app.getId(), "KYC_APPLICATION", "KYC_SUBMITTED", 1,
                Map.of("userId", userId.toString(),
                        "requestedTier", request.requestedTier().name()));

        log.info("KYC application submitted: applicationId={} requestedTier={}", app.getId(), request.requestedTier());
        return KycApplicationResponse.from(app);
    }

    @Override
    @Transactional
    public KycDocumentResponse uploadDocument(UUID userId, DocumentType documentType, MultipartFile file) {
        KycApplication app = findByUserId(userId);

        if (app.getStatus() == KycStatus.VERIFIED) {
            throw new InvalidKycStateTransitionException("Documents cannot be added to a verified application");
        }

        if (file.isEmpty()) {
            throw new InvalidKycStateTransitionException("Uploaded file is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
            log.warn("Content-Type not provided for document upload: applicationId={} documentType={} — defaulting to application/octet-stream",
                    app.getId(), documentType);
        }

        IDocumentStorageService.StorageReference ref = storageService.upload(app.getId(), userId, documentType.name(),
                file);

        KycDocument document = KycDocument.builder()
                .application(app)
                .documentType(documentType)
                .storageBucket(ref.bucket())
                .storageKey(ref.objectKey())
                .contentType(contentType)
                .fileSizeBytes(file.getSize())
                .build();
        documentRepository.save(document);

        log.info("Document saved: applicationId={} documentType={}", app.getId(), documentType);
        return KycDocumentResponse.from(document);
    }

    @Override
    public List<KycDocumentResponse> getMyDocuments(UUID userId) {
        KycApplication app = findByUserId(userId);
        return documentRepository.findAllByApplicationId(app.getId()).stream()
                .map(doc -> {
                    String url = storageService.generatePresignedUrl(doc.getStorageBucket(), doc.getStorageKey());
                    return KycDocumentResponse.from(doc, url);
                })
                .collect(Collectors.toList());
    }

    @Override
    public Page<KycApplicationResponse> listApplications(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            KycStatus kycStatus;
            try {
                kycStatus = KycStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidKycStateTransitionException(
                        "Invalid KYC status filter '" + status + "'. Valid values are: " +
                                java.util.Arrays.stream(KycStatus.values())
                                        .map(Enum::name)
                                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            return applicationRepository.findAllByStatus(kycStatus, pageable)
                    .map(KycApplicationResponse::from);
        }
        return applicationRepository.findAll(pageable).map(KycApplicationResponse::from);
    }

    @Override
    public KycApplicationResponse getApplicationById(UUID applicationId) {
        return KycApplicationResponse.from(
                applicationRepository.findById(applicationId)
                        .orElseThrow(
                                () -> new KycApplicationNotFoundException("Application not found: " + applicationId)));
    }

    @Override
    @Transactional
    public KycApplicationResponse reviewApplication(UUID applicationId, UUID reviewerId, AdminReviewRequest request) {
        KycApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new KycApplicationNotFoundException("Application not found: " + applicationId));

        if (app.getStatus() != KycStatus.UNDER_REVIEW) {
            throw new InvalidKycStateTransitionException(
                    "Application must be UNDER_REVIEW to be reviewed. Current status=" + app.getStatus());
        }

        app.setReviewedBy(reviewerId);
        app.setReviewedAt(Instant.now());

        if ("APPROVED".equalsIgnoreCase(request.decision())) {
            app.setStatus(KycStatus.VERIFIED);
            app.setCurrentTier(app.getRequestedTier());

            publishOutboxEvent(app.getId(), "KYC_APPLICATION", "KYC_VERIFIED", 1,
                    Map.of("userId", app.getUserId().toString(),
                            "verifiedTier", app.getCurrentTier().name()));

            log.info("KYC APPROVED: applicationId={} tier={}", applicationId, app.getCurrentTier());

        } else if ("REJECTED".equalsIgnoreCase(request.decision())) {
            if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                throw new InvalidKycStateTransitionException("Rejection reason is required when rejecting");
            }
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason(request.rejectionReason());

            publishOutboxEvent(app.getId(), "KYC_APPLICATION", "KYC_REJECTED", 1,
                    Map.of("userId", app.getUserId().toString(),
                            "rejectionReason", request.rejectionReason()));

            log.info("KYC REJECTED: applicationId={}", applicationId);
        } else {
            throw new InvalidKycStateTransitionException("Invalid decision: " + request.decision()
                    + ". Must be APPROVED or REJECTED");
        }

        applicationRepository.save(app);
        return KycApplicationResponse.from(app);
    }

    private KycApplication findByUserId(UUID userId) {
        return applicationRepository.findByUserId(userId)
                .orElseThrow(() -> new KycApplicationNotFoundException(userId));
    }

    private void publishOutboxEvent(UUID aggregateId, String aggregateType,
            String eventType, int version, Map<String, Object> payload) {
        try {
            GenericDomainEvent domainEvent = new GenericDomainEvent(
                    eventType,
                    version,
                    aggregateId,
                    aggregateType,
                    payload
            );

            String payloadJson = objectMapper.writeValueAsString(domainEvent);
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(domainEvent.getAggregateId())
                    .aggregateType(domainEvent.getAggregateType())
                    .eventType(domainEvent.getEventType())
                    .eventVersion(domainEvent.getEventVersion())
                    .payload(payloadJson)
                    .idempotencyKey(domainEvent.getIdempotencyKey())
                    .published(false)
                    .build();
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to serialize outbox event: eventType={}", eventType, e);
            throw new RuntimeException("Outbox event serialization failed", e);
        }
    }
}
