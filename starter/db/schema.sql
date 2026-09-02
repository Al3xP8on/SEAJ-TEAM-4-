
CREATE TABLE accounts (
    account_id   SERIAL PRIMARY KEY,
    account_name TEXT NOT NULL,
    account_type TEXT NOT NULL CHECK (account_type IN ('STANDARD', 'MICRO', 'MINI', 'MANAGED', 'DEMO')),
    currency     TEXT NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE instruments (
    instrument_id SERIAL PRIMARY KEY,
    ticker        TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    asset_class   TEXT NOT NULL,
    currency      TEXT NOT NULL
);

CREATE TABLE portfolios (
    portfolio_id   SERIAL PRIMARY KEY,
    portfolio_name TEXT NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    order_id      SERIAL PRIMARY KEY,
    account_id    INT      NOT NULL REFERENCES accounts(account_id),
    instrument_id INT      NOT NULL REFERENCES instruments(instrument_id),
    side          TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity      INT NOT NULL CHECK (quantity > 0),
    price         INT NOT NULL CHECK (price > 0),
    status        TEXT NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'FILLED', 'CANCELLED', 'REJECTED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- holdings: how much of each instrument a portfolio currently holds
CREATE TABLE portfolio_instruments (
    portfolio_id  INT NOT NULL REFERENCES portfolios(portfolio_id),
    instrument_id INT NOT NULL REFERENCES instruments(instrument_id),
    quantity      INT NOT NULL CHECK (quantity >= 0),
    PRIMARY KEY (portfolio_id, instrument_id)
);

-- link accounts to the portfolios they hold
CREATE TABLE account_portfolio (
    account_id   INT NOT NULL REFERENCES accounts(account_id),
    portfolio_id INT NOT NULL REFERENCES portfolios(portfolio_id),
    PRIMARY KEY (account_id, portfolio_id)
);
