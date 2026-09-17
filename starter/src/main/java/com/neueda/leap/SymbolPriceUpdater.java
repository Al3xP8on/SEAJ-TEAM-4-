package com.neueda.leap;

import com.neueda.leap.exceptions.InvalidPriceException;
import com.neueda.leap.exceptions.InvalidSymbolPriceException;


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
