package com.fintechwave.ledger.repository;

import com.fintechwave.ledger.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByOwnerIdAndAccountCode(UUID ownerId, String accountCode);

    Optional<Account> findByAccountCodeAndOwnerIdIsNull(String accountCode);

    boolean existsByOwnerIdAndAccountCode(UUID ownerId, String accountCode);
}
