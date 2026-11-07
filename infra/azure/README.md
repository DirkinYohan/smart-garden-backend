# Despliegue en Azure (Container Apps, escalado a cero)

Cada microservicio es **su propia Azure Container App** (imagen, escalado, secretos y revisiones propias), todas en
un mismo **Container Apps Environment** del plan **Consumption**. Solo `api-gateway` es público. El frontend se
despliega aparte en Vercel; la base de datos sigue en Supabase y el broker MQTT de producción es **EMQX Cloud
Serverless**.

## Arquitectura

```
  Navegador (Vercel)                          ESP32 ── MQTT/TLS 8883 ──►  EMQX Cloud Serverless
        │ HTTPS + WSS (JWT)                                                     ▲
        ▼                                                                       │ MQTT/TLS (siempre conectado)
 ┌────────────── Azure Container Apps Environment (Consumption, eastus) ────────┼───────────────┐
 │                                                                              │               │
 │  api-gateway  (ingress EXTERNO, 0–2 réplicas)                                │               │
 │     │  gRPC sobre HTTP/2 (h2c, puerto 80 del ingress interno)                │               │
 │     ├────────────►  telemetry-service (interno, SIEMPRE 1 réplica) ──────────┘               │
 │     ├────────────►  irrigation-service (interno, 0–1) ──► telemetry-service                  │
 │     └────────────►  ai-service (interno, 0–1) ──► telemetry-service, irrigation-service      │
 │                        ▲                                                                     │
 │  Job "ai-scheduled-analysis" (cron, cada 60 min): grpcurl → AiService/RunScheduledAnalysis   │
 └──────────────────────────────────────────────────────────────────────────────────────────────┘
        Supabase PostgreSQL (un proyecto por servicio, Session pooler 5432, sslmode=require)
        Log Analytics (retención 30 días, tope de ingesta 0,15 GB/día)
```

Dentro del entorno cada app se llama por su nombre: `static://telemetry-service:80`, `static://irrigation-service:80`,
`static://ai-service:80` (HTTP/2 en texto plano por el ingress interno con `transport: http2`). Los canales de Spring
gRPC se configuran solo con variables de entorno (`*_GRPC_TARGET`).

## Escalado

| App | Ingress | Mín. | Máx. | vCPU / memoria | Cómo despierta |
|---|---|---|---|---|---|
| `api-gateway` | Externo (HTTPS + WSS) | **0** | 2 | 0.5 / 1 GiB | Peticiones HTTP / WebSocket de la web |
| `telemetry-service` | Interno (gRPC) | **1** | 1 | 0.25 / 0.5 GiB | — siempre activo |
| `irrigation-service` | Interno (gRPC) | **0** | 1 | 0.5 / 1 GiB | Llamadas gRPC del gateway o de la IA |
| `ai-service` | Interno (gRPC) | **0** | 1 | 0.5 / 1 GiB | Llamadas gRPC + el job programado |

- **Cooldown 120 s** (por defecto 300 s): tras 2 minutos sin tráfico la app vuelve a 0 réplicas.
- **¿Por qué `telemetry-service` no escala a cero?** Es el consumidor MQTT: tiene que estar conectado al broker para
  recibir en tiempo real los datos del ESP32 y enviarle comandos. Un servicio dormido no tiene conexión MQTT, y ningún
  mensaje MQTT puede "despertarlo" (Container Apps despierta por HTTP). Aun así, **el ESP32 sigue regando solo**
  aunque todo el backend esté dormido: decide con los últimos umbrales guardados en su memoria.
- **Tareas programadas:** con escalado a cero `@Scheduled` no se ejecuta. En Azure `AI_SCHEDULER_ENABLED=false` y el
  job `ai-scheduled-analysis` (imagen `smart-garden-ai-scheduler`: grpcurl + los `.proto`) llama a
  `RunScheduledAnalysis`. En local y en Docker el `@Scheduled` sigue activo.
- **Cambio de cultivo:** en Azure `ai-service` no mantiene abierto `StreamConfigChanges` (`AI_CONFIG_WATCH_ENABLED=false`,
  un stream abierto le impediría dormir). El gateway, tras un `PUT /config` correcto, espera 10 s (debounce) y llama a
  `RequestAnalysis(reason = CONFIG_CHANGED)`; la IA conserva su espaciado y omite configuraciones ya analizadas.
- **Streams del gateway:** con `GRPC_STREAMS_ON_DEMAND=true` los 4 streams (`StreamTelemetry`, `StreamDeviceStatus`,
  `StreamConfigChanges`, `StreamAnalyses`) se abren con el primer cliente WebSocket y se cierran 30 s después de que se
  va el último. El ingress corta las peticiones largas (240 s); la reconexión con backoff (1 s → 30 s) las reabre.
  STOMP envía heartbeats cada 10 s en ambos sentidos.

## Arranque en frío

JVM para contenedores pequeños (`JAVA_OPTS` en `main.bicep`): `-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k`,
heap acotado por servicio, y **AppCDS** (archivo `app.jsa` generado en el `Dockerfile` con un arranque de
entrenamiento `-Dspring.context.exit=onRefresh`). El *AOT cache* de JEP 483 requiere Java 24+, y el proyecto usa
Java 21. No se activó `spring.main.lazy-initialization` global: Flyway, el usuario admin inicial, el servidor gRPC y
la conexión MQTT tienen que arrancar al inicio.

Probes: *startup* `/actuator/health/liveness` (hasta 3 min), *readiness* `/actuator/health/readiness`, *liveness*
cada 30 s, todos en el puerto de gestión.

## Desplegar

Requisitos: Azure CLI (`az login --use-device-code`), Docker Desktop y GitHub CLI (`gh auth login --web --scopes
write:packages,workflow`). Variables en `smart-garden-backend/.env` (nunca en git): las de Supabase, `GEMINI_API_KEY`,
`JWT_SECRET`, `ADMIN_PASSWORD`, `WEATHER_LAT/LON`, `MQTT_URL`, `MQTT_USERNAME`, `MQTT_PASSWORD` y, para el firmware,
`MQTT_DEVICE_USERNAME`, `MQTT_DEVICE_PASSWORD`.

```bash
infra/azure/aca.sh check                 # sesiones y variables
infra/azure/aca.sh login-registry        # docker login ghcr.io con gh
infra/azure/aca.sh build all             # 5 imágenes etiquetadas con el SHA del commit
infra/azure/aca.sh provision             # Bicep: entorno + 4 apps + job + logs
infra/azure/aca.sh budget 5              # alerta de presupuesto por correo
```

**Despliegue independiente de un servicio:**

- Con CI: cada microservicio tiene su workflow `.github/workflows/deploy-<servicio>.yml`, que se ejecuta **solo**
  cuando cambian sus archivos, `grpc-contracts/`, `common/` o el `pom.xml` raíz. Compila y prueba ese servicio,
  sube `ghcr.io/<owner>/smart-garden-<servicio>:<sha>` y actualiza **solo** esa Container App. Azure se autentica por
  OpenID Connect con la identidad administrada `smart-garden-github-deploy` (rol Contributor solo sobre
  `smart-garden-aca-rg`); en GitHub Secrets van `AZURE_CLIENT_ID`, `AZURE_TENANT_ID` y `AZURE_SUBSCRIPTION_ID`.
- Sin CI: `infra/deploy-<servicio>.sh` (por ejemplo `infra/deploy-ai-service.sh`).

Las imágenes se etiquetan siempre con el SHA del commit, nunca `latest`.

## Variables y secretos

Cada app recibe **solo sus** secretos como *secretos de Container Apps*, referenciados como variables de entorno:

| App | Secretos | Variables relevantes |
|---|---|---|
| `api-gateway` | `JWT_SECRET`, `ADMIN_PASSWORD`, `GATEWAY_DB_*`, `INTERNAL_TOKEN` | `CORS_ALLOWED_ORIGINS`, `GRPC_DEADLINE=90s`, `GRPC_STREAMS_ON_DEMAND=true`, `NOTIFY_AI_ON_CONFIG_CHANGE=true`, `SPRINGDOC_ENABLED=false`, solo `/actuator/health` expuesto |
| `telemetry-service` | `TELEMETRY_DB_*`, `MQTT_PASSWORD`, `INTERNAL_TOKEN` | `MQTT_URL`, `MQTT_USERNAME`, `MQTT_CLIENT_ID=smartgarden-backend-telemetry`, `MQTT_CLEAN_SESSION=false` |
| `irrigation-service` | `IRRIGATION_DB_*`, `INTERNAL_TOKEN` | `TELEMETRY_GRPC_TARGET` |
| `ai-service` | `AI_DB_*`, `GEMINI_API_KEY`, `WEATHER_LAT`, `WEATHER_LON`, `INTERNAL_TOKEN` | `AI_SCHEDULER_ENABLED=false`, `AI_CONFIG_WATCH_ENABLED=false`, `GEMINI_MODEL` |
| job | `INTERNAL_TOKEN` | `AI_TARGET=ai-service:80` |

`aca.sh provision` lee el `.env`, escribe un archivo temporal de parámetros con permisos 0600 en
`infra/azure/.secrets/` (ignorado por git) y lo borra al terminar. Nada secreto se imprime.

Logs: nivel `WARN` en general e `INFO` para `com.smartgarden`.

## MQTT en producción: EMQX Cloud Serverless

- TLS en el puerto **8883** (no hay 1883), **SNI obligatorio**, sin conexiones anónimas. El certificado lo firma
  **DigiCert Global Root G2** (incluida en el JRE; el firmware la lleva en `mqtt_config.h`).
- `telemetry-service`: `clientId` fijo y único (`smartgarden-backend-telemetry-sub` / `-pub`), sesión persistente,
  QoS 1. Los tópicos y el contrato MQTT no cambian.
- Verificado contra el broker: los **mensajes retenidos** (CONFIG, `online`/`offline`) y el **Last Will** funcionan.
  Cuotas relevantes del plan: 2000 retenidos sin caducidad, sesión MQTT 3.x de 2 h, 10 publicaciones/s por cliente,
  10 suscripciones por cliente, 1 GB de tráfico y 1 millón de minutos de conexión al mes.
- Credenciales (las crea el usuario en la consola de EMQX): una para el backend (`MQTT_USERNAME`) y otra para el
  ESP32 (`MQTT_DEVICE_USERNAME`). `aca.sh firmware` genera `mqtt_config.h` (ignorado por git).
- El firmware envía la telemetría **por eventos**: al instante si cambia la bomba, `sensorOk` o la CONFIG; cuando la
  humedad varía 2 puntos o más (mínimo 1 s entre envíos); y un latido cada 10 s. Keepalive MQTT de 30 s. Tráfico
  esperado: 0,15–0,3 GB/mes. Si tras 24 h la consola de EMQX muestra más de 30 MB/día, subir
  `UMBRAL_CAMBIO_HUMEDAD` a 3.

## Costos

Precios de Azure Container Apps (Consumption, eastus, API de precios de Azure): vCPU activa US$0,000024/s, vCPU en
reposo US$0,000003/s, memoria US$0,000003/GiB-s. Beneficio gratuito mensual por suscripción: 180.000 vCPU-s,
360.000 GiB-s y 2 millones de solicitudes.

| Concepto (mes de 30 días) | vCPU-s | GiB-s |
|---|---|---|
| `telemetry-service` siempre activo (0.25 / 0.5) | 648.000 | 1.296.000 |
| `api-gateway` ~3 h/día (0.5 / 1) | 162.000 | 324.000 |
| `ai-service` (job cada 60 min + uso web) y `irrigation-service` | ~60.000 | ~120.000 |

Estimación: **≈ US$7/mes** si `telemetry-service` está casi siempre en tarifa de reposo y **≈ US$20/mes** si cuenta
como activo. Log Analytics queda dentro de los 5 GB gratis gracias al tope diario. Hay una alerta de presupuesto
de US$5/mes (50 %, 80 % y 100 % previsto) que avisa por correo.

## Ver el estado y los logs

```bash
infra/azure/aca.sh status                       # réplicas, imágenes y ejecuciones del job
infra/azure/aca.sh logs api-gateway 100         # últimas líneas de una app
infra/azure/aca.sh run-job                      # ejecutar el análisis programado ahora
```

En el portal: grupo de recursos `smart-garden-aca-rg` → cada Container App → *Revisiones y réplicas*, *Flujo de
registro* y *Métricas* (réplicas, solicitudes). El job: `ai-scheduled-analysis` → *Historial de ejecución*.
