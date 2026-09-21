-- SEAJ OLTP schema - Clients Table
-- trading clients (1 client : many accounts)

CREATE TABLE clients (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id       VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_on      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_clients_client_id ON clients(client_id);
CREATE INDEX idx_clients_status ON clients(status);

-- Seed data
INSERT INTO clients (client_id, name, email, phone, status) VALUES
    ('CLI-0001', 'Alice Johnson', 'alice.johnson@email.com', '+44-1234-567890', 'ACTIVE'),
    ('CLI-0002', 'Brian Osei', 'brian.osei@email.com', '+44-1234-567891', 'ACTIVE'),
    ('CLI-0003', 'Carla Mendes', 'carla.mendes@email.com', '+44-1234-567892', 'SUSPENDED'),
    ('CLI-0004', 'Diane Carter', 'diane.carter@email.com', '+44-1234-567893', 'ACTIVE'),
    ('CLI-0005', 'Ethan Brooks', 'ethan.brooks@email.com', '+44-1234-567894', 'ACTIVE');
