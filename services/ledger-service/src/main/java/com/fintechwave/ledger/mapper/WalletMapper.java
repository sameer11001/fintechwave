package com.fintechwave.ledger.mapper;

import com.fintechwave.ledger.domain.entity.Account;
import com.fintechwave.ledger.domain.entity.Balance;
import com.fintechwave.ledger.dto.response.WalletResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "ownerId", source = "account.ownerId")
    @Mapping(target = "balance", source = "balance.amount")
    @Mapping(target = "currency", source = "balance.currency")
    @Mapping(target = "createdAt", source = "account.createdAt")
    @Mapping(target = "kycTier", ignore = true) // Ignored for now, enrichment happens later if needed
    WalletResponse toResponse(Account account, Balance balance);
}
