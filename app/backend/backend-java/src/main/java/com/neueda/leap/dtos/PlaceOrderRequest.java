package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Request to place a new order")
public class PlaceOrderRequest {
    
    @Schema(description = "Account ID placing the order", example = "ACC-001")
    @NotBlank(message = "Account ID must not be blank")
    private String accountId;
    
    @Schema(description = "Trading symbol/instrument identifier", example = "AAPL")
    @NotBlank(message = "Symbol must not be blank")
    private String symbol;
    
    @Schema(description = "Number of units to order (must be positive)", example = "100")
    @Positive(message = "Quantity must be positive")
    private int quantity;
    
    @Schema(description = "Price per unit (must be positive)", example = "150.50")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    public PlaceOrderRequest() {
    }

    public PlaceOrderRequest(String accountId, String symbol, int quantity, BigDecimal price) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
    }

    public String getAccountId() {
        return this.accountId;
    }

    public String getSymbol() {
        return this.symbol;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
