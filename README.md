# Repositorio de Roles Entel — versión modernizada

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

La base arranca **vacía**. Hay dos pasos: crear un usuario para entrar, y (opcional) cargar los datos reales del legado.

### 3.1 Crear el usuario administrador

```bash
curl -X POST http://localhost:8080/api/usuarios \
  -H "Content-Type: application/json" \
  -d '{"nombres":"Admin","apellidos":"Demo","email":"admin@entel.demo","password":"Demo1234!","tipo":"ADMINISTRADOR"}'
```

Luego se entra en http://localhost:5173 con `admin@entel.demo` / `Demo1234!`.

### 3.2 Migrar el dump MySQL legado (opcional)

Sin esto los módulos (Áreas, Cargos, Roles, Ubicaciones, etc.) aparecen vacíos. El dump `.sql` del sistema
legado **no está en este repositorio**; hay que pedirlo al equipo y tenerlo en disco.

```bash
# Compilar el CLI (una sola vez)
backend/mvnw -q -f database/pom.xml package -DskipTests

# Migrar (el stack del paso 2 debe estar arriba: usa el LocalStack de :4566)
java -jar database/target/migration-cli.jar migrate "/ruta/al/dump.sql" --target=localstack

# Comprobar que lo cargado coincide con el dump
java -jar database/target/migration-cli.jar verify "/ruta/al/dump.sql" --target=localstack
```

Con el dump de referencia se cargan unos 17.873 items (áreas, cargos, ubicaciones, roles, suministros y atributos).
Los datos persisten en el volumen Docker `localstack-data`, así que sobreviven a `./stop.sh` y a reinicios.

> El CLI solo acepta `--target=localstack`; está bloqueado a propósito para no apuntar a AWS real.

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
| Login falla o los módulos salen vacíos | Falta crear el usuario (3.1) o migrar el dump (3.2). |
| `mvn test` en `database/` borra los datos del backend | Esos tests usan el mismo LocalStack `:4566`. Ejecutar `./stop.sh` antes de correrlos. |
| En Windows `npm run dev` dice "'vite' no se reconoce" | Usar `./start.sh`, que invoca Vite directamente con `node`. |
| El backend queda `unhealthy` | `docker logs entel-role-repository-backend`; el arranque de LocalStack/Kafka puede tardar un minuto. |

## 7. Documentación por componente

- `backend/README.md`, `backend/docs/architecture.md`, `backend/docs/conventions.md`
- `frontend/README.md`, `frontend/docs/`
- `database/docs/migration-plan.md`, `database/docs/verification.md`
- `handoff.md`: estado y decisiones del proyecto. `OPENCODE_RULES.md`: reglas de trabajo con agentes.
