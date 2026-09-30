package com.neueda.leap.repositories;

import com.neueda.leap.models.Account;
import com.neueda.leap.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountId(String accountId);
    List<Account> findByStatus(AccountStatus status);
    List<Account> findByName(String name);
    List<Account> findByStatusOrderByLastUpdatedDesc(AccountStatus status);
}