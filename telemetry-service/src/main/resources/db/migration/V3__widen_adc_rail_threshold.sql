-- A capacitive sensor in dry air reads ~3300–3500 on the ESP32; values >= 4000 (≈98 % of the 12-bit range) only
-- happen with a disconnected / floating input (seen in production: raw 4089 reported as 0 % moisture).
UPDATE telemetry_reading SET sensor_ok = FALSE WHERE raw >= 4000 AND sensor_ok = TRUE;
