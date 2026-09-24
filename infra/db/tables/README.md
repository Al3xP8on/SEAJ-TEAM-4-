# SEAJ Database Tables Organization

Database tables are now organized by service for better clarity and maintainability.

## Structure

```
tables/
├── shared/                    # Shared reference data
│   ├── 00-setup.sql          # Database setup & extensions
│   ├── 01-clients.sql        # Client/customer data
│   └── 03-instruments.sql    # Trading instruments
│
├── account-service/          # Account Service tables
│   └── 02-accounts.sql       # Account information & balances
│
├── order-service/            # Order Service tables
│   ├── 04-orders.sql         # Order records
│   └── 06-order_history.sql  # Order state changes
│
├── positions-service/        # Positions Service tables
│   ├── 05-positions.sql      # Current holdings
│   ├── 07-price_history.sql  # Historical pricing
│   └── 08-current_prices.sql # Latest market prices
│
└── index.sql                 # Database indices
```

## Service Mapping

### Shared Tables (Reference Data)
- **clients** - Customer information (used by all services)
- **instruments** - Tradable securities (used by orders & positions)

### Account Service
- **accounts** - Trading accounts, balances, status

### Order Service
- **orders** - Order records (audit trail)
- **order_history** - Order state changes over time

### Positions Service
- **positions** - Current holdings per account
- **price_history** - Historical price data
- **current_prices** - Latest market prices

## Schema Files

The actual schema is defined in consolidated files used by Docker:

- `../SEAJ_db_DEMO.sql` - Demo environment (development)
- `../SEAJ_db_DEV.sql` - Dev environment

These files contain all table definitions and are executed by the Dockerfile during container initialization.

## Individual Table Files

The individual SQL files (00-setup.sql, 01-clients.sql, etc.) are provided for:
- Reference and documentation
- Manual database operations
- Understanding table structure
- Future service extraction to separate databases

## Adding New Tables

1. Create SQL file with appropriate number prefix in service folder
2. Add table definition to corresponding schema file (SEAJ_db_DEMO.sql, etc.)
3. Update this README
4. Rebuild Docker image

## Dependencies

```
Shared (Reference Data)
    ↓
    ├── Accounts Service (accounts table)
    ├── Order Service (orders, order_history)
    └── Positions Service (positions, price_history, current_prices)
```

- All services depend on **shared** tables (clients, instruments)
- Services don't directly depend on each other (separate tables per service)

---

**Status:** Tables organized by service  
**Last Updated:** 2026-09-23  
**Docker Integration:** Uses consolidated schema files (SEAJ_db_DEMO.sql, SEAJ_db_DEV.sql)
