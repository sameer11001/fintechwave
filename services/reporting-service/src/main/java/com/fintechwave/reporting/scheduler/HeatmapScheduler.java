package com.fintechwave.reporting.scheduler;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintechwave.reporting.domain.search.TransactionDocument;
import com.fintechwave.reporting.dto.HeatmapResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeatmapScheduler {

    private final ElasticsearchOperations esOps;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY = "reporting:heatmap:7x24";

    @Scheduled(fixedDelay = 15 * 60 * 1000) // every 15 minutes
    public void computeHeatmap() {
        log.info("Heatmap pre-compute starting");

        Instant from = Instant.now().minus(7, ChronoUnit.DAYS);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.range(r -> r.date(d -> d.field("occurredAt").gte(from.toString()))))
                .withAggregation("by_hour", Aggregation.of(a -> a
                        .dateHistogram(dh -> dh.field("occurredAt").calendarInterval(CalendarInterval.Hour))
                        .aggregations("sum_amount", Aggregation.of(sa -> sa.sum(s -> s.field("amount"))))))
                .withMaxResults(0)
                .build();

        SearchHits<TransactionDocument> searchHits = esOps.search(query, TransactionDocument.class);

        double[][] matrix = new double[7][24];

        if (searchHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
            ElasticsearchAggregation eAgg = aggregations.get("by_hour");
            if (eAgg != null) {
                Aggregate aggregate = eAgg.aggregation().getAggregate();
                if (aggregate != null && aggregate.isDateHistogram()) {
                    List<DateHistogramBucket> buckets = aggregate.dateHistogram().buckets().array();
                    for (DateHistogramBucket bucket : buckets) {
                        String key = bucket.keyAsString();
                        ZonedDateTime dt = Instant.parse(key).atZone(ZoneId.systemDefault());

                        // dayOfWeek: 1 (Monday) to 7 (Sunday)
                        int dayOfWeek = dt.getDayOfWeek().getValue() - 1; // 0 for Monday, 6 for Sunday
                        int hourOfDay = dt.getHour();

                        Aggregate sumAgg = bucket.aggregations().get("sum_amount");
                        double sum = sumAgg != null && sumAgg.isSum() ? sumAgg.sum().value() : 0.0;

                        matrix[dayOfWeek][hourOfDay] += sum;
                    }
                }
            }
        }

        HeatmapResponse response = new HeatmapResponse(
                List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                IntStream.range(0, 24).boxed().collect(Collectors.toList()),
                matrix);

        try {
            redisTemplate.opsForValue().set(
                    CACHE_KEY,
                    objectMapper.writeValueAsString(response),
                    Duration.ofMinutes(20) // slightly longer than the schedule
            );
            log.info("Heatmap cached in Redis (key={})", CACHE_KEY);
        } catch (Exception e) {
            log.error("Failed to cache heatmap", e);
        }
    }
}
