package com.fintechwave.kyc.repository;

import com.fintechwave.kyc.domain.entity.KycApplication;
import com.fintechwave.kyc.domain.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycApplicationRepository extends JpaRepository<KycApplication, UUID> {

    Optional<KycApplication> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<KycApplication> findAllByStatus(KycStatus status, Pageable pageable);
}
