FROM postgres:15

ENV POSTGRES_PASSWORD=my_secure_password

RUN echo "host all all 10.9.0.0/16 scram-sha-256" >> /usr/share/postgresql/15/pg_hba.conf.sample \
    && echo "host all all 10.9.0.0/16 scram-sha-256" >> /var/lib/postgresql/data/pg_hba.conf || true

RUN printf '#!/bin/bash\necho "host all all 10.9.0.0/16 scram-sha-256" >> "$PGDATA/pg_hba.conf"\n' > /docker-entrypoint-initdb.d/01-restrict-ip.sh \
    && chmod +x /docker-entrypoint-initdb.d/01-restrict-ip.sh

ARG SCHEMA_PATH_DEMO=db/SEAJ_db_DEMO.sql
ARG SCHEMA_PATH_DEV=db/SEAJ_db_DEV.sql

COPY ${SCHEMA_PATH_DEMO} /docker-entrypoint-initdb.d/02-demo-schema.sql
COPY ${SCHEMA_PATH_DEV} /opt/seaj/dev-schema.sql

RUN printf '#!/bin/bash\nset -e\npsql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL\n    CREATE DATABASE "SEAJ_db_DEV";\nEOSQL\npsql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "SEAJ_db_DEV" -f /opt/seaj/dev-schema.sql\n' > /docker-entrypoint-initdb.d/03-create-dev-db.sh \
    && chmod +x /docker-entrypoint-initdb.d/03-create-dev-db.sh

VOLUME /var/lib/postgresql/data

EXPOSE 5432