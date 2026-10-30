/*
  Smart Garden - Firmware ESP32 (riego local + WiFi + MQTT)

  Conexiones (las mismas que ya funcionan):
  - Sensor MH:  AO -> GPIO34, VCC -> GPIO25, GND -> GND, DO sin conectar
  - Relé:       IN -> GPIO26, VCC -> 3V3, GND -> GND
  - OLED:       VCC -> GPIO27, GND -> GND, SDA -> GPIO21, SCL -> GPIO22

  Librerías: Adafruit SSD1306, Adafruit GFX, PubSubClient, ArduinoJson (v7)

  El WiFi y el broker se configuran abajo, en el bloque "CONFIGURA ESTO".
  En el repositorio van vacíos: escribe los tuyos en tu copia local.
*/

#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

// ================== CONFIGURA ESTO ==================
// Red WiFi de 2.4 GHz y broker MQTT. Rellena SSID y PASS en tu copia local:
// el repositorio es público, así que aquí van vacíos a propósito.
const char* WIFI_SSID = "";
const char* WIFI_PASS = "";
const char* MQTT_HOST = "192.168.0.224";
const uint16_t MQTT_PORT = 1883;
const char* MQTT_USER = "";   // vacío si Mosquitto es anónimo
const char* MQTT_PASS = "";
const char* DEVICE_ID = "esp32-huerta-01";
// ====================================================

const char* TOPIC_TELEMETRY = "smartgarden/esp32/telemetry";
const char* TOPIC_STATUS    = "smartgarden/esp32/status";
const char* TOPIC_COMMANDS  = "smartgarden/esp32/commands";

const int PIN_SENSOR     = 34;
const int PIN_RELE       = 26;
const int PIN_SENSOR_VCC = 25;
const int PIN_OLED_VCC   = 27;

// Calibración del sensor (ajústala con el monitor serie):
//   - al aire (seco)  -> VALOR_SECO
//   - en agua         -> VALOR_MOJADO
const int VALOR_SECO   = 4095;
const int VALOR_MOJADO = 1500;

// Ventana de validez del ADC. El tope superior sale de la calibración: TODO el rango calibrado
// (de VALOR_MOJADO a VALOR_SECO) es una lectura válida. Un 4000 fijo rompía el modo automático:
// con un "seco" de 4095 la tierra seca pasaba del tope, la lectura se tomaba por avería y la
// bomba se apagaba sin respetar los umbrales.
const int CRUDO_MINIMO_VALIDO = 10;
const int CRUDO_MAXIMO_VALIDO = VALOR_SECO;

// Una lectura pegada al raíl solo cuenta como avería si se repite: un pico aislado del ADC no
// debe cortar el riego.
const uint8_t LECTURAS_RAIL_PARA_AVERIA = 5;

// Si la bomba lleva este tiempo encendida y la humedad no sube, el sensor no está en la tierra.
const unsigned long SIN_RESPUESTA_MS = 300000;
const int MEJORA_MINIMA = 2;

const int HUMEDAD_CRITICA = 15;
const unsigned long MANUAL_MAX_SEG   = 120;
// Tiempos mínimos de marcha y de parada: con lecturas cada 500 ms el relé podía conmutar
// varias veces por segundo cerca del umbral (desgaste del contacto y golpe de ariete).
const unsigned long MIN_ON_MS        = 30000;
const unsigned long MIN_OFF_MS       = 15000;
const unsigned long LECTURA_MS       = 500;   // lee el sensor 2 veces por segundo
const unsigned long TELEMETRIA_MS    = 5000;  // envío de "latido" aunque no cambie nada
const unsigned long RECONEXION_MS    = 10000;

const bool RELE_ACTIVO_EN_BAJO = true;

Adafruit_SSD1306 pantalla(128, 64, &Wire, -1);
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
Preferences prefs;

String modo = "AUTO";
int umbralMin = 30;
int umbralMax = 70;
bool pausaLluvia = false;

bool regando = false;
int crudo = 0;
int humedad = 0;
bool sensorOk = false;
unsigned long manualInicio = 0;
unsigned long manualDuracionMs = 0;
unsigned long ultimaLectura = 0;
unsigned long ultimaTelemetria = 0;
unsigned long ultimoIntentoMqtt = 0;
int ultimaHumedadEnviada = -1;
bool ultimoSensorOkEnviado = false;

uint8_t lecturasEnRail = 0;
bool sensorAveriado = false;
int humedadAveria = 0;
int humedadAlIniciar = 0;
unsigned long inicioRiego = 0;

void escribirRele(bool encender) {
  pinMode(PIN_RELE, OUTPUT);
  if (RELE_ACTIVO_EN_BAJO) digitalWrite(PIN_RELE, encender ? LOW : HIGH);
  else                     digitalWrite(PIN_RELE, encender ? HIGH : LOW);
}

void publicarTelemetria();

unsigned long ultimoCambioBomba = 0;

void bomba(bool encender) {
  if (encender == regando) return;
  ultimoCambioBomba = millis();
  if (encender) {
    humedadAlIniciar = humedad;
    inicioRiego = millis();
  }
  regando = encender;
  escribirRele(encender);
  Serial.printf("Bomba %s\n", encender ? "ENCENDIDA" : "APAGADA");
  publicarTelemetria();
}

void leerSensor() {
  long suma = 0;
  for (int i = 0; i < 20; i++) {
    suma += analogRead(PIN_SENSOR);
    delay(5);
  }
  crudo = suma / 20;
  humedad = constrain(map(crudo, VALOR_SECO, VALOR_MOJADO, 0, 100), 0, 100);

  bool enRail = crudo <= CRUDO_MINIMO_VALIDO || crudo >= CRUDO_MAXIMO_VALIDO;
  lecturasEnRail = enRail ? (lecturasEnRail < 255 ? lecturasEnRail + 1 : 255) : 0;
  sensorOk = !sensorAveriado && lecturasEnRail < LECTURAS_RAIL_PARA_AVERIA;

  // La avería se limpia cuando la humedad mejora de verdad: el sensor vuelve a responder.
  if (sensorAveriado && humedad > humedadAveria + MEJORA_MINIMA) {
    sensorAveriado = false;
    Serial.println("Sensor recuperado");
  }
}

void cargarConfig() {
  prefs.begin("garden", true);
  modo        = prefs.getString("modo", "AUTO");
  umbralMin   = prefs.getInt("min", 30);
  umbralMax   = prefs.getInt("max", 70);
  pausaLluvia = prefs.getBool("lluvia", false);
  prefs.end();
}

void guardarConfig() {
  prefs.begin("garden", false);
  prefs.putString("modo", modo);
  prefs.putInt("min", umbralMin);
  prefs.putInt("max", umbralMax);
  prefs.putBool("lluvia", pausaLluvia);
  prefs.end();
}

void aplicarConfig(JsonDocument& doc) {
  String nuevoModo = doc["mode"] | modo;
  int nuevoMin     = doc["minMoisture"] | umbralMin;
  int nuevoMax     = doc["maxMoisture"] | umbralMax;
  bool nuevaPausa  = doc["rainPause"] | pausaLluvia;

  bool modoValido = nuevoModo == "AUTO" || nuevoModo == "MANUAL" || nuevoModo == "AI";
  bool rangoValido = nuevoMin >= 0 && nuevoMax <= 100 && (nuevoMax - nuevoMin) >= 10;
  if (!modoValido || !rangoValido) {
    Serial.println("CONFIG inválida, se ignora");
    return;
  }

  bool cambioModo = nuevoModo != modo;
  modo = nuevoModo;
  umbralMin = nuevoMin;
  umbralMax = nuevoMax;
  pausaLluvia = nuevaPausa;
  guardarConfig();
  Serial.printf("CONFIG: modo=%s min=%d max=%d lluvia=%d\n",
                modo.c_str(), umbralMin, umbralMax, pausaLluvia);

  if (cambioModo) {
    manualDuracionMs = 0;
    bomba(false);
  }
}

void aplicarBomba(JsonDocument& doc) {
  if (modo != "MANUAL") {
    Serial.println("PUMP ignorado: no está en modo MANUAL");
    return;
  }
  String accion = doc["action"] | "";
  if (accion == "ON") {
    unsigned long seg = doc["durationSec"] | 30;
    seg = constrain(seg, 1UL, MANUAL_MAX_SEG);
    manualInicio = millis();
    manualDuracionMs = seg * 1000UL;
    bomba(true);
  } else if (accion == "OFF") {
    manualDuracionMs = 0;
    bomba(false);
  }
}

void alRecibirMensaje(char* topic, byte* payload, unsigned int largo) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, largo)) {
    Serial.println("Mensaje MQTT no es JSON válido");
    return;
  }
  String tipo = doc["type"] | "";
  if (tipo == "CONFIG")    aplicarConfig(doc);
  else if (tipo == "PUMP") aplicarBomba(doc);
}

bool wifiConectadoAntes = false;

void mantenerConexion() {
  bool wifiOk = WiFi.status() == WL_CONNECTED;
  if (wifiOk != wifiConectadoAntes) {
    wifiConectadoAntes = wifiOk;
    if (wifiOk) Serial.printf("WiFi conectado. IP del ESP32: %s\n", WiFi.localIP().toString().c_str());
    else        Serial.println("WiFi desconectado, reintentando...");
  }
  if (!wifiOk) return;
  if (mqtt.connected()) return;
  if (millis() - ultimoIntentoMqtt < RECONEXION_MS) return;
  ultimoIntentoMqtt = millis();

  Serial.print("Conectando a MQTT... ");
  const char* user = strlen(MQTT_USER) ? MQTT_USER : nullptr;
  const char* pass = strlen(MQTT_PASS) ? MQTT_PASS : nullptr;
  if (mqtt.connect(DEVICE_ID, user, pass, TOPIC_STATUS, 1, true, "offline")) {
    Serial.println("OK");
    mqtt.publish(TOPIC_STATUS, "online", true);
    mqtt.subscribe(TOPIC_COMMANDS, 1);
    publicarTelemetria();
  } else {
    Serial.printf("falló (estado %d)\n", mqtt.state());
  }
}

void publicarTelemetria() {
  if (!mqtt.connected()) return;
  JsonDocument doc;
  doc["deviceId"]    = DEVICE_ID;
  doc["raw"]         = crudo;
  doc["moisture"]    = humedad;
  doc["pumpOn"]      = regando;
  doc["sensorOk"]    = sensorOk;
  doc["mode"]        = modo;
  doc["minMoisture"] = umbralMin;
  doc["maxMoisture"] = umbralMax;
  doc["rainPause"]   = pausaLluvia;
  doc["uptimeMs"]    = millis();

  char buffer[384];
  size_t n = serializeJson(doc, buffer, sizeof(buffer));
  bool publicado = mqtt.publish(TOPIC_TELEMETRY, (const uint8_t*)buffer, n, false);
  if (!publicado) {
    // El cliente se está cayendo o el buffer se llenó: se reintenta en el siguiente ciclo
    Serial.println("No se pudo publicar la telemetría");
    ultimaTelemetria = 0;
    return;
  }
  ultimaTelemetria = millis();
  ultimaHumedadEnviada = humedad;
  ultimoSensorOkEnviado = sensorOk;
}

void controlarRiego() {
  if (!sensorOk) {
    bomba(false);
    return;
  }
  if (modo == "MANUAL") {
    if (regando && millis() - manualInicio >= manualDuracionMs) bomba(false);
    return;
  }

  // Si lleva mucho tiempo regando y la humedad no sube, el sensor no está en la tierra:
  // se corta el riego y se marca para revisión (esto sustituye al tope fijo de 4000).
  if (regando && millis() - inicioRiego > SIN_RESPUESTA_MS && humedad <= humedadAlIniciar + MEJORA_MINIMA) {
    sensorAveriado = true;
    humedadAveria = humedad;
    Serial.println("Riego sin efecto: revisar el sensor");
    bomba(false);
    return;
  }
  bool puedeIniciar = !pausaLluvia || humedad < HUMEDAD_CRITICA;
  unsigned long desdeCambio = millis() - ultimoCambioBomba;
  if (!regando && humedad < umbralMin && puedeIniciar && desdeCambio >= MIN_OFF_MS) {
    bomba(true);
  } else if (regando && humedad > umbralMax && desdeCambio >= MIN_ON_MS) {
    bomba(false);
  }
}

void mostrar() {
  pantalla.clearDisplay();
  pantalla.setTextColor(SSD1306_WHITE);
  pantalla.setTextSize(1);

  pantalla.setCursor(0, 0);
  pantalla.print(WiFi.status() == WL_CONNECTED ? "WiFi OK" : "Sin WiFi");
  pantalla.setCursor(70, 0);
  pantalla.print(mqtt.connected() ? "Nube OK" : "Sin nube");

  if (sensorOk) {
    pantalla.setTextSize(3);
    pantalla.setCursor(0, 14);
    pantalla.print(humedad);
    pantalla.print("%");
  } else {
    pantalla.setCursor(0, 20);
    pantalla.print("REVISAR SENSOR");
  }

  pantalla.setTextSize(1);
  pantalla.setCursor(0, 40);
  pantalla.print("Bomba: ");
  pantalla.print(regando ? "REGANDO" : "apagada");

  pantalla.setCursor(0, 52);
  pantalla.printf("%s %d-%d%%", modo.c_str(), umbralMin, umbralMax);
  if (pausaLluvia) pantalla.print(" lluvia");

  pantalla.display();
}

void setup() {
  Serial.begin(115200);

  escribirRele(false);

  pinMode(PIN_SENSOR_VCC, OUTPUT);
  digitalWrite(PIN_SENSOR_VCC, HIGH);
  pinMode(PIN_OLED_VCC, OUTPUT);
  digitalWrite(PIN_OLED_VCC, HIGH);
  delay(100);

  analogSetAttenuation(ADC_11db);

  if (!pantalla.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    Serial.println("No se encontró la pantalla OLED");
  }

  cargarConfig();
  Serial.printf("Config guardada: modo=%s min=%d max=%d\n",
                modo.c_str(), umbralMin, umbralMax);

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  // Sin ahorro de energía: los comandos del panel llegan sin los 100-300 ms de retardo del modem sleep
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(alRecibirMensaje);
  mqtt.setBufferSize(512);
  mqtt.setKeepAlive(15);
  mqtt.setSocketTimeout(5);
}

void loop() {
  mantenerConexion();
  mqtt.loop();

  if (millis() - ultimaLectura >= LECTURA_MS) {
    ultimaLectura = millis();
    leerSensor();
    controlarRiego();
    mostrar();

    // Tiempo real: si la humedad cambió, se envía de inmediato
    if (humedad != ultimaHumedadEnviada || sensorOk != ultimoSensorOkEnviado) {
      publicarTelemetria();
    }
  }

  if (millis() - ultimaTelemetria >= TELEMETRIA_MS) {
    publicarTelemetria();
    if (!mqtt.connected()) ultimaTelemetria = millis();
  }
}
