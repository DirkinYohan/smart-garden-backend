-- v1.2: nivel de humedad calculado en Java (SENSOR_ERROR, VERY_DRY, DRY, OPTIMAL, WET, SATURATED).
-- NULL en los análisis guardados antes de esta versión.
ALTER TABLE analysis ADD COLUMN moisture_level VARCHAR(16);
