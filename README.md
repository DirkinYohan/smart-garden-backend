# Smart Garden – Backend

Backend IoT para monitorear una huerta y automatizar su riego con IA. Microservicios **Spring Boot 4.1** que se comunican **exclusivamente por gRPC sobre HTTP/2** (Spring gRPC), con un **ESP32** conectado por **MQTT**, análisis con **Google Gemini** (Spring AI) y clima de **Open-Meteo**. El frontend Angular (`smart-garden-web`) consume solo el `api-gateway` (REST + WebSocket/STOMP).

> **Principio clave:** el backend **configura y supervisa**; la decisión instantánea de encender/apagar la bomba la toma el **ESP32** con los últimos umbrales recibidos (sigue regando aunque el servidor o Internet fallen). Solo en modo `MANUAL` el backend ordena la bomba.

---

## 1. Arquitectura

```
                         REST /api/v1 + STOMP /ws (JWT)
   Angular (4200)  ───────────────────────────────────────►  api-gateway :8080
                                                              │  esquema gateway (usuarios)
                         gRPC / HTTP/2 (h2c)                  │
              ┌───────────────────────────────┬───────────────┼───────────────────┐
              ▼                               ▼               ▼                   │
   telemetry-service :9091 ◄──── irrigation-service :9092 ◄── ai-service :9093 ───┘
   esquema telemetry            esquema irrigation          esquema ai
        │  ▲                                                     │  │
   MQTT │  │ MQTT (CONFIG retenido / PUMP)              Gemini ◄─┘  └─► Open-Meteo
        ▼  │
     Mosquitto :1883  ◄──────────►  ESP32 (sensor humedad + relé + bomba + OLED)

   Los 4 esquemas viven en la base "postgres" de un proyecto Supabase (Session pooler, TLS).
```

| Servicio | Puerto | Gestión (Actuator) | Esquema (Supabase) | Responsabilidad |
|---|---|---|---|---|
| `api-gateway` | HTTP **8080** | 8080 `/actuator/health` | `gateway` | REST, WebSocket STOMP, JWT, Swagger, mapeo de errores |
| `telemetry-service` | gRPC **9091** | 8081 | `telemetry` | MQTT ⇄ ESP32, lecturas, eventos de riego, estado online |
| `irrigation-service` | gRPC **9092** | 8082 | `irrigation` | Modo, cultivo, umbrales, perfiles, riego manual |
| `ai-service` | gRPC **9093** | 8083 | `ai` | Clima, análisis Gemini + respaldo por reglas |
| PostgreSQL 17 (Supabase) | 5432 (Session pooler) | — | base `postgres`, un esquema por servicio | |
| Mosquitto 2 | 1883 | — | — | Broker MQTT |

**Grafo de llamadas gRPC (sin ciclos):** gateway → telemetry, irrigation, ai · irrigation → telemetry · ai → telemetry, irrigation.

### Versiones (verificadas contra Maven Central)

| Componente | Versión | Nota |
|---|---|---|
| Spring Boot | 4.1.1 | La de los `pom.xml` existentes |
| Spring gRPC | 1.1.1 | Integrado en Boot 4.1 (`spring-boot-starter-grpc-server/client`) |
| grpc-java / protobuf-java | 1.83.1 / 4.35.1 | Gestionados por Boot |
| protobuf-maven-plugin (ascopes) | 5.1.8 | Con `protoc-gen-grpc-java` (configurado por el parent de Boot) |
| Spring AI | 2.0.1 | Compilado contra Boot 4.1.1 · `spring-ai-starter-model-google-genai` |
| springdoc-openapi | 3.1.1 | Compatible con Boot 4.1 |
| jjwt | 0.13.0 | |
| Java | 21 | |

### Estructura

```
smart-garden-backend/
├── pom.xml                 agregador (packaging pom): grpc-contracts primero, luego los 4 servicios
├── grpc-contracts/         .proto (common, telemetry, irrigation, ai) → clases Java + stubs gRPC
├── telemetry-service/      com.smartgarden.telemetry   (mqtt, grpc, service, entity, repository, config)
├── irrigation-service/     com.smartgarden.irrigation  (grpc, client, service, domain, entity, repository)
├── ai-service/             com.smartgarden.ai          (grpc, client, service, domain, entity, repository)
├── api-gateway/            com.smartgarden.gateway     (web, security, stream, grpc, dto, entity, config)
├── infra/mosquitto/mosquitto.conf
├── docker-compose.yml
└── .env.example
```

Cada servicio es una app Spring Boot independiente con **su propia base de datos** (un proyecto de Supabase por servicio, ver [sección 3](#3-bases-de-datos-en-supabase)) y sus migraciones Flyway en `src/main/resources/db/migration`; conserva `spring-boot-starter-parent` como parent y el agregador solo compila todo junto.

---

## 2. Cómo levantarlo

### Todo con Docker

```bash
cp .env.example .env        # y completa GATEWAY_DB_*/TELEMETRY_DB_*/IRRIGATION_DB_*/AI_DB_*, JWT_SECRET, ADMIN_PASSWORD, GEMINI_API_KEY…
docker compose up --build
```

- API: <http://localhost:8080/api/v1> · Swagger UI: <http://localhost:8080/swagger-ui.html>
- Las bases de datos son **4 proyectos de Supabase**, uno por servicio (ver [sección 3](#3-bases-de-datos-en-supabase)); el compose no levanta PostgreSQL.
- El compose espera los healthchecks: mosquitto → telemetry → irrigation → ai → gateway.
- Sin `GEMINI_API_KEY` todo funciona igual: los análisis usan el **respaldo por reglas** (`source = RULES`).

### Desde el IDE (perfil `local`, por defecto)

```bash
docker compose up -d mosquitto                 # solo el broker (las bases están en Supabase)
./mvnw install -DskipTests                       # compila grpc-contracts + servicios
```

Luego ejecuta las clases `*Application` en este orden: `TelemetryServiceApplication`, `IrrigationServiceApplication`, `AiServiceApplication`, `ApiGatewayApplication`. El perfil `local` apunta gRPC y MQTT a `localhost`. Las credenciales se leen de `smart-garden-backend/.env` (`spring.config.import`), así que no hace falta configurar variables en el IDE; si el `.env` no define `JWT_SECRET` o `ADMIN_PASSWORD`, el perfil `local` usa un secreto de desarrollo y `admin123` (en Docker son obligatorios). En Docker se usa el perfil `docker` (nombres de servicio de la red).

### Variables de entorno (`.env`)

| Variable | Uso | Por defecto |
|---|---|---|
| `GATEWAY_DB_HOST` / `_USER` / `_PASSWORD` | Proyecto de Supabase del **api-gateway** (`gateway_db`) | — (obligatorio) |
| `TELEMETRY_DB_HOST` / `_USER` / `_PASSWORD` | Proyecto del **telemetry-service** (`telemetry_db`) | — (obligatorio) |
| `IRRIGATION_DB_HOST` / `_USER` / `_PASSWORD` | Proyecto del **irrigation-service** (`irrigation_db`) | — (obligatorio) |
| `AI_DB_HOST` / `_USER` / `_PASSWORD` | Proyecto del **ai-service** (`ai_db`) | — (obligatorio) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Solo para la base local offline (perfil `localdb`) | `smartgarden` / `smartgarden` |
| `GEMINI_API_KEY` | API key de Google AI Studio (plan gratuito). Vacía ⇒ solo reglas | — |
| `GEMINI_MODEL` | Modelo **Flash** de Gemini | `gemini-3.8-flash` |
| `GEMINI_FALLBACK_MODELS` | Modelos de respaldo (separados por comas) que se prueban si el principal responde 503, 429 o 404 | `gemini-flash-lite-latest` |
| `JWT_SECRET` | Secreto HMAC-SHA256 (≥ 32 caracteres). El gateway no arranca sin él | — |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Credencial **inicial de un solo uso**: solo se usan con la base vacía. Vacía ⇒ se genera una contraseña aleatoria de 16 caracteres (se muestra una vez en el log) | `admin` / — |
| `ADMIN_RESET_PASSWORD` | Recuperación: restablece la contraseña del admin en el arranque y obliga a cambiarla. Quitar después de usar | — |
| `WEATHER_LAT` / `WEATHER_LON` | Ubicación de la huerta para Open-Meteo | `4.711` / `-74.0721` (Bogotá) |
| `MQTT_HOST` | Broker visto desde telemetry-service (déjalo sin definir: Docker usa `mosquitto`, el IDE `localhost`) | según perfil |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | Solo si desactivas el acceso anónimo | — |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend (separados por coma) | `http://localhost:4200` |

Otras propiedades útiles: `AI_ANALYSIS_INTERVAL` (por defecto `30m`), `AI_ANALYSIS_INITIAL_DELAY` (`2m`), `smartgarden.telemetry.persist-interval` (`30s`), `smartgarden.telemetry.offline-timeout` (`30s`).

---

## 3. Bases de datos en Supabase

La persistencia usa **un proyecto de Supabase por microservicio** (*database-per-service*): cada servicio tiene
**su propio servidor PostgreSQL**, su propio pooler y sus propias credenciales. Un servicio no puede leer ni
escribir los datos de otro.

| Microservicio | Proyecto | Host del pooler | Esquema | Tablas (Flyway) |
|---|---|---|---|---|
| api-gateway | `gateway_db` | `aws-0-us-east-1.pooler.supabase.com` | `gateway` | `app_user` |
| telemetry-service | `telemetry_db` | `aws-0-us-east-2.pooler.supabase.com` | `telemetry` | `telemetry_reading`, `irrigation_event` |
| irrigation-service | `irrigation_db` | `aws-1-us-west-2.pooler.supabase.com` | `irrigation` | `crop_profile`, `irrigation_config` |
| ai-service | `ai_db` | `aws-1-us-west-2.pooler.supabase.com` | `ai` | `analysis` |

En cada proyecto la base de datos se llama **`postgres`** (es el nombre que Supabase da a la base); lo que cambia
por servicio es el **host** y el **usuario** del pooler (`postgres.<PROJECT-REF>`).

### Por qué cada base mantiene su propio esquema

Aunque ahora cada servicio tiene un servidor exclusivo, se sigue usando un esquema propio (`gateway`,
`telemetry`, `irrigation`, `ai`) en lugar de `public`:

- **Seguridad:** Supabase expone el esquema `public` a través de su *Data API* (REST con la anon key). Las tablas
  sin RLS quedarían accesibles desde fuera; en un esquema propio no aparecen en la API.
- **Sin cambios internos:** la configuración de Flyway (`schemas`, `default-schema`, `create-schemas`) e
  Hibernate (`default_schema`) queda igual que antes, y cada servicio conserva su `flyway_schema_history`.

### Session pooler (5432), no Transaction pooler (6543)

| Opción de Supabase | Puerto | ¿Sirve? |
|---|---|---|
| **Session pooler** | **5432** | **Sí.** IPv4 y soporta los *prepared statements* del lado del servidor que usa Hibernate/JDBC. |
| Transaction pooler | 6543 | **No.** No mantiene la sesión: los *prepared statements* fallan. Supabase no lo recomienda para Spring Boot. |
| Conexión directa (`db.<ref>.supabase.co`) | 5432 | No: solo IPv6 en el plan gratuito. |

`sslmode=require` es obligatorio en los cuatro casos. Solo si el 5432 no conectara en algún proyecto se podría usar
el 6543 **como último recurso**, añadiendo `prepareThreshold=0` a la URL (desactiva los *prepared statements* para
esa conexión) y dejando constancia del motivo.

Configuración de cada servicio (`application.yaml`, compartida por los perfiles `local` y `docker`). Este es el
de telemetry-service; los demás solo cambian la variable, el puerto del pooler y el esquema:

```yaml
spring:
  datasource:
    # Un proyecto por servicio: la base siempre es "postgres", el host y el usuario son los del pooler propio
    url: jdbc:postgresql://${TELEMETRY_DB_HOST}:5432/postgres?sslmode=require&currentSchema=telemetry
    username: ${TELEMETRY_DB_USER}
    password: ${TELEMETRY_DB_PASSWORD}
    hikari:
      # Cada proyecto tiene su propio pooler: 5 conexiones por servicio
      maximum-pool-size: 5
      minimum-idle: 1
      connection-timeout: 20000
      keepalive-time: 300000
  flyway:
    schemas: telemetry
    default-schema: telemetry
    create-schemas: true
  jpa:
    properties:
      hibernate:
        default_schema: telemetry
    hibernate:
      ddl-auto: validate   # Flyway manda; Hibernate solo valida
```

### Variables de entorno: una credencial por servicio

Cada servicio recibe **solo** las variables de su proyecto; `docker-compose.yml` se las pasa de forma individual
(ningún contenedor ve las credenciales de otro). En `smart-garden-backend/.env` (nunca en el código, los YAML, el
README, los tests ni los logs):

```dotenv
GATEWAY_DB_HOST=     GATEWAY_DB_USER=     GATEWAY_DB_PASSWORD=
TELEMETRY_DB_HOST=   TELEMETRY_DB_USER=   TELEMETRY_DB_PASSWORD=
IRRIGATION_DB_HOST=  IRRIGATION_DB_USER=  IRRIGATION_DB_PASSWORD=
AI_DB_HOST=          AI_DB_USER=          AI_DB_PASSWORD=
```

`.env` está en `.gitignore` y en `.dockerignore`, y se guarda en **UTF-8** (algunas contraseñas pueden llevar
caracteres como `ñ`; Docker Compose respeta la codificación). Desde el IDE cada servicio lo lee con
`spring.config.import`. La contraseña va en `spring.datasource.password`, no dentro de la URL, así que no hay que
codificar caracteres especiales; solo si la pusieras en una URL tendrías que codificar `@` como `%40`.

Para obtener los datos de cada proyecto: <https://supabase.com/dashboard> → proyecto → **Connect** →
**Session pooler** → copiar **host** (`aws-0-<región>.pooler.supabase.com` o `aws-1-…`) y **usuario**
(`postgres.<PROJECT-REF>`).

### Qué conexión usar

| Opción de Supabase | Puerto | ¿Sirve? |
|---|---|---|
| **Session pooler** | 5432 | **Sí.** IPv4 y soporta los *prepared statements* del lado del servidor que usa Hibernate. |
| Transaction pooler | 6543 | No: no soporta *prepared statements* del servidor. |
| Conexión directa (`db.<ref>.supabase.co`) | 5432 | No: solo IPv6 en el plan gratuito. |

`sslmode=require` es obligatorio. Cada servicio abre su propio pool de **5** conexiones contra el pooler de su proyecto (antes, los cuatro compartían uno).

**Errores típicos:**
- `Tenant or user not found`: el host no coincide con el del proyecto (revisa `aws-0` vs `aws-1` y la región).
- `password authentication failed`: la contraseña cambió. Puedes restablecerla en *Project Settings → Database*.

### Límites del plan gratuito

- **500 MB** por proyecto. Las lecturas se guardan como máximo cada 30 s (unas 2 900 filas al día), muy por debajo del límite.
- **Cada proyecto se pausa tras 1 semana sin actividad.** Se restaura desde el panel de Supabase con **Restore**;
  mientras está pausado el servicio correspondiente no arranca (falla Hikari/Flyway). El más expuesto es
  `gateway_db`, que solo recibe actividad cuando alguien entra a la web: si la app lleva días sin usarse, entra
  primero al panel de Supabase y restaura ese proyecto.
- **2 proyectos activos por organización** en el plan gratuito. Con 4 proyectos hacen falta **dos organizaciones**
  (por ejemplo, dos cuentas/equipos) o un plan de pago; si los cuatro proyectos están en la misma organización, dos
  aparecerán pausados.

### Verificar los esquemas

En el *Table Editor* de cada proyecto (eligiendo el esquema) o con `psql`, usando las variables del servicio:

```bash
# telemetry-service; repite cambiando TELEMETRY_DB_* por GATEWAY_DB_*, IRRIGATION_DB_* o AI_DB_*
docker run --rm -it --env-file .env postgres:16 sh -c \
  'PGPASSWORD="$TELEMETRY_DB_PASSWORD" psql "host=$TELEMETRY_DB_HOST port=5432 dbname=postgres user=$TELEMETRY_DB_USER sslmode=require"'
```

Cada proyecto debe tener **solo** el esquema de su servicio y su propia tabla de Flyway:

```sql
select table_schema, table_name
from information_schema.tables
where table_schema not in ('pg_catalog', 'information_schema')
order by 1, 2;

select installed_rank, version, description, success
from telemetry.flyway_schema_history;   -- el esquema de ese servicio
```

También existe `db-check.yml`, un compose auxiliar que prueba la conexión de los cuatro proyectos con `psql`
(sin arrancar la aplicación y sin imprimir contraseñas):

```bash
docker compose -f db-check.yml run --rm -T gateway      # y telemetry / irrigation / ai
```

### Volver a una base local (sin Internet)

El servicio `postgres` sigue en `docker-compose.yml`, pero dentro del perfil de Compose `local-db`, así que no arranca por defecto. Al crearse ejecuta `infra/postgres/init-databases.sh`, que crea **una base por servicio**
(`gateway_db`, `telemetry_db`, `irrigation_db`, `ai_db`), igual que en producción. El perfil de Spring `localdb`
(`application-localdb.yaml`) apunta a la base de cada servicio y mantiene su esquema.

```bash
# Todo en Docker con la base local
SPRING_PROFILES_ACTIVE=docker,localdb docker compose --profile local-db up --build

# Desde el IDE: levanta la base y el broker, y ejecuta cada servicio con SPRING_PROFILES_ACTIVE=local,localdb
docker compose --profile local-db up -d postgres mosquitto
```

Usa `POSTGRES_USER` / `POSTGRES_PASSWORD` (por defecto `smartgarden`). Los datos de la base local y los de Supabase son independientes.

El script `infra/postgres/init-databases.sh` se ejecuta **solo la primera vez** (volumen vacío). Si ya tenías el
volumen `smart-garden_postgres-data` de antes (con una sola base), bórralo una vez para que se creen las 4:

```bash
docker compose --profile local-db down -v     # OJO: borra los datos locales
docker compose --profile local-db up -d postgres
```

### El proyecto anterior queda como respaldo

Antes de este cambio, los cuatro servicios compartían **un solo proyecto de Supabase** con cuatro esquemas. Ese
proyecto **no se borra ni se modifica**: queda congelado como respaldo de los datos anteriores (usuarios, lecturas,
análisis). Las credenciales antiguas siguen en `.env` comentadas (`SUPABASE_DB_*`) por si hiciera falta volver atrás
temporalmente.

Si más adelante quieres liberar ese proyecto, se pueden eliminar sus esquemas desde el *SQL Editor* del panel
(no lo hagas hasta estar seguro de no necesitar los datos):

```sql
drop schema if exists gateway    cascade;
drop schema if exists telemetry  cascade;
drop schema if exists irrigation cascade;
drop schema if exists ai         cascade;
```

---

## 4. Contrato MQTT con el ESP32

| Dirección | Tópico | Retenido | Payload |
|---|---|---|---|
| ESP32 → servidor (cada 5 s) | `smartgarden/esp32/telemetry` | no | `{"deviceId":"esp32-huerta-01","raw":2350,"moisture":45,"pumpOn":false,"sensorOk":true,"mode":"AUTO","minMoisture":30,"maxMoisture":70,"rainPause":false,"uptimeMs":123456}` |
| ESP32 → servidor | `smartgarden/esp32/status` | **sí** | `"online"` (Last Will: `"offline"`) |
| servidor → ESP32 | `smartgarden/esp32/commands` | **sí** | `{"type":"CONFIG","mode":"AUTO","minMoisture":30,"maxMoisture":70,"rainPause":false}` |
| servidor → ESP32 | `smartgarden/esp32/commands` | no | `{"type":"PUMP","action":"ON","durationSec":30}` · `{"type":"PUMP","action":"OFF"}` |

**Semántica en el ESP32**

- `AUTO` y `AI`: el ESP32 riega por umbrales (enciende bajo `minMoisture`, apaga sobre `maxMoisture`). En `AI` los umbrales los fija la IA.
- `MANUAL`: ignora los umbrales y obedece los comandos `PUMP`, con un tope de seguridad de **120 s**.
- `rainPause = true`: no inicia riego automático salvo humedad crítica (**< 15 %**).
- Como `CONFIG` es retenido, el ESP32 recibe la última configuración apenas se reconecta. irrigation-service la republica al arrancar y reintenta cada 15 s si telemetry o el broker están caídos.

**Lógica del servidor**

- Lecturas guardadas como máximo cada 30 s y **siempre** que cambia `pumpOn`.
- Una lectura con `raw` pegado a un riel del ADC de 12 bits (`raw ≥ 4000` o `raw ≤ 10`; un sensor capacitivo en aire seco da ~3300–3500) se marca `sensorOk = false` aunque el ESP32 diga lo contrario. Corresponde a un sensor desconectado, fuera de la tierra o en corto, y el ESP32 la reporta como 0 % o 100 %. Esas lecturas se guardan, pero se excluyen del historial, de las estadísticas y de la IA. Recomendación para el firmware: enviar `sensorOk: false` en ese caso.
- Eventos de riego: `pumpOn` false→true abre un evento; true→false lo cierra (duración, humedad inicial/final, modo).
- El dispositivo está *offline* si su estado es `"offline"` o no llega lectura en 30 s.
- Payloads inválidos se registran en el log (`WARN Discarding invalid MQTT payload…`) y se descartan sin tumbar el servicio.

### Simular el ESP32 con `mosquitto_pub`

Con el compose levantado (usa el cliente incluido en el contenedor de Mosquitto):

```bash
# Estado "online" (retenido)
docker compose exec mosquitto mosquitto_pub -t smartgarden/esp32/status -r -m '"online"'

# Una lectura (repite cada ~5 s para mantener el dispositivo online)
docker compose exec mosquitto mosquitto_pub -t smartgarden/esp32/telemetry -m '{"deviceId":"esp32-huerta-01","raw":2350,"moisture":45,"pumpOn":false,"sensorOk":true,"mode":"AUTO","minMoisture":30,"maxMoisture":70,"rainPause":false,"uptimeMs":123456}'

# Ciclo de riego: bomba encendida y luego apagada (genera un evento de riego)
docker compose exec mosquitto mosquitto_pub -t smartgarden/esp32/telemetry -m '{"deviceId":"esp32-huerta-01","raw":2900,"moisture":28,"pumpOn":true,"sensorOk":true,"mode":"AUTO","minMoisture":30,"maxMoisture":70,"rainPause":false,"uptimeMs":130000}'
docker compose exec mosquitto mosquitto_pub -t smartgarden/esp32/telemetry -m '{"deviceId":"esp32-huerta-01","raw":1800,"moisture":71,"pumpOn":false,"sensorOk":true,"mode":"AUTO","minMoisture":30,"maxMoisture":70,"rainPause":false,"uptimeMs":190000}'

# Desconexión (lo que publicaría el Last Will)
docker compose exec mosquitto mosquitto_pub -t smartgarden/esp32/status -r -m '"offline"'

# Ver los comandos que el servidor envía al ESP32
docker compose exec mosquitto mosquitto_sub -t smartgarden/esp32/commands -v
```

Bucle en bash que simula el envío cada 5 s:

```bash
while true; do m=$((40 + RANDOM % 20)); docker compose exec -T mosquitto mosquitto_pub -t smartgarden/esp32/telemetry -m "{\"deviceId\":\"esp32-huerta-01\",\"raw\":2300,\"moisture\":$m,\"pumpOn\":false,\"sensorOk\":true,\"mode\":\"AUTO\",\"minMoisture\":30,\"maxMoisture\":70,\"rainPause\":false,\"uptimeMs\":0}"; sleep 5; done
```

### Seguridad MQTT (usuario y contraseña)

En desarrollo Mosquitto permite clientes anónimos. Para exigir credenciales:

1. Crea el archivo de contraseñas: `docker compose exec mosquitto mosquitto_passwd -c -b /mosquitto/config/passwd smartgarden <contraseña>` (o genéralo localmente en `infra/mosquitto/passwd` y móntalo).
2. En `infra/mosquitto/mosquitto.conf`: `allow_anonymous false` y descomenta `password_file /mosquitto/config/passwd`. Si el `.conf` está montado como solo lectura, monta también el `passwd`.
3. Define `MQTT_USERNAME` / `MQTT_PASSWORD` en `.env`, configura las mismas credenciales en el firmware del ESP32 y reinicia (`docker compose up -d`).

---

## 5. Contratos gRPC

Archivos en `grpc-contracts/src/main/protobuf/` (`package smartgarden.<servicio>.v1`, `java_multiple_files`, `google.protobuf.Timestamp`):

| Servicio | RPC |
|---|---|
| `TelemetryService` | `GetCurrentStatus`, `GetHistory(24h/7d/30d)`, `GetIrrigationEvents(limit)`, `SendDeviceCommand(PUMP\|CONFIG)`, **`StreamTelemetry`** (stream), **`StreamDeviceStatus`** (stream) |
| `IrrigationService` | `GetConfig`, `UpdateConfig` (parcial, campos `optional`), `ListCropProfiles`, `UpsertCropProfile`, `ManualIrrigation` (solo MANUAL → `FAILED_PRECONDITION`), `ApplyAiRecommendation` (solo modo AI), **`StreamConfigChanges`** (stream) |
| `AiService` | `GetLatestAnalysis` (`NOT_FOUND` si no hay), `ListAnalyses(limit)`, `RequestAnalysis` (1/min → `RESOURCE_EXHAUSTED`), `GetWeather`, **`StreamAnalyses`** (stream) |

Los servidores tienen **reflection** activada, así que puedes explorarlos con `grpcurl`:

```bash
docker run --rm fullstorydev/grpcurl -plaintext host.docker.internal:9091 list
docker run --rm fullstorydev/grpcurl -plaintext -d '{"range":"HISTORY_RANGE_24H"}' host.docker.internal:9091 smartgarden.telemetry.v1.TelemetryService/GetHistory
docker run --rm fullstorydev/grpcurl -plaintext host.docker.internal:9091 smartgarden.telemetry.v1.TelemetryService/StreamTelemetry
```

### gRPC con TLS

Dentro de la red Docker gRPC va en texto plano (h2c). Para activar TLS con Spring gRPC se usan *SSL bundles* de Spring Boot:

```yaml
# Servidor (p. ej. telemetry-service)
spring:
  ssl:
    bundle:
      pem:
        grpc-server:
          keystore:
            certificate: file:/certs/telemetry.crt
            private-key: file:/certs/telemetry.key
  grpc:
    server:
      ssl:
        bundle: grpc-server
        # client-auth: require   # para mTLS (añade un truststore al bundle)

# Cliente (gateway, irrigation, ai)
spring:
  ssl:
    bundle:
      pem:
        grpc-client:
          truststore:
            certificate: file:/certs/ca.crt
  grpc:
    client:
      channel:
        telemetry:
          target: static://telemetry-service:9091
          ssl:
            enabled: true
            bundle: grpc-client
```

Monta los certificados como volumen (`./certs:/certs:ro`) y emite el certificado del servidor con el nombre del servicio Docker (`telemetry-service`) en el SAN.

---

## 6. API REST (api-gateway)

Prefijo `/api/v1`, JSON, todo protegido con `Authorization: Bearer <JWT>` salvo el login. Documentación interactiva en **`/swagger-ui.html`**.

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/login` | `{username,password}` → `{token, expiresAt, mustChangePassword, username}`. **423** si la cuenta está bloqueada |
| GET | `/auth/me` | `{username, role, mustChangePassword, passwordChangedAt}` |
| PUT | `/auth/password` | `{currentPassword, newPassword}` → `{token, expiresAt}` (token nuevo; cierra las demás sesiones) |
| GET | `/status` | Lectura actual + `deviceOnline` |
| GET | `/telemetry/history?range=24h\|7d\|30d` | Serie agregada (5 min / 1 h / 6 h) |
| GET | `/irrigation/events?limit=20` | Historial de riegos |
| GET | `/config` | Configuración actual |
| PUT | `/config` | Actualiza modo, cultivo, umbrales, `rainPause`, `aiEnabled` (solo los campos enviados) |
| GET | `/crops` | Perfiles de cultivo |
| POST | `/crops` | Crea un perfil (extra) |
| PUT | `/crops/{id}` | Edita un perfil |
| POST | `/pump/start` | `{durationSec}` (1–120, solo MANUAL) |
| POST | `/pump/stop` | Detiene la bomba (solo MANUAL) |
| GET | `/ai/analysis/latest` | Último análisis |
| GET | `/ai/analysis?limit=10` | Historial de análisis |
| POST | `/ai/analysis` | Análisis inmediato (máx. 1 por minuto) |
| GET | `/ai/stats?days=7\|30` | Estadísticas de la IA y humedad diaria (ver sección 7) |
| GET | `/weather` | Clima actual + pronóstico 12 h |

**Errores** — cuerpo uniforme `{timestamp, status, error, message, path}`. Mapeo gRPC → HTTP: `INVALID_ARGUMENT`→400, `NOT_FOUND`→404, `FAILED_PRECONDITION`→409, `UNAVAILABLE`→503, además `RESOURCE_EXHAUSTED`→429, `DEADLINE_EXCEEDED`→504, sin token→401.

### Usuarios y contraseñas

**Primer inicio (base vacía).** El gateway crea el administrador con `must_change_password = true`:

- con `ADMIN_PASSWORD` definida, esa es la contraseña inicial;
- si está vacía, genera una aleatoria de 16 caracteres y la muestra **una sola vez** en el log:
  `Contraseña inicial del administrador: … (cámbiala al iniciar sesión)`.

Mientras `must_change_password` sea `true`, el token solo permite `GET /auth/me` y `PUT /auth/password`; cualquier otra ruta (y el CONNECT del WebSocket) responde **403** `{"error":"PASSWORD_CHANGE_REQUIRED"}`.

**Cambiar la contraseña.** Desde la aplicación (*menú de usuario → Cambiar contraseña*) o por API:

```bash
TOKEN=$(curl -s $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"inicial"}' | jq -r .token)
curl -s -X PUT $API/auth/password -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"currentPassword":"inicial","newPassword":"ClaveNueva2026"}'
```

Política: mínimo 10 caracteres, al menos una mayúscula, una minúscula y un número, distinta de la actual y del nombre de usuario, y fuera de la lista de contraseñas comunes. Al cambiarla se guarda solo el hash **BCrypt (factor 12)**, se marca `password_changed_at` y se incrementa `token_version`, lo que **invalida todos los tokens anteriores** (las demás sesiones se cierran). El endpoint devuelve un token nuevo.

**Bloqueo por intentos fallidos.** Tras **5 intentos seguidos** la cuenta se bloquea **15 minutos** (`locked_until`); el login responde **423** con los minutos restantes. Un login correcto reinicia el contador y el mensaje de error es siempre "Usuario o contraseña incorrectos" (no revela si el usuario existe).

**Recuperar el acceso (contraseña olvidada).**

1. Añade `ADMIN_RESET_PASSWORD=UnaClaveTemporal1` al `.env` (o pásala como variable de entorno).
2. Arranca el gateway: restablece la contraseña, obliga a cambiarla (`must_change_password = true`) e invalida los tokens anteriores. En el log aparece un **WARN** recordándolo.
3. Entra con esa contraseña temporal, cámbiala en la aplicación y **borra la variable** del `.env` (vuelve a arrancar).

Los tokens llevan los claims `tv` (token_version) y `mcp` (mustChangePassword); el gateway los comprueba en cada petición con una caché de 30 s por usuario.

### Ejemplos `curl`

```bash
API=http://localhost:8080/api/v1

# Login (guarda el token)
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<ADMIN_PASSWORD>"}' | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"

# Telemetría
curl -H "$AUTH" $API/status
curl -H "$AUTH" "$API/telemetry/history?range=24h"
curl -H "$AUTH" "$API/telemetry/history?range=7d"
curl -H "$AUTH" "$API/irrigation/events?limit=20"

# Configuración
curl -H "$AUTH" $API/config
curl -X PUT -H "$AUTH" -H 'Content-Type: application/json' $API/config -d '{"mode":"AUTO","cropProfileId":2}'
curl -X PUT -H "$AUTH" -H 'Content-Type: application/json' $API/config -d '{"minMoisture":35,"maxMoisture":65,"rainPause":true}'
curl -X PUT -H "$AUTH" -H 'Content-Type: application/json' $API/config -d '{"mode":"AI"}'

# Perfiles de cultivo
curl -H "$AUTH" $API/crops
curl -X PUT -H "$AUTH" -H 'Content-Type: application/json' $API/crops/2 \
  -d '{"name":"Tomate","minMoisture":42,"maxMoisture":72,"description":"Riego regular"}'
curl -X POST -H "$AUTH" -H 'Content-Type: application/json' $API/crops \
  -d '{"name":"Albahaca","minMoisture":40,"maxMoisture":65,"description":"Prefiere suelo bien drenado"}'

# Riego manual (requiere modo MANUAL; si no → 409)
curl -X PUT -H "$AUTH" -H 'Content-Type: application/json' $API/config -d '{"mode":"MANUAL"}'
curl -X POST -H "$AUTH" -H 'Content-Type: application/json' $API/pump/start -d '{"durationSec":30}'
curl -X POST -H "$AUTH" $API/pump/stop

# IA y clima
curl -H "$AUTH" $API/ai/analysis/latest
curl -H "$AUTH" "$API/ai/analysis?limit=10"
curl -X POST -H "$AUTH" $API/ai/analysis       # una vez por minuto, si no → 429
curl -H "$AUTH" "$API/ai/stats?days=7"
curl -H "$AUTH" $API/weather
```

Ejemplo de error:

```json
{"timestamp":"2026-10-21T20:31:47.573Z","status":409,"error":"Conflict","message":"El riego manual solo está permitido en modo MANUAL","path":"/api/v1/pump/start"}
```

### WebSocket (STOMP)

Endpoint `ws://localhost:8080/ws` (WebSocket nativo, sin SockJS). El JWT va en el header nativo `Authorization` del frame **CONNECT**; los clientes solo pueden suscribirse:

| Tópico | Origen gRPC | Contenido |
|---|---|---|
| `/topic/telemetry` | `StreamTelemetry` | Cada lectura nueva (mismo formato que `reading` de `/status`) |
| `/topic/device-status` | `StreamDeviceStatus` | `{deviceId, online, reason, changedAt}` en cada cambio online/offline |
| `/topic/config` | `StreamConfigChanges` | Configuración completa tras cada cambio (usuario, IA o sistema) |
| `/topic/analysis` | `StreamAnalyses` | Cada análisis nuevo |

Ejemplo con `@stomp/stompjs` (Angular):

```ts
const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 5000,
});
client.onConnect = () => {
  client.subscribe('/topic/telemetry', m => console.log(JSON.parse(m.body)));
  client.subscribe('/topic/device-status', m => console.log(JSON.parse(m.body)));
};
client.activate();
```

Los streams gRPC del gateway se reconectan solos con *backoff* exponencial (1 s → 30 s) y `waitForReady`, así que reiniciar un servicio no requiere reiniciar el gateway.

---

## 7. IA (ai-service)

### Flujo de un análisis

1. Cada 30 min (`@Scheduled`, configurable) o con `POST /ai/analysis` recolecta los datos. Cada fuente es opcional: si una falla, se analiza con lo disponible.
   - De **telemetry**: estado actual (`GetCurrentStatus`), historial de 24 h, lecturas válidas de las últimas 12 h (`GetRecentSamples`) y riegos.
   - De **irrigation**: configuración y perfil del cultivo.
   - De **Open-Meteo**: lluvia y precipitación de las próximas 12 h, temperatura y humedad del aire.
2. **Humedad actual:** sale siempre de `GetCurrentStatus` y solo de una lectura **válida** de menos de **5 minutos**. Válida significa `sensorOk` y `raw` fuera de los rieles del ADC. Si no hay ninguna, el análisis lo dice ("No hay lecturas recientes del sensor"; con `CHECK_SENSOR`) y nunca usa 0 %.
3. Calcula en Java la **velocidad de secado**. Luego llama a Gemini (Spring AI `ChatClient`, salida estructurada `GeminiAnswer`) para que redacte `headline`, `summary`, `riskLevel`, umbrales, `pauseForRain` y 1–4 `recommendationDetails`. Se le indican las acciones disponibles.
4. Valida y acota: umbrales 10–90 con `max − min ≥ 10`, resumen de máximo 2 frases, 1 a 4 recomendaciones.
5. Calcula en Java, sin Gemini, la **proyección**, `healthStatus`, `nextAction`, `hoursUntilNextIrrigation` y qué **acciones** se ofrecen.
6. Si Gemini no está disponible o falla ⇒ **respaldo por reglas**. Llena exactamente los mismos campos, con `source = RULES` y `fallbackReason` (código).
7. Guarda el análisis. Si el modo es `AI`, lo aplica con `IrrigationService.ApplyAiRecommendation`, que envía un CONFIG retenido al ESP32.

### Gemini y motivos de respaldo (`fallbackReason`)

Modelo por defecto: **`gemini-3.8-flash`**, configurable con `GEMINI_MODEL`. `gemini-2.5-flash` ya no está disponible para API keys nuevas: responde `404`.

| Código | Cuándo |
|---|---|
| `NO_API_KEY` | `GEMINI_API_KEY` vacía (el chat de Spring AI se desactiva con `spring.ai.model.chat=none`) |
| `QUOTA_EXCEEDED` | `429` / `RESOURCE_EXHAUSTED` (cuota del plan gratuito) |
| `AUTH_ERROR` | API key inválida o sin permisos |
| `MODEL_NOT_FOUND` | El modelo de `GEMINI_MODEL` no existe o no está disponible para la key |
| `UNAVAILABLE` | `503`: Gemini sobrecargado ("high demand"), es temporal |
| `TIMEOUT` | Sin respuesta en `smartgarden.ai.gemini-timeout` (25 s) |
| `INVALID_RESPONSE` | La respuesta no cumple el formato (faltan umbrales, titular, recomendaciones…) |
| `ERROR` | Cualquier otro error |

`fallbackDetail` guarda el mensaje técnico, saneado y sin claves (`key=***`), y cada respaldo se registra en el log como `Gemini fallback: <código> (<detalle>)`.

**Plan gratuito:** **una llamada por modelo y análisis**. Si el modelo principal responde `503` (sobrecargado), `429` (sin cuota) o `404`, la misma petición se envía una vez a cada modelo de `GEMINI_FALLBACK_MODELS`, que tiene su propia capacidad y cuota. Todos los intentos comparten los 25 s de `gemini-timeout`, y si todos fallan, el `fallbackReason` es el del modelo principal. Están desactivados tanto los reintentos de Spring AI (`spring.ai.retry.max-attempts: 0`) como los del SDK de Google (`GeminiClientConfig`: `HttpRetryOptions.attempts(1)`). `RequestAnalysis` está limitado a uno por minuto (`429`), y el análisis programado se omite si `aiEnabled = false`.

### Velocidad de secado (Java)

Regresión lineal sobre las lecturas válidas de las últimas 12 h. Se excluyen las lecturas con la bomba encendida y los 15 min siguientes. Las lecturas restantes forman tramos de secado separados por riegos. Para que los saltos de los riegos no distorsionen la recta, se usa una pendiente **agrupada por tramo**:

```
pendiente = Σ_tramo Σ_i (t_i − t̄_tramo)(m_i − m̄_tramo) / Σ_tramo Σ_i (t_i − t̄_tramo)²     (t en horas)
```

- Confianza `HIGH`: ≥ 20 lecturas, ≥ 6 h y R² ≥ 0,6.
- Confianza `MEDIUM`: ≥ 8 lecturas, ≥ 3 h y R² ≥ 0,3.
- Si no, `LOW`.
- Con menos de 4 lecturas útiles o menos de 1 h de datos, la velocidad es `null`.
- El resultado se acota a [−10, +5] puntos/h.

### Proyección de humedad (Java)

```
m(0) = humedad actual válida
m(k) = acotar( m(k−1) + r·Δt(k) + lluvia(k), 0, 100 )        k = 1…24 (horas en punto siguientes)
r    = velocidad de secado (puntos/h; 0 si se desconoce)
lluvia(k) = min(2,5 · P(k), 5)  si la probabilidad de esa hora ≥ 70 % y P(k) > 0 mm
            (P = precipitación pronosticada de la hora; suma máxima de lluvia en 24 h: 15 puntos)
```

- El pronóstico cubre 12 h; después se asume que no llueve.
- `estimatedNextIrrigationAt` es cuando la proyección cruza el mínimo (interpolación lineal). Si el suelo ya está bajo el mínimo, es "ahora". Si no ocurre en 24 h, es `null`.
- `hoursUntilNextIrrigation` se deriva de esa fecha.
- `confidence` es la de la velocidad de secado (`LOW` si se desconoce).
- Sin lectura válida no hay proyección: `points` vacío.

### Estado y siguiente acción (Java)

| Condición (primera que se cumpla) | `healthStatus` | `nextAction` |
|---|---|---|
| Última lectura reciente pero inválida (sensor fallando) | `CRITICAL` | `CHECK_SENSOR` |
| Sin lectura válida en 5 min | `ATTENTION` | `CHECK_SENSOR` |
| Humedad < mínimo | `CRITICAL` | `PAUSED_FOR_RAIN` si aplica la pausa y humedad ≥ 15 %; si no, `IRRIGATE_NOW` |
| Humedad < mínimo + 5 | `ATTENTION` | `PAUSED_FOR_RAIN` si aplica la pausa; si no, `WAIT` |
| Humedad > máximo + 10 | `ATTENTION` | `WAIT` |
| En rango | `GOOD` | `PAUSED_FOR_RAIN` si aplica la pausa; si no, `WAIT` |

"Aplica la pausa" significa dos cosas a la vez:
- Se espera lluvia: probabilidad máxima en 12 h ≥ 70 % y precipitación > 0.
- La pausa está activa: `rainPause` de la configuración, o modo `AI` con `pauseForRain`.

### Acciones sugeridas (`recommendationDetails[].action`)

| Acción | Se ofrece solo si | Llamada REST equivalente |
|---|---|---|
| `APPLY_THRESHOLDS` | el modo no es `AI` y los umbrales recomendados difieren de los actuales | `PUT /config {minMoisture, maxMoisture}` |
| `ENABLE_AI_MODE` | el modo no es `AI` | `PUT /config {mode:"AI"}` |
| `ENABLE_RAIN_PAUSE` | modo no `AI`, pausa apagada y se espera lluvia | `PUT /config {rainPause:true}` |
| `DISABLE_RAIN_PAUSE` | modo no `AI`, pausa activa y no va a llover (< 40 % y < 0,5 mm) | `PUT /config {rainPause:false}` |
| `CHECK_SENSOR` | sensor fallando o sin lectura válida (se agrega siempre en ese caso) | — (revisión física) |

Gemini (o las reglas) proponen la acción, pero el servicio descarta las que no aplican y pone la etiqueta en español.

### Contrato `GET /api/v1/ai/analysis/latest` (también `/ai/analysis`, `POST /ai/analysis` y `/topic/analysis`)

Los campos marcados *v1.1* son nuevos y aditivos. En los análisis anteriores a v1.1 son `null` (o `[]` en `recommendationDetails`).

```jsonc
{
  "id": "4b0e…", "createdAt": "2026-10-28T16:31:49Z",
  "source": "GEMINI | RULES", "trigger": "SCHEDULED | MANUAL",
  "recommendedMin": 45, "recommendedMax": 75, "pauseForRain": true,
  "hoursUntilNextIrrigation": 7.5,          // derivado de projection.estimatedNextIrrigationAt (null si no aplica)
  "riskLevel": "LOW | MEDIUM | HIGH",
  "summary": "La humedad del suelo (51 %) está dentro del rango…",
  "recommendations": ["…"],                 // = recommendationDetails[].detail
  "applied": true,
  "fallbackReason": "UNAVAILABLE",          // código (ver tabla) o null si source = GEMINI
  "input": { … },                           // formato anterior, se mantiene
  "headline": "Tu cilantro está bien hidratado",                     // v1.1
  "healthStatus": "GOOD | ATTENTION | CRITICAL",                     // v1.1
  "nextAction": "IRRIGATE_NOW | WAIT | PAUSED_FOR_RAIN | CHECK_SENSOR", // v1.1
  "recommendationDetails": [                                         // v1.1
    { "title": "Lluvia prevista", "detail": "Probabilidad de lluvia de 100 %…",
      "category": "IRRIGATION | WEATHER | SENSOR | CROP", "priority": "HIGH | MEDIUM | LOW",
      "action": { "type": "APPLY_THRESHOLDS | ENABLE_AI_MODE | DISABLE_RAIN_PAUSE | ENABLE_RAIN_PAUSE | CHECK_SENSOR",
                  "label": "Aplicar umbrales recomendados" } }       // action puede ser null
  ],
  "inputSnapshot": {                                                 // v1.1
    "currentMoisture": 51, "avgMoisture24h": 48.2, "minMoisture24h": 31, "maxMoisture24h": 76,
    "dryingRatePerHour": -1.8, "irrigations24h": 3, "pumpMinutes24h": 4.5,
    "rainProbability12h": 100.0, "precipitation12h": 9.3, "temperature": 13.7, "airHumidity": 92.0,
    "cropName": "Cilantro", "cropMin": 45, "cropMax": 75, "currentMin": 45, "currentMax": 75,
    "mode": "AUTO", "sensorOk": true, "rainPause": true, "readingAgeMinutes": 0.4
  },
  "projection": {                                                    // v1.1
    "points": [ { "timestamp": "2026-10-28T17:00:00Z", "moisture": 49.5 } ],  // 24 puntos, cada hora
    "dryingRatePerHour": -1.8, "estimatedNextIrrigationAt": "2026-10-28T23:30:00Z",
    "confidence": "LOW | MEDIUM | HIGH"
  },
  "fallbackDetail": "Failed to generate content | 503 . This model is currently experiencing high demand…"  // v1.1
}
```

En `inputSnapshot` pueden ser `null`:
- `currentMoisture` y `readingAgeMinutes`, si no hay lectura válida;
- los agregados de 24 h, si no hay datos;
- `dryingRatePerHour`, si no se puede calcular;
- los datos de clima, si Open-Meteo no responde.

### Contrato `GET /api/v1/ai/stats?days=7|30`

```jsonc
{
  "days": 7, "totalAnalyses": 336, "geminiCount": 300, "rulesCount": 36,
  "riskDistribution":   { "LOW": 200, "MEDIUM": 100, "HIGH": 36 },
  "healthDistribution": { "GOOD": 250, "ATTENTION": 60, "CRITICAL": 26 },   // solo análisis v1.1
  "rainPauses": 12,                                                          // análisis con pauseForRain = true
  "thresholdHistory": [ { "timestamp": "…", "min": 45, "max": 75, "source": "GEMINI", "riskLevel": "LOW" } ],
  "dailyMoisture": [ { "date": "2026-10-27", "avg": 52.1, "min": 38, "max": 74, "irrigations": 3, "pumpMinutes": 4.5 } ]
}
```

- `dailyMoisture` viene de telemetry-service (`GetDailyStats`): un elemento por día local (`GARDEN_TIMEZONE`, por defecto `America/Bogota`), con solo lecturas válidas.
- Los días sin lecturas tienen `avg/min/max = null`.
- `days` distinto de 7 o 30 ⇒ `400`.

---

## 8. Tests

```bash
./mvnw test
```

| Módulo | Tests |
|---|---|
| telemetry-service | Parseo/validación MQTT (incluye lecturas en el riel del ADC), comandos MQTT, derivación de eventos de riego, política de persistencia, agregación del historial y por día |
| irrigation-service | Validación de umbrales + **integración gRPC** (servidor in-process de Spring gRPC, H2 + Flyway, telemetry simulado): config sembrada, cambio de cultivo, `INVALID_ARGUMENT`, `FAILED_PRECONDITION`, modo AI y `StreamConfigChanges` |
| ai-service | Reproducción del error del 0 % (`InputCollectorTest`), velocidad de secado, proyección, estado y acción, acciones sugeridas, clasificación del respaldo (sin claves en el detalle), validación de la respuesta de Gemini, respaldo por reglas, mapeo de Open-Meteo |
| api-gateway | Contexto completo (H2): 401 con cuerpo uniforme, token inválido, login, 400 de Bean Validation, 503 con servicios caídos, Swagger/health públicos; JWT; mapeo gRPC→HTTP |

Los tests no necesitan Docker ni Supabase: usan H2 en modo PostgreSQL, con los mismos esquemas por servicio y las mismas migraciones Flyway.

---

## 9. Notas de implementación

- **Estructura:** solo existía `api-gateway`; se creó `smart-garden-backend/` (se movió `api-gateway` adentro) y se generaron `telemetry-service`, `irrigation-service` y `ai-service` con la misma plantilla (Boot 4.1.1, Java 21, YAML). El `pom.xml` del gateway tenía `grpc-server`; se cambió a `grpc-client`.
- **Actuator en servicios gRPC:** los servicios gRPC incluyen `spring-boot-starter-webmvc` solo para exponer `/actuator/health` en un puerto de gestión (8081–8083), usado por los healthchecks de Docker. La API de negocio sigue siendo solo gRPC.
- **Extras sobre el mínimo:** RPC `StreamDeviceStatus` (alimenta `/topic/device-status`), endpoint `POST /crops`, mapeos 429/504.
- **Jackson 3:** Boot 4 usa `tools.jackson.*` (las anotaciones siguen en `com.fasterxml.jackson.annotation`).
