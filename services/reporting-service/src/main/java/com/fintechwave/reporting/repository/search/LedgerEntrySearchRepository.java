package com.fintechwave.reporting.repository.search;

import com.fintechwave.reporting.domain.search.LedgerEntryDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface LedgerEntrySearchRepository extends ElasticsearchRepository<LedgerEntryDocument, String> {
}
