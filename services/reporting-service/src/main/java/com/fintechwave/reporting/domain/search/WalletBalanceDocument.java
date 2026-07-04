package com.fintechwave.reporting.domain.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Document(indexName = "fintechwave-wallet-balances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceDocument {

    @Id
    private String userId;

    @Field(type = FieldType.Double)
    private BigDecimal balance;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Date)
    private Instant lastUpdatedAt;
}
