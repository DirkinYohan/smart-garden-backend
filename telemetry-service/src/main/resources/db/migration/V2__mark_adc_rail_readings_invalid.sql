-- Readings with the ADC stuck at a rail (raw >= 4090 or <= 10) come from a disconnected / shorted sensor
-- and were stored as 0 % or 100 % moisture. Mark them invalid so they are excluded from every aggregate.
UPDATE telemetry_reading SET sensor_ok = FALSE WHERE raw >= 4090 OR raw <= 10;
