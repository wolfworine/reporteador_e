#!/bin/bash
set -e

echo "Verificando entorno de migracion..."

BUILD_CMD="mvn"
TEST_CMD="mvn test"

echo ""
echo "Build: $BUILD_CMD clean compile"
$BUILD_CMD clean compile

# Los tests de integracion de la feature 2 (design_dynamodb_schema) apuntan
# al LocalStack de docker-compose.yml (ver
# src/test/java/com/entel/migration/localstack/LocalStackIntegrationSupport.java
# para el porque: Testcontainers no logra localizar el daemon Docker en
# algunos entornos Windows, asi que se opto por el LocalStack ya definido
# aqui en vez de un contenedor efimero gestionado desde Java). Se levanta
# antes de "mvn test" y se apaga al final para no dejar infraestructura
# corriendo (nunca AWS real, ver docs/conventions.md).
echo ""
echo "Levantando LocalStack (docker compose up -d localstack)..."
docker compose up -d localstack

echo "Esperando a que LocalStack quede healthy..."
LOCALSTACK_READY=0
for i in $(seq 1 30); do
  status="$(docker inspect --format='{{.State.Health.Status}}' entel-migration-localstack 2>/dev/null || true)"
  if [ "$status" = "healthy" ]; then
    LOCALSTACK_READY=1
    break
  fi
  sleep 2
done

if [ "$LOCALSTACK_READY" -ne 1 ]; then
  echo "ERROR: LocalStack no quedo healthy a tiempo."
  docker compose logs localstack || true
  docker compose down
  exit 1
fi

cleanup() {
  echo ""
  echo "Apagando LocalStack (docker compose down)..."
  docker compose down
}
trap cleanup EXIT

echo ""
echo "Tests: $TEST_CMD"
$TEST_CMD

echo ""
echo "OK. Proyecto listo para trabajar."
