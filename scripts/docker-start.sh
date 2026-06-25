#!/bin/sh
# =====================================================================
# Script de arranque: establece tunel SSH hacia MySQL antes de iniciar
# la aplicacion Spring Boot. Replica el tunel SSH que usa DBeaver.
# =====================================================================
set -e

echo "=== Estableciendo tunel SSH hacia MySQL ==="
echo "    Host SSH : ${SSH_HOST}:${SSH_PORT:-22}"
echo "    Usuario  : ${SSH_USER}"

# Lanzar tunel SSH en background
# -N  : no ejecutar comandos remotos
# -f  : ir a background
# -L  : reenviar 127.0.0.1:3306 local -> 127.0.0.1:3306 en el servidor SSH
sshpass -p "${SSH_PASSWORD}" ssh -N -f \
  -o StrictHostKeyChecking=no \
  -o ServerAliveInterval=30 \
  -o ServerAliveCountMax=5 \
  -o ConnectTimeout=15 \
  -L 127.0.0.1:3306:127.0.0.1:3306 \
  -p "${SSH_PORT:-22}" \
  "${SSH_USER}@${SSH_HOST}"

echo "=== Esperando que el puerto MySQL este disponible... ==="
MAX_WAIT=60
WAITED=0
until nc -z 127.0.0.1 3306 2>/dev/null; do
  if [ "$WAITED" -ge "$MAX_WAIT" ]; then
    echo "ERROR: Puerto 3306 no disponible tras ${MAX_WAIT}s. Abortando."
    exit 1
  fi
  echo "    Intento ${WAITED}s / ${MAX_WAIT}s..."
  sleep 2
  WAITED=$((WAITED + 2))
done

echo "=== Puerto MySQL disponible. Iniciando Spring Boot... ==="
# ── Límites JVM para VM con 2 GB RAM (contenedor 768m) ───────────────────────
# -XX:+UseContainerSupport    : la JVM lee el memory limit del contenedor Docker
# -XX:MaxRAMPercentage=40.0   : heap máximo = 40% de 768m = ~307 MB
# -XX:InitialRAMPercentage=15 : heap inicial conservador
# -XX:MaxMetaspaceSize=96m    : limitar Metaspace (clases cargadas)
# -XX:+UseG1GC                : G1 maneja mejor la concurrencia de Spring Boot
# -XX:MaxGCPauseMillis=200    : objetivo de pausa de GC máxima
# -XX:+ExitOnOutOfMemoryError : detener el contenedor limpiamente si hay OOM
exec java $JAVA_OPTS \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=35.0 \
  -XX:InitialRAMPercentage=15.0 \
  -XX:MaxMetaspaceSize=160m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod \
  -jar /app/app.jar
