package com.fintechwave.fraud.query.repository;

import com.fintechwave.fraud.query.entity.FraudRiskProfileView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface FraudRiskProfileViewRepository extends MongoRepository<FraudRiskProfileView, UUID> {
}
