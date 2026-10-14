# The demo's one-off seed job: psql from the official image, plus Python for the importer (standard library only)
FROM postgres:17-alpine

RUN apk add --no-cache python3 tzdata

WORKDIR /demo
COPY import_real_data.py seed.sql seed.sh ./
RUN chmod +x seed.sh

ENTRYPOINT ["/demo/seed.sh"]
