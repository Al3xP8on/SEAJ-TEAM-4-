-- Schema for first draft of the SEAJ db:

CREATE TABLE accounts (
    account_id      SERIAL PRIMARY KEY,
    client_id       INTEGER NOT NULL,
    account_type    TEXT NOT NULL,
    opened_date     DATE NOT NULL,
    currency        TEXT NOT NULL
);

CREATE TABLE instruments (
    instrument_id   SERIAL PRIMARY KEY,
    ticker          TEXT NOT NULL,
    name            TEXT NOT NULL,
    asset_class     TEXT NOT NULL
);

CREATE TABLE orders (
    order_id        SERIAL PRIMARY KEY,
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id   INTEGER NOT NULL REFERENCES instruments(instrument_id),
    order_type      TEXT NOT NULL,
    quantity        NUMERIC(10, 2) NOT NULL,
    price           NUMERIC(10, 2) NOT NULL,
    order_date      DATE NOT NULL
);

CREATE TABLE portfolio (
    portfolio_id    SERIAL PRIMARY KEY,
    portfolio_name  TEXT NOT NULL,
    description     TEXT
);

CREATE TABLE portfolio_instruments (
    portfolio_id        INTEGER NOT NULL REFERENCES portfolio(portfolio_id),
    instrument_id       INTEGER NOT NULL REFERENCES instruments(instrument_id),
    target_weight_pct   NUMERIC(5, 2) NOT NULL,
    PRIMARY KEY (portfolio_id, instrument_id)
);

CREATE TABLE account_portfolios (
    account_id      INTEGER NOT NULL REFERENCES accounts(account_id),
    portfolio_id    INTEGER NOT NULL REFERENCES portfolio(portfolio_id),
    subscribed_date DATE NOT NULL,
    PRIMARY KEY (account_id, portfolio_id)
);

-- Draft 1 dummy data for SEAJ db

INSERT INTO accounts (client_id, account_type, opened_date, currency) VALUES
    (1, 'ISA', '2023-01-15', 'GBP'),
    (2, 'GIA', '2023-03-01', 'GBP'),
    (3, 'SIPP', '2022-11-01', 'GBP');

INSERT INTO instruments (ticker, name, asset_class) VALUES
    ('GLBEQ1', 'Global Equity Index Fund', 'Equity'),
    ('CORPB1', 'Sterling Corporate Bond Fund', 'Bond'),
    ('GILT10', 'UK 10-Year Gilt', 'Bond'),
    ('CASHGBP', 'Cash (GBP)', 'Cash');

INSERT INTO orders (account_id, instrument_id, order_type, quantity, price, order_date) VALUES
    (1, 1, 'BUY', 100.00, 12.50, '2023-01-16'),
    (1, 2, 'BUY', 50.00, 25.00, '2023-01-16'),
    (2, 1, 'BUY', 200.00, 12.75, '2023-03-02'),
    (3, 3, 'BUY', 75.00, 98.20, '2022-11-02'),
    (3, 3, 'SELL', 25.00, 99.10, '2023-06-01');

INSERT INTO portfolio (portfolio_name, description) VALUES
    ('Balanced Growth', '40% equity, 30% bonds, 30% cash'),
    ('Income Focus', '60% bonds, 30% gilts, 10% cash');

INSERT INTO portfolio_instruments (portfolio_id, instrument_id, target_weight_pct) VALUES
    (1, 1, 40.00),
    (1, 2, 30.00),
    (1, 4, 30.00),
    (2, 2, 60.00),
    (2, 3, 30.00),
    (2, 4, 10.00);

INSERT INTO account_portfolios (account_id, portfolio_id, subscribed_date) VALUES
    (1, 1, '2023-01-15'),
    (2, 1, '2023-03-01'),
    (3, 2, '2022-11-01');
