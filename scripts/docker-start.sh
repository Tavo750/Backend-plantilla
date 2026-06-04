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
sshpass -e ssh -N -f \
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
exec java $JAVA_OPTS \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod \
  -jar /app/app.jar
