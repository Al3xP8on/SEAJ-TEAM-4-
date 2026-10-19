package com.neueda.leap.repositories;

import com.neueda.leap.models.AccountStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, Long> {
    List<AccountStatusHistory> findByAccount_IdOrderByChangedOnDesc(Long accountId);
}