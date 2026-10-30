# Guía de despliegue en producción

Frontend en **Vercel**, backend en **Render** con **cuatro microservicios independientes** y un **broker MQTT
gestionado**. Esta guía se ejecuta a mano una sola vez; después, cada `git push` redespliega solo lo que cambió.

> Nada de esta guía contiene credenciales reales. Todos los valores son ejemplos.

---

## Parte A. Backend en Render

### A.1 Cuenta y repositorio

1. Crea una cuenta en <https://render.com> y conecta tu GitHub.
2. El blueprint vive en `smart-garden-backend/render.yaml` (rama `main`).

### A.2 Variables de entorno: inventario completo

Genera los secretos antes de empezar:

```bash
openssl rand -base64 32   # INTERNAL_TOKEN (el mismo en los 4 servicios)
openssl rand -base64 48   # JWT_SECRET
```

| Variable | Servicio(s) | Ejemplo | Notas |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | todos | `docker` | Ya viene en el blueprint |
| `PORT` | servicios gRPC | `9091` / `9092` / `9093` | Dirección privada = `nombre:PORT` |
| `INTERNAL_TOKEN` | todos | `k7Qm...` | **Idéntico** en los cuatro; si no coincide, gRPC responde `UNAUTHENTICATED` |
| `JWT_SECRET` | gateway | `...` | ≥ 32 caracteres |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | gateway | `admin` / `...` | Solo se usan si la base está vacía |
| `GATEWAY_DB_HOST` / `_USER` / `_PASSWORD` | gateway | `aws-0-...pooler.supabase.com` | Supabase → Connect → Session pooler (5432) |
| `TELEMETRY_DB_*` | telemetry | — | Proyecto Supabase propio |
| `IRRIGATION_DB_*` | irrigation | — | Proyecto Supabase propio |
| `AI_DB_*` | ai | — | Proyecto Supabase propio |
| `MQTT_HOST` | telemetry | `xxxx.s1.eu.hivemq.cloud` | Broker público |
| `MQTT_PORT` / `MQTT_SCHEME` | telemetry | `8883` / `ssl` | TLS obligatorio en un broker público |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | telemetry | — | Del panel del broker |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | ai | `AIza...` / `gemini-2.0-flash` | Sin clave, la IA usa el respaldo por reglas |
| `CORS_ALLOWED_ORIGINS` | gateway | `https://smart-garden.vercel.app` | Dominio del frontend (sin barra final) |
| `SPRINGDOC_ENABLED` | gateway | `false` | Swagger apagado en producción |
| `TELEMETRY_GRPC_TARGET` | gateway, ai, irrigation | `static://smart-garden-telemetry:9091` | Red privada |
| `IRRIGATION_GRPC_TARGET` | gateway, ai | `static://smart-garden-irrigation:9092` | Red privada |
| `AI_GRPC_TARGET` | gateway | `static://smart-garden-ai:9093` | Red privada |
| `GARDEN_TIMEZONE` | telemetry, ai | `America/Bogota` | Estadísticas diarias |

El inventario vive también en `.env.example` (backend) y `.env.example` (frontend).

### A.3 MQTT en producción

Render **no** permite alojar un broker MQTT: no expone puertos TCP arbitrarios a Internet, y los *free web
services* no reciben tráfico de la red privada. Por eso:

1. Crea un broker gestionado (HiveMQ Cloud tiene plan gratuito con 100 conexiones y TLS en 8883, o EMQX
   Cloud).
2. Crea un usuario y contraseña de dispositivo en el panel del broker.
3. Configura `telemetry-service` con `MQTT_HOST`, `MQTT_PORT=8883`, `MQTT_SCHEME=ssl`, usuario y
   contraseña.
4. Apunta el **ESP32** al mismo broker: `MQTT_HOST`, `MQTT_PORT = 8883` y TLS en el firmware
   (`WiFiClientSecure`; si el broker usa un certificado público de Let's Encrypt, `setInsecure()` basta
   para una demo, o carga la CA raíz para producción).
5. Mantén los mismos tópicos: `smartgarden/esp32/telemetry`, `/status`, `/commands`.

Mosquitto local (`docker-compose.yml`) sigue sirviendo para desarrollo; no se usa en producción.

### A.4 Crear los servicios

1. **New + → Blueprint** → repositorio `smart-garden-backend`, rama `main`.
2. Render crea los cuatro servicios del `render.yaml`: un *web service* (gateway) y tres *private services*.
3. Rellena las variables `sync: false` de cada uno (tabla A.2) y pulsa **Apply**.
4. Espera a que los cuatro estén **Live**.

### A.5 Direcciones privadas y health checks

- El gateway es el único con URL pública: `https://smart-garden-gateway.onrender.com`.
- Los otros tres **no** tienen URL pública; se alcanzan en `nombre:puerto` dentro de la red privada.
- Health check del gateway: `GET /actuator/health` (ya configurado en el blueprint).
- Los servicios gRPC no tienen health check HTTP porque su puerto expone gRPC; si el proceso no arranca,
  Render marca el despliegue como fallido.
- Todos deben estar en la **misma región** (`oregon`) para compartir red privada.

### A.6 Verificar

```bash
BASE=https://smart-garden-gateway.onrender.com
curl -s $BASE/actuator/health

TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"TU_PASSWORD"}' | python -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s $BASE/api/v1/status  -H "Authorization: Bearer $TOKEN"   # gateway -> telemetry
curl -s $BASE/api/v1/config  -H "Authorization: Bearer $TOKEN"   # gateway -> irrigation
curl -s $BASE/api/v1/ai/analysis/latest -H "Authorization: Bearer $TOKEN"  # gateway -> ai
```

Si las tres últimas responden, los tres saltos gRPC funcionan.

---

## Parte B. Frontend en Vercel

### B.1 Requisito: una sola variable

El frontend lee **una única** variable, `API_GATEWAY_URL`. `scripts/set-env.mjs` genera
`src/environments/environment.ts` en tiempo de build y deriva de ella la URL de la API y la del WebSocket:

```
API_GATEWAY_URL=https://smart-garden-gateway.onrender.com
  -> apiUrl: 'https://smart-garden-gateway.onrender.com/api/v1'
  -> wsUrl:  'wss://smart-garden-gateway.onrender.com/ws'
```

Si falta la variable, el build falla con un mensaje explícito (así no se publica una app sin backend).

### B.2 Importar el repositorio

1. <https://vercel.com> → **Add New… → Project** → importa `smart-garden-web`.
2. Framework preset: **Other** (el `vercel.json` fija todo).
3. Root directory: la raíz del repositorio.

### B.3 Configuración de build (ya en `vercel.json`)

| Ajuste | Valor |
|---|---|
| Install command | `npm ci` |
| Build command | `node scripts/set-env.mjs && npm run build` |
| Output directory | `dist/smart-garden-web/browser` |
| Rewrites | `/(.*)` → `/index.html` (rutas de Angular) |

### B.4 Variables en Vercel

**Settings → Environment Variables** (Production, Preview y Development):

| Nombre | Valor |
|---|---|
| `API_GATEWAY_URL` | `https://smart-garden-gateway.onrender.com` |

### B.5 Desplegar y conectar con el backend

1. **Deploy**. Cuando termine, copia la URL (`https://smart-garden-web.vercel.app`).
2. Vuelve a Render → gateway → `CORS_ALLOWED_ORIGINS` = esa URL → guarda y espera el redeploy.
3. Abre la app, inicia sesión y comprueba que el panel recibe datos.

---

## Parte C. Verificación final

- [ ] `curl https://<gateway>/actuator/health` → `{"status":"UP"}`
- [ ] Login correcto desde la app de Vercel (sin errores de CORS en la consola del navegador)
- [ ] Panel muestra humedad y «Última lectura hace X s» actualizándose
- [ ] El indicador de conexión dice **Conectado** (WebSocket/STOMP en `wss://`)
- [ ] Control: cambiar modo y umbrales persiste y llega al ESP32 (revisa el monitor serie)
- [ ] Historial muestra lecturas (la base de telemetría está escribiendo)
- [ ] IA: «Analizar ahora» responde en menos de 45 s y guarda el análisis
- [ ] El ESP32 publica en el broker gestionado (`MQTT_HOST`/8883/TLS) y recibe CONFIG
- [ ] Reiniciar cada servicio en Render no deja la app caída (el gateway devuelve 503 mientras el
      dependiente arranca y se recupera solo)
- [ ] `INTERNAL_TOKEN` idéntico en los cuatro (si no, `/status` devuelve 503)

### Diagnóstico rápido

| Síntoma | Causa probable |
|---|---|
| 503 en todas las rutas | Un servicio interno caído o `INTERNAL_TOKEN` distinto |
| 401 al instante | `JWT_SECRET` cambió: vuelve a iniciar sesión |
| Login OK pero panel vacío | WebSocket bloqueado (revisa `wsUrl` y el CORS) |
| «Revisar sensor» con datos | El broker no entrega telemetría al servicio |
| IA en «reglas de respaldo» | `GEMINI_API_KEY` vacía o sin cuota |

---

## Registro de cambios de esta preparación

| Archivo | Cambio |
|---|---|
| `render.yaml` | Blueprint con los 4 servicios + `buildFilter` por servicio (un push solo redespliega lo que cambió) |
| `*/src/main/resources/application.yaml` | `server.address: 0.0.0.0`, `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 20s`, puerto gRPC por `PORT` |
| `telemetry-service/.../MqttProperties.java` + yaml | `scheme` configurable (`tcp`/`ssl`) y `MQTT_SCHEME` para brokers con TLS |
| `.env.example` | Inventario completo (MQTT, IA, direcciones gRPC, URL pública) |
| `smart-garden-web/scripts/set-env.mjs` | Genera `environment.ts` desde `API_GATEWAY_URL` |
| `smart-garden-web/vercel.json` | Build, salida y rewrites de Vercel |
| `smart-garden-web/.env.example` | Documenta la única variable del frontend |
| `smart-garden-web/package.json` | `build` ejecuta el script antes de `ng build` |
