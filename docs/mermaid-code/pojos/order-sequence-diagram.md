# Order Sequence Diagram

```mermaid
sequenceDiagram
    participant User
    participant Order
    participant Account
    participant Instrument
    participant OrderHistory

    User->>Order: Create Order(account, instrument, qty, price, side)
    
    Order->>Account: validateAccount()
    Account-->>Order: Account is Active
    
    Order->>Instrument: validateInstrument()
    Instrument-->>Order: Instrument is Tradable
    
    Order->>Order: validateQuantity() & validatePrice()
    Order->>Order: Set status = NEW
    
    alt Order Valid
        Order->>OrderHistory: Record NEW status
        OrderHistory-->>Order: Created
        Order-->>User: Order Created (ID, Status=NEW)
    else Validation Failed
        Order-->>User: OrderException thrown
    end
    
    User->>Order: execute()
    
    Order->>Account: canAfford(totalValue)?
    alt Sufficient Funds
        Account->>Account: deductFunds()
        Account-->>Order: Funds deducted
        Order->>Order: Set status = EXECUTED
        Order->>OrderHistory: Record EXECUTED status
        OrderHistory-->>Order: Status change recorded
        Order-->>User: Order Executed Successfully
    else Insufficient Funds
        Account-->>Order: InsufficientFundsException
        Order->>Order: Set status = REJECTED
        Order->>OrderHistory: Record REJECTED status
        Order-->>User: Order Rejected - Insufficient Funds
    end
```

## Order Lifecycle Flow

1. **Order Creation**: User submits order with account, instrument, quantity, and price
2. **Validation**: Order validates account (active), instrument (tradable), and price/quantity
3. **Status Set to NEW**: Order is created with NEW status and audit record
4. **Order Execution**: User requests order execution
5. **Fund Check**: System checks if account has sufficient funds
6. **Execution or Rejection**: 
   - Success: Status changed to EXECUTED, funds deducted, audit record created
   - Failure: Status set to REJECTED, no funds deducted, audit record created
