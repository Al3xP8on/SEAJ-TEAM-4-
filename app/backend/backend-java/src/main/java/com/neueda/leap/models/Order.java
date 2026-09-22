package com.neueda.leap.models;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderErrorCode;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.validators.OrderValidator;
import com.neueda.leap.utils.Utils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

//Order entity representing a trading order.
public class Order {

    private final String id;
    private final Account account;
    private final Instrument instrument;
    private final long quantity;
    private final BigDecimal price;
    private final OrderSide side;
    private final String idempotencyKey;
    private final LocalDateTime createdAt;

    private OrderStatus status;

    public Order() {
        this.id = null;
        this.account = null;
        this.instrument = null;
        this.quantity = 0;
        this.price = null;
        this.side = null;
        this.idempotencyKey = null;
        this.status = OrderStatus.NEW;
        this.createdAt = null;
    }

    // Primary constructor for creating new orders
    public Order(Account account, Instrument instrument, long quantity, BigDecimal price, OrderSide side, String idempotencyKey) {
        this.id = UUID.randomUUID().toString();
        this.account = OrderValidator.validateAccount(account);
        this.instrument = OrderValidator.validateInstrument(instrument);
        this.quantity = OrderValidator.validateQuantity(quantity);
        this.price = OrderValidator.validatePrice(price);
        this.side = Objects.requireNonNull(side, "Order side cannot be null");
        this.idempotencyKey = OrderValidator.validateIdempotencyKey(idempotencyKey);
        this.status = OrderStatus.NEW;
        this.createdAt = LocalDateTime.now();
    }

    // Constructor for loading existing orders from database
    public Order(String id, Account account, Instrument instrument, long quantity, BigDecimal price,
                 OrderSide side, String idempotencyKey, OrderStatus status, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "Order ID cannot be null");
        this.account = Objects.requireNonNull(account, "Account cannot be null");
        this.instrument = Objects.requireNonNull(instrument, "Instrument cannot be null");
        this.quantity = quantity;  // Already validated in DB
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.side = Objects.requireNonNull(side, "Order side cannot be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "Idempotency key cannot be null");
        this.status = Objects.requireNonNull(status, "Order status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
    }

    public void execute() {
        if (!OrderValidator.isValidForExecution(this)) {
            transitionToRejected("Failed execution validation");
            throw new OrderException(
                "Order cannot be executed in current state",
                OrderErrorCode.EXECUTION_FAILED,
                id
            );
        }

        try {
            BigDecimal totalValue = Utils.calculateTotalValue(this.price, this.quantity);

            // Process account debit/credit
            if (side.isBuy()) {
                account.debit(totalValue);
            } else {
                account.credit(totalValue);
            }

            transitionToExecuted();

        } catch (Exception e) {
            transitionToRejected("Execution error: " + e.getMessage());
            throw new OrderException(
                "Order execution failed: " + e.getMessage(),
                OrderErrorCode.EXECUTION_ERROR,
                id
            );
        }
    }

    // Cancel a pending order.
    public void cancel() {
        if (!status.canBeCancelled()) {
            throw new OrderException(
                "Order in " + status + " state cannot be cancelled",
                OrderErrorCode.CANCEL_INVALID_STATE,
                id
            );
        }
        transitionToCancelled();
    }

    private void transitionToExecuted() {
        if (!status.canTransitionToExecuted()) {
            throw new OrderException(
                "Cannot transition from " + status + " to EXECUTED",
                OrderErrorCode.INVALID_STATE_TRANSITION,
                id
            );
        }
        this.status = OrderStatus.EXECUTED;
    }

    private void transitionToCancelled() {
        if (!status.canBeCancelled()) {
            throw new OrderException(
                "Cannot transition from " + status + " to CANCELLED",
                OrderErrorCode.INVALID_STATE_TRANSITION,
                id
            );
        }
        this.status = OrderStatus.CANCELLED;
    }

    private void transitionToRejected(String reason) {
        this.status = OrderStatus.REJECTED;
    }

    // Calculate total order value (price * quantity)
    public BigDecimal calculateTotalValue() {
        return Utils.calculateTotalValue(this.price, this.quantity);
    }

    //  object contract methods 

    @Override
    public String toString() {
        return String.format(
            "Order{id=%s, account=%s, instrument=%s, qty=%d, price=%s, side=%s, status=%s, created=%s}",
            id, 
            account != null ? account.getAccountId() : "null",
            instrument != null ? instrument.getSymbol() : "null",
            quantity, 
            price, 
            side, 
            status, 
            createdAt
        );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Order)) return false;
        
        Order order = (Order) object;
        return Objects.equals(this.id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String getId() { 
        return id; 
    }

    public Account getAccount() { 
        return account; 
    }

    public Instrument getInstrument() { 
        return instrument; 
    }

    public long getQuantity() { 
        return quantity; 
    }

    public BigDecimal getPrice() { 
        return price; 
    }

    public OrderSide getSide() { 
        return side; 
    }

    public OrderStatus getStatus() { 
        return status; 
    }

    public String getIdempotencyKey() { 
        return idempotencyKey; 
    }

    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }

    public void setStatus(OrderStatus status) { 
        this.status = Objects.requireNonNull(status, "Status cannot be null");
    }
}
