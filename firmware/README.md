# Firmware del ESP32

Riego local con sensor de humedad, pantalla OLED y conexión MQTT al stack de Smart Garden.
El ESP32 decide **cuándo** abrir la válvula; el backend solo le manda los umbrales y la orden manual.

## Conexiones

| Elemento | Pin ESP32 | Notas |
|---|---|---|
| Sensor de humedad (MH) | AO → **GPIO34**, VCC → GPIO25, GND → GND | GPIO34 es ADC1: ADC2 no funciona con WiFi |
| Relé de la bomba | IN → **GPIO26**, VCC → 3V3 | Lógica activa en bajo (`RELE_ACTIVO_EN_BAJO`) |
| Pantalla OLED | SDA → **GPIO21**, SCL → **GPIO22**, VCC → GPIO27 | I2C, dirección 0x3C |

Librerías: **Adafruit SSD1306**, **Adafruit GFX**, **PubSubClient**, **ArduinoJson v7**.

## Credenciales

El WiFi y la IP del broker **no** están en el repositorio:

```bash
copy secrets.h.example secrets.h   # y edita tus valores
```

`firmware/secrets.h` está en `.gitignore`. Si el broker es anónimo
(`infra/mosquitto/mosquitto.conf` con `allow_anonymous true`), deja `MQTT_USER` y `MQTT_PASS` vacíos.

## Calibración del sensor

1. Abre el monitor serie a **115200 baudios**.
2. Con el sensor **al aire** (seco del todo), anota el valor `raw` que imprime la telemetría → `VALOR_SECO`.
3. Con el sensor **en un vaso de agua**, anota ese `raw` → `VALOR_MOJADO`.
4. Ajusta las dos constantes y vuelve a flashear.

La humedad se calcula con `map(raw, VALOR_SECO, VALOR_MOJADO, 0, 100)`, acotada a 0–100 %.

> **Importante:** `raw` ≥ **4000** se considera *sensor fuera del suelo o desconectado*, igual que en el
> backend (así lo confirmó una lectura real de 4089 con el sensor al aire). Por eso `VALOR_SECO` debe
> quedar por debajo de 4000; si tu sensor satura en 4095 al secarse, la lectura se marcará como
> "revisar sensor" y el riego automático no actuará.

## Contrato MQTT

### Telemetría → `smartgarden/esp32/telemetry` (no retenida)

```json
{
  "deviceId": "esp32-huerta-01",
  "raw": 2118,
  "moisture": 76,
  "pumpOn": false,
  "sensorOk": true,
  "mode": "AI",
  "minMoisture": 50,
  "maxMoisture": 80,
  "rainPause": true,
  "uptimeMs": 1132638
}
```

Se envía cada **5 s** (latido) y **en cuanto cambia** la humedad o el estado del sensor.

### Estado → `smartgarden/esp32/status` (retenido)

`online` al conectar, `offline` como *last will* del broker.

### Comandos ← `smartgarden/esp32/commands` (QoS 1)

```json
{ "type": "CONFIG", "mode": "AUTO", "minMoisture": 30, "maxMoisture": 70, "rainPause": true }
```

```json
{ "type": "PUMP", "action": "ON", "durationSec": 30 }
```

- `CONFIG` llega **retenido**: el dispositivo lo recibe al reconectar.
- `PUMP` solo se atiende en modo `MANUAL` y con `durationSec` entre 1 y **120** (el backend aplica el mismo tope).
- Valores inválidos (modo desconocido o `max - min < 10`) se ignoran con un aviso por el puerto serie.

## Seguridad y robustez

- La bomba **nunca** arranca con el sensor inválido (`sensorOk = false`).
- Tiempo mínimo de marcha (**30 s**) y de parada (**15 s**) para que el relé no repique cerca del umbral.
- Pausa por lluvia: no se aplica si la humedad está por debajo de **15 %** (crítica), la misma regla que la IA.
- Si el `publish` de telemetría falla, se reintenta en el siguiente ciclo.
- Sin ahorro de energía del WiFi, para que los comandos del panel lleguen sin retardo.
