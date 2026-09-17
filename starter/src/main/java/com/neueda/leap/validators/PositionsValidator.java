package com.neueda.leap;
import com.neueda.leap.exceptions.InsufficientHoldingsException;

import java.math.BigDecimal;

public class PositionsValidator {

    public void validateAccountId(long accountId) {
        if(accountId <= 0){
            throw new IllegalArgumentException("Account ID must be positive");
        }
    }

    public void validateSymbol(String symbol) {
        if(symbol == null || symbol.isBlank()){
            throw new IllegalArgumentException("Symbol must not be null or blank");
        }
        if(symbol.length() > 20){
            throw new IllegalArgumentException("Symbol length must not exceed 20 characters");
        }
    }

    public void validateQuantity(int quantity) {
        if(quantity < 0){
            throw new IllegalArgumentException("Quantity must not be negative");
        }
    }

    public void validateAverageCost(BigDecimal averageCost) {
        if(averageCost == null){
            throw new IllegalArgumentException("Average cost must not be null");
        }
        if(averageCost.compareTo(BigDecimal.ZERO) < 0){
            throw new IllegalArgumentException("Average cost must not be negative");
        }
    }

    public void validateQuantityChange(int quantityChange) {
        if(quantityChange == 0){
            throw new IllegalArgumentException("Quantity change must not be zero");
        }
    }

    public void validatePrice(BigDecimal price) {
        if(price == null){
            throw new IllegalArgumentException("Price can't be null");
        }
        if(price.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Price can't be zero or negative");
        }
    }

    public void validateCurrentPrice(BigDecimal currentPrice) {
        if(currentPrice == null){
            throw new IllegalArgumentException("Current price must not be null");
        }
        if(currentPrice.compareTo(BigDecimal.ZERO) < 0){
            throw new IllegalArgumentException("Current price must not be negative");
        }
    }

    public void validateSufficientHoldings(int newQuantity, String symbol) throws InsufficientHoldingsException {
        if(newQuantity < 0){
            throw new InsufficientHoldingsException("Insufficient holdings for " + symbol);
        }
    }
}
