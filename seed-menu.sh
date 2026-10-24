#!/bin/bash
# ----------------------------------------------------------------------------
# seed-menu.sh — carga los 10 ítems de menú del sistema legado. El dump
# principal no trae la tabla `menu` (los ítems salen de
# repositorio_entel/version 3.8.1/prueba 20150921 1349.sql), así que se crean
# por POST /api/menu en orden: el contador del backend les asigna los códigos
# 1..10. Idempotente: si ya hay menú para ADMINISTRADOR, no hace nada.
# ----------------------------------------------------------------------------
set -uo pipefail
API="${API_URL:-http://localhost:8080}"
log() { echo "[seed-menu] $*"; }

existente="$(curl -sf "$API/api/menu/ADMINISTRADOR" || echo "[]")"
if [ -n "$existente" ] && [ "$existente" != "[]" ]; then
  log "El menú ya existe."
  exit 0
fi

# nombre|tipo|tipoUsuario|action
ITEMS=(
  "Aplicacion|SUBMENU|ADMINISTRADOR|aplicacionManagedBean"
  "Area|SUBMENU|ADMINISTRADOR|areaManagedBean"
  "Cargo|SUBMENU|ADMINISTRADOR|cargoManagedBean"
  "Ubicacion|SUBMENU|ADMINISTRADOR|ubicacionManagedBean"
  "Rol|SUBMENU|ADMINISTRADOR|rolManagedBean"
  "Suministro|SUBMENU|ADMINISTRADOR|suministroManagedBean"
  "Detalle|SUBMENU|ADMINISTRADOR|detalleManagedBean"
  "Reporte|SUBMENU|OPERADOR|reporteManagedBean"
  "Usuario|SUBMENU|ADMINISTRADOR|usuarioManagedBean"
  "Listado|ITEM|OPERADOR|.mantenimiento"
)
for fila in "${ITEMS[@]}"; do
  IFS='|' read -r nombre tipo tu action <<<"$fila"
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/menu" -H "Content-Type: application/json" \
    -d "{\"nombre\":\"$nombre\",\"tipo\":\"$tipo\",\"tipoUsuario\":\"$tu\",\"estado\":\"ACTIVO\",\"action\":\"$action\"}")"
  [ "$code" = "201" ] && log "$nombre creado." || log "$nombre: falló (HTTP $code)." >&2
done
