package com.fintechwave.ledger.query.repository;

import com.fintechwave.ledger.query.entity.WalletSummaryView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface WalletSummaryViewRepository extends MongoRepository<WalletSummaryView, UUID> {
}
