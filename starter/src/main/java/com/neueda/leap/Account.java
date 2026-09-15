import java.time.LocalDateTime;

public class Account {
    // Fields
    private Long id;
    private String accountId;
    private String holderName;
    private float cashBalance;
    private String status;
    private int version;
    private LocalDateTime lastUpdated;

    // Constructors
    public Account() {
    }

    public Account(String accountId, String holderName, float cashBalance, 
                   String status) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
        this.version = 0;
        this.lastUpdated = LocalDateTime.now();
    }

    // Methods
    public void debit(float amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (cashBalance < amount) {
            throw new IllegalStateException("Insufficient funds");
        }
        this.cashBalance = cashBalance - amount;
        this.lastUpdated = LocalDateTime.now();
        this.version++;
    }

    public void credit(float amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.cashBalance = cashBalance + amount;
        this.lastUpdated = LocalDateTime.now();
        this.version++;
    }

    // Account Status Checks --> need to change for ENUM
    public boolean isActive() {
        
    }

    public boolean isValidForTrading() {
        return isActive() && cashBalance >= 0;
    }

    // Trading Operations
    public boolean canAfford(float amount) {
        if (amount <= 0) {
            return false;
        }
        return cashBalance >= amount;
    }

    public void deposit(float amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        credit(amount);
    }

    public void withdraw(float amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        debit(amount);
    }

    public void suspendAccount() {
      
    }

    public void closeAccount() {
        if (cashBalance > 0) {
            throw new IllegalStateException("Cannot close account with remaining cash balance");
        }
        this.status = AccountStatus.CLOSED;
        updateTimestamp();
    }

    // Utility Methods
    private void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
        this.version++;
    }

    @Override
    public String toString() {
        return String.format(
            "Account{id=%d, accountId='%s', holder='%s', balance=%.2f, status=%s, version=%d}",
            id, accountId, holderName, cashBalance, status, version
        );
    }

    // Getters (Public Read Access)
    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }
    public float getCashBalance() { return cashBalance; }
    public String getStatus() { return status; }
    public int getVersion() { return version; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }

    // Setters - Limited to Non-Core Fields
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public void setStatus(String status) { 
       //Add status validation here
    }

    // Private Setters - Internal State Only
    private void setId(Long id) { this.id = id; }
    private void setVersion(int version) { this.version = version; }
    private void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    private void setCashBalance(float cashBalance) { this.cashBalance = cashBalance; }
}