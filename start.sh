#!/bin/bash
# ----------------------------------------------------------------------------
# start.sh — levanta el proyecto completo para desarrollo local:
#   1) backend/docker-compose.yml (LocalStack + Kafka + backend, los 3 juntos)
#   2) frontend (Vite dev server, npm run dev, puerto 5173)
#
# Idempotente: `docker compose up -d` ya valida el archivo y reconcilia el
# estado por su cuenta (no toca lo que ya está corriendo y coincide con la
# config); el frontend sigue el mismo chequeo de puerto que antes, seguro de
# correr varias veces seguidas.
#
# Reescrito 2026-08-25 (pedido explícito del usuario: "modifícalo para que
# use el compose"): hasta ahora este script levantaba el backend a mano con
# `./mvnw quarkus:dev` y dejaba que Quarkus Dev Services auto-provisionara
# Kafka vía Testcontainers -- de ahí salían los contenedores Kafka + el
# reaper `ryuk` sueltos, fuera de cualquier `docker-compose`, que el usuario
# encontró al cerrar el proyecto. Ahora `backend/docker-compose.yml` trae los
# 3 servicios (`localstack`, `kafka`, `backend` -- el jar empaquetado,
# corriendo en un contenedor, no `quarkus:dev`), así que este script delega
# todo eso a `docker compose up -d` en vez de reimplementar esa lógica acá.
#
# Si cambiaste código Java desde el último build de la imagen, corré
# `--build` (ver más abajo) para repaquetizar y reconstruir antes de subir.
#
# La base de datos (herramienta CLI de migración, ../database/) no es un
# servicio persistente -- se ejecuta bajo demanda
# (`java -jar database/target/migration-cli.jar ...`), no la levanta este
# script. Su docker-compose.yml usa el MISMO puerto 4566 que el de backend/
# con datos distintos -- nunca correr los dos LocalStack a la vez.
#
# Uso: ./start.sh          -> levanta todo con la imagen del backend ya
#                              construida (rápido; falla si nunca se
#                              construyó -- corré --build la primera vez)
#      ./start.sh --build  -> además repaquetiza (`./mvnw package -DskipTests`)
#                              y reconstruye la imagen del backend antes de
#                              subir el stack -- usalo después de tocar
#                              código Java
# ----------------------------------------------------------------------------
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT_DIR/.run"
mkdir -p "$RUN_DIR"

FRONTEND_PORT=5173
CONTAINERS=(entel-role-repository-localstack entel-role-repository-kafka entel-role-repository-backend)

BUILD=false
[ "${1:-}" = "--build" ] && BUILD=true

log()  { echo "[start] $*"; }
warn() { echo "[start] ⚠️  $*" >&2; }
err()  { echo "[start] ❌ $*" >&2; }

port_pid() {
  # Devuelve el PID que escucha en el puerto dado (Windows netstat vía Git
  # Bash), o vacío -- nunca falla con exit != 0 (grep sin match no debe
  # abortar el script bajo `set -e pipefail`).
  netstat -ano 2>/dev/null | grep -i "LISTENING" | grep -E ":$1[[:space:]]" | awk '{print $NF}' | head -n1 || true
}

# --- 0) Prechequeos ---------------------------------------------------------
if ! docker info >/dev/null 2>&1; then
  err "Docker no está corriendo (o no responde). Iniciá Docker Desktop y volvé a correr este script."
  exit 1
fi
log "Docker OK."

# --- 1) Backend + LocalStack + Kafka (docker compose de backend/) ----------
if [ "$BUILD" = true ]; then
  log "Repaquetizando el backend (./mvnw package -DskipTests)..."
  (cd "$ROOT_DIR/backend" && ./mvnw -q -o package -DskipTests)
  log "Reconstruyendo la imagen del backend (docker compose build backend)..."
  (cd "$ROOT_DIR/backend" && docker compose build backend)
fi

log "Levantando/validando el stack (docker compose up -d: localstack + kafka + backend)..."
(cd "$ROOT_DIR/backend" && docker compose up -d)

log "Esperando a que los 3 contenedores estén healthy..."
for container in "${CONTAINERS[@]}"; do
  for i in $(seq 1 40); do
    status="$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "unknown")"
    if [ "$status" = "healthy" ]; then
      log "$container healthy."
      break
    fi
    if [ "$i" = "40" ]; then
      warn "$container no reportó healthy tras 40 intentos (última lectura: $status) -- revisá 'docker logs $container'."
    fi
    sleep 2
  done
done

# --- 2) Frontend (Vite dev server, sigue en el host) ------------------------
existing_frontend_pid="$(port_pid "$FRONTEND_PORT")"
if [ -n "$existing_frontend_pid" ]; then
  log "Ya hay algo escuchando en el puerto $FRONTEND_PORT (PID $existing_frontend_pid) -- asumo que es el frontend, no lo relanzo."
else
  log "Levantando frontend (vite) en background -- log: .run/frontend.log"
  (
    cd "$ROOT_DIR/frontend"
    # `node node_modules/vite/bin/vite.js` en vez de `npm run dev` --
    # encontrado 2026-08-25: esta máquina tiene la variable de entorno de
    # Windows `NoDefaultCurrentDirectoryInExePath=1`, que rompe la
    # resolución de binarios locales (`node_modules/.bin/vite`) que hace
    # `cmd.exe` por debajo de `npm run <script>` -- el síntoma es "'vite' no
    # se reconoce como un comando interno o externo" aunque el binario exista
    # y `npm`/`node` sí estén en el PATH. Invocar el entrypoint JS de Vite
    # directo con `node` no depende de esa resolución de `cmd.exe`, así que
    # funciona igual con o sin esa variable seteada.
    nohup node node_modules/vite/bin/vite.js > "$RUN_DIR/frontend.log" 2>&1 &
    echo $! > "$RUN_DIR/frontend.pid"
  )
  log "Frontend arrancando (PID $(cat "$RUN_DIR/frontend.pid"))."
fi

echo
log "Listo. URLs esperadas:"
log "  Backend:    http://localhost:8080  (health: /q/health/ready)"
log "  Frontend:   http://localhost:5173"
log "  LocalStack: http://localhost:4566/_localstack/health"
log "  Kafka:      localhost:9092"
log "Logs del backend/localstack/kafka: 'docker compose -f backend/docker-compose.yml logs -f <servicio>'."
log "Log del frontend: $RUN_DIR/frontend.log -- './stop.sh' para bajar todo."
