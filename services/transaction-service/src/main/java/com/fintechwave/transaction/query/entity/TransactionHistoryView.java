package com.fintechwave.transaction.query.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Document(collection = "transaction_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryView {

    @Id
    private UUID id;

    @Indexed
    private UUID senderId;

    @Indexed
    private UUID receiverId;

    private BigDecimal amount;
    private String currency;
    private String type;
    private String status;
    private String failureReason;

    private Instant createdAt;
    private Instant updatedAt;
}
