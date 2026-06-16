package com.fintechwave.reporting.repository.search;

import com.fintechwave.reporting.domain.search.UserDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {
    Page<UserDocument> findByKycTier(String kycTier, Pageable pageable);

    Page<UserDocument> findByStatus(String status, Pageable pageable);
}
