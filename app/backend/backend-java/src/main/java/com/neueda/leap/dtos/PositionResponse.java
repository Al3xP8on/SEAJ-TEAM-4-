package com.neueda.leap.dtos;

import java.math.BigDecimal;

public class PositionResponse{
    private final String accountId;
    private final String symbol;
    private final int quantity;
    private final BigDecimal averageCost;

    public PositionResponse(String accountId, String symbol, int quantity, BigDecimal averageCost){
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    public String getAccountId(){
        return this.accountId;
    }

    public String getSymbol(){
        return this.symbol;
    }

    public int getQuantity(){
        return this.quantity;
    }

    public BigDecimal getAverageCost(){
        return this.averageCost;
    }
}