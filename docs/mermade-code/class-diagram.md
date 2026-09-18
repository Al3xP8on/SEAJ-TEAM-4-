# Class Diagram - Order Management System

```mermaid
classDiagram
    class Order {
        -String id
        -Account account
        -Instrument instrument
        -long quantity
        -BigDecimal price
        -OrderSide side
        -OrderStatus status
        -LocalDateTime createdAt
        +execute()
        +cancel()
        +calculateTotalValue()
    }
    
    class Account {
        -Long id
        -String accountId
        -String holderName
        -BigDecimal cashBalance
        -AccountStatus status
        +isActive()
        +canAfford()
        +deductFunds()
    }
    
    class Instrument {
        -String symbol
        -String name
        -String assetClass
        -String currency
        -boolean tradable
        +isTradable()
        +getSymbol()
    }
    
    class OrderHistory {
        -Long id
        -String orderId
        -OrderStatus status
        -LocalDateTime changedOn
    }
    
    class SymbolPrice {
        -String symbol
        -BigDecimal price
        -ZonedDateTime timestamp
    }
    
    class Positions {
        -long accountId
        -String symbol
        -int quantity
        -BigDecimal averageCost
    }
    
    class PriceHistory {
        -String symbol
        -LocalDate priceDate
        -BigDecimal open
        -BigDecimal high
        -BigDecimal low
        -BigDecimal close
        -long volume
    }
    
    Order --> Account
    Order --> Instrument
    OrderHistory --> Order
    Positions --> Account
    Positions --> Instrument
    SymbolPrice --> Instrument
    PriceHistory --> Instrument
```

## Class Relationships

- **Order** → places trades against an **Account** and **Instrument**
- **OrderHistory** → tracks all status changes for an **Order**
- **Positions** → holds an **Account's** holdings in an **Instrument**
- **SymbolPrice** → current market price of an **Instrument**
- **PriceHistory** → historical OHLCV data for an **Instrument**
