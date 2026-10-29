#!/bin/sh
# Crea una base por microservicio en el PostgreSQL local (perfil "local-db" de docker-compose).
# Cada servicio mantiene su propio esquema dentro de su base, igual que en Supabase.
set -e

for db in gateway_db telemetry_db irrigation_db ai_db; do
  echo "Creando base $db si no existe..."
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
    SELECT 'CREATE DATABASE $db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
SQL
done
