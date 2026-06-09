package com.fintechwave.reporting.repository;

import com.fintechwave.reporting.domain.entity.FailedTxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FailedTxRateRepository extends JpaRepository<FailedTxRate, UUID> {
    Optional<FailedTxRate> findByReportDate(LocalDate date);
    List<FailedTxRate> findByReportDateBetweenOrderByReportDateDesc(LocalDate from, LocalDate to);
}
