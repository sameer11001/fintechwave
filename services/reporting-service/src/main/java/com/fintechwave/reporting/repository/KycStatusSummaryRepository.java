package com.fintechwave.reporting.repository;

import com.fintechwave.reporting.domain.entity.KycStatusSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycStatusSummaryRepository extends JpaRepository<KycStatusSummary, UUID> {
    Optional<KycStatusSummary> findByUserId(UUID userId);
    Page<KycStatusSummary> findByKycStatusOrderByUpdatedAtDesc(String kycStatus, Pageable pageable);
}
