package com.neueda.leap.validators;

import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.enums.OrderErrorCode;
import com.neueda.leap.utils.Utils;

import java.math.BigDecimal;
import java.util.Objects;

public class OrderValidator {
    
    private static final BigDecimal MINIMUM_PRICE = BigDecimal.ZERO;
    private static final long MINIMUM_QUANTITY = 1L;

    private OrderValidator() {}

    public static Account validateAccount(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        if (!account.isActive()) {
            throw new OrderException(
                "Account is not in active state",
                OrderErrorCode.ACCOUNT_NOT_ACTIVE,
                account.getAccountId()
            );
        }
        return account;
    }

    public static Instrument validateInstrument(Instrument instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        if (!instrument.isTradable()) {
            throw new OrderException(
                "Instrument is not tradable",
                OrderErrorCode.INSTRUMENT_NOT_TRADABLE,
                instrument.getSymbol()
            );
        }
        return instrument;
    }

    public static long validateQuantity(long quantity) {
        if (quantity < MINIMUM_QUANTITY) {
            throw new OrderException(
                "Quantity must be at least " + MINIMUM_QUANTITY,
                OrderErrorCode.INVALID_QUANTITY
            );
        }
        return quantity;
    }

    public static BigDecimal validatePrice(BigDecimal price) {
        Objects.requireNonNull(price, "Price cannot be null");
        if (price.compareTo(MINIMUM_PRICE) <= 0) {
            throw new OrderException(
                "Price must be positive",
                OrderErrorCode.INVALID_PRICE
            );
        }
        return price;
    }

    public static String validateIdempotencyKey(String key) {
        Objects.requireNonNull(key, "Idempotency key cannot be null");
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            throw new OrderException(
                "Idempotency key cannot be empty",
                OrderErrorCode.EMPTY_IDEMPOTENCY_KEY
            );
        }
        if (trimmed.length() > 100) {
            throw new OrderException(
                "Idempotency key cannot exceed 100 characters",
                OrderErrorCode.IDEMPOTENCY_KEY_TOO_LONG
            );
        }
        return trimmed;
    }

    public static boolean isValidForExecution(Order order) {
        if (!order.getStatus().canTransitionToExecuted()) {
            return false;
        }

        if (!order.getInstrument().isTradable()) {
            return false;
        }

        // Check account has sufficient funds for BUY orders
        if (order.getSide().isBuy()) {
            BigDecimal totalValue = Utils.calculateTotalValue(order.getPrice(), order.getQuantity());
            return order.getAccount().canAfford(totalValue);
        }

        return true;
    }
}
