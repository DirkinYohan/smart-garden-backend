-- Approximate soil-moisture ranges (%), editable from the UI.
INSERT INTO crop_profile (name, min_moisture, max_moisture, description, updated_at) VALUES
    ('Genérico', 30, 70, 'Rango general para la mayoría de hortalizas', CURRENT_TIMESTAMP),
    ('Tomate',   40, 70, 'Riego regular; evitar encharcamiento para prevenir hongos', CURRENT_TIMESTAMP),
    ('Lechuga',  50, 80, 'Raíz superficial; necesita humedad constante', CURRENT_TIMESTAMP),
    ('Cilantro', 45, 75, 'Sensible al estrés hídrico; espiga con calor y sequía', CURRENT_TIMESTAMP),
    ('Pimentón', 40, 70, 'Riego moderado y uniforme durante la floración', CURRENT_TIMESTAMP),
    ('Fresa',    50, 75, 'Humedad constante; evitar mojar frutos y hojas', CURRENT_TIMESTAMP);

INSERT INTO irrigation_config (id, mode, crop_profile_id, min_moisture, max_moisture, rain_pause, ai_enabled, updated_at, updated_by)
SELECT 1, 'AUTO', id, min_moisture, max_moisture, FALSE, TRUE, CURRENT_TIMESTAMP, 'SYSTEM'
FROM crop_profile WHERE name = 'Genérico';
