package com.fintechwave.transaction.service;

import com.fintechwave.transaction.domain.entity.KycProjection;
import com.fintechwave.transaction.domain.enums.KycTier;
import com.fintechwave.transaction.domain.enums.TransactionType;
import com.fintechwave.transaction.exception.InsufficientKycTierException;
import com.fintechwave.transaction.repository.KycProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycPolicyService {

        private static final Map<TransactionType, KycTier> MINIMUM_TIER = Map.of(
                        TransactionType.CASH_IN, KycTier.TIER_1,
                        TransactionType.CASH_OUT, KycTier.TIER_1,
                        TransactionType.P2P, KycTier.TIER_1,
                        TransactionType.BILL_PAY, KycTier.TIER_1);

        private final KycProjectionRepository kycProjectionRepository;

        public void enforce(UUID userId, TransactionType operation) {
                KycProjection projection = kycProjectionRepository.findByUserId(userId)
                                .orElseThrow(() -> new InsufficientKycTierException(
                                                "KYC record not found for user. Please complete identity verification."));

                KycTier required = MINIMUM_TIER.getOrDefault(operation, KycTier.TIER_1);

                if (!projection.getCurrentTier().isAtLeast(required)) {
                        throw new InsufficientKycTierException(
                                        "Operation " + operation + " requires KYC tier " + required +
                                                        ". Your current tier is " + projection.getCurrentTier() + ".");
                }
        }
}
