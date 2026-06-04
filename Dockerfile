# =====================================================================
# Dockerfile - Backend Spring Boot con tunel SSH integrado
# El contenedor establece el tunel SSH a MySQL antes de arrancar la app
# =====================================================================

# -- Etapa 1: Compilacion -----------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

COPY src ./src
RUN mvn clean package -DskipTests -B --no-transfer-progress

# -- Etapa 2: Imagen de ejecucion ligera --------------------------------
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Tasf.B2B Team"
LABEL description="Backend Tasf.B2B - Spring Boot 3 + Java 21 + SSH tunnel"

WORKDIR /app

# Instalar herramientas SSH y utilidades de red
RUN apk add --no-cache openssh-client sshpass netcat-openbsd

# Crear usuario no-root por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Crear directorio de logs
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Copiar el JAR compilado
COPY --from=build /app/target/*.jar app.jar

# Copiar el script de arranque
COPY scripts/docker-start.sh /app/docker-start.sh
RUN chmod +x /app/docker-start.sh && chown appuser:appgroup /app/docker-start.sh

RUN chown appuser:appgroup /app/app.jar

USER appuser

EXPOSE 3000

# El script establece el tunel SSH y luego lanza Spring Boot
ENTRYPOINT ["/app/docker-start.sh"]
