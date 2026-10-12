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
import java.util.UUID;

@Service
public class AccountService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private AccountValidator validator;

    public AccountResponse createAccount(AccountInput input) throws DuplicateAccountException {
        if (accountRepository.findByUsername(input.username()).isPresent()) {
            throw new DuplicateAccountException("Username already taken: " + input.username());
        }
        if (input.email() != null && accountRepository.findByEmail(input.email()).isPresent()) {
            throw new DuplicateAccountException("Email already in use: " + input.email());
        }
        if (input.phone() != null && accountRepository.findByPhone(input.phone()).isPresent()) {
            throw new DuplicateAccountException("Phone number already in use: " + input.phone());
        }

        String accountId = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Account account = new Account(
            accountId, input.username(), input.password(),
            input.email(), input.phone(), AccountStatus.ACTIVE
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

    public Account getAccountByUsername(String username) throws AccountNotFoundException {
        Account account = accountRepository.findByUsername(username).orElse(null);
        if (account == null) {
            throw new AccountNotFoundException("Account with username " + username + " not found");
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

    public void deleteAccount(Long accountId) throws AccountNotFoundException {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException("Account with ID " + accountId + " not found");
        }
        accountRepository.deleteById(accountId);
    }

    public void addAccount(Account account) {
        accountRepository.save(account);
    }

    public boolean accountExists(Long accountId) {
        return accountRepository.existsById(accountId);
    }
}
