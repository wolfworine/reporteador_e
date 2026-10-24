# Repositorio de Roles — versión modernizada

Migración del sistema legado "WiseVision Role Repository Basic" (JSF 2.2 + PrimeFaces + Spring 4 + MySQL 5.6)
a una arquitectura moderna. El repositorio tiene tres componentes:

| Carpeta     | Qué es                                                         | Stack                                        |
|-------------|----------------------------------------------------------------|----------------------------------------------|
| `backend/`  | API REST (puerto 8080), reportes asíncronos y eventos SSE      | Quarkus 3, Java 21, Mutiny, DynamoDB, Kafka  |
| `frontend/` | Aplicación web (puerto 5173)                                   | React 19, TypeScript, Vite                   |
| `database/` | CLI que migra el dump MySQL legado hacia DynamoDB              | Java 21 (fat-jar)                            |

En desarrollo local, AWS se emula con **LocalStack** (DynamoDB, Secrets Manager, KMS) y Kafka corre en un
contenedor propio. No se necesita ninguna credencial real de AWS.

```
 navegador ──► frontend :5173 ──► backend :8080 ──► LocalStack :4566 (DynamoDB / Secrets / KMS)
                                        └────────► Kafka :9092
 database/ (CLI, bajo demanda) ─────────────────► LocalStack :4566
```

## 1. Requisitos previos

| Herramienta          | Versión         | Para qué                                              |
|----------------------|-----------------|-------------------------------------------------------|
| Docker + Compose v2  | reciente        | LocalStack, Kafka y el contenedor del backend         |
| JDK                  | **21**          | Compilar backend y CLI de migración                   |
| Node.js + npm        | 20 LTS o mayor  | Frontend                                              |
| Bash                 | cualquiera      | Ejecutar `start.sh` / `stop.sh` (en Windows: **Git Bash**) |
| Puertos libres       | 4566, 9092, 8080, 5173 | —                                              |

Maven no hay que instalarlo: el repositorio trae el wrapper (`backend/mvnw`).

Verificación rápida:

```bash
docker info          # debe responder (Docker Desktop encendido)
java -version        # 21.x
node -v && npm -v
```

## 2. Instalación y primer arranque

Todos los comandos se ejecutan desde la raíz del repositorio.

```bash
# 1) Clonar
git clone https://github.com/wolfworine/reporteador_e.git
cd reporteador_e

# 2) Dependencias del frontend
cd frontend && npm install && cd ..

# 3) Compilar el backend, construir su imagen y levantar todo
./start.sh --build
```

`./start.sh --build` hace, en orden:

1. Compila el backend (`./mvnw package -DskipTests`).
2. Construye la imagen Docker del backend.
3. Levanta con `docker compose` (`backend/docker-compose.yml`): **LocalStack + Kafka + backend**, y espera a que
   los tres estén `healthy`.
4. Arranca el frontend (Vite) en segundo plano. Su log queda en `.run/frontend.log`.

> La primera vez hace falta `--build`, porque la imagen del backend todavía no existe. Después basta `./start.sh`,
> y se vuelve a usar `--build` solo cuando cambie el código Java.
> `start.sh` es idempotente: si algo ya está corriendo, no lo relanza.

Cuando termina, estas URLs responden:

| Servicio    | URL                                              |
|-------------|--------------------------------------------------|
| Frontend    | http://localhost:5173                            |
| Backend     | http://localhost:8080 (health: `/q/health/ready`) |
| LocalStack  | http://localhost:4566/_localstack/health         |
| Kafka       | `localhost:9092`                                 |

## 3. Cargar datos

La base arranca **vacía** y `start.sh` la llena sola, en tres pasos idempotentes (se pueden repetir sin duplicar):

| Script | Qué carga | Origen |
|---|---|---|
| `seed-data.sh` | Áreas, cargos, ubicaciones, aplicación, roles, suministros y atributos (~17.873 items) | Dump MySQL legado `database/seed/legacy-dump.sql`, migrado a DynamoDB con el CLI de `database/` |
| `seed-menu.sh` | Los 10 ítems del menú | API `POST /api/menu` (el dump principal no trae la tabla `menu`) |
| `seed-users.sh` | Los 4 usuarios del legado | API `POST /api/usuarios` (el CLI no migra la tabla `usuario`) |

La primera vez `seed-data.sh` compila el CLI (necesita JDK 21). Si ya hay datos, no hace nada.

### 3.1 Recargar o migrar otro dump a mano

```bash
./seed-data.sh --force     # vuelve a cargar database/seed/legacy-dump.sql

# Otro dump distinto:
backend/mvnw -q -f database/pom.xml package -DskipTests
java -jar database/target/migration-cli.jar migrate "/ruta/al/dump.sql" --target=localstack
java -jar database/target/migration-cli.jar verify  "/ruta/al/dump.sql" --target=localstack
```

Los datos persisten en el volumen Docker `localstack-data` y sobreviven a `./stop.sh`.

**Estado de referencia (rollback):** la base debe contener solo 17.873 items del dump + 10 ítems de menú + 4 usuarios.
Si hay datos de más (p. ej. reportes generados en pruebas), `./reset-data.sh` borra el volumen y recarga exactamente eso.
El CLI solo acepta `--target=localstack`; está bloqueado a propósito para no apuntar a AWS real.

### 3.2 Iniciar sesión

Entrar en http://localhost:5173 con alguno de estos usuarios (se crean al levantar; la contraseña es la del
sistema original y está en `seed-users.sh`):

| Email                         | Nombre           |
|-------------------------------|------------------|
| `admin@wisevisioncorp.com`    | Mark Falcon      |
| `employer@wisevisioncorp.com` | Cesar Valenzuela |
| `Raven@hotmail.com`           | Jorge Raven      |
| `employer2@hotmail.com`       | hola hola        |

## 4. Uso diario

```bash
./start.sh                 # levantar todo
./start.sh --build         # levantar recompilando el backend (tras cambiar código Java)
./stop.sh                  # bajar todo (frontend + contenedores)
./stop.sh --keep-docker    # bajar solo el frontend y dejar backend/LocalStack/Kafka arriba
```

Logs:

```bash
docker compose -f backend/docker-compose.yml logs -f backend     # o: localstack, kafka
tail -f .run/frontend.log
```

Borrar **también los datos** (empezar de cero):

```bash
./stop.sh && docker compose -f backend/docker-compose.yml down -v
```

## 5. Desarrollo y pruebas

```bash
# Backend con recarga en caliente (en vez del contenedor del backend)
./stop.sh                                   # libera el puerto 8080
cd backend && docker compose up -d localstack kafka
./mvnw quarkus:dev                          # Dev UI: http://localhost:8080/q/dev/

# Tests
cd backend  && ./mvnw test                  # backend
cd frontend && npm test                     # frontend (vitest)
cd frontend && npm run lint && npm run build
```

Configuración del frontend: copiar `frontend/.env.example` a `.env` si el backend no está en
`http://localhost:8080` (variable `VITE_API_BASE_URL`).

## 6. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `start.sh` dice "Docker no está corriendo" | Encender Docker Desktop y reintentar. |
| `docker compose build backend` falla por falta de `target/quarkus-app` | Es la primera vez: usar `./start.sh --build`. |
| Puerto 4566 / 8080 / 5173 ocupado | Algo más lo está usando; cerrarlo o ejecutar `./stop.sh`. |
| Login falla o los módulos salen vacíos | Los usuarios los crea `start.sh`; si faltan, ejecutar `./seed-users.sh`. Si los módulos salen vacíos, `./seed-data.sh --force`. |
| `mvn test` en `database/` borra los datos del backend | Esos tests usan el mismo LocalStack `:4566`. Ejecutar `./stop.sh` antes de correrlos. |
| En Windows `npm run dev` dice "'vite' no se reconoce" | Usar `./start.sh`, que invoca Vite directamente con `node`. |
| El backend queda `unhealthy` | `docker compose -f backend/docker-compose.yml logs backend`; el arranque de LocalStack/Kafka puede tardar un minuto. |

## 7. Documentación por componente

- `backend/README.md`, `backend/docs/architecture.md`, `backend/docs/conventions.md`
- `frontend/README.md`, `frontend/docs/`
- `database/docs/migration-plan.md`, `database/docs/verification.md`
- `handoff.md`: estado y decisiones del proyecto. `OPENCODE_RULES.md`: reglas de trabajo con agentes.
