package com.fintechwave.reporting.service;

import com.fintechwave.reporting.domain.search.TransactionDocument;
import com.fintechwave.reporting.domain.search.UserDocument;
import com.fintechwave.reporting.repository.search.TransactionSearchRepository;
import com.fintechwave.reporting.repository.search.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexingService {

    private final TransactionSearchRepository txSearchRepo;
    private final UserSearchRepository userSearchRepo;

    public void indexTransaction(UUID txId, UUID senderId, UUID receiverId, BigDecimal amount, String currency, String type, String status, Instant occurredAt) {
        TransactionDocument doc = TransactionDocument.builder()
                .id(txId.toString())
                .senderId(senderId != null ? senderId.toString() : null)
                .receiverId(receiverId != null ? receiverId.toString() : null)
                .amount(amount)
                .currency(currency)
                .type(type)
                .status(status)
                .occurredAt(occurredAt)
                .build();
        txSearchRepo.save(doc);
        log.info("Indexed transaction into Elasticsearch: {}", txId);
    }

    public void indexUserRegistration(UUID userId, UUID keycloakId, String email, String firstName, String lastName, String status) {
        UserDocument doc = UserDocument.builder()
                .id(userId.toString())
                .keycloakId(keycloakId.toString())
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .status(status)
                .kycTier("TIER_0")
                .build();
        userSearchRepo.save(doc);
        log.info("Indexed user registration into Elasticsearch: {}", userId);
    }

    public void indexKycUpdate(UUID userId, String kycTier) {
        userSearchRepo.findById(userId.toString()).ifPresent(doc -> {
            doc.setKycTier(kycTier);
            userSearchRepo.save(doc);
            log.info("Updated KYC tier in Elasticsearch for user: {}", userId);
        });
    }
}
