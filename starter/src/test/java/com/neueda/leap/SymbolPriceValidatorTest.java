package com.neueda.leap;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

import com.neueda.leap.validators.SymbolPriceValidator;

import com.neueda.leap.exceptions.InvalidSymbolPriceException;
import com.neueda.leap.exceptions.InvalidPriceException;

import com.neueda.leap.models.SymbolPrice;


public class SymbolPriceValidatorTest {
    private SymbolPriceValidator symbolPriceValidator;

    @BeforeEach
    void setUp(){
        symbolPriceValidator = new SymbolPriceValidator();
    }
    
    @Test 
    @DisplayName("throws InvalidPriceException when newPrice object is null")
    void testNullNewPriceThrowsInvalidPriceException(){
        BigDecimal newPrice = null;
        assertThrows(
            InvalidPriceException.class,
            () -> symbolPriceValidator.validateNewPrice(newPrice)
        );  
    }
    
    @ParameterizedTest 
    @ValueSource(doubles = {-0.01, -10.0, -100.0})
    @DisplayName("throws InvalidPriceException when newPrice is negative")
    void testNegativeNewPriceThrowsInvalidPriceException(double newPriceValue){
        BigDecimal newPrice = BigDecimal.valueOf(newPriceValue);
        assertThrows(
            InvalidPriceException.class,
            () -> symbolPriceValidator.validateNewPrice(newPrice)
        );
    }

    @Test
    @DisplayName("throws InvalidSymbolPriceException when symbolPrice is null")
    void testValidateSymbolPriceThrowsInvalidSymbolPriceException(){
       SymbolPrice symbolPrice = null;
       assertThrows(
         InvalidSymbolPriceException.class,
         () -> symbolPriceValidator.validateSymbolPrice(symbolPrice)
       );
    }
}
