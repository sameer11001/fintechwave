package com.fintechwave.ledger.repository;

import com.fintechwave.ledger.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByOwnerIdAndAccountCode(UUID ownerId, String accountCode);

    Optional<Account> findByAccountCodeAndOwnerIdIsNull(String accountCode);

    boolean existsByOwnerIdAndAccountCode(UUID ownerId, String accountCode);
}
