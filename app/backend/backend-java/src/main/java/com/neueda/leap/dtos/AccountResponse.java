package com.neueda.leap.dtos;
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

    @Schema(description="Timestamp of last account modification", example="2026-09-22T10:30:00Z")
    Optional<LocalDateTime> lastUpdated
) {

    public AccountResponse(long id, String accountId, String holderName, BigDecimal cashBalance, AccountStatus status, Optional<LocalDateTime> lastUpdated){
        this.id = id;
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
        this.lastUpdated = lastUpdated;
    }
}
