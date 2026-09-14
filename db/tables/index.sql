-- SEAJ OLTP schema - Master Index
-- This file orchestrates the creation of the entire database schema
-- Execute in the following order to ensure dependencies are met:
-- 1. Setup (extensions and enums)
-- 2. Clients
-- 3. Accounts (depends on Clients)
-- 4. Instruments
-- 5. Orders (depends on Accounts and Instruments)
-- 6. Positions (depends on Accounts and Instruments)
-- 7. Order History (depends on Orders)

\i 00-setup.sql
\i 01-clients.sql
\i 02-accounts.sql
\i 03-instruments.sql
\i 04-orders.sql
\i 05-positions.sql
\i 06-order_history.sql
