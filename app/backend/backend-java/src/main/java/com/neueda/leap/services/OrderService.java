package com.neueda.leap.services;

import com.neueda.leap.models.Account;
import com.neueda.leap.models.Instrument;
import com.neueda.leap.models.Order;
import com.neueda.leap.models.OrderHistory;
import com.neueda.leap.observability.OrderTelemetry;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.OrderErrorCode;
import com.neueda.leap.exceptions.OrderException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.validators.OrderValidator;
import com.neueda.leap.validators.AccountValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.TimeInForce;
import com.neueda.leap.enums.OrderType;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentsRepository;
import com.neueda.leap.repositories.PositionsRepository;
import com.neueda.leap.messaging.ExecutionEvent;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.RoundingMode;
import java.time.Instant;


@Service
@Transactional
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderHistoryRepository orderHistoryRepository;
    

    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentsRepository instruments;
    @Autowired private PositionsRepository positions;
    @Autowired private MarketPriceService prices;
    @Autowired private OrderSettlementService settlement;
    @PersistenceContext private EntityManager entityManager;

    public Order placeOrder(PlaceOrderRequest request) throws DuplicateOrderException {
        if (request.getTimeInForce() != TimeInForce.GTC) {
            throw invalid("Only good-till-cancelled orders are supported");
        }
        Account account = accounts.findByAccountId(request.getAccountId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account = accounts.findByIdForUpdate(account.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        Instrument instrument = instruments.findBySymbol(request.getSymbol().trim().toUpperCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
        String key = request.getIdempotencyKey() == null ? UUID.randomUUID().toString() : request.getIdempotencyKey().trim();
        OrderValidator.validateIdempotencyKey(key);
        var previous = orderRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            Order old = previous.get();
            if (!old.getAccount().getAccountId().equals(account.getAccountId())
                || !old.getInstrument().getSymbol().equals(instrument.getSymbol())
                || old.getSide() != request.getSide() || old.getQuantity() != request.getQuantity()
                || old.getOrderType() != request.getOrderType()
                || (old.getOrderType() == OrderType.LIMIT
                    && (request.getPrice() == null || old.getPrice().compareTo(request.getPrice()) != 0))) {
                throw new DuplicateOrderException("This submission key has already been used for another order");
            }
            return old;
        }
        BigDecimal price = request.getPrice();
        if (request.getOrderType() == OrderType.MARKET) {
            var quote = prices.getLatestPrice(instrument.getSymbol()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No current quote available. Try a limit order or retry later."));
            price = (request.getSide() == OrderSide.BUY ? quote.askPrice() : quote.bidPrice()).setScale(2, request.getSide() == OrderSide.BUY ? RoundingMode.CEILING : RoundingMode.FLOOR);
        }
        if (price == null || price.scale() > 2) throw invalid("Enter a positive limit price with at most two decimal places");
        OrderValidator.validatePrice(price);
        OrderValidator.validateQuantity(request.getQuantity());
        List<Order> pending = getOrdersByAccount(account.getAccountId()).stream()
            .filter(o -> o.getStatus().canBeCancelled()).toList();
        if (request.getSide() == OrderSide.BUY) {
            long held = positions.findByAccountIdAndSymbol(account.getId(), instrument.getSymbol()).map(p -> (long) p.getQuantity()).orElse(0L);
            long queued = pending.stream().filter(o -> o.getSide() == OrderSide.BUY && o.getInstrument().getSymbol().equals(instrument.getSymbol())).mapToLong(Order::getQuantity).sum();
            if (held + queued + request.getQuantity() > Integer.MAX_VALUE) throw invalid("Resulting position quantity is too large");
            BigDecimal reserved = pending.stream().filter(o -> o.getSide() == OrderSide.BUY)
                .map(Order::calculateTotalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (account.getCashBalance().subtract(reserved).compareTo(price.multiply(BigDecimal.valueOf(request.getQuantity()))) < 0)
                throw invalid("Insufficient available cash after pending buy orders");
        } else {
            int held = positions.findByAccountIdAndSymbol(account.getId(), instrument.getSymbol()).map(p -> p.getQuantity()).orElse(0);
            long reserved = pending.stream().filter(o -> o.getSide() == OrderSide.SELL && o.getInstrument().getSymbol().equals(instrument.getSymbol()))
                .mapToLong(Order::getQuantity).sum();
            if (held - reserved < request.getQuantity()) throw invalid("Insufficient holdings after pending sell orders");
        }
        Order order = createOrder(account, instrument, request.getQuantity(), price, request.getSide(), key);
        order.setOrderType(request.getOrderType());
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);
        recordOrderHistory(order.getId(), OrderStatus.PENDING);
        OrderTelemetry.placed(order);
        return order;
    }

    private OrderException invalid(String message) {
        return new OrderException(message, OrderErrorCode.EXECUTION_FAILED);
    }

    // Creates a new order with idempotency protection.
    // Checks if an order with the same idempotency key already exists to prevent duplicates.
    public Order createOrder(Account account, Instrument instrument, long quantity, BigDecimal price, OrderSide side, String idempotencyKey) throws DuplicateOrderException {
        logger.info("OrderService.createOrder - Validating account: {} (Status: {}, Balance: {})", 
            account.getAccountId(), account.getStatus(), account.getCashBalance());
        OrderValidator.validateAccount(account);
        logger.debug("Account validation passed for: {}", account.getAccountId());
        
        logger.info("OrderService.createOrder - Validating instrument: {}", instrument.getSymbol());
        OrderValidator.validateInstrument(instrument);
        logger.debug("Instrument validation passed for: {}", instrument.getSymbol());
        
        OrderValidator.validateQuantity(quantity);
        OrderValidator.validatePrice(price);
        OrderValidator.validateIdempotencyKey(idempotencyKey);
        
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            throw new DuplicateOrderException(
                "Order with idempotency key '" + idempotencyKey + "' already exists. Order ID: " + existingOrder.get().getId()
            );
        }

        // Create and validate the order through the domain model
        Order order = new Order(account, instrument, quantity, price, side, idempotencyKey);
        Order savedOrder = orderRepository.save(order);

        recordOrderHistory(savedOrder.getId(), OrderStatus.NEW);

        return savedOrder;
    }

    // Validates the order state, processes account debits/credits, and tracks status change.
    public Order executeOrder(UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        var quote = prices.getLatestPrice(order.getInstrument().getSymbol()).orElseThrow(() -> invalid("No current quote available"));
        BigDecimal price = order.getSide() == OrderSide.BUY ? quote.askPrice() : quote.bidPrice();
        if (!settlement.settle(new ExecutionEvent(UUID.randomUUID(), orderId,
            order.getAccount().getAccountId(), order.getInstrument().getSymbol(), order.getSide(),
            Math.toIntExact(order.getQuantity()), price, order.getPrice(), "MANUAL", Instant.now())))
            throw invalid("Order is not eligible for execution");
        return findOrderOrThrow(orderId);
    }

    // Cancels a pending order and records the status change.
    public Order cancelOrder(UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        order.cancel();

        Order cancelledOrder = orderRepository.save(order);
        recordOrderHistory(orderId, OrderStatus.CANCELLED);
        OrderTelemetry.cancelled(cancelledOrder);

        return cancelledOrder;
    }

    // Rejects an order
    // Records the rejection in order history.
    public Order rejectOrder(UUID orderId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw invalid("Provide a rejection reason of up to 500 characters");
        Order order = findOrderOrThrow(orderId);
        if (!order.getStatus().canBeCancelled()) throw invalid("Only pending orders can be rejected");
        order.setStatus(OrderStatus.REJECTED);

        Order rejectedOrder = orderRepository.save(order);
        OrderHistory entry = new OrderHistory(orderId, OrderStatus.REJECTED);
        entry.setReason(reason);
        orderHistoryRepository.save(entry);
        OrderTelemetry.rejected(rejectedOrder, OrderTelemetry.RejectStage.ADMIN);

        return rejectedOrder;
    }

    // Returns an Optional that will be empty if the order does not exist.
    public Optional<Order> getOrderById(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    // Returns an empty list if the account has no orders.
    public List<Order> getOrdersByAccount(String accountId) {
        AccountValidator.validateAccountId(accountId);
        return orderRepository.findByAccountId(accountId);
    }

    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> getPendingOrdersByAccount(String accountId) {
        return orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.NEW);
    }

    // Shows all status transitions for a specific order.
    public List<OrderHistory> getOrderHistory(UUID orderId) {
        return orderHistoryRepository.findByOrderId(orderId);
    }

    public boolean isOrderValidForExecution(UUID orderId) {
        return orderRepository.findById(orderId)
            .map(order -> OrderValidator.isValidForExecution(order))
            .orElse(false);
    }

    public BigDecimal getOrderTotalValue(UUID orderId) {
        return orderRepository.findById(orderId)
            .map(order -> com.neueda.leap.utils.Utils.calculateTotalValue(order.getPrice(), order.getQuantity()))
            .orElseThrow(() -> new OrderException("Order not found", OrderErrorCode.EXECUTION_FAILED, orderId.toString()));
    }

    public List<Order> getFilledOrdersByAccount(String accountId) {
        return orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.FILLED);
    }

    public List<Order> getCancelledOrdersByAccount(String accountId) {
        return orderRepository.findByAccountIdAndStatus(accountId, OrderStatus.CANCELLED);
    }

    // Returns all orders in the system (admin/reporting only)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    private void recordOrderHistory(UUID orderId, OrderStatus status) {
        OrderHistory history = new OrderHistory(orderId, status);
        orderHistoryRepository.save(history);
    }

    private Order findOrderOrThrow(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new OrderException("Order not found", OrderErrorCode.EXECUTION_FAILED, orderId.toString()));
    }
}
