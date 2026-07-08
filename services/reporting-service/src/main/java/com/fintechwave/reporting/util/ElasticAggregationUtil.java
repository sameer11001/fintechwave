package com.fintechwave.reporting.util;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;

import java.math.BigDecimal;
import java.util.Map;

public final class ElasticAggregationUtil {

    private ElasticAggregationUtil() {
    }

    /**
     * Extracts a {@link BigDecimal} sum from a {@code filter} aggregation bucket.
     *
     * <p>
     * Handles the common pattern: bucket → filter child → sum child.
     * </p>
     *
     * @param bucketAggs the aggregation map from a terms-bucket (e.g.
     *                   {@code bucket.aggregations()})
     * @param filterName the name of the filter aggregation (e.g. {@code "debits"})
     * @param sumName    the name of the nested sum aggregation (e.g.
     *                   {@code "sum_debits"})
     * @return the sum value, or {@link BigDecimal#ZERO} if any node is null or the
     *         wrong type
     */
    public static BigDecimal extractFilteredSum(
            Map<String, Aggregate> bucketAggs,
            String filterName,
            String sumName) {
        Aggregate filterAgg = bucketAggs.get(filterName);
        if (filterAgg != null && filterAgg.isFilter()) {
            Aggregate sumAgg = filterAgg.filter().aggregations().get(sumName);
            if (sumAgg != null && sumAgg.isSum()) {
                return BigDecimal.valueOf(sumAgg.sum().value());
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Safely retrieves a named {@link ElasticsearchAggregation} from the search
     * hits
     * aggregations, returning {@code null} if not present.
     *
     * @param aggregations the top-level aggregations object cast from
     *                     {@code searchHits.getAggregations()}
     * @param name         the aggregation name
     * @return the aggregation, or {@code null}
     */
    public static ElasticsearchAggregation get(ElasticsearchAggregations aggregations, String name) {
        return aggregations.get(name);
    }

    /**
     * Extracts a {@code double} sum from a top-level sum aggregation.
     *
     * @param aggregations the top-level aggregations object
     * @param aggName      the name of the sum aggregation
     * @return the sum value, or {@code 0.0} if not present
     */
    public static double extractTopLevelSum(ElasticsearchAggregations aggregations, String aggName) {
        ElasticsearchAggregation eAgg = aggregations.get(aggName);
        if (eAgg != null && eAgg.aggregation().getAggregate().isSum()) {
            return eAgg.aggregation().getAggregate().sum().value();
        }
        return 0.0;
    }

    /**
     * Extracts a {@link BigDecimal} sum from a top-level sum aggregation.
     *
     * @param aggregations the top-level aggregations object
     * @param aggName      the name of the sum aggregation
     * @return the sum value, or {@link BigDecimal#ZERO} if not present
     */
    public static BigDecimal extractTopLevelSumAsBigDecimal(
            ElasticsearchAggregations aggregations, String aggName) {
        return BigDecimal.valueOf(extractTopLevelSum(aggregations, aggName));
    }
}
