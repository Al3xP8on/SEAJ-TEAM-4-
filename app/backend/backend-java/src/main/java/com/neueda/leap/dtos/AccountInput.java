package com.neueda.leap.dtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Optional;

public record AccountInput (
    @Schema(description="Internal database ID", example="1")
    @Positive(message="ID must be a positive number.")
    long id,

    @Schema(description="Public account identifier (must be unique)", example="ACC-0006")
    @NotBlank(message="Account ID must not be blank.")
    String accountId,

    @Schema(description="Name of the account holder", example="Frank Smith")
    @NotBlank(message="Holder name must not be blank.")
    String holderName,

    @Schema(description="Initial cash balance (optional, defaults to 0)", example="5000.00")
    @PositiveOrZero(message="Cash balance must be zero or positive.")
    Optional<BigDecimal> cashBalance
){
    public AccountInput(long id, String accountId, String holderName, Optional<BigDecimal> cashBalance){
        this.id = id;
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
    }
}
