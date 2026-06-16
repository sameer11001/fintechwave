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

@Document(indexName = "fintechwave-transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String senderId;

    @Field(type = FieldType.Keyword)
    private String receiverId;

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Date)
    private java.time.Instant occurredAt;
}
