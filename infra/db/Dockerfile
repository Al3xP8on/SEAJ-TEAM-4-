FROM postgres:15

ENV POSTGRES_PASSWORD=my_secure_password

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/15/pg_hba.conf.sample

RUN printf '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"\n' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

ARG SCHEMA_PATH_DEMO=db/SEAJ_db_DEMO.sql
ARG SCHEMA_PATH_DEV=db/SEAJ_db_DEV.sql

COPY ${SCHEMA_PATH_DEMO} /docker-entrypoint-initdb.d/02-demo-schema.sql
COPY ${SCHEMA_PATH_DEV} /opt/seaj/DEV-schema.sql

# Copy individual table definitions from infra/db/tables/
COPY infra/db/tables/00-setup.sql /docker-entrypoint-initdb.d/04-setup.sql
COPY infra/db/tables/01-clients.sql /docker-entrypoint-initdb.d/05-clients.sql
COPY infra/db/tables/02-accounts.sql /docker-entrypoint-initdb.d/06-accounts.sql
COPY infra/db/tables/03-instruments.sql /docker-entrypoint-initdb.d/07-instruments.sql
COPY infra/db/tables/04-orders.sql /docker-entrypoint-initdb.d/08-orders.sql
COPY infra/db/tables/05-positions.sql /docker-entrypoint-initdb.d/09-positions.sql
COPY infra/db/tables/06-order_history.sql /docker-entrypoint-initdb.d/10-order_history.sql
COPY infra/db/tables/07-price_history.sql /docker-entrypoint-initdb.d/11-price_history.sql
COPY infra/db/tables/08-current_prices.sql /docker-entrypoint-initdb.d/12-current_prices.sql
COPY infra/db/tables/index.sql /docker-entrypoint-initdb.d/13-index.sql

RUN printf '#!/bin/bash\nset -e\npsql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL\n    CREATE DATABASE "SEAJ_db_DEV";\nEOSQL\npsql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "SEAJ_db_DEV" -f /opt/seaj/DEV-schema.sql\n' > /docker-entrypoint-initdb.d/03-create-dev-db.sh \
    && chmod +x /docker-entrypoint-initdb.d/03-create-dev-db.sh


EXPOSE 5432