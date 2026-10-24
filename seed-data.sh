#!/bin/bash
# ----------------------------------------------------------------------------
# seed-data.sh — carga en DynamoDB (LocalStack) los datos del sistema legado:
# áreas, cargos, ubicaciones, aplicación, roles, suministros y atributos.
# Lee el dump MySQL versionado en database/seed/legacy-dump.sql con el CLI de
# database/ (migrate). Lo llama start.sh con el backend ya healthy.
#
# Idempotente: si /api/areas ya devuelve datos no hace nada. Forzar la
# recarga: ./seed-data.sh --force (migrate reescribe las mismas claves, no
# duplica). Los usuarios los crea seed-users.sh (el CLI no migra `usuario`).
# Solo para desarrollo local (el CLI rechaza --target=aws).
# ----------------------------------------------------------------------------
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API="${API_URL:-http://localhost:8080}"
DUMP="$ROOT_DIR/database/seed/legacy-dump.sql"
JAR="$ROOT_DIR/database/target/migration-cli.jar"

log() { echo "[seed-data] $*"; }

if [ "${1:-}" != "--force" ]; then
  areas="$(curl -sf "$API/api/areas" || echo "")"
  if [ -n "$areas" ] && [ "$areas" != "[]" ]; then
    log "Ya hay datos cargados (use --force para recargar)."
    exit 0
  fi
fi

if [ ! -f "$DUMP" ]; then log "No existe $DUMP" >&2; exit 1; fi

if [ ! -f "$JAR" ]; then
  log "Compilando el CLI de migración (una sola vez)..."
  "$ROOT_DIR/backend/mvnw" -q -f "$ROOT_DIR/database/pom.xml" package -DskipTests || { log "Falló la compilación del CLI." >&2; exit 1; }
fi

log "Migrando el dump a LocalStack..."
java -jar "$JAR" migrate "$DUMP" --target=localstack
