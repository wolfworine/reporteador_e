#!/bin/bash
# ----------------------------------------------------------------------------
# reset-data.sh — vuelve la base local al estado de referencia ("rollback"):
# borra el volumen de LocalStack (DynamoDB) y recarga solo lo migrado:
#   17.873 items del dump + 10 ítems de menú + 4 usuarios.
# Descarta todo lo demás (p. ej. reportes generados en pruebas).
# Uso: ./reset-data.sh
# ----------------------------------------------------------------------------
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "[reset] Bajando el stack y borrando el volumen de datos..."
(cd "$ROOT_DIR/backend" && docker compose down -v)
echo "[reset] Levantando de nuevo y recargando datos..."
"$ROOT_DIR/start.sh"
