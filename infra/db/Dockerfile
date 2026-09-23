FROM postgres:15

ENV POSTGRES_PASSWORD=my_secure_password

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/15/pg_hba.conf.sample

RUN printf '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"\n' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

# Copy individual table files organized by service
COPY infra/db/tables /opt/seaj/tables

# Build schema files by concatenating individual table files in correct FK dependency order
RUN cat > /docker-entrypoint-initdb.d/02-build-demo-schema.sh << 'EOFBUILD'
#!/bin/bash
set -e

TABLES_DIR=/opt/seaj/tables
OUTPUT_FILE=/docker-entrypoint-initdb.d/02-demo-schema.sql

{
    echo "-- SEAJ OLTP schema (Postgres) - Generated from organized service table files"
    echo "-- Database: DEMO"
    echo "-- $(date)"
    echo ""
    
    echo "-- =========================================="
    echo "-- SETUP: Extensions and Enums"
    echo "-- =========================================="
    cat "$TABLES_DIR/shared/00-setup.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- SHARED TABLES: Reference Data"
    echo "-- =========================================="
    cat "$TABLES_DIR/shared/01-clients.sql"
    echo ""
    cat "$TABLES_DIR/shared/03-instruments.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- ACCOUNT SERVICE TABLES"
    echo "-- =========================================="
    cat "$TABLES_DIR/account-service/02-accounts.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- ORDER SERVICE TABLES"
    echo "-- =========================================="
    cat "$TABLES_DIR/order-service/04-orders.sql"
    echo ""
    cat "$TABLES_DIR/order-service/06-order_history.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- POSITIONS SERVICE TABLES"
    echo "-- =========================================="
    cat "$TABLES_DIR/positions-service/05-positions.sql"
    echo ""
    cat "$TABLES_DIR/positions-service/07-price_history.sql"
    echo ""
    cat "$TABLES_DIR/positions-service/08-current_prices.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- INDICES"
    echo "-- =========================================="
    cat "$TABLES_DIR/index.sql"
    
} > "$OUTPUT_FILE"

echo "Schema generated: $OUTPUT_FILE ($(wc -l < "$OUTPUT_FILE") lines)"
EOFBUILD

chmod +x /docker-entrypoint-initdb.d/02-build-demo-schema.sh

# Build DEV schema the same way
RUN cat > /docker-entrypoint-initdb.d/02-build-dev-schema.sh << 'EOFBUILD'
#!/bin/bash
set -e

TABLES_DIR=/opt/seaj/tables
OUTPUT_FILE=/opt/seaj/DEV-schema.sql

{
    echo "-- SEAJ OLTP schema (Postgres) - Generated from organized service table files"
    echo "-- Database: DEV"
    echo "-- $(date)"
    echo ""
    
    echo "-- =========================================="
    echo "-- SETUP: Extensions and Enums"
    echo "-- =========================================="
    cat "$TABLES_DIR/shared/00-setup.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- SHARED TABLES: Reference Data"
    echo "-- =========================================="
    cat "$TABLES_DIR/shared/01-clients.sql"
    echo ""
    cat "$TABLES_DIR/shared/03-instruments.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- ACCOUNT SERVICE TABLES"
    echo "-- =========================================="
    cat "$TABLES_DIR/account-service/02-accounts.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- ORDER SERVICE TABLES"
    echo "-- =========================================="
    cat "$TABLES_DIR/order-service/04-orders.sql"
    echo ""
    cat "$TABLES_DIR/order-service/06-order_history.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- POSITIONS SERVICE TABLES"
    echo "-- =========================================="
    cat "$TABLES_DIR/positions-service/05-positions.sql"
    echo ""
    cat "$TABLES_DIR/positions-service/07-price_history.sql"
    echo ""
    cat "$TABLES_DIR/positions-service/08-current_prices.sql"
    echo ""
    
    echo "-- =========================================="
    echo "-- INDICES"
    echo "-- =========================================="
    cat "$TABLES_DIR/index.sql"
    
} > "$OUTPUT_FILE"

echo "Schema generated: $OUTPUT_FILE ($(wc -l < "$OUTPUT_FILE") lines)"
EOFBUILD

chmod +x /docker-entrypoint-initdb.d/02-build-dev-schema.sh

# Run the build scripts
RUN /docker-entrypoint-initdb.d/02-build-demo-schema.sh && /docker-entrypoint-initdb.d/02-build-dev-schema.sh

# Create and run DEV database initialization
RUN cat > /docker-entrypoint-initdb.d/03-create-dev-db.sh << 'EOFDEV'
#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE "SEAJ_db_DEV";
EOSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "SEAJ_db_DEV" -f /opt/seaj/DEV-schema.sql
EOFDEV

chmod +x /docker-entrypoint-initdb.d/03-create-dev-db.sh

EXPOSE 5432