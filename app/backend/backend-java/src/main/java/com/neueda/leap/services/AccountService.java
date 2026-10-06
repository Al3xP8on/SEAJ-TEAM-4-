package com.neueda.leap.services;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.models.Positions;
import com.neueda.leap.dtos.AccountInput;
import com.neueda.leap.dtos.AccountResponse;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateAccountException;
import com.neueda.leap.validators.AccountValidator;
import com.neueda.leap.repositories.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private AccountValidator validator;

    public AccountResponse createAccount(AccountInput input) throws DuplicateAccountException {
        if (accountRepository.findByAccountId(input.accountId()).isPresent()) {
            throw new DuplicateAccountException("Account ID already exists: " + input.accountId());
        }
        if (accountRepository.findByUsername(input.username()).isPresent()) {
            throw new DuplicateAccountException("Username already taken: " + input.username());
        }

        BigDecimal balance = input.cashBalance() != null ? input.cashBalance() : BigDecimal.ZERO;
        Account account = new Account(
            input.accountId(), input.username(), input.password(),
            input.name(), input.email(), input.phone(),
            balance, AccountStatus.ACTIVE
        );
        Account saved = accountRepository.save(account);
        return new AccountResponse(
            saved.getId(), saved.getAccountId(), saved.getName(),
            saved.getCashBalance(), saved.getStatus(),
            Optional.of(saved.getLastUpdated())
        );
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccount(Long accountId) throws AccountNotFoundException {
        Account account = accountRepository.findById(accountId).orElse(null);
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
        accountRepository.save(account);
    }

    public boolean accountExists(Long accountId) {
        return accountRepository.existsById(accountId);
    }
}
