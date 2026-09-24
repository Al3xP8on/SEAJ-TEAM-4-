package com.neueda.leap.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import com.neueda.leap.interfaces.Closable;
import com.neueda.leap.validators.AccountValidator;
import com.neueda.leap.enums.AccountStatus;

@Entity
@Table(name = "accounts")
public class Account implements Closable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String accountId;
    
    @Column(nullable = false)
    private String name;
    
    @Column
    private String email;
    
    @Column
    private String phone;
    
    @Column(nullable = false)
    private BigDecimal cashBalance;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;
    
    @Column(nullable = false)
    private int version;
    
    @Column(nullable = false, name = "created_on")
    private LocalDateTime createdOn;
    
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    // JPA no-arg constructor (required for Hibernate)
    protected Account() {
    }

    public Account(String accountId, String name, String email, String phone, BigDecimal cashBalance, 
                   AccountStatus status) {
        AccountValidator.validateAccountId(accountId);
        AccountValidator.validatePositiveAmount(cashBalance, "Initial balance");
        
        this.accountId = accountId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.cashBalance = cashBalance;
        this.status = status;
        this.version = 0;
        this.createdOn = LocalDateTime.now();
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
            "Account{id=%d, accountId='%s', name='%s', balance=%s, status=%s, version=%d}",
            id, accountId, name, cashBalance, status, version
        );
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getHolderName() { return name; }  // Backward compatibility
    public BigDecimal getCashBalance() { return cashBalance; }
    public AccountStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public LocalDateTime getCreatedOn() { return createdOn; }

    public void setAccountId(String accountId) {
        AccountValidator.validateAccountId(accountId);
        this.accountId = accountId;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public void setStatus(AccountStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
        updateTimestamp();
    }
}