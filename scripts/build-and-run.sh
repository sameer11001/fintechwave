#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# FintechWave — Build & Run
# Usage:
#   ./scripts/build-and-run.sh           # build + start all services
#   ./scripts/build-and-run.sh down      # stop and remove containers
#   ./scripts/build-and-run.sh logs      # tail logs for all services
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker/docker-compose.yml"

case "${1:-up}" in
  up)
    echo "⚙️  Building Maven artifacts..."
    mvn -f "$REPO_ROOT/pom.xml" clean package -DskipTests -q

    echo "🐳  Building images and starting containers..."
    docker compose -f "$COMPOSE_FILE" up --build -d

    echo ""
    echo "✅  All services are starting. Endpoints:"
    echo "    IAM Service         → http://localhost:8081"
    echo "    IAM Swagger UI      → http://localhost:8081/swagger-ui.html"
    echo "    Transaction Service → http://localhost:8082"
    echo ""
    echo "📋  Tail logs with: ./scripts/build-and-run.sh logs"
    ;;

  down)
    echo "🛑  Stopping all containers..."
    docker compose -f "$COMPOSE_FILE" down
    ;;

  logs)
    docker compose -f "$COMPOSE_FILE" logs -f
    ;;

  *)
    echo "Unknown command: ${1}"
    echo "Usage: $0 [up|down|logs]"
    exit 1
    ;;
esac
