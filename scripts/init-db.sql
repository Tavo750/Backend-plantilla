-- =====================================================================
-- Script de inicialización de base de datos MySQL 8.4
-- Se ejecuta automáticamente al crear el contenedor MySQL
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `baseEquipo7G` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `baseEquipo7G`;

-- Deshabilitar llaves foráneas temporalmente para la creación limpia de tablas
SET FOREIGN_KEY_CHECKS = 0;

-- Eliminar tablas existentes si es necesario para recrearlas de cero
DROP TABLE IF EXISTS `aerolinea`;
DROP TABLE IF EXISTS `aeropuerto`;
DROP TABLE IF EXISTS `almacenamiento_aeropuerto`;
DROP TABLE IF EXISTS `asignacion_vuelo`;
DROP TABLE IF EXISTS `configuracion_simulacion`;
DROP TABLE IF EXISTS `envio_maletas`;
DROP TABLE IF EXISTS `envio_replanificacion`;
DROP TABLE IF EXISTS `evento_operativo`;
DROP TABLE IF EXISTS `metrica_operativa`;
DROP TABLE IF EXISTS `parametro_semaforo`;
DROP TABLE IF EXISTS `plan_ruta`;
DROP TABLE IF EXISTS `politica_entrega`;
DROP TABLE IF EXISTS `replanificacion`;
DROP TABLE IF EXISTS `reporte_comparativo`;
DROP TABLE IF EXISTS `resultado_colapso`;
DROP TABLE IF EXISTS `resultado_simulacion`;
DROP TABLE IF EXISTS `snapshot_aeropuerto_colapso`;
DROP TABLE IF EXISTS `snapshot_aeropuerto_dia`;
DROP TABLE IF EXISTS `snapshot_colapso`;
DROP TABLE IF EXISTS `snapshot_diario`;
DROP TABLE IF EXISTS `snapshot_vuelo_dia`;
DROP TABLE IF EXISTS `tramo_ruta`;
DROP TABLE IF EXISTS `ubicacion_envio`;
DROP TABLE IF EXISTS `usuarios`;
DROP TABLE IF EXISTS `vuelo`;

CREATE TABLE `aerolinea` (
  `activa` BOOLEAN NOT NULL,
  `id_aerolinea` INT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(20) NOT NULL,
  `nombre` VARCHAR(120) NOT NULL,
  `contrasena` VARCHAR(100) NOT NULL,
  PRIMARY KEY (`id_aerolinea`),
  UNIQUE KEY `aerolinea_codigo_key` (`codigo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `aeropuerto` (
  `activo` BOOLEAN NOT NULL,
  `capacidad` INT NOT NULL,
  `gmt` INT NOT NULL,
  `id_aeropuerto` INT NOT NULL AUTO_INCREMENT,
  `codigo` VARCHAR(10) NOT NULL,
  `codigo_oaci` VARCHAR(10) NOT NULL,
  `continente` VARCHAR(20) NOT NULL,
  `latitud` VARCHAR(50) NOT NULL,
  `longitud` VARCHAR(50) NOT NULL,
  `ciudad` VARCHAR(100) NOT NULL,
  `pais` VARCHAR(100) NOT NULL,
  CONSTRAINT `aeropuerto_continente_check` CHECK (`continente` IN ('AMERICA', 'EUROPA', 'ASIA')),
  PRIMARY KEY (`id_aeropuerto`),
  UNIQUE KEY `aeropuerto_codigo_key` (`codigo`),
  UNIQUE KEY `aeropuerto_codigo_oaci_key` (`codigo_oaci`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `almacenamiento_aeropuerto` (
  `cantidad_actual` INT NOT NULL,
  `id_aeropuerto` INT NOT NULL,
  `id_almacenamiento` INT NOT NULL AUTO_INCREMENT,
  `id_parametro` INT NOT NULL,
  `timestamp_calculo` DATETIME(6) NOT NULL,
  `nivel_semaforo` VARCHAR(10) NOT NULL,
  CONSTRAINT `almacenamiento_aeropuerto_nivel_semaforo_check` CHECK (`nivel_semaforo` IN ('VERDE', 'AMBAR', 'ROJO')),
  PRIMARY KEY (`id_almacenamiento`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `asignacion_vuelo` (
  `cantidad_asignada` INT NOT NULL,
  `id_asignacion` INT NOT NULL AUTO_INCREMENT,
  `id_envio` INT NOT NULL,
  `id_parametro` INT NOT NULL,
  `id_plan_ruta` INT NOT NULL,
  `id_vuelo` INT NOT NULL,
  `nivel_semaforo` VARCHAR(10) NOT NULL,
  `estado_asignacion` VARCHAR(30) NOT NULL,
  CONSTRAINT `asignacion_vuelo_nivel_semaforo_check` CHECK (`nivel_semaforo` IN ('VERDE', 'AMBAR', 'ROJO')),
  PRIMARY KEY (`id_asignacion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `configuracion_simulacion` (
  `demanda_inicial` INT,
  `dias_periodo` INT,
  `id_configuracion` INT NOT NULL AUTO_INCREMENT,
  `id_politica` INT NOT NULL,
  `incremento_demanda` INT,
  `fecha_inicio` DATETIME(6) NOT NULL,
  `tipo_algoritmo` VARCHAR(10) NOT NULL,
  `tipo_escenario` VARCHAR(30) NOT NULL,
  `descripcion` VARCHAR(200),
  CONSTRAINT `configuracion_simulacion_tipo_algoritmo_check` CHECK (`tipo_algoritmo` IN ('ALNS', 'DECO')),
  CONSTRAINT `configuracion_simulacion_tipo_escenario_check` CHECK (`tipo_escenario` IN ('TIEMPO_REAL', 'SIMULACION_PERIODO', 'SIMULACION_COLAPSO')),
  PRIMARY KEY (`id_configuracion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `envio_maletas` (
  `cantidad` INT NOT NULL,
  `id_aerolinea` INT NOT NULL,
  `id_aeropuerto_destino` INT NOT NULL,
  `id_aeropuerto_origen` INT NOT NULL,
  `id_envio` INT NOT NULL AUTO_INCREMENT,
  `id_politica` INT NOT NULL,
  `prioridad` INT NOT NULL,
  `fecha_limite_entrega` DATETIME(6) NOT NULL,
  `fecha_registro` DATETIME(6) NOT NULL,
  `estado` VARCHAR(20) NOT NULL,
  `hora_registrada` TIME(6) NOT NULL,
  CONSTRAINT `envio_maletas_estado_check` CHECK (`estado` IN ('REGISTRADA', 'EN_ESPERA', 'EN_TRANSITO', 'ENTREGADA', 'RETRASADA')),
  PRIMARY KEY (`id_envio`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `envio_replanificacion` (
  `id_envio` INT NOT NULL,
  `id_plan_ruta_anterior` INT NOT NULL,
  `id_plan_ruta_nuevo` INT,
  `id_replanificacion` INT NOT NULL,
  `sla_salvado` BOOLEAN NOT NULL,
  PRIMARY KEY (`id_envio`, `id_replanificacion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `evento_operativo` (
  `es_manual` BOOLEAN NOT NULL,
  `id_aeropuerto` INT,
  `id_evento` INT NOT NULL AUTO_INCREMENT,
  `id_vuelo` INT,
  `timestamp_evento` DATETIME(6) NOT NULL,
  `tipo_evento` VARCHAR(30) NOT NULL,
  `registrado_por` VARCHAR(100),
  `descripcion` TEXT,
  CONSTRAINT `evento_operativo_tipo_evento_check` CHECK (`tipo_evento` IN ('CANCELACION_VUELO', 'RETRASO_VUELO', 'REPLANIFICACION_MANUAL', 'COLAPSO_AEROPUERTO')),
  PRIMARY KEY (`id_evento`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `metrica_operativa` (
  `en_transito` INT NOT NULL,
  `entregadas` INT NOT NULL,
  `id_configuracion` INT,
  `id_metrica` INT NOT NULL AUTO_INCREMENT,
  `retrasadas` INT NOT NULL,
  `rutas_activas` INT NOT NULL,
  `tasa_cumplimiento_sla` DECIMAL(5,4),
  `tasa_retraso` DECIMAL(5,4),
  `total_maletas` INT NOT NULL,
  `utilizacion_promedio` DECIMAL(5,4),
  `fecha_calculo` DATETIME(6) NOT NULL,
  `tipo_algoritmo` VARCHAR(10),
  `tipo_escenario` VARCHAR(30) NOT NULL,
  CONSTRAINT `metrica_operativa_tipo_algoritmo_check` CHECK (`tipo_algoritmo` IN ('ALNS', 'DECO')),
  CONSTRAINT `metrica_operativa_tipo_escenario_check` CHECK (`tipo_escenario` IN ('TIEMPO_REAL', 'SIMULACION_PERIODO', 'SIMULACION_COLAPSO')),
  PRIMARY KEY (`id_metrica`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `parametro_semaforo` (
  `activo` BOOLEAN NOT NULL,
  `id_parametro` INT NOT NULL AUTO_INCREMENT,
  `umbral_ambar` DECIMAL(5,2) NOT NULL,
  `umbral_rojo` DECIMAL(5,2) NOT NULL,
  `entidad` VARCHAR(50) NOT NULL,
  PRIMARY KEY (`id_parametro`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `plan_ruta` (
  `cumple_sla` BOOLEAN NOT NULL,
  `es_factible` BOOLEAN NOT NULL,
  `es_vigente` BOOLEAN NOT NULL,
  `id_envio` INT NOT NULL,
  `id_plan_ruta` INT NOT NULL AUTO_INCREMENT,
  `tiempo_computo_ms` INT,
  `fecha_creacion` DATETIME(6) NOT NULL,
  `fecha_limite` DATETIME(6) NOT NULL,
  `tipo_algoritmo` VARCHAR(10) NOT NULL,
  CONSTRAINT `plan_ruta_tipo_algoritmo_check` CHECK (`tipo_algoritmo` IN ('ALNS', 'DECO')),
  PRIMARY KEY (`id_plan_ruta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `politica_entrega` (
  `activa` BOOLEAN NOT NULL,
  `dias_distinto_continente` INT NOT NULL,
  `dias_mismo_continente` INT NOT NULL,
  `horas_transito_inter` DECIMAL(4,2) NOT NULL,
  `horas_transito_intra` DECIMAL(4,2) NOT NULL,
  `id_politica` INT NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id_politica`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `replanificacion` (
  `envios_afectados` INT NOT NULL,
  `envios_rescatados` INT NOT NULL,
  `id_evento` INT NOT NULL,
  `id_replanificacion` INT NOT NULL AUTO_INCREMENT,
  `tiempo_computo_ms` INT,
  `timestamp_fin` DATETIME(6),
  `timestamp_inicio` DATETIME(6) NOT NULL,
  `tipo_algoritmo` VARCHAR(10) NOT NULL,
  `estado` VARCHAR(20) NOT NULL,
  CONSTRAINT `replanificacion_estado_check` CHECK (`estado` IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADA', 'FALLIDA')),
  CONSTRAINT `replanificacion_tipo_algoritmo_check` CHECK (`tipo_algoritmo` IN ('ALNS', 'DECO')),
  PRIMARY KEY (`id_replanificacion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reporte_comparativo` (
  `id_reporte` INT NOT NULL AUTO_INCREMENT,
  `id_resultado_alns` INT,
  `id_resultado_deco` INT,
  `tasa_sla_alns` DECIMAL(5,4),
  `tasa_sla_deco` DECIMAL(5,4),
  `tiempo_computo_alns` INT,
  `tiempo_computo_deco` INT,
  `fecha_generacion` DATETIME(6) NOT NULL,
  `algoritmo_ganador` VARCHAR(10),
  `observaciones` TEXT,
  CONSTRAINT `reporte_comparativo_algoritmo_ganador_check` CHECK (`algoritmo_ganador` IN ('ALNS', 'DECO')),
  PRIMARY KEY (`id_reporte`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `resultado_colapso` (
  `aeropuertos_colapsados` INT NOT NULL,
  `id_configuracion` INT NOT NULL,
  `id_resultado_colapso` INT NOT NULL AUTO_INCREMENT,
  `iteraciones_ejecutadas` INT NOT NULL,
  `score_colapso` DECIMAL(10,4),
  `timestamp_colapso` DATETIME(6),
  `nivel_colapso_final` VARCHAR(15) NOT NULL,
  CONSTRAINT `resultado_colapso_nivel_colapso_final_check` CHECK (`nivel_colapso_final` IN ('NORMAL', 'TENSION', 'CRITICO', 'COLAPSADO')),
  PRIMARY KEY (`id_resultado_colapso`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `resultado_simulacion` (
  `id_configuracion` INT NOT NULL,
  `id_resultado` INT NOT NULL AUTO_INCREMENT,
  `tasa_cumplimiento_sla` DECIMAL(5,4),
  `tiempo_ejecucion_min` INT,
  `total_en_espera` INT NOT NULL,
  `total_en_transito` INT NOT NULL,
  `total_entregadas` INT NOT NULL,
  `total_retrasadas` INT NOT NULL,
  `fecha_generacion` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id_resultado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `snapshot_aeropuerto_colapso` (
  `almacenamiento_actual` INT NOT NULL,
  `cantidad_en_cola` INT NOT NULL,
  `colapsado` BOOLEAN NOT NULL,
  `id_aeropuerto` INT NOT NULL,
  `id_snap_aero_colapso` INT NOT NULL AUTO_INCREMENT,
  `id_snapshot_colapso` INT NOT NULL,
  `porcentaje_utilizacion` DECIMAL(5,2),
  `nivel_semaforo` VARCHAR(10) NOT NULL,
  CONSTRAINT `snapshot_aeropuerto_colapso_nivel_semaforo_check` CHECK (`nivel_semaforo` IN ('VERDE', 'AMBAR', 'ROJO')),
  PRIMARY KEY (`id_snap_aero_colapso`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `snapshot_aeropuerto_dia` (
  `almacenamiento_actual` INT NOT NULL,
  `id_aeropuerto` INT NOT NULL,
  `id_snapshot_aeropuerto` INT NOT NULL AUTO_INCREMENT,
  `id_snapshot_diario` INT NOT NULL,
  `porcentaje_uso` DECIMAL(5,2),
  `nivel_semaforo` VARCHAR(10) NOT NULL,
  CONSTRAINT `snapshot_aeropuerto_dia_nivel_semaforo_check` CHECK (`nivel_semaforo` IN ('VERDE', 'AMBAR', 'ROJO')),
  PRIMARY KEY (`id_snapshot_aeropuerto`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `snapshot_colapso` (
  `id_resultado_colapso` INT NOT NULL,
  `id_snapshot_colapso` INT NOT NULL AUTO_INCREMENT,
  `iteracion` INT NOT NULL,
  `score_colapso` DECIMAL(10,4),
  `total_en_espera` INT NOT NULL,
  `total_en_transito` INT NOT NULL,
  `total_entregadas` INT NOT NULL,
  `total_retrasadas` INT NOT NULL,
  `timestamp_registro` DATETIME(6) NOT NULL,
  `nivel_colapso` VARCHAR(15) NOT NULL,
  CONSTRAINT `snapshot_colapso_nivel_colapso_check` CHECK (`nivel_colapso` IN ('NORMAL', 'TENSION', 'CRITICO', 'COLAPSADO')),
  PRIMARY KEY (`id_snapshot_colapso`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `snapshot_diario` (
  `dia` INT NOT NULL,
  `en_espera` INT NOT NULL,
  `en_transito` INT NOT NULL,
  `entregadas` INT NOT NULL,
  `id_resultado` INT NOT NULL,
  `id_snapshot_diario` INT NOT NULL AUTO_INCREMENT,
  `retrasadas` INT NOT NULL,
  `fecha` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id_snapshot_diario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `snapshot_vuelo_dia` (
  `carga_asignada` INT NOT NULL,
  `id_snapshot_diario` INT NOT NULL,
  `id_snapshot_vuelo` INT NOT NULL AUTO_INCREMENT,
  `id_vuelo` INT NOT NULL,
  `porcentaje_uso` DECIMAL(5,2),
  `nivel_semaforo` VARCHAR(10) NOT NULL,
  `estado` VARCHAR(20),
  CONSTRAINT `snapshot_vuelo_dia_estado_check` CHECK (`estado` IN ('PROGRAMADO', 'EN_ABORDAJE', 'EN_VUELO', 'ATERRIZANDO', 'EN_DESCARGA', 'RETRASADO', 'CANCELADO')),
  CONSTRAINT `snapshot_vuelo_dia_nivel_semaforo_check` CHECK (`nivel_semaforo` IN ('VERDE', 'AMBAR', 'ROJO')),
  PRIMARY KEY (`id_snapshot_vuelo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tramo_ruta` (
  `cantidad_asignada` INT NOT NULL,
  `holgura_horas` DECIMAL(4,2) NOT NULL,
  `id_aeropuerto_llegada` INT NOT NULL,
  `id_aeropuerto_salida` INT NOT NULL,
  `id_plan_ruta` INT NOT NULL,
  `id_tramo_ruta` INT NOT NULL AUTO_INCREMENT,
  `id_vuelo` INT NOT NULL,
  `orden` INT NOT NULL,
  `llegada_programada` DATETIME(6) NOT NULL,
  `salida_programada` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id_tramo_ruta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ubicacion_envio` (
  `es_ubicacion_actual` BOOLEAN NOT NULL,
  `id_aeropuerto` INT NOT NULL,
  `id_envio` INT NOT NULL,
  `id_ubicacion_envio` INT NOT NULL AUTO_INCREMENT,
  `timestamp_llegada` DATETIME(6) NOT NULL,
  `timestamp_salida` DATETIME(6),
  PRIMARY KEY (`id_ubicacion_envio`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `usuarios` (
  `estado` BOOLEAN NOT NULL,
  `actualizado_en` DATETIME(6),
  `creado_en` DATETIME(6),
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `apellido_materno` VARCHAR(100),
  `apellido_paterno` VARCHAR(100) NOT NULL,
  `nombre` VARCHAR(100) NOT NULL,
  `puesto` VARCHAR(100),
  `correo` VARCHAR(150) NOT NULL,
  `foto_url` VARCHAR(500),
  `contrasena` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `usuarios_correo_key` (`correo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `vuelo` (
  `capacidad_maxima` INT NOT NULL,
  `duracion_horas` DECIMAL(4,2) NOT NULL,
  `es_intercontinental` BOOLEAN NOT NULL,
  `id_aeropuerto_destino` INT NOT NULL,
  `id_aeropuerto_origen` INT NOT NULL,
  `id_vuelo` INT NOT NULL AUTO_INCREMENT,
  `hora_llegada` DATETIME(6) NOT NULL,
  `hora_salida` DATETIME(6) NOT NULL,
  `estado` VARCHAR(20) NOT NULL,
  `codigo_vuelo` VARCHAR(30) NOT NULL,
  CONSTRAINT `vuelo_estado_check` CHECK (`estado` IN ('PROGRAMADO', 'EN_ABORDAJE', 'EN_VUELO', 'ATERRIZANDO', 'EN_DESCARGA', 'RETRASADO', 'CANCELADO')),
  PRIMARY KEY (`id_vuelo`),
  UNIQUE KEY `vuelo_codigo_vuelo_key` (`codigo_vuelo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `asignacion_vuelo` ADD CONSTRAINT `fk11vutnlfmruykhx6590j924gk` FOREIGN KEY (`id_parametro`) REFERENCES `parametro_semaforo` (`id_parametro`);
ALTER TABLE `envio_maletas` ADD CONSTRAINT `fk1g6eaktlx07qiwcvvj4t6kwju` FOREIGN KEY (`id_politica`) REFERENCES `politica_entrega` (`id_politica`);
ALTER TABLE `resultado_colapso` ADD CONSTRAINT `fk2gpuv8lgpox4bk64skct2mpab` FOREIGN KEY (`id_configuracion`) REFERENCES `configuracion_simulacion` (`id_configuracion`);
ALTER TABLE `reporte_comparativo` ADD CONSTRAINT `fk3q7a9e1t96jxecthuas5stjpj` FOREIGN KEY (`id_resultado_deco`) REFERENCES `resultado_simulacion` (`id_resultado`);
ALTER TABLE `envio_maletas` ADD CONSTRAINT `fk3u6mxhea4f446jge4jlbwiw53` FOREIGN KEY (`id_aerolinea`) REFERENCES `aerolinea` (`id_aerolinea`);
ALTER TABLE `snapshot_aeropuerto_colapso` ADD CONSTRAINT `fk446n71okgegmhy63ymix1t9ed` FOREIGN KEY (`id_aeropuerto`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `tramo_ruta` ADD CONSTRAINT `fk5u9he574o3m4s5dk3baga8axw` FOREIGN KEY (`id_plan_ruta`) REFERENCES `plan_ruta` (`id_plan_ruta`);
ALTER TABLE `envio_replanificacion` ADD CONSTRAINT `fkb9k8d0kdiuskhr78oqv5g0lu9` FOREIGN KEY (`id_plan_ruta_anterior`) REFERENCES `plan_ruta` (`id_plan_ruta`);
ALTER TABLE `reporte_comparativo` ADD CONSTRAINT `fkbrum8a7op3rkl2gcw6og7e2hy` FOREIGN KEY (`id_resultado_alns`) REFERENCES `resultado_simulacion` (`id_resultado`);
ALTER TABLE `plan_ruta` ADD CONSTRAINT `fkd8jl79n7lmvr11yx87bo36sdh` FOREIGN KEY (`id_envio`) REFERENCES `envio_maletas` (`id_envio`);
ALTER TABLE `ubicacion_envio` ADD CONSTRAINT `fkdw76bxxl7pytsb4qbakoacr6o` FOREIGN KEY (`id_aeropuerto`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `almacenamiento_aeropuerto` ADD CONSTRAINT `fke1sxwnportdccra3j200fdac4` FOREIGN KEY (`id_aeropuerto`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `configuracion_simulacion` ADD CONSTRAINT `fkeg2h8aa166nobs7gkfs82rkw2` FOREIGN KEY (`id_politica`) REFERENCES `politica_entrega` (`id_politica`);
ALTER TABLE `envio_replanificacion` ADD CONSTRAINT `fkg7rh3iee0cwna1gp734e125hs` FOREIGN KEY (`id_replanificacion`) REFERENCES `replanificacion` (`id_replanificacion`);
ALTER TABLE `asignacion_vuelo` ADD CONSTRAINT `fkgwyvdi0ct589sds3jadb3dp8s` FOREIGN KEY (`id_vuelo`) REFERENCES `vuelo` (`id_vuelo`);
ALTER TABLE `envio_replanificacion` ADD CONSTRAINT `fkhocw1jgmwoixbe9qf6pvbkc3m` FOREIGN KEY (`id_envio`) REFERENCES `envio_maletas` (`id_envio`);
ALTER TABLE `replanificacion` ADD CONSTRAINT `fkij5wm2544h330se84m2paf4bu` FOREIGN KEY (`id_evento`) REFERENCES `evento_operativo` (`id_evento`);
ALTER TABLE `vuelo` ADD CONSTRAINT `fkiqpru4unqrmy8nlqx94xgyjlo` FOREIGN KEY (`id_aeropuerto_origen`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `envio_maletas` ADD CONSTRAINT `fkiy7lvnes1w7a0a204r9jb17d1` FOREIGN KEY (`id_aeropuerto_destino`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `evento_operativo` ADD CONSTRAINT `fkj7ruftcp9xl4527r86n0wg0aw` FOREIGN KEY (`id_vuelo`) REFERENCES `vuelo` (`id_vuelo`);
ALTER TABLE `snapshot_colapso` ADD CONSTRAINT `fkjagwl61d23byl096o39j9txh6` FOREIGN KEY (`id_resultado_colapso`) REFERENCES `resultado_colapso` (`id_resultado_colapso`);
ALTER TABLE `ubicacion_envio` ADD CONSTRAINT `fkjkqhilgprrrikr7qfiwqf2ksj` FOREIGN KEY (`id_envio`) REFERENCES `envio_maletas` (`id_envio`);
ALTER TABLE `tramo_ruta` ADD CONSTRAINT `fkk4gespjtx6gtxoihm87vxdwf4` FOREIGN KEY (`id_vuelo`) REFERENCES `vuelo` (`id_vuelo`);
ALTER TABLE `asignacion_vuelo` ADD CONSTRAINT `fkktolhvvddm2in76vr1tqotwcq` FOREIGN KEY (`id_envio`) REFERENCES `envio_maletas` (`id_envio`);
ALTER TABLE `snapshot_diario` ADD CONSTRAINT `fkl04440q7r662naiuqjruxtw06` FOREIGN KEY (`id_resultado`) REFERENCES `resultado_simulacion` (`id_resultado`);
ALTER TABLE `vuelo` ADD CONSTRAINT `fkm1u1j5vajeegpyhohvu1anpo0` FOREIGN KEY (`id_aeropuerto_destino`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `envio_replanificacion` ADD CONSTRAINT `fknm34wsf3aicbqutd7xnp96mp9` FOREIGN KEY (`id_plan_ruta_nuevo`) REFERENCES `plan_ruta` (`id_plan_ruta`);
ALTER TABLE `metrica_operativa` ADD CONSTRAINT `fko7eonelw3qd0sqdljaw29146y` FOREIGN KEY (`id_configuracion`) REFERENCES `configuracion_simulacion` (`id_configuracion`);
ALTER TABLE `tramo_ruta` ADD CONSTRAINT `fkob2qp0ydrv3cwj0y38qouxr1l` FOREIGN KEY (`id_aeropuerto_llegada`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `snapshot_aeropuerto_dia` ADD CONSTRAINT `fkoe9tmye6p55c40sb6621pd3ao` FOREIGN KEY (`id_aeropuerto`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `snapshot_aeropuerto_colapso` ADD CONSTRAINT `fkq76saoinwvqtikjp54fnn1fkf` FOREIGN KEY (`id_snapshot_colapso`) REFERENCES `snapshot_colapso` (`id_snapshot_colapso`);
ALTER TABLE `tramo_ruta` ADD CONSTRAINT `fkqjgcj8k96ligj62jqhs4v5jc7` FOREIGN KEY (`id_aeropuerto_salida`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `snapshot_vuelo_dia` ADD CONSTRAINT `fkqwon25hvyvmtpeewvms3a4bnt` FOREIGN KEY (`id_vuelo`) REFERENCES `vuelo` (`id_vuelo`);
ALTER TABLE `snapshot_aeropuerto_dia` ADD CONSTRAINT `fkr8q1m1tudj2y664ed2j9ghvxk` FOREIGN KEY (`id_snapshot_diario`) REFERENCES `snapshot_diario` (`id_snapshot_diario`);
ALTER TABLE `almacenamiento_aeropuerto` ADD CONSTRAINT `fkrce2w041g8wmfcg0i58vofw3g` FOREIGN KEY (`id_parametro`) REFERENCES `parametro_semaforo` (`id_parametro`);
ALTER TABLE `evento_operativo` ADD CONSTRAINT `fkrctek219585oh7dodmxisp0jo` FOREIGN KEY (`id_aeropuerto`) REFERENCES `aeropuerto` (`id_aeropuerto`);
ALTER TABLE `snapshot_vuelo_dia` ADD CONSTRAINT `fksdfhkfowng8dtof2y9ejg1nkf` FOREIGN KEY (`id_snapshot_diario`) REFERENCES `snapshot_diario` (`id_snapshot_diario`);
ALTER TABLE `asignacion_vuelo` ADD CONSTRAINT `fksmmo5fqv78ob7v8ymcp5kaifa` FOREIGN KEY (`id_plan_ruta`) REFERENCES `plan_ruta` (`id_plan_ruta`);
ALTER TABLE `resultado_simulacion` ADD CONSTRAINT `fkta93ro0ff5crpt11xxfjot61j` FOREIGN KEY (`id_configuracion`) REFERENCES `configuracion_simulacion` (`id_configuracion`);
ALTER TABLE `envio_maletas` ADD CONSTRAINT `fkth9bq270f519wf9lwo5khm7cf` FOREIGN KEY (`id_aeropuerto_origen`) REFERENCES `aeropuerto` (`id_aeropuerto`);
