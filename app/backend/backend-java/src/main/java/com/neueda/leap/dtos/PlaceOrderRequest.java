package com.neueda.leap.dtos;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.TimeInForce;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    
    @Schema(description = "Order side - BUY or SELL", example = "BUY")
    @NotNull(message = "Side must not be null")
    private OrderSide side;
    
    @Schema(description = "Number of units to order (must be positive)", example = "100")
    @Positive(message = "Quantity must be positive")
    private int quantity;
    
    @Schema(description = "Price per unit (must be positive)", example = "150.50")
    @Positive(message = "Price must be positive")
    private BigDecimal price;
    
    @Schema(description = "Time in force - how long order remains active (defaults to GTC)", example = "GTC")
    private TimeInForce timeInForce = TimeInForce.GTC;

    public PlaceOrderRequest() {
    }

    public PlaceOrderRequest(String accountId, String symbol, OrderSide side, int quantity, BigDecimal price) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.timeInForce = TimeInForce.GTC;
    }

    public PlaceOrderRequest(String accountId, String symbol, OrderSide side, int quantity, BigDecimal price, TimeInForce timeInForce) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.timeInForce = timeInForce;
    }

    public String getAccountId() {
        return this.accountId;
    }

    public String getSymbol() {
        return this.symbol;
    }
    
    public OrderSide getSide() {
        return this.side;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public BigDecimal getPrice() {
        return this.price;
    }
    
    public TimeInForce getTimeInForce() {
        return this.timeInForce;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public void setSide(OrderSide side) {
        this.side = side;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }
}
