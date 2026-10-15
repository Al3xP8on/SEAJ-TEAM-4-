package com.neueda.leap.dtos;

import com.neueda.leap.models.Account;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Optional;
import com.neueda.leap.enums.AccountStatus;
import java.math.BigDecimal;

public record AccountResponse(

    @Schema(description="Internal database ID", example="1")
    @Positive(message="ID must be positive.")
    long id,

    @Schema(description="Public account identifier", example="ACC-0001")
    @NotBlank(message="Account ID is required.")
    String accountId,

    @Schema(description="Name of the account holder", example="Alice Johnson")
    @NotBlank(message="Holder name is required.")
    String holderName,

    @Schema(description="Current cash balance in account currency", example="10000.00")
    @NotNull(message="Cash balance must not be null.")
    BigDecimal cashBalance,

    @Schema(description="Current status of the account")
    @NotNull(message="Account status is required.")
    AccountStatus status,

    @Schema(description="Comma-separated account roles", example="USER")
    @NotBlank(message="Roles are required.")
    String roles,

    @Schema(description="Timestamp of last account modification", example="2026-09-22T10:30:00Z")
    Optional<LocalDateTime> lastUpdated
) {

    public AccountResponse(long id, String accountId, String holderName, BigDecimal cashBalance, AccountStatus status, String roles, Optional<LocalDateTime> lastUpdated){
        this.id = id;
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
        this.roles = roles;
        this.lastUpdated = lastUpdated;
    }

    public AccountResponse(long id, String accountId, String holderName, BigDecimal cashBalance, AccountStatus status, Optional<LocalDateTime> lastUpdated){
        this(id, accountId, holderName, cashBalance, status, "USER", lastUpdated);
    }

    /** Maps an account entity to the API response; login and contact details are not included */
    public static AccountResponse from(Account account) {
        return new AccountResponse(
            account.getId() == null ? 0L : account.getId(),
            account.getAccountId(),
            account.getName(),
            account.getCashBalance(),
            account.getStatus(),
            account.getRoles(),
            Optional.ofNullable(account.getLastUpdated())
        );
    }
}
