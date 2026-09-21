package com.neueda.leap.interfaces;
import java.math.BigDecimal;

public interface PriceValidator {
    BigDecimal validatePrice(BigDecimal newPrice);
}
