-- SEAJ OLTP schema - Accounts Table
-- trading accounts and cash balances (users and accounts combined)

CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(32) NOT NULL UNIQUE,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    cash_balance    NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (cash_balance >= 0),
    status          account_status NOT NULL DEFAULT 'ACTIVE',
    version         INT NOT NULL DEFAULT 0,
    created_on      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_updated    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_accounts_account_id ON accounts(account_id);
CREATE INDEX idx_accounts_username ON accounts(username);
CREATE INDEX idx_accounts_status ON accounts(status);

-- Seed data
INSERT INTO accounts (account_id, username, password_hash, name, email, phone, cash_balance, status) VALUES
    ('ACC-0001', 'alice', '$2a$10$u9fH8wJ4K3mN5pQrXzYaBehc3RvKQ2L9mP7nK4qR8sT2uV3wXyZ9i', 'Alice Johnson', 'alice.johnson@email.com', '+44-1234-567890', 10000.00, 'ACTIVE'),
    ('ACC-0002', 'brian', '$2a$10$k8lM9nO0pQ1rS2tU3vW4xYahb2cD5eF6gH7iJ8kL9mN0oP1qR2sT', 'Brian Osei', 'brian.osei@email.com', '+44-1234-567891', 5000.00, 'ACTIVE'),
    ('ACC-0003', 'carla', '$2a$10$v5wX9yZ1aB2cD3eF4gH5iJaj3kL6mN7oP8qR9sT0uV1wX2yZ3aB', 'Carla Mendes', 'carla.mendes@email.com', '+44-1234-567892', 0.00, 'SUSPENDED'),
    ('ACC-0004', 'diane', '$2a$10$n2oP4qR6sT8uV0wX1yZ2aBbk4lM7nO9pQ0rS1tU2vW3xY4zA5bC', 'Diane Carter', 'diane.carter@email.com', '+44-1234-567893', 25000.00, 'ACTIVE'),
    ('ACC-0005', 'ethan', '$2a$10$p6qR8sT0uV2wX3yZ4aB5cCcl5mN8oP0qR1sT2uV3wX4yZ5aB6cD', 'Ethan Brooks', 'ethan.brooks@email.com', '+44-1234-567894', 8000.00, 'ACTIVE');
