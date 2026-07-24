#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generador configurable para la prueba de OPERACIONES DÍA A DÍA (MoraPack).

Único valor dinámico principal: HORA_PRESENTACION (hora local de Lima en que inicia la prueba).
A partir de esa hora genera TODO lo pedido en el enunciado:
  1) Planes de vuelo adicionales en CSV (codigoVuelo, origen, destino, horaSalida, horaLlegada,
     duracionHoras, capacidadMaxima, esIntercontinental), con horas en el huso local del origen/destino.
  2) Guía de envíos individuales (12 por sede)         -> simple: origen, destino, cantidad (a mano)
  3) Registros de envíos de archivo (12 por sede)      -> ids 10000001..10000012 (formato del enunciado)
  4) CSV de carga masiva por sede (origen,destino,cantidad) -> para el botón "Carga Masiva" de la app

Cada sede usa su propio huso: la hh-mm y la fecha de los envíos quedan en la hora local del
aeropuerto de origen (Lima, Buenos Aires, Copenhague, Delhi).

Uso:  ajusta la sección CONFIGURACIÓN y ejecuta:  python generar_datos_prueba.py
"""

from datetime import datetime, timedelta
import os

# ═══════════════════════ CONFIGURACIÓN ═══════════════════════
HORA_PRESENTACION   = 11        # Hora de inicio de la prueba, en hora LOCAL de Lima (SPIM), 24h
MINUTO_PRESENTACION = 0         # Minuto de inicio (la prueba arranca en hora exacta -> 0)
FECHA               = None      # None = hoy; o fija: "2026-07-24"
CAPACIDAD_VUELO     = 150       # #### del patrón (se imprime como 0150)
MINUTO_VUELO        = 12        # Minuto fijo de los vuelos del patrón base (:12 en el enunciado)
CLIENTE             = "0007729" # IdClien (único, no relevante)
EQUIPO              = 4         # 4 = incluye Delhi (VIDP); 3 = excluye Delhi
CARPETA_SALIDA      = "salida_prueba_diaadia"
# ═════════════════════════════════════════════════════════════

# Huso (GMT entero, tal como está en la BD) de cada aeropuerto
GMT = {
    "SPIM": -5, "SABE": -3, "EKCH": 2, "VIDP": 5,           # sedes
    "SCEL": -3, "SVMI": -4, "SBBR": -3, "SKBO": -5,          # destinos Sudamérica
    "SGAS": -4, "SUAA": -3,
    "EBCI": 2, "LBSF": 3, "OAKB": 4, "OPKC": 5,              # destinos Europa/Asia
    "EHAM": 2, "OMDB": 4,
}
SUDAMERICA  = {"SPIM", "SABE", "SCEL", "SVMI", "SBBR", "SKBO", "SGAS", "SUAA"}
EUROPA_ASIA = {"EKCH", "VIDP", "EBCI", "LBSF", "OAKB", "OPKC", "EHAM", "OMDB"}

# Destinos y cantidades a registrar (iguales para todas las sedes, del enunciado)
DESTINOS = [
    ("SCEL", 180), ("SVMI", 180), ("SBBR", 10), ("SKBO", 10),
    ("SGAS", 15),  ("SUAA", 15),  ("EBCI", 180), ("LBSF", 180),
    ("OAKB", 10),  ("OPKC", 10),  ("EHAM", 15),  ("OMDB", 15),
]

GMT_BASE = GMT["SPIM"]  # Lima es la referencia de la hora de presentación


def duracion_horas(origen, destino):
    """Duración del vuelo según el enunciado."""
    if origen in ("SPIM", "SABE"):                 # sedes de Sudamérica
        return 6 if destino in SUDAMERICA else 12
    else:                                          # EKCH, VIDP (Europa/Asia)
        return 4 if destino in EUROPA_ASIA else 13


def instante_utc_inicio():
    """Instante UTC en que inicia la prueba (derivado de la hora local de Lima)."""
    if FECHA:
        base = datetime.strptime(FECHA, "%Y-%m-%d")
    else:
        base = datetime.now()
    base = base.replace(hour=HORA_PRESENTACION, minute=MINUTO_PRESENTACION,
                        second=0, microsecond=0)
    # Lima local -> UTC:  UTC = local - GMT_lima
    return base - timedelta(hours=GMT_BASE)


def local_de(codigo, utc):
    """Devuelve el datetime LOCAL del aeropuerto `codigo` para un instante UTC dado."""
    return utc + timedelta(hours=GMT[codigo])


# Columnas del CSV de carga de vuelos (formato del sistema)
PLAN_COLUMNS = ["codigoVuelo", "origen", "destino", "horaSalida", "horaLlegada",
                "duracionHoras", "capacidadMaxima", "esIntercontinental"]


def region(codigo):
    return "SA" if codigo in SUDAMERICA else "EA"


def es_intercontinental(origen, destino):
    """Intercontinental = el vuelo cruza de una región a otra (Sudamérica ↔ Europa/Asia)."""
    return region(origen) != region(destino)


def generar_planes(utc_inicio):
    """Genera las filas de planes de vuelo (una por vuelo) con los 8 campos del CSV.
    Las horas quedan en el HUSO LOCAL del origen (salida) y del destino (llegada)."""
    filas = []
    utc_vuelo = utc_inicio.replace(minute=MINUTO_VUELO)   # los vuelos salen a la hora :MINUTO_VUELO
    dests = [d for d, _ in DESTINOS]
    for origen in SEDES_ACTIVAS:
        salida_local = local_de(origen, utc_vuelo)                      # datetime local del origen
        for destino in dests:
            if destino == origen:
                continue
            dur = duracion_horas(origen, destino)
            llegada_local = local_de(destino, utc_vuelo + timedelta(hours=dur))   # datetime local del destino
            codigo = f"{origen}-{destino}-{salida_local:%Y%m%d}-{salida_local:%H%M}-{CAPACIDAD_VUELO:04d}"
            filas.append({
                "codigoVuelo":        codigo,
                "origen":             origen,
                "destino":            destino,
                "horaSalida":         salida_local.strftime("%Y-%m-%dT%H:%M:%S"),
                "horaLlegada":        llegada_local.strftime("%Y-%m-%dT%H:%M:%S"),
                "duracionHoras":      dur,
                "capacidadMaxima":    CAPACIDAD_VUELO,
                "esIntercontinental": "true" if es_intercontinental(origen, destino) else "false",
            })
    return filas


def generar_envios(sede, utc_inicio, id_inicial):
    """Genera los 12 registros de envíos de una sede en formato id-fecha-hora-dest-cant-cliente,
    con la fecha/hora en el huso local de la sede."""
    local = local_de(sede, utc_inicio)
    aaaammdd = local.strftime("%Y%m%d")
    hh = local.strftime("%H")
    mm = local.strftime("%M")
    lineas = []
    for i, (dest, cant) in enumerate(DESTINOS):
        idenv = f"{id_inicial + i:08d}"
        lineas.append(f"{idenv}-{aaaammdd}-{hh}-{mm}-{dest}-{cant:03d}-{CLIENTE}")
    return lineas


def generar_individual(sede):
    """Guía SIMPLE para registrar a mano los envíos individuales de una sede:
    solo origen, destino y cantidad (los 3 datos que se teclean en el formulario)."""
    lineas = [f"# Individuales {sede} — registrar a mano (origen, destino, cantidad)",
              "ORIGEN  DESTINO  CANTIDAD"]
    for dest, cant in DESTINOS:
        lineas.append(f"{sede:<6}  {dest:<7}  {cant}")
    return lineas


def generar_csv(sede):
    """CSV para el botón Carga Masiva de la app: origen,destino,cantidad.
    El origen es la sede; los destinos y cantidades del enunciado."""
    return ["origen,destino,cantidad"] + [f"{sede},{d},{c}" for d, c in DESTINOS]


def escribir(ruta, lineas):
    with open(ruta, "w", encoding="utf-8") as f:
        f.write("\n".join(lineas) + "\n")


def escribir_csv(ruta, filas, columnas):
    lineas = [",".join(columnas)]
    for f in filas:
        lineas.append(",".join(str(f[c]) for c in columnas))
    escribir(ruta, lineas)


def main():
    global SEDES_ACTIVAS
    SEDES_ACTIVAS = ["SPIM", "SABE", "EKCH", "VIDP"]
    if EQUIPO == 3:
        SEDES_ACTIVAS = ["SPIM", "SABE", "EKCH"]   # equipos de 3: sin Delhi

    utc_inicio = instante_utc_inicio()

    # La salida SIEMPRE queda junto a este .py (no depende de desde dónde lo ejecutes).
    # __file__ = ubicación real del script; así nunca escribe en el CWD (VS Code, etc.).
    base_dir = os.path.dirname(os.path.abspath(__file__))
    carpeta = os.path.join(base_dir, CARPETA_SALIDA)
    os.makedirs(carpeta, exist_ok=True)

    print("=" * 64)
    print("  GENERADOR PRUEBA OPERACIONES DÍA A DÍA")
    print("=" * 64)
    print(f"  Hora de presentación (Lima): {HORA_PRESENTACION:02d}:{MINUTO_PRESENTACION:02d}")
    print(f"  Instante UTC de inicio:      {utc_inicio:%Y-%m-%d %H:%M}")
    print(f"  Equipo de {EQUIPO} -> sedes: {', '.join(SEDES_ACTIVAS)}")
    print(f"  Salida en carpeta:           {carpeta}")
    print("-" * 64)
    print("  Hora local de inicio por sede:")
    for s in SEDES_ACTIVAS:
        print(f"    {s}: {local_de(s, utc_inicio):%Y-%m-%d %H:%M}")
    print("=" * 64)

    # 1) Planes de vuelo (CSV con los campos del sistema)
    planes = generar_planes(utc_inicio)
    escribir_csv(os.path.join(carpeta, "planes_vuelo_adicionales.csv"), planes, PLAN_COLUMNS)
    print(f"\n[1] Planes de vuelo adicionales ({len(planes)} vuelos) -> planes_vuelo_adicionales.csv:")
    print("    " + ",".join(PLAN_COLUMNS))
    for f in planes:
        print("    " + ",".join(str(f[c]) for c in PLAN_COLUMNS))

    # 2) y 3) Envíos individuales y de archivo por sede
    print("\n[2] Envíos INDIVIDUALES — guía simple para registrar a mano (origen, destino, cantidad):")
    for s in SEDES_ACTIVAS:
        ind = generar_individual(s)
        escribir(os.path.join(carpeta, f"envios_individual_{s}.txt"), ind)
        print(f"\n  *** {s}")
        for l in ind:
            print("    " + l)

    print("\n[3] Registros de envíos de ARCHIVO (ids 10000001..):")
    for s in SEDES_ACTIVAS:
        env = generar_envios(s, utc_inicio, 10000001)
        escribir(os.path.join(carpeta, f"envios_archivo_{s}.txt"), env)
        print(f"    envios_archivo_{s}.txt  ({len(env)} envíos)")

    # 4) CSV de carga masiva por sede (destino,cantidad) para la app
    print("\n[4] CSV de carga masiva (destino,cantidad) por sede:")
    for s in SEDES_ACTIVAS:
        escribir(os.path.join(carpeta, f"carga_masiva_{s}.csv"), generar_csv(s))
        print(f"    carga_masiva_{s}.csv")

    print("\nListo. Revisa la carpeta:", carpeta)


if __name__ == "__main__":
    main()
