package com.neueda.leap.services;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.models.Positions;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.validators.AccountValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {
    
    private final Map<Long, Account> accountStore = new HashMap<>();
    
    @Autowired
    private AccountValidator validator;

    public Account getAccount(Long accountId) throws AccountNotFoundException {
        Account account = accountStore.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException("Account with ID " + accountId + " not found");
        }
        return account;
    }

    public BigDecimal getBalance(Long accountId) throws AccountNotFoundException {
        Account account = getAccount(accountId);
        return account.getCashBalance();
    }

    public Positions getPositions(Long accountId) throws AccountNotFoundException {
        Account account = getAccount(accountId);
        throw new UnsupportedOperationException("Positions retrieval not yet implemented - requires database integration");
    }

    public OrderHistory getOrders(Long accountId) throws AccountNotFoundException {
        Account account = getAccount(accountId);
        return new OrderHistory();
    }

    public void addAccount(Account account) {
        if (account.getId() != null) {
            accountStore.put(account.getId(), account);
        }
    }

    public boolean accountExists(Long accountId) {
        return accountStore.containsKey(accountId);
    }
}
