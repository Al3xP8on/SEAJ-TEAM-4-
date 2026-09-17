package com.neueda.leap;
import java.math.BigDecimal;
import java.util.Objects;
import java.math.RoundingMode;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.validators.PositionsValidator;


public class Positions {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final long accountId;
    private final String symbol;
    private int quantity;
    private BigDecimal averageCost;
    private final PositionsValidator validator;

    public Positions(long accountId, String symbol, int quantity, BigDecimal averageCost) {
        this(accountId, symbol, quantity, averageCost, new PositionsValidator());
    }

    public Positions(long accountId, String symbol, int quantity, BigDecimal averageCost, PositionsValidator validator) {
        this.validator = validator;

        validator.validateAccountId(accountId);
        validator.validateSymbol(symbol);
        validator.validateQuantity(quantity);
        validator.validateAverageCost(averageCost);

        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost.setScale(SCALE, ROUNDING_MODE);
    }

    public void apply(int quantityChange, BigDecimal price) throws InsufficientHoldingsException {
        validator.validateQuantityChange(quantityChange);
        validator.validatePrice(price);

        int newQuantity = this.quantity + quantityChange;

        validator.validateSufficientHoldings(newQuantity, symbol);

        if(quantityChange > 0){
            BigDecimal currentValue = averageCost.multiply(BigDecimal.valueOf(this.quantity));
            BigDecimal additionalValue = price.multiply(BigDecimal.valueOf(quantityChange));
            BigDecimal totalCost = currentValue.add(additionalValue);
            averageCost = totalCost.divide(BigDecimal.valueOf(newQuantity), SCALE, ROUNDING_MODE);
        }

        this.quantity = newQuantity;

        if(this.quantity == 0){
            averageCost = BigDecimal.ZERO;
        }
    }

    public BigDecimal marketValue(BigDecimal currentPrice){
        validator.validateCurrentPrice(currentPrice);
        return currentPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING_MODE);    
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
        if(!(o instanceof Positions)){
            return false;
        }
        Positions position = (Positions) o;
        return accountId == position.accountId && symbol.equals(position.symbol);
    }

    @Override
    public int hashCode(){
        return Objects.hash(accountId,symbol);
    }

    @Override
    public String toString(){
        return "Positions{" +
                "accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", averageCost=" + averageCost +
                '}';
    }

}