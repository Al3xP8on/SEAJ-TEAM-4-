package com.neueda.leap.validators;

import java.math.BigDecimal;

import com.neueda.leap.models.SymbolPrice;

import com.neueda.leap.exceptions.InvalidPriceException;
import com.neueda.leap.exceptions.InvalidSymbolPriceException;

public class SymbolPriceValidator {
    public void validateNewPrice(BigDecimal newPrice) throws InvalidPriceException {
        if(newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0){
            throw new InvalidPriceException("Invalid price: " + newPrice);
        } 
    }

    public void validateSymbolPrice(SymbolPrice symbolPrice) throws InvalidSymbolPriceException {
        if(symbolPrice == null){
            throw new InvalidSymbolPriceException("SymbolPrice cannot be null.");
        }
    }
}
