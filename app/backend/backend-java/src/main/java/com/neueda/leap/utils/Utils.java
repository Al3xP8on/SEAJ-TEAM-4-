package com.neueda.leap.utils;

import java.math.BigDecimal;

public class Utils {
    public static BigDecimal calculateTotalValue(BigDecimal price, long quantity) {
        return price.multiply(new BigDecimal(quantity));
    }

}
