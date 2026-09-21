package com.neueda.leap.services;

import com.neueda.leap.exceptions.InvalidPriceException;
import com.neueda.leap.exceptions.InvalidSymbolPriceException;
import com.neueda.leap.exceptions.InvalidSymbolException;
import com.neueda.leap.models.SymbolPrice;
import com.neueda.leap.validators.SymbolPriceValidator;

import java.time.ZonedDateTime;
import java.math.BigDecimal;

public class SymbolPriceUpdater {
    private final SymbolPriceValidator validator;

    public SymbolPriceUpdater(SymbolPriceValidator validator){
        this.validator = validator;
    }

    public SymbolPrice updateSymbolPrice(SymbolPrice symbolPrice, BigDecimal newPrice){
        
        if(symbolPrice == null){
            throw new InvalidSymbolPriceException("SymbolPrice cannot be null");
        }
        
        validator.validatePrice(newPrice);
        validator.validateSymbol(symbolPrice.getSymbol());    
        return new SymbolPrice(symbolPrice.getSymbol(), newPrice);
    }
}

