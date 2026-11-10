#!/usr/bin/env bash
# Start Zookeeper, Kafka, Postgres, and Redis for local JVM development.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILES=(-f docker-compose.yml)
USE_ALT_PG=false

for arg in "$@"; do
  case "$arg" in
    --alt-postgres)
      USE_ALT_PG=true
      ;;
    -h|--help)
      echo "Usage: $0 [--alt-postgres]"
      echo "  --alt-postgres  Map Postgres to host 5433 (see docker-compose.dev.yml)"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ "$USE_ALT_PG" == true ]]; then
  COMPOSE_FILES+=(-f docker-compose.dev.yml)
  echo "Postgres will listen on localhost:5433 — run the app with POSTGRES_PORT=5433"
fi

docker compose "${COMPOSE_FILES[@]}" up -d zookeeper kafka postgres redis
docker compose "${COMPOSE_FILES[@]}" ps
