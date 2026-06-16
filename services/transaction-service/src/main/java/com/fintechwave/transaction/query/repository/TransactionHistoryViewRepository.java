package com.fintechwave.transaction.query.repository;

import com.fintechwave.transaction.query.entity.TransactionHistoryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionHistoryViewRepository extends MongoRepository<TransactionHistoryView, UUID> {
    List<TransactionHistoryView> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(UUID senderId, UUID receiverId);

    Page<TransactionHistoryView> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(UUID senderId, UUID receiverId,
            Pageable pageable);
}
