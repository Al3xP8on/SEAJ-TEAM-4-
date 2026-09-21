package com.neueda.leap.validators;

import com.neueda.leap.interfaces.SymbolValidator;
import com.neueda.leap.interfaces.PriceValidator;

import java.math.BigDecimal;

import com.neueda.leap.models.SymbolPrice;

import com.neueda.leap.exceptions.InvalidPriceException;
import com.neueda.leap.exceptions.InvalidSymbolPriceException;
import com.neueda.leap.exceptions.InvalidSymbolException;
public class SymbolPriceValidator implements SymbolValidator, PriceValidator {

    public String validateSymbol(String symbol){
        if(symbol == null || symbol.trim().isEmpty()){
            throw new InvalidSymbolException("Invalid symbol for SymbolPrice: " + symbol);
        }
        return symbol;
    }
    
    public BigDecimal validatePrice(BigDecimal price){
        if(price == null || price.compareTo(BigDecimal.ZERO) <= 0){
            throw new InvalidPriceException("Invalid price for SymbolPrice: " + price);
        }
        return price;
    }
}
