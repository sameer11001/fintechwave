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

@Document(indexName = "fintechwave-ledger-entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryDocument {

    @Id
    private String id;                    // entryLine idempotencyKey as string

    @Field(type = FieldType.Keyword)
    private String transactionId;

    @Field(type = FieldType.Keyword)
    private String accountCode;           // "1000", "2000", "3000", "3001", "3002" ...

    @Field(type = FieldType.Keyword)
    private String accountType;           // "ASSET" | "LIABILITY" | "REVENUE" | "EXPENSE"

    @Field(type = FieldType.Keyword)
    private String entryType;             // "DEBIT" | "CREDIT"

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private String description;

    @Field(type = FieldType.Date)
    private Instant occurredAt;           // set to Instant.now() at index time
}
