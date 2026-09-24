FROM postgres:15

ENV POSTGRES_PASSWORD=my_secure_password

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/15/pg_hba.conf.sample

RUN printf '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"\n' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

# Copy modular schema files in execution order
COPY infra/db/tables/00-setup.sql /docker-entrypoint-initdb.d/02-setup.sql
COPY infra/db/tables/01-clients.sql /docker-entrypoint-initdb.d/03-clients.sql
COPY infra/db/tables/02-accounts.sql /docker-entrypoint-initdb.d/04-accounts.sql
COPY infra/db/tables/03-instruments.sql /docker-entrypoint-initdb.d/05-instruments.sql
COPY infra/db/tables/04-orders.sql /docker-entrypoint-initdb.d/06-orders.sql
COPY infra/db/tables/05-positions.sql /docker-entrypoint-initdb.d/07-positions.sql
COPY infra/db/tables/06-order_history.sql /docker-entrypoint-initdb.d/08-order_history.sql
COPY infra/db/tables/07-price_history.sql /docker-entrypoint-initdb.d/09-price_history.sql
COPY infra/db/tables/08-current_prices.sql /docker-entrypoint-initdb.d/10-current_prices.sql
COPY infra/db/tables/index.sql /docker-entrypoint-initdb.d/11-indexes.sql


EXPOSE 5432