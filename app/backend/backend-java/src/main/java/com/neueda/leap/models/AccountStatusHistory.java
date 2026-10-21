package com.neueda.leap.models;

import com.neueda.leap.enums.AccountStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_status_history")
public class AccountStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "admin_id", nullable = false)
    private Account administrator;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", nullable = false)
    private AccountStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false)
    private AccountStatus newStatus;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "changed_on", nullable = false) private LocalDateTime changedOn;

    protected AccountStatusHistory() {}

    public AccountStatusHistory(Account account, Account administrator, AccountStatus previousStatus,
                                 AccountStatus newStatus, String reason) {
        this.account = account; this.administrator = administrator; this.previousStatus = previousStatus;
        this.newStatus = newStatus; this.reason = reason; this.changedOn = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Account getAdministrator() { return administrator; }
    public AccountStatus getPreviousStatus() { return previousStatus; }
    public AccountStatus getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public LocalDateTime getChangedOn() { return changedOn; }
}