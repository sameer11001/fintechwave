package com.fintechwave.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.reporting.domain.search.TransactionDocument;
import com.fintechwave.reporting.domain.search.WalletBalanceDocument;
import com.fintechwave.reporting.dto.DashboardSummaryResponse;
import com.fintechwave.reporting.dto.HeatmapResponse;
import com.fintechwave.reporting.dto.KycSummaryResponse;
import com.fintechwave.reporting.scheduler.HeatmapScheduler;
import com.fintechwave.reporting.scheduler.HeatmapScheduler;
import com.fintechwave.core.cache.RedisCounterUtil;
import com.fintechwave.reporting.util.ElasticAggregationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardReportService {

    private final StringRedisTemplate redisTemplate;
    private final ElasticsearchOperations esOps;
    private final ObjectMapper objectMapper;
    private final HeatmapScheduler heatmapScheduler;

    public DashboardSummaryResponse getDashboardSummary() {
        // Get counts from Redis
        long activeUsersCount = RedisCounterUtil.getLong(redisTemplate, "reporting:active_users_count");
        long pendingKycApprovals = RedisCounterUtil.getLong(redisTemplate, "reporting:pending_kyc_count");

        // Get Total AUM from Wallet Balances
        double totalAum = 0.0;
        NativeQuery aumQuery = NativeQuery.builder()
                .withAggregation("total_aum", co.elastic.clients.elasticsearch._types.aggregations.Aggregation.of(a -> a
                        .sum(s -> s.field("balance"))))
                .withMaxResults(0)
                .build();
        SearchHits<WalletBalanceDocument> aumHits = esOps.search(aumQuery, WalletBalanceDocument.class);
        if (aumHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) aumHits.getAggregations();
            totalAum = ElasticAggregationUtil.extractTopLevelSum(aggregations, "total_aum");
        }

        // Get Daily Volume from Transactions
        double dailyVolume = 0.0;
        Instant yesterday = Instant.now().minus(24, ChronoUnit.HOURS);
        NativeQuery volumeQuery = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.term(t -> t.field("status").value("COMPLETED")))
                        .must(m -> m.range(r -> r.date(d -> d.field("occurredAt").gte(yesterday.toString()))))))
                .withAggregation("daily_volume",
                        co.elastic.clients.elasticsearch._types.aggregations.Aggregation.of(a -> a
                                .sum(s -> s.field("amount"))))
                .withMaxResults(0)
                .build();
        SearchHits<TransactionDocument> volumeHits = esOps.search(volumeQuery, TransactionDocument.class);
        if (volumeHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) volumeHits.getAggregations();
            dailyVolume = ElasticAggregationUtil.extractTopLevelSum(aggregations, "daily_volume");
        }

        return new DashboardSummaryResponse(totalAum, dailyVolume, activeUsersCount, pendingKycApprovals);
    }

    public HeatmapResponse getActivityHeatmap() throws Exception {
        String cached = redisTemplate.opsForValue().get("reporting:heatmap:7x24");
        if (cached == null) {
            heatmapScheduler.computeHeatmap();
            cached = redisTemplate.opsForValue().get("reporting:heatmap:7x24");
        }
        if (cached == null) {
            return new HeatmapResponse(List.of(), List.of(), new double[0][0]);
        }
        return objectMapper.readValue(cached, HeatmapResponse.class);
    }

    public KycSummaryResponse getKycSummary() {
        long pending = RedisCounterUtil.getLong(redisTemplate, "reporting:pending_kyc_count");
        long approved = RedisCounterUtil.getLong(redisTemplate, "reporting:kyc_approved_count");
        long rejected = RedisCounterUtil.getLong(redisTemplate, "reporting:kyc_rejected_count");

        return new KycSummaryResponse(pending, approved, rejected);
    }
}
