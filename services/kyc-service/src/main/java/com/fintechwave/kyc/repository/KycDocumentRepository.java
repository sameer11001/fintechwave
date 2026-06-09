package com.fintechwave.kyc.repository;

import com.fintechwave.kyc.domain.entity.KycDocument;
import com.fintechwave.kyc.domain.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findAllByApplicationId(UUID applicationId);

    Optional<KycDocument> findByApplicationIdAndDocumentType(UUID applicationId, DocumentType documentType);

    boolean existsByApplicationIdAndDocumentType(UUID applicationId, DocumentType documentType);
}
