package com.neueda.leap;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/*Account domain model following SOLID principles.*/
public class Account implements Tradeable, Closable {
    // Fields
    private Long id;
    private String accountId;
    private String holderName;
    private BigDecimal cashBalance;
    private AccountStatus status;
    private int version;
    private LocalDateTime lastUpdated;

    // Constructors
    public Account(String accountId, String holderName, BigDecimal cashBalance, 
                   AccountStatus status) {
        AccountValidator.validateAccountId(accountId);
        AccountValidator.validateHolderName(holderName);
        AccountValidator.validatePositiveAmount(cashBalance, "Initial balance");
        
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
        this.version = 0;
        this.lastUpdated = LocalDateTime.now();
    }

    // Trading Operations (Tradeable Interface)
    @Override
    public void debit(BigDecimal amount) {
        this.cashBalance = TransactionProcessor.processDebit(this.cashBalance, amount);
        updateTimestamp();
    }

    @Override
    public void credit(BigDecimal amount) {
        this.cashBalance = TransactionProcessor.processCredit(this.cashBalance, amount);
        updateTimestamp();
    }

    @Override
    public boolean canAfford(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return this.cashBalance.compareTo(amount) >= 0;
    }

    @Override
    public boolean isValidForTrading() {
        return isActive() && this.cashBalance.compareTo(BigDecimal.ZERO) >= 0;
    }

    // Account Status Checks
    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    // User-Friendly Trading Operations
    public void deposit(BigDecimal amount) {
        AccountValidator.validatePositiveAmount(amount, "Deposit");
        credit(amount);
    }

    public void withdraw(BigDecimal amount) {
        AccountValidator.validatePositiveAmount(amount, "Withdrawal");
        debit(amount);
    }

    // Closable Interface
    @Override
    public void suspendAccount() {
        this.status = AccountStatus.SUSPENDED;
        updateTimestamp();
    }

    @Override
    public void closeAccount() {
        AccountValidator.validateCanClose(this.cashBalance);
        this.status = AccountStatus.CLOSED;
        updateTimestamp();
    }

    // Utility Methods
    private void updateTimestamp() {
        this.lastUpdated = TransactionProcessor.getUpdatedTimestamp();
        this.version = TransactionProcessor.incrementVersion(this.version);
    }

    @Override
    public String toString() {
        return String.format(
            "Account{id=%d, accountId='%s', holder='%s', balance=%s, status=%s, version=%d}",
            id, accountId, holderName, cashBalance, status, version
        );
    }

    // Getters (Public Read Access)
    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public AccountStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }

    // Setters - Limited to Non-Core Fields
    public void setAccountId(String accountId) {
        AccountValidator.validateAccountId(accountId);
        this.accountId = accountId;
    }
    
    public void setHolderName(String holderName) {
        AccountValidator.validateHolderName(holderName);
        this.holderName = holderName;
    }
    
    public void setStatus(AccountStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
        updateTimestamp();
    }
}
