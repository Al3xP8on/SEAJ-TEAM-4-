package com.neueda.leap.models;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.OrderException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

//Order entity representing a trading order.
public class Order {
    
    private static final BigDecimal MINIMUM_PRICE = BigDecimal.ZERO;
    private static final long MINIMUM_QUANTITY = 1L;

    // Immutable Fields
    private final String id;
    private final Account account;
    private final Instrument instrument;
    private final long quantity;
    private final BigDecimal price;
    private final OrderSide side;
    private final String idempotencyKey;
    private final LocalDateTime createdAt;

    // Mutable State Fields
    private OrderStatus status;

    // No-arg constructor for ORM/serialization frameworks.
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
    public Order(Account account, Instrument instrument, long quantity, BigDecimal price, 
                 OrderSide side, String idempotencyKey) {
        this.id = UUID.randomUUID().toString();
        this.account = validateAccount(account);
        this.instrument = validateInstrument(instrument);
        this.quantity = validateQuantity(quantity);
        this.price = validatePrice(price);
        this.side = Objects.requireNonNull(side, "Order side cannot be null");
        this.idempotencyKey = validateIdempotencyKey(idempotencyKey);
        this.status = OrderStatus.NEW;
        this.createdAt = LocalDateTime.now();
    }

    // Constructor for loading existing orders from database
    // Used by ORM/persistence layer
    public Order(String id, Account account, Instrument instrument, long quantity, BigDecimal price,
                 OrderSide side, String idempotencyKey, OrderStatus status, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "Order ID cannot be null");
        this.account = validateAccount(account);
        this.instrument = validateInstrument(instrument);
        this.quantity = quantity;  // Already validated in DB
        this.price = price;         // Already validated in DB
        this.side = Objects.requireNonNull(side, "Order side cannot be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "Idempotency key cannot be null");
        this.status = Objects.requireNonNull(status, "Order status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
    }

    //  VALIDATION METHODS 
    private static Account validateAccount(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");
        if (!account.isActive()) {
            throw new OrderException("Account is not in active state", "ACCOUNT_INACTIVE", "ERR_001");
        }
        return account;
    }

    private static Instrument validateInstrument(Instrument instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        if (!instrument.isTradable()) {
            throw new OrderException("Instrument is not tradable", "INSTRUMENT_NOT_TRADABLE", "ERR_002");
        }
        return instrument;
    }

    private static long validateQuantity(long quantity) {
        if (quantity < MINIMUM_QUANTITY) {
            throw new OrderException(
                "Quantity must be at least " + MINIMUM_QUANTITY,
                "INVALID_QUANTITY",
                "ERR_003"
            );
        }
        return quantity;
    }

    private static BigDecimal validatePrice(BigDecimal price) {
        Objects.requireNonNull(price, "Price cannot be null");
        if (price.compareTo(MINIMUM_PRICE) <= 0) {
            throw new OrderException(
                "Price must be positive",
                "INVALID_PRICE",
                "ERR_004"
            );
        }
        return price;
    }

    private static String validateIdempotencyKey(String key) {
        Objects.requireNonNull(key, "Idempotency key cannot be null");
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            throw new OrderException(
                "Idempotency key cannot be empty",
                "EMPTY_IDEMPOTENCY_KEY",
                "ERR_005"
            );
        }
        if (trimmed.length() > 100) {
            throw new OrderException(
                "Idempotency key cannot exceed 100 characters",
                "IDEMPOTENCY_KEY_TOO_LONG",
                "ERR_006"
            );
        }
        return trimmed;
    }

    //  business logic methods
    // Validate if order can transition to PENDING state.
    public boolean isValidForExecution() {
        // Check state transition validity
        if (!status.canTransitionToExecuted()) {
            return false;
        }

        if (!instrument.isTradable()) {
            return false;
        }

        // Check account has sufficient funds for BUY orders
        if (side.isBuy()) {
            BigDecimal totalValue = calculateTotalValue();
            return account.canAfford(totalValue.floatValue());
        }

        // SELL orders always valid if account is active
        return true;
    }

    // Validate order state and execute it.
    public void execute() {
        if (!isValidForExecution()) {
            transitionToRejected("Failed execution validation");
            throw new OrderException(
                "Order cannot be executed in current state",
                id,
                "EXECUTION_FAILED"
            );
        }

        try {
            BigDecimal totalValue = calculateTotalValue();

            // Process account debit/credit
            if (side.isBuy()) {
                account.debit(totalValue.floatValue());
            } else {
                account.credit(totalValue.floatValue());
            }

            // Transition to executed state
            transitionToExecuted();

        } catch (Exception e) {
            transitionToRejected("Execution error: " + e.getMessage());
            throw new OrderException(
                "Order execution failed: " + e.getMessage(),
                id,
                "EXECUTION_ERROR"
            );
        }
    }

    // Cancel a pending order.
    public void cancel() {
        if (!status.canBeCancelled()) {
            throw new OrderException(
                "Order in " + status + " state cannot be cancelled",
                id,
                "CANCEL_INVALID_STATE"
            );
        }
        transitionToCancelled();
    }

    // Calculate total order value (price × quantity)
    public BigDecimal calculateTotalValue() {
        return price.multiply(new BigDecimal(quantity));
    }

    //  state transition methods
    private void transitionToExecuted() {
        if (!status.canTransitionToExecuted()) {
            throw new OrderException(
                "Cannot transition from " + status + " to EXECUTED",
                id,
                "INVALID_STATE_TRANSITION"
            );
        }
        this.status = OrderStatus.EXECUTED;
    }

    private void transitionToCancelled() {
        if (!status.canBeCancelled()) {
            throw new OrderException(
                "Cannot transition from " + status + " to CANCELLED",
                id,
                "INVALID_STATE_TRANSITION"
            );
        }
        this.status = OrderStatus.CANCELLED;
    }

    private void transitionToRejected(String reason) {
        this.status = OrderStatus.REJECTED;
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

    // Orders are equal if they have the same ID (database identity)
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Order)) return false;
        
        Order order = (Order) object;
        return Objects.equals(this.id, order.id);
    }

    // Hash code based on order ID (consistent with equals contract)
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    //  accessor methods 
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

    //  mutation methods 
    // Internal state transitions use dedicated transition methods
    public void setStatus(OrderStatus status) { 
        this.status = Objects.requireNonNull(status, "Status cannot be null");
    }

    //  private setter methods 
    private void setId(String id) { 
        // ID is set via constructor, this is for ORM only
    }
}
