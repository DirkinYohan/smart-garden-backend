-- Credenciales iniciales de un solo uso, cambio de contraseña y bloqueo por intentos fallidos.
-- Las contraseñas siguen guardándose solo como hash BCrypt (factor 12).
ALTER TABLE app_user ADD COLUMN must_change_password BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE app_user ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN token_version INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE app_user ADD COLUMN failed_login_attempts INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
