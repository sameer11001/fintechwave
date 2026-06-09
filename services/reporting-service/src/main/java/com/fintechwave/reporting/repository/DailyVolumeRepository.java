package com.fintechwave.reporting.repository;

import com.fintechwave.reporting.domain.entity.DailyVolume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyVolumeRepository extends JpaRepository<DailyVolume, UUID> {
    Optional<DailyVolume> findByReportDateAndTransactionTypeAndCurrency(
            LocalDate date, String transactionType, String currency);
    List<DailyVolume> findByReportDateBetweenOrderByReportDateDesc(LocalDate from, LocalDate to);
}
