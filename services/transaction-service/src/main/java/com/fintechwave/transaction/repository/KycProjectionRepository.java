package com.fintechwave.transaction.repository;

import com.fintechwave.transaction.domain.entity.KycProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycProjectionRepository extends JpaRepository<KycProjection, UUID> {
    Optional<KycProjection> findByUserId(UUID userId);
}
