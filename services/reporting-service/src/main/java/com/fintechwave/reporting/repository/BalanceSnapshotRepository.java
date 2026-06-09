package com.fintechwave.reporting.repository;

import com.fintechwave.reporting.domain.entity.BalanceSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BalanceSnapshotRepository extends JpaRepository<BalanceSnapshot, UUID> {
    Page<BalanceSnapshot> findByUserIdOrderBySnapshotAtDesc(UUID userId, Pageable pageable);
}
