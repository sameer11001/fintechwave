package com.fintechwave.transaction.mapper;

import com.fintechwave.transaction.domain.entity.TransactionRecord;
import com.fintechwave.transaction.domain.enums.TransactionStatus;
import com.fintechwave.transaction.domain.enums.TransactionType;
import com.fintechwave.transaction.dto.response.TransactionResponse;
import com.fintechwave.transaction.query.entity.TransactionHistoryView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = { TransactionType.class, TransactionStatus.class })
public interface TransactionMapper {

    TransactionResponse toResponse(TransactionRecord entity);

    @Mapping(target = "transactionType", expression = "java(TransactionType.valueOf(view.getType()))")
    @Mapping(target = "status", expression = "java(TransactionStatus.valueOf(view.getStatus()))")
    @Mapping(target = "feeAmount", ignore = true)
    @Mapping(target = "stripePaymentIntentId", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    TransactionResponse toResponseQuery(TransactionHistoryView view);
}
