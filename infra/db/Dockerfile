FROM postgres:15

ENV POSTGRES_PASSWORD=my_secure_password

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/15/pg_hba.conf.sample

RUN printf '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"\n' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

# Copy individual table files organized by service
COPY infra/db/tables /opt/seaj/tables

# Build DEMO schema by concatenating individual table files
RUN printf '#!/bin/bash\nset -e\nTABLES_DIR=/opt/seaj/tables\nOUTPUT_FILE=/docker-entrypoint-initdb.d/02-demo-schema.sql\n{\n  cat "$TABLES_DIR/shared/00-setup.sql"\n  echo ""\n  cat "$TABLES_DIR/shared/01-clients.sql"\n  echo ""\n  cat "$TABLES_DIR/shared/03-instruments.sql"\n  echo ""\n  cat "$TABLES_DIR/account-service/02-accounts.sql"\n  echo ""\n  cat "$TABLES_DIR/order-service/04-orders.sql"\n  echo ""\n  cat "$TABLES_DIR/order-service/06-order_history.sql"\n  echo ""\n  cat "$TABLES_DIR/positions-service/05-positions.sql"\n  echo ""\n  cat "$TABLES_DIR/positions-service/07-price_history.sql"\n  echo ""\n  cat "$TABLES_DIR/positions-service/08-current_prices.sql"\n  echo ""\n  cat "$TABLES_DIR/index.sql"\n} > "$OUTPUT_FILE"\n' > /docker-entrypoint-initdb.d/02-build-demo-schema.sh \
    && chmod +x /docker-entrypoint-initdb.d/02-build-demo-schema.sh \
    && /docker-entrypoint-initdb.d/02-build-demo-schema.sh

# Build DEV schema the same way  
RUN printf '#!/bin/bash\nset -e\nTABLES_DIR=/opt/seaj/tables\nOUTPUT_FILE=/opt/seaj/DEV-schema.sql\n{\n  cat "$TABLES_DIR/shared/00-setup.sql"\n  echo ""\n  cat "$TABLES_DIR/shared/01-clients.sql"\n  echo ""\n  cat "$TABLES_DIR/shared/03-instruments.sql"\n  echo ""\n  cat "$TABLES_DIR/account-service/02-accounts.sql"\n  echo ""\n  cat "$TABLES_DIR/order-service/04-orders.sql"\n  echo ""\n  cat "$TABLES_DIR/order-service/06-order_history.sql"\n  echo ""\n  cat "$TABLES_DIR/positions-service/05-positions.sql"\n  echo ""\n  cat "$TABLES_DIR/positions-service/07-price_history.sql"\n  echo ""\n  cat "$TABLES_DIR/positions-service/08-current_prices.sql"\n  echo ""\n  cat "$TABLES_DIR/index.sql"\n} > "$OUTPUT_FILE"\n' > /docker-entrypoint-initdb.d/02-build-dev-schema.sh \
    && chmod +x /docker-entrypoint-initdb.d/02-build-dev-schema.sh \
    && /docker-entrypoint-initdb.d/02-build-dev-schema.sh

# Create and run DEV database initialization
RUN printf '#!/bin/bash\nset -e\npsql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL\n    CREATE DATABASE "SEAJ_db_DEV";\nEOSQL\npsql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "SEAJ_db_DEV" -f /opt/seaj/DEV-schema.sql\n' > /docker-entrypoint-initdb.d/03-create-dev-db.sh \
    && chmod +x /docker-entrypoint-initdb.d/03-create-dev-db.sh

EXPOSE 5432