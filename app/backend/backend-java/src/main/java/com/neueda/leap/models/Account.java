package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.neueda.leap.interfaces.Closable;
import com.neueda.leap.validators.AccountValidator;
import com.neueda.leap.enums.AccountStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
public class Account implements Closable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "account_id", nullable = false, unique = true, length = 50)
    private String accountId;
    
    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;
    
    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;
    
    @Column(name = "version", nullable = false)
    @Version
    private int version;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    // Default constructor for JPA
    public Account() {
    }

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

    @Override
    public void debit(BigDecimal amount) {
        if (this.cashBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        this.cashBalance = this.cashBalance.subtract(amount);
        updateTimestamp();
    }

    @Override
    public void credit(BigDecimal amount) {
        this.cashBalance = this.cashBalance.add(amount);
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

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public void deposit(BigDecimal amount) {
        AccountValidator.validatePositiveAmount(amount, "Deposit");
        credit(amount);
    }

   
    public void withdraw(BigDecimal amount) {
        AccountValidator.validatePositiveAmount(amount, "Withdrawal");
        debit(amount);
    }

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

    private void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
        this.version++;
    }

    @Override
    public String toString() {
        return String.format(
            "Account{id=%d, accountId='%s', holder='%s', balance=%s, status=%s, version=%d}",
            id, accountId, holderName, cashBalance, status, version
        );
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public AccountStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }

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