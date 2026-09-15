-- SEAJ OLTP schema - Setup
-- Extensions and Enums

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- for gen_random_uuid()

-- Enums (section 6.4)
CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'CLOSED');
CREATE TYPE order_side     AS ENUM ('BUY', 'SELL');
CREATE TYPE order_status   AS ENUM ('NEW', 'FILLED', 'REJECTED', 'CANCELLED');
