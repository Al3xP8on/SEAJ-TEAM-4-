package com.neueda.leap.dtos;

import java.math.BigDecimal;

public record ClosePositionRequest(
        BigDecimal closingPrice,
        int closingQuantity
) {
}