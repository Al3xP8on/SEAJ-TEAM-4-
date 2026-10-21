FROM postgres:15

ENV POSTGRES_PASSWORD=my_secure_password

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/15/pg_hba.conf.sample

RUN printf '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"\n' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

# Copy modular schema files in execution order
COPY tables/00-setup.sql /docker-entrypoint-initdb.d/02-setup.sql
COPY tables/01-clients.sql /docker-entrypoint-initdb.d/03-clients.sql
COPY tables/02-accounts.sql /docker-entrypoint-initdb.d/04-accounts.sql
COPY tables/03-instruments.sql /docker-entrypoint-initdb.d/05-instruments.sql
COPY tables/04-orders.sql /docker-entrypoint-initdb.d/06-orders.sql
COPY tables/05-positions.sql /docker-entrypoint-initdb.d/07-positions.sql
COPY tables/06-order_history.sql /docker-entrypoint-initdb.d/08-order_history.sql
COPY tables/07-price_history.sql /docker-entrypoint-initdb.d/09-price_history.sql
COPY tables/08-current_prices.sql /docker-entrypoint-initdb.d/10-current_prices.sql
COPY tables/index.sql /docker-entrypoint-initdb.d/11-indexes.sql
COPY migrations/02-order-event-outbox.sql /docker-entrypoint-initdb.d/12-order-event-outbox.sql
COPY migrations/04-pre-trade-compliance.sql /docker-entrypoint-initdb.d/13-pre-trade-compliance.sql
COPY migrations/03-account-status-history.sql /docker-entrypoint-initdb.d/13-account-status-history.sql

COPY migrations/04-market-catalogue.sql /docker-entrypoint-initdb.d/14-market-catalogue.sql

COPY migrations/05-bond-cash-market-catalogue.sql /docker-entrypoint-initdb.d/15-bond-cash-market-catalogue.sql

COPY migrations/06-actual-cash-catalogue.sql /docker-entrypoint-initdb.d/16-actual-cash-catalogue.sql

COPY migrations/07-expand-commodities.sql /docker-entrypoint-initdb.d/17-expand-commodities.sql

EXPOSE 5432
