package com.fintechwave.reporting.repository.search;

import com.fintechwave.reporting.domain.search.TransactionDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface TransactionSearchRepository extends ElasticsearchRepository<TransactionDocument, String> {
    Page<TransactionDocument> findBySenderIdOrReceiverId(String senderId, String receiverId, Pageable pageable);

    Page<TransactionDocument> findByStatus(String status, Pageable pageable);
}
