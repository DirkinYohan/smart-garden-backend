# Despliegue en Render

Cuatro microservicios, **cuatro servicios de Render separados**, desplegados desde el mismo repositorio
mediante el blueprint [`render.yaml`](../../render.yaml).

| Servicio | Tipo en Render | Puerto | Expuesto a Internet |
|---|---|---|---|
| `smart-garden-gateway` | Web service (público) | `$PORT` (Render) | Sí: REST + WebSocket/STOMP |
| `smart-garden-telemetry` | Private service | 9091 (gRPC) | No |
| `smart-garden-irrigation` | Private service | 9092 (gRPC) | No |
| `smart-garden-ai` | Private service | 9093 (gRPC) | No |

Los tres servicios internos hablan **gRPC sobre HTTP/2 sin cifrar (h2c)**. La red privada de Render admite
cualquier protocolo y casi cualquier puerto, así que no hace falta cambiar nada del código ni añadir un
balanceador.

## 1. Antes de desplegar

1. **Cuatro bases de datos.** Cada servicio usa su propio proyecto de Supabase (database-per-service) con su
   esquema (`gateway`, `telemetry`, `irrigation`, `ai`). Render no incluye Postgres por servicio: las
   cadenas de conexión salen de Supabase.
2. **Un broker MQTT accesible desde Internet.** Render no expone puertos TCP arbitrarios, por lo que el
   Mosquitto del `docker-compose.yml` **no** puede ser público. Usa un broker gestionado (HiveMQ Cloud
   gratis, EMQX Cloud) y conecta el ESP32 a ese broker. `telemetry-service` se conecta con
   `MQTT_HOST`, `MQTT_PORT`, `MQTT_SCHEME=ssl` y las credenciales.
3. **Un secreto compartido.** `INTERNAL_TOKEN` debe tener **el mismo valor** en los cuatro servicios.
   Genera uno, por ejemplo: `openssl rand -base64 32`.

## 2. Desplegar

1. En Render: **New + → Blueprint**.
2. Elige el repositorio `smart-garden-backend` y la rama `main`. Render lee `render.yaml`.
3. Rellena las variables marcadas con `sync: false`:

| Servicio | Variables |
|---|---|
| gateway | `INTERNAL_TOKEN`, `JWT_SECRET`, `ADMIN_PASSWORD`, `GATEWAY_DB_HOST`, `GATEWAY_DB_USER`, `GATEWAY_DB_PASSWORD`, `CORS_ALLOWED_ORIGINS` |
| telemetry | `INTERNAL_TOKEN`, `TELEMETRY_DB_*`, `MQTT_HOST`, `MQTT_PORT`, `MQTT_USERNAME`, `MQTT_PASSWORD` |
| irrigation | `INTERNAL_TOKEN`, `IRRIGATION_DB_*` |
| ai | `INTERNAL_TOKEN`, `AI_DB_*`, `GEMINI_API_KEY` |

4. Pulsa **Apply**. Render construye cada imagen con su Dockerfile y el contexto de la raíz del repo.

## 3. Verificar

```bash
# 1) el gateway responde (sustituye por tu URL de Render)
curl https://smart-garden-gateway.onrender.com/actuator/health

# 2) el login funciona y las tres dependencias gRPC también
TOKEN=$(curl -s -X POST https://smart-garden-gateway.onrender.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"TU_PASSWORD"}' | jq -r .token)

curl -s https://smart-garden-gateway.onrender.com/api/v1/status -H "Authorization: Bearer $TOKEN"
curl -s https://smart-garden-gateway.onrender.com/api/v1/config -H "Authorization: Bearer $TOKEN"
curl -s https://smart-garden-gateway.onrender.com/api/v1/ai/analysis/latest -H "Authorization: Bearer $TOKEN"
```

Si `/status` responde, el gateway llegó a `telemetry-service` por gRPC; si `/config` responde, llegó a
`irrigation-service`; si `/ai/analysis/latest` responde, `ai-service` está arriba y con base de datos.

## 4. Frontend (opcional)

El repositorio `smart-garden-web` incluye su propio `render.yaml` para publicarlo como **sitio estático**:

- Build: `npm ci && npm run build`
- Publicar: `dist/smart-garden-web/browser`
- Después del despliegue, pon la URL del sitio en `CORS_ALLOWED_ORIGINS` del gateway
  (por ejemplo `https://smart-garden-web.onrender.com`) y cambia `apiUrl`/`wsUrl` del frontend a la URL
  pública del gateway.

## 5. Coste y límites

| Recurso | Plan | Nota |
|---|---|---|
| Gateway | starter ($7/mes) | En plan gratuito el servicio **se duerme** a los 15 min sin tráfico |
| 3 private services | starter ($7/mes cada uno) | No hay plan gratuito para private services |
| Supabase ×4 | free | Se pausan tras una semana sin uso |
| Broker MQTT | free (HiveMQ Cloud) | 100 conexiones, TLS en 8883 |

Los **free web services de Render no pueden recibir tráfico de la red privada**, por eso los tres servicios
gRPC no pueden ir en plan gratuito.

## 6. Notas de configuración

- **Puertos**: el gateway escucha en `$PORT` (Render lo inyecta). Los servicios gRPC escuchan en el puerto
  que define `PORT` en el blueprint (9091/9092/9093), y esa es la dirección privada
  (`smart-garden-telemetry:9091`).
- **Flyway** corre al arrancar y crea/migra el esquema; necesita permiso de escritura en Supabase (lo tiene).
- **CORS**: `CORS_ALLOWED_ORIGINS` debe incluir el dominio del frontend, separado por comas.
- **Swagger**: se publica apagado (`SPRINGDOC_ENABLED=false`); actívalo solo para probar.
- **Región**: los cuatro servicios deben estar en la **misma región** (`oregon`) para compartir red privada.
