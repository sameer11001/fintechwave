package com.fintechwave.reporting.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import com.fintechwave.reporting.domain.search.LedgerEntryDocument;
import com.fintechwave.reporting.domain.search.WalletBalanceDocument;
import com.fintechwave.reporting.dto.NetFlowResponse;
import com.fintechwave.reporting.dto.ReconciliationStatusResponse;
import com.fintechwave.reporting.dto.RevenueTrendResponse;
import com.fintechwave.reporting.dto.TrialBalanceResponse;
import com.fintechwave.reporting.dto.AccountBalanceDTO;
import com.fintechwave.reporting.dto.WalletBucket;
import com.fintechwave.reporting.dto.WalletDistributionResponse;
import lombok.RequiredArgsConstructor;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LedgerReportService {

    private final ElasticsearchOperations esOps;

    public RevenueTrendResponse getRevenueTrend(String period) {
        Instant from = calculateFrom(period);
        CalendarInterval calendarInterval = "1M".equals(period) ? CalendarInterval.Week : CalendarInterval.Day;

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .must(m -> m.terms(t -> t.field("accountCode").terms(t2 -> t2.value(List.of(
                                        co.elastic.clients.elasticsearch._types.FieldValue.of("3000"),
                                        co.elastic.clients.elasticsearch._types.FieldValue.of("3001"),
                                        co.elastic.clients.elasticsearch._types.FieldValue.of("3002")
                                )))))
                                .must(m -> m.term(t -> t.field("entryType").value("CREDIT")))
                                .must(m -> m.range(r -> r.date(d -> d.field("occurredAt").gte(from.toString()))))
                        )
                )
                .withAggregation("by_period", Aggregation.of(a -> a
                        .dateHistogram(dh -> dh
                                .field("occurredAt")
                                .calendarInterval(calendarInterval)
                        )
                        .aggregations("revenue_sum", Aggregation.of(sa -> sa
                                .sum(s -> s.field("amount"))))
                ))
                .withMaxResults(0)
                .build();

        SearchHits<LedgerEntryDocument> searchHits = esOps.search(query, LedgerEntryDocument.class);

        List<String> labels = new ArrayList<>();
        List<Double> revenue = new ArrayList<>();

        if (searchHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
            ElasticsearchAggregation eAgg = aggregations.get("by_period");
            if (eAgg != null) {
                Aggregate aggregate = eAgg.aggregation().getAggregate();
                if (aggregate != null && aggregate.isDateHistogram()) {
                    List<DateHistogramBucket> buckets = aggregate.dateHistogram().buckets().array();
                    for (DateHistogramBucket bucket : buckets) {
                        labels.add(bucket.keyAsString());
                        Aggregate sumAgg = bucket.aggregations().get("revenue_sum");
                        double sum = sumAgg != null && sumAgg.isSum() ? sumAgg.sum().value() : 0.0;
                        revenue.add(sum / 1000.0);
                    }
                }
            }
        }

        return new RevenueTrendResponse(period, labels, revenue);
    }

    public WalletDistributionResponse getWalletDistribution() {
        NativeQuery query = NativeQuery.builder()
                .withAggregation("balance_buckets", Aggregation.of(a -> a
                        .range(r -> r.field("balance")
                                .ranges(
                                        AggregationRange.of(ar -> ar.key("$0-$100").from(0.0).to(100.0)),
                                        AggregationRange.of(ar -> ar.key("$100-$1K").from(100.0).to(1000.0)),
                                        AggregationRange.of(ar -> ar.key("$1K-$10K").from(1000.0).to(10000.0)),
                                        AggregationRange.of(ar -> ar.key("$10K+").from(10000.0))
                                )
                        )
                ))
                .withMaxResults(0)
                .build();

        SearchHits<WalletBalanceDocument> searchHits = esOps.search(query, WalletBalanceDocument.class);
        List<WalletBucket> buckets = new ArrayList<>();

        if (searchHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
            ElasticsearchAggregation eAgg = aggregations.get("balance_buckets");
            if (eAgg != null) {
                Aggregate aggregate = eAgg.aggregation().getAggregate();
                if (aggregate != null && aggregate.isRange()) {
                    List<RangeBucket> rangeBuckets = aggregate.range().buckets().array();
                    for (RangeBucket bucket : rangeBuckets) {
                        buckets.add(new WalletBucket(bucket.key(), bucket.docCount()));
                    }
                }
            }
        }

        return new WalletDistributionResponse(buckets);
    }

    public NetFlowResponse getNetFlow(int days) {
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);

        Aggregation cashInAgg = Aggregation.of(a -> a
                .filter(f -> f.bool(b -> b
                        .must(m -> m.term(t -> t.field("accountCode").value("1000")))
                        .must(m -> m.term(t -> t.field("entryType").value("DEBIT")))
                        .must(m -> m.range(r -> r.date(d -> d.field("occurredAt").gte(from.toString()))))
                ))
                .aggregations("daily", Aggregation.of(da -> da
                        .dateHistogram(dh -> dh.field("occurredAt").calendarInterval(CalendarInterval.Day))
                        .aggregations("sum", Aggregation.of(s -> s.sum(sm -> sm.field("amount"))))
                ))
        );

        Aggregation cashOutAgg = Aggregation.of(a -> a
                .filter(f -> f.bool(b -> b
                        .must(m -> m.term(t -> t.field("accountCode").value("1000")))
                        .must(m -> m.term(t -> t.field("entryType").value("CREDIT")))
                        .must(m -> m.range(r -> r.date(d -> d.field("occurredAt").gte(from.toString()))))
                ))
                .aggregations("daily", Aggregation.of(da -> da
                        .dateHistogram(dh -> dh.field("occurredAt").calendarInterval(CalendarInterval.Day))
                        .aggregations("sum", Aggregation.of(s -> s.sum(sm -> sm.field("amount"))))
                ))
        );

        NativeQuery query = NativeQuery.builder()
                .withAggregation("cash_in", cashInAgg)
                .withAggregation("cash_out", cashOutAgg)
                .withMaxResults(0)
                .build();

        SearchHits<LedgerEntryDocument> searchHits = esOps.search(query, LedgerEntryDocument.class);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> cashInList = new ArrayList<>();
        List<BigDecimal> cashOutList = new ArrayList<>();
        List<BigDecimal> netList = new ArrayList<>();

        if (searchHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
            
            // Generate labels for the last 'days' days
            for (int i = days; i >= 0; i--) {
                ZonedDateTime dt = Instant.now().minus(i, ChronoUnit.DAYS).atZone(ZoneId.systemDefault());
                String label = dt.format(DateTimeFormatter.ofPattern("MMM d"));
                labels.add(label);
                
                // Defaults
                cashInList.add(BigDecimal.ZERO);
                cashOutList.add(BigDecimal.ZERO);
                netList.add(BigDecimal.ZERO);
            }

            // Extract cash in
            ElasticsearchAggregation inEAgg = aggregations.get("cash_in");
            if (inEAgg != null) {
                Aggregate inAgg = inEAgg.aggregation().getAggregate();
                if (inAgg != null && inAgg.isFilter()) {
                    Aggregate dailyIn = inAgg.filter().aggregations().get("daily");
                    if (dailyIn != null && dailyIn.isDateHistogram()) {
                        List<DateHistogramBucket> buckets = dailyIn.dateHistogram().buckets().array();
                        for (DateHistogramBucket bucket : buckets) {
                            String key = bucket.keyAsString();
                            ZonedDateTime dt = Instant.parse(key).atZone(ZoneId.systemDefault());
                            String label = dt.format(DateTimeFormatter.ofPattern("MMM d"));
                            int idx = labels.indexOf(label);
                            if (idx >= 0) {
                                Aggregate sumAgg = bucket.aggregations().get("sum");
                                double sum = sumAgg != null && sumAgg.isSum() ? sumAgg.sum().value() : 0.0;
                                cashInList.set(idx, BigDecimal.valueOf(sum));
                            }
                        }
                    }
                }
            }

            // Extract cash out
            ElasticsearchAggregation outEAgg = aggregations.get("cash_out");
            if (outEAgg != null) {
                Aggregate outAgg = outEAgg.aggregation().getAggregate();
                if (outAgg != null && outAgg.isFilter()) {
                    Aggregate dailyOut = outAgg.filter().aggregations().get("daily");
                    if (dailyOut != null && dailyOut.isDateHistogram()) {
                        List<DateHistogramBucket> buckets = dailyOut.dateHistogram().buckets().array();
                        for (DateHistogramBucket bucket : buckets) {
                            String key = bucket.keyAsString();
                            ZonedDateTime dt = Instant.parse(key).atZone(ZoneId.systemDefault());
                            String label = dt.format(DateTimeFormatter.ofPattern("MMM d"));
                            int idx = labels.indexOf(label);
                            if (idx >= 0) {
                                Aggregate sumAgg = bucket.aggregations().get("sum");
                                double sum = sumAgg != null && sumAgg.isSum() ? sumAgg.sum().value() : 0.0;
                                cashOutList.set(idx, BigDecimal.valueOf(sum));
                            }
                        }
                    }
                }
            }
            
            // Calculate net
            for (int i = 0; i < labels.size(); i++) {
                netList.set(i, cashInList.get(i).subtract(cashOutList.get(i)));
            }
        }

        return new NetFlowResponse(labels, cashInList, cashOutList, netList);
    }

    public ReconciliationStatusResponse getReconciliationStatus() {
        // 1. Calculate user liabilities (sum of all wallet balances from read model)
        NativeQuery walletQuery = NativeQuery.builder()
                .withAggregation("total_wallet_balance", Aggregation.of(a -> a
                        .sum(s -> s.field("balance"))))
                .withMaxResults(0)
                .build();

        SearchHits<WalletBalanceDocument> walletHits = esOps.search(walletQuery, WalletBalanceDocument.class);
        BigDecimal userLiabilitiesReadModel = BigDecimal.ZERO;
        if (walletHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) walletHits.getAggregations();
            ElasticsearchAggregation agg = aggregations.get("total_wallet_balance");
            if (agg != null && agg.aggregation().getAggregate().isSum()) {
                userLiabilitiesReadModel = BigDecimal.valueOf(agg.aggregation().getAggregate().sum().value());
            }
        }

        // 2. Calculate platform float balance (1000) and ledger user liabilities (2000)
        Aggregation debitAgg = Aggregation.of(a -> a
                .filter(f -> f.term(t -> t.field("entryType").value("DEBIT")))
                .aggregations("sum_debits", Aggregation.of(s -> s.sum(sm -> sm.field("amount"))))
        );

        Aggregation creditAgg = Aggregation.of(a -> a
                .filter(f -> f.term(t -> t.field("entryType").value("CREDIT")))
                .aggregations("sum_credits", Aggregation.of(s -> s.sum(sm -> sm.field("amount"))))
        );

        NativeQuery ledgerQuery = NativeQuery.builder()
                .withAggregation("by_account", Aggregation.of(a -> a
                        .terms(t -> t.field("accountCode").size(100))
                        .aggregations("debits", debitAgg)
                        .aggregations("credits", creditAgg)
                ))
                .withMaxResults(0)
                .build();

        SearchHits<LedgerEntryDocument> ledgerHits = esOps.search(ledgerQuery, LedgerEntryDocument.class);
        BigDecimal assetFloatBalance = BigDecimal.ZERO;
        BigDecimal ledgerUserLiabilities = BigDecimal.ZERO;

        if (ledgerHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) ledgerHits.getAggregations();
            ElasticsearchAggregation eAgg = aggregations.get("by_account");

            if (eAgg != null && eAgg.aggregation().getAggregate().isSterms()) {
                List<StringTermsBucket> buckets = eAgg.aggregation().getAggregate().sterms().buckets().array();
                for (StringTermsBucket bucket : buckets) {
                    String accountCode = bucket.key().stringValue();
                    BigDecimal debits = BigDecimal.ZERO;
                    BigDecimal credits = BigDecimal.ZERO;

                    Aggregate debitFilter = bucket.aggregations().get("debits");
                    if (debitFilter != null && debitFilter.isFilter()) {
                        Aggregate sumAgg = debitFilter.filter().aggregations().get("sum_debits");
                        if (sumAgg != null && sumAgg.isSum()) {
                            debits = BigDecimal.valueOf(sumAgg.sum().value());
                        }
                    }

                    Aggregate creditFilter = bucket.aggregations().get("credits");
                    if (creditFilter != null && creditFilter.isFilter()) {
                        Aggregate sumAgg = creditFilter.filter().aggregations().get("sum_credits");
                        if (sumAgg != null && sumAgg.isSum()) {
                            credits = BigDecimal.valueOf(sumAgg.sum().value());
                        }
                    }

                    if ("1000".equals(accountCode)) {
                        assetFloatBalance = debits.subtract(credits);
                    } else if ("2000".equals(accountCode)) {
                        ledgerUserLiabilities = credits.subtract(debits);
                    }
                }
            }
        }

        // True divergence checks if the read model matches the ledger source-of-truth for account 2000
        BigDecimal divergenceDiscrepancy = userLiabilitiesReadModel.subtract(ledgerUserLiabilities).abs();
        String reconStatus = divergenceDiscrepancy.compareTo(BigDecimal.ZERO) == 0 ? "HEALTHY" : "DIVERGED";

        return new ReconciliationStatusResponse(
                assetFloatBalance,
                userLiabilitiesReadModel,
                divergenceDiscrepancy,
                reconStatus,
                Instant.now()
        );
    }

    public TrialBalanceResponse getTrialBalance(String period) {
        Instant from = calculateFrom(period);

        Aggregation debitAgg = Aggregation.of(a -> a
                .filter(f -> f.term(t -> t.field("entryType").value("DEBIT")))
                .aggregations("sum_debits", Aggregation.of(s -> s.sum(sm -> sm.field("amount"))))
        );

        Aggregation creditAgg = Aggregation.of(a -> a
                .filter(f -> f.term(t -> t.field("entryType").value("CREDIT")))
                .aggregations("sum_credits", Aggregation.of(s -> s.sum(sm -> sm.field("amount"))))
        );

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.range(r -> r.date(d -> d.field("occurredAt").gte(from.toString()))))
                .withAggregation("by_account", Aggregation.of(a -> a
                        .terms(t -> t.field("accountCode").size(100))
                        .aggregations("debits", debitAgg)
                        .aggregations("credits", creditAgg)
                ))
                .withMaxResults(0)
                .build();

        SearchHits<LedgerEntryDocument> searchHits = esOps.search(query, LedgerEntryDocument.class);

        List<AccountBalanceDTO> accounts = new ArrayList<>();
        BigDecimal totalSystemDebits = BigDecimal.ZERO;
        BigDecimal totalSystemCredits = BigDecimal.ZERO;

        if (searchHits.getAggregations() != null) {
            ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
            ElasticsearchAggregation eAgg = aggregations.get("by_account");
            
            if (eAgg != null && eAgg.aggregation().getAggregate().isSterms()) {
                List<StringTermsBucket> buckets = eAgg.aggregation().getAggregate().sterms().buckets().array();
                for (StringTermsBucket bucket : buckets) {
                    String accountCode = bucket.key().stringValue();
                    String accountName = getAccountName(accountCode);
                    String accountType = getAccountType(accountCode);

                    BigDecimal debits = BigDecimal.ZERO;
                    Aggregate debitFilter = bucket.aggregations().get("debits");
                    if (debitFilter != null && debitFilter.isFilter()) {
                        Aggregate sumAgg = debitFilter.filter().aggregations().get("sum_debits");
                        if (sumAgg != null && sumAgg.isSum()) {
                            debits = BigDecimal.valueOf(sumAgg.sum().value());
                        }
                    }

                    BigDecimal credits = BigDecimal.ZERO;
                    Aggregate creditFilter = bucket.aggregations().get("credits");
                    if (creditFilter != null && creditFilter.isFilter()) {
                        Aggregate sumAgg = creditFilter.filter().aggregations().get("sum_credits");
                        if (sumAgg != null && sumAgg.isSum()) {
                            credits = BigDecimal.valueOf(sumAgg.sum().value());
                        }
                    }

                    BigDecimal balance = calculateBalance(accountType, debits, credits);

                    accounts.add(new AccountBalanceDTO(accountCode, accountName, accountType, debits, credits, balance));
                    
                    totalSystemDebits = totalSystemDebits.add(debits);
                    totalSystemCredits = totalSystemCredits.add(credits);
                }
            }
        }

        boolean isBalanced = totalSystemDebits.compareTo(totalSystemCredits) == 0;
        return new TrialBalanceResponse(accounts, totalSystemDebits, totalSystemCredits, isBalanced);
    }

    private String getAccountName(String accountCode) {
        return switch (accountCode) {
            case "1000" -> "Platform Float";
            case "2000" -> "User Wallets";
            case "3000" -> "Transaction Fees";
            case "3001" -> "Withdrawal Fees";
            case "3002" -> "Deposit Fees";
            default -> "Unknown Account";
        };
    }

    private String getAccountType(String accountCode) {
        if (accountCode.startsWith("1")) return "ASSET";
        if (accountCode.startsWith("2")) return "LIABILITY";
        if (accountCode.startsWith("3")) return "REVENUE";
        if (accountCode.startsWith("4")) return "EXPENSE";
        return "UNKNOWN";
    }

    private BigDecimal calculateBalance(String accountType, BigDecimal debits, BigDecimal credits) {
        if ("ASSET".equals(accountType) || "EXPENSE".equals(accountType)) {
            return debits.subtract(credits);
        } else {
            return credits.subtract(debits);
        }
    }

    private Instant calculateFrom(String period) {
        if ("1W".equalsIgnoreCase(period)) {
            return Instant.now().minus(7, ChronoUnit.DAYS);
        } else if ("3M".equalsIgnoreCase(period)) {
            return Instant.now().minus(90, ChronoUnit.DAYS);
        } else {
            // Default 1M
            return Instant.now().minus(30, ChronoUnit.DAYS);
        }
    }
}
