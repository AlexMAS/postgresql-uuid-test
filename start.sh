#!/usr/bin/env sh

set -e

export COMMAND=$@

docker compose up -d --force-recreate
docker logs -f postgresql-uuid-test
