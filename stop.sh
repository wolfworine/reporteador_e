#!/bin/bash
# ----------------------------------------------------------------------------
# stop.sh — baja todo lo que levantó start.sh: frontend (host) y el stack de
# backend/docker-compose.yml (LocalStack + Kafka + backend), y barre
# contenedores Testcontainers huérfanos por si algo quedó vivo tras un kill
# forzado (ej. Dev Services de un `mvn test` interrumpido a mitad).
#
# Reescrito 2026-08-25 (mismo pedido que start.sh: "modifícalo para que use
# el compose"): el backend ya no corre como proceso `mvn quarkus:dev` en el
# host -- vive en el contenedor `entel-role-repository-backend` -- así que
# ya no tiene sentido buscarlo por puerto/PID y matarlo con `taskkill`; se
# baja con el resto del stack via `docker compose down`.
#
# Uso: ./stop.sh              -> baja todo, incluido el stack de docker compose
#      ./stop.sh --keep-docker  -> deja LocalStack+Kafka+backend corriendo,
#                                   solo baja el frontend (útil si vas a
#                                   seguir pegándole al backend sin reiniciarlo)
# ----------------------------------------------------------------------------
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT_DIR/.run"
KEEP_DOCKER=false
[ "${1:-}" = "--keep-docker" ] && KEEP_DOCKER=true

FRONTEND_PORT=5173
LOCALSTACK_CONTAINER="entel-role-repository-localstack"

log()  { echo "[stop] $*"; }
warn() { echo "[stop] ⚠️  $*" >&2; }

port_pid() {
  netstat -ano 2>/dev/null | grep -i "LISTENING" | grep -E ":$1[[:space:]]" | awk '{print $NF}' | head -n1 || true
}

# Mata un árbol de procesos por PID: intenta cierre suave primero
# (taskkill sin //F), y si sigue vivo tras unos segundos, fuerza con //F //T.
kill_tree() {
  local pid="$1" label="$2"
  [ -z "$pid" ] && return 0
  if ! tasklist //FI "PID eq $pid" 2>/dev/null | grep -q "$pid"; then
    return 0
  fi
  log "Deteniendo $label (PID $pid, cierre suave)..."
  taskkill //PID "$pid" //T >/dev/null 2>&1 || true
  for i in $(seq 1 10); do
    if ! tasklist //FI "PID eq $pid" 2>/dev/null | grep -q "$pid"; then
      log "$label detenido."
      return 0
    fi
    sleep 1
  done
  warn "$label seguía vivo tras 10s -- forzando (taskkill //F //T)."
  taskkill //PID "$pid" //F //T >/dev/null 2>&1 || true
}

# Mata TODO PID relevante para un servicio: el que reporta netstat como
# LISTENING en su puerto (fuente de verdad -- npm es un wrapper que arranca
# un proceso hijo real con OTRO PID, así que el `$!` que guardó start.sh casi
# nunca es el que termina escuchando el puerto) más el que haya en el
# pidfile por si acaso, sin duplicar.
stop_service() {
  local port="$1" label="$2" pidfile="$RUN_DIR/$3"
  local pids="" p
  p="$(port_pid "$port")"; [ -n "$p" ] && pids="$p"
  if [ -f "$pidfile" ]; then
    p="$(cat "$pidfile" 2>/dev/null || true)"
    if [ -n "$p" ] && [[ "$pids" != *"$p"* ]]; then
      pids="$pids $p"
    fi
  fi
  pids="$(echo "$pids" | xargs -n1 2>/dev/null | sort -u | xargs)"
  if [ -z "$pids" ]; then
    log "$label no estaba corriendo (puerto $port libre)."
  else
    for pid in $pids; do
      kill_tree "$pid" "$label"
    done
  fi
  rm -f "$pidfile"
}

# --- 1) Frontend (sigue en el host) ------------------------------------------
stop_service "$FRONTEND_PORT" "frontend" "frontend.pid"

# --- 2) Stack de backend/docker-compose.yml (localstack + kafka + backend) --
if [ "$KEEP_DOCKER" = true ]; then
  log "--keep-docker: localstack + kafka + backend se dejan corriendo."
else
  if [ "$(docker ps -a --filter "name=^${LOCALSTACK_CONTAINER}\$" -q)" != "" ]; then
    log "Bajando el stack (docker compose -f backend/docker-compose.yml down)..."
    (cd "$ROOT_DIR/backend" && docker compose down)
  else
    log "El stack de docker compose no estaba corriendo."
  fi

  # --- 3) Barrido de contenedores Testcontainers huérfanos -------------------
  # Cualquier Dev Services que haya quedado colgado (ej. si un `mvn test` se
  # interrumpió a mitad y Ryuk no alcanzó a limpiar su contenedor) queda
  # etiquetado `org.testcontainers=true` -- siempre seguro de borrar, son
  # efímeros por diseño.
  orphans="$(docker ps -a --filter "label=org.testcontainers=true" -q)"
  if [ -n "$orphans" ]; then
    log "Limpiando contenedores Testcontainers huérfanos..."
    docker rm -f $orphans >/dev/null 2>&1 || true
  fi
fi

log "Listo."
