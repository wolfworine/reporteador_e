#!/bin/bash
# ----------------------------------------------------------------------------
# seed-users.sh — crea los usuarios del sistema legado en el backend local.
# Los llama start.sh después de que el backend está healthy. Idempotente: solo
# crea los que aún no existen (el backend responde 409 si el email ya existe).
# La herramienta database/ no migra la tabla `usuario` (fuera de alcance), así
# que estos usuarios se cargan por la API, que guarda la contraseña hasheada.
# Solo para desarrollo local (LocalStack): no usar contra un entorno real.
# ----------------------------------------------------------------------------
set -uo pipefail

API="${API_URL:-http://localhost:8080}"

# email|nombres|apellidos|password|tipo
USUARIOS=(
  "admin@wisevisioncorp.com|Mark|Falcon|admin1|ADMINISTRADOR"
  "employer@wisevisioncorp.com|Cesar|Valenzuela|employer|OPERADOR"
  "Raven@hotmail.com|Jorge|Raven|12345|OPERADOR"
  "employer2@hotmail.com|hola|hola|employer2|OPERADOR"
)

log() { echo "[seed-users] $*"; }

for i in $(seq 1 30); do
  curl -sf "$API/q/health/ready" >/dev/null 2>&1 && break
  [ "$i" = "30" ] && { log "El backend no responde en $API; no se cargan usuarios." >&2; exit 1; }
  sleep 2
done

for fila in "${USUARIOS[@]}"; do
  IFS='|' read -r email nombres apellidos password tipo <<<"$fila"
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/usuarios" \
    -H "Content-Type: application/json" \
    -d "{\"nombres\":\"$nombres\",\"apellidos\":\"$apellidos\",\"email\":\"$email\",\"password\":\"$password\",\"tipo\":\"$tipo\"}")"
  if [ "$code" = "201" ]; then log "$email creado.";
  elif [ "$code" = "409" ]; then log "$email ya existe.";
  else log "$email: falló (HTTP $code)." >&2; fi
done
