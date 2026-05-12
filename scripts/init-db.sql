-- =====================================================================
-- Script de inicialización de base de datos
-- Se ejecuta automáticamente al crear el contenedor PostgreSQL
-- =====================================================================

-- Crear esquema si no existe
CREATE SCHEMA IF NOT EXISTS public;

-- Extensión para UUID (opcional, si se necesita en el futuro)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================================
-- Mensaje de confirmación
-- =====================================================================
DO $$
BEGIN
    RAISE NOTICE 'Base de datos plantilla_db inicializada correctamente.';
END $$;
