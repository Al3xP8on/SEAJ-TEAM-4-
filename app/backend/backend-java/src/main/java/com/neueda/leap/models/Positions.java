package com.neueda.leap.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;
import com.neueda.leap.enums.PositionStatus;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.validators.PositionsValidator;

@Entity
@Table(name = "positions")
public class Positions {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "average_cost", nullable = false, precision = 19, scale = SCALE)
    private BigDecimal averageCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PositionStatus status = PositionStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "realized_pnl", precision = 19, scale = SCALE)
    private BigDecimal realizedPnL;

    

    @Transient
    private final PositionsValidator validator;

    public Positions() {
        this.validator = new PositionsValidator();
        this.status = PositionStatus.OPEN;
        this.openedAt = LocalDateTime.now();
    }

    public Positions(long accountId, String symbol, int quantity, BigDecimal averageCost) {
        this(accountId, symbol, quantity, averageCost, new PositionsValidator());
    }

    public Positions(long accountId, String symbol, int quantity, BigDecimal averageCost, PositionsValidator validator) {
        this.validator = validator;
        this.status = PositionStatus.OPEN;
        this.openedAt = LocalDateTime.now();

        validator.validateAccountId(accountId);
        validator.validateSymbol(symbol);
        validator.validateQuantity(quantity);
        validator.validateAverageCost(averageCost);

        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost.setScale(SCALE, ROUNDING_MODE);
    }

    public void apply(int quantityChange, BigDecimal price) throws InsufficientHoldingsException {
        validator.validateQuantityChange(quantityChange);
        validator.validatePrice(price);

        int newQuantity = this.quantity + quantityChange;

        validator.validateSufficientHoldings(newQuantity, symbol);

        if(quantityChange > 0){
            BigDecimal currentValue = averageCost.multiply(BigDecimal.valueOf(this.quantity));
            BigDecimal additionalValue = price.multiply(BigDecimal.valueOf(quantityChange));
            BigDecimal totalCost = currentValue.add(additionalValue);
            averageCost = totalCost.divide(BigDecimal.valueOf(newQuantity), SCALE, ROUNDING_MODE);
        }

        this.quantity = newQuantity;

        if(this.quantity == 0){
            averageCost = BigDecimal.ZERO;
            this.status = PositionStatus.CLOSED;
            this.closedAt = LocalDateTime.now();
        }
    }

    public BigDecimal marketValue(BigDecimal currentPrice){
        validator.validateCurrentPrice(currentPrice);
        return currentPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING_MODE);    
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public PositionStatus getStatus() {
        return status;
    }

    public void setStatus(PositionStatus status) {
        this.status = status;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public BigDecimal getRealizedPnL() {
        return realizedPnL;
    }

    public void setRealizedPnL(BigDecimal realizedPnL) {
        this.realizedPnL = realizedPnL;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o ){
            return true;
        }
        if(!(o instanceof Positions)){
            return false;
        }
        Positions position = (Positions) o;
        return accountId == position.accountId && symbol.equals(position.symbol);
    }

    @Override
    public int hashCode(){
        return Objects.hash(accountId,symbol);
    }

    @Override
    public String toString(){
        return "Positions{" +
                "accountId=" + accountId +
                ", symbol='" + symbol + '\'' +
                ", quantity=" + quantity +
                ", averageCost=" + averageCost +
                '}';
    }

}