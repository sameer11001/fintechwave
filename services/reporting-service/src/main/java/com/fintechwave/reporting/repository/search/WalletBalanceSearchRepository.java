package com.fintechwave.reporting.repository.search;

import com.fintechwave.reporting.domain.search.WalletBalanceDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface WalletBalanceSearchRepository extends ElasticsearchRepository<WalletBalanceDocument, String> {
}
