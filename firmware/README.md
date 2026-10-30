# Firmware del ESP32

Riego local con sensor de humedad, pantalla OLED y conexión MQTT al stack de Smart Garden.
El dispositivo recibe del backend los umbrales la orden manual, y **decide localmente** cuándo abrir la
válvula: así sigue regando aunque se caiga la red.

## Conexiones

| Elemento | Pin ESP32 | Notas |
|---|---|---|
| Sensor de humedad (MH) | AO → **GPIO34**, VCC → GPIO25, GND → GND | GPIO34 es ADC1: ADC2 no funciona con WiFi |
| Relé de la bomba | IN → **GPIO26**, VCC → 3V3 | Lógica activa en bajo (`RELE_ACTIVO_EN_BAJO`) |
| Pantalla OLED | SDA → **GPIO21**, SCL → **GPIO22**, VCC → GPIO27 | I2C, dirección 0x3C |

Librerías: **Adafruit SSD1306**, **Adafruit GFX**, **PubSubClient**, **ArduinoJson v7**.

## Cómo abrirlo y compilarlo

```
firmware/
├─ README.md
└─ smart_garden_esp32/
   └─ smart_garden_esp32.ino     ← ábrelo con el IDE de Arduino
```

La carpeta se llama igual que el archivo porque el IDE exige esa correspondencia. Placa: **ESP32 Dev Module**.

## Credenciales

Al principio del archivo, en el bloque `CONFIGURA ESTO`:

```cpp
const char* WIFI_SSID = "";              // tu red de 2.4 GHz
const char* WIFI_PASS = "";              // tu contraseña
const char* MQTT_HOST = "192.168.0.224"; // IP del PC con Docker
const uint16_t MQTT_PORT = 1883;
const char* MQTT_USER = "";              // vacío si Mosquitto es anónimo
const char* MQTT_PASS = "";
const char* DEVICE_ID = "esp32-huerta-01";
```

> El repositorio es público: en lo publicado `WIFI_SSID` y `WIFI_PASS` van vacíos. Rellénalos en tu copia y
> evita subirlos. Para que Git no te los pida en cada `git status`:
>
> ```bash
> git update-index --skip-worktree firmware/smart_garden_esp32/smart_garden_esp32.ino
> ```

## Calibración del sensor

1. Abre el monitor serie a **115200 baudios**.
2. Con el sensor **al aire**, anota el `raw` → `VALOR_SECO`.
3. Con el sensor **en agua**, anota el `raw` → `VALOR_MOJADO`.
4. Ajusta las dos constantes y vuelve a flashear.

La humedad es `map(raw, VALOR_SECO, VALOR_MOJADO, 0, 100)` acotada a 0–100 %.
Si `raw < 400` (`CRUDO_MINIMO_VALIDO`) se considera que el sensor está desconectado o en corto.

## Reglas que aplica el sketch

| Regla | Condición |
|---|---|
| Abrir la válvula (AUTO e IA) | `humedad < umbralMin` y no hay pausa por lluvia |
| Excepción a la pausa por lluvia | se riega igual si `humedad < 15 %` (crítica) |
| Cerrar la válvula (AUTO e IA) | `humedad > umbralMax` |
| Sensor inválido | la bomba se apaga (`raw < 400`) |
| Orden manual | solo en modo `MANUAL`; `durationSec` acotado a 1–120 s y apagado automático al cumplirse |
| Cambio de modo | apaga la bomba |
| CONFIG inválida | se ignora (modo desconocido, `min < 0`, `max > 100` o `max - min < 10`) |
| Configuración | se guarda en NVS y sobrevive a reinicios |

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

Cada **5 s** (latido) y **en cuanto cambia** la humedad o el estado del sensor.

### Estado → `smartgarden/esp32/status` (retenido)

`online` al conectar y `offline` como *last will* del broker.

### Comandos ← `smartgarden/esp32/commands` (QoS 1)

```json
{ "type": "CONFIG", "mode": "AUTO", "minMoisture": 33, "maxMoisture": 80, "rainPause": false }
```

```json
{ "type": "PUMP", "action": "ON", "durationSec": 30 }
```

- `CONFIG` llega **retenido**: el dispositivo lo recibe al reconectar.
- `PUMP` solo se atiende en modo `MANUAL`.
