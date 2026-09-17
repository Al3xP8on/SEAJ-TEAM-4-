package com.neueda.leap.services;

import com.neueda.leap.exceptions.InvalidPriceException;
import com.neueda.leap.exceptions.InvalidSymbolPriceException;
import com.neueda.leap.models.SymbolPrice;
import com.neueda.leap.validators.SymbolPriceValidator;

import java.time.ZonedDateTime;
import java.math.BigDecimal;

public class SymbolPriceUpdater {
    private final SymbolPriceValidator validator;

    public SymbolPriceUpdater(){
        this.validator = new SymbolPriceValidator();
    }

    public SymbolPrice updateSymbol(SymbolPrice symbolPrice, BigDecimal newPrice) throws InvalidPriceException, InvalidSymbolPriceException{
        validator.validateNewPrice(newPrice);
        validator.validateSymbolPrice(symbolPrice);    
        return new SymbolPrice(symbolPrice.getSymbol(), newPrice, ZonedDateTime.now());
    }
}
