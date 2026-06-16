package com.fintechwave.kyc.query.repository;

import com.fintechwave.kyc.query.entity.KycApplicationView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.UUID;

public interface KycApplicationViewRepository extends MongoRepository<KycApplicationView, UUID> {
    Optional<KycApplicationView> findByUserId(UUID userId);

    Page<KycApplicationView> findByStatus(String status, Pageable pageable);
}
