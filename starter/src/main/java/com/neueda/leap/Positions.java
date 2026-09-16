package com.neueda.leap;
import java.math.BigDecimal;
import java.util.Objects;
import java.math.RoundingMode;


public class Positions {

    private final long accountId;
    private final String symbol;
    private int quantity;
    private BigDecimal averageCost;

    public Positions(long accountId, String symbol, int quantity, BigDecimal averageCost) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }

    public void apply(int quantity, BigDecimal price) {
        int newQuantity = this.quantity + quantity;

        if(quantityChange > 0){
            BigDecimal currentValue = averageCost.multiply(BigDecimal.valueOf(this.quantity));
            BigDecimal  additionalValue = price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal totalCost = currentValue.add(additionalValue);
            averageCost = totalCost.divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);
        }

        quantity = newQuantity;

        if(quantity == 0){
            averageCost = BigDecimal.ZERO.setScale(2);
        }

    }

    public BigDecimal marketValue(BigDecimal currentPrice){
        if(currentPrice == null){
            return BigDecimal.ZERO.setScale(2);
        }
        return currentPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);    
    }

    public long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o ){
            return true;
        }
        if(!(o instanceof Position)){
            return false;
        }
        return accountId == position.accountId && symbol.equals(position.symbol);
    }

    @Override
    public int hashCode(){
        return Objects.hash(accountId,symbol);
    }

    @Override
    public String toString(){
        return "Position{" +
                "accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", averageCost=" + averageCost +
                '}';
    }

}