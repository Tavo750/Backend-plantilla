# =====================================================================
# Dockerfile - Tasf.B2B Backend (Spring Boot)
# Build multi-etapa: compilación con Maven → runtime ligero Alpine
# Imagen base Eclipse Temurin 21 JRE Alpine (~85 MB en runtime)
# =====================================================================

# ── Etapa 1: Compilación ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copiar solo pom.xml primero → cachear dependencias entre builds
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copiar código fuente y compilar (sin tests para deploy rápido)
COPY src ./src
RUN mvn clean package -DskipTests -B --no-transfer-progress

# ── Etapa 2: Imagen de ejecución ligera ──────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Tasf.B2B Team"
LABEL description="Backend Tasf.B2B - Spring Boot 3 + Java 21"

WORKDIR /app

# Crear usuario no-root por seguridad (buena práctica en producción)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Crear directorio de logs con permisos correctos
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Copiar solo el JAR compilado (no el .env, que viene de docker-compose)
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup /app/app.jar

USER appuser

# Puerto 8080 (estándar Spring Boot producción)
EXPOSE 8080

# JAVA_OPTS es inyectado desde docker-compose (límite de heap)
# -XX:+UseContainerSupport  → respeta los límites de memoria del contenedor
# -XX:+UseG1GC              → GC eficiente en bajo heap
# -Dspring.profiles.active  → activa el perfil de producción
ENTRYPOINT ["sh", "-c", \
  "java $JAVA_OPTS \
   -XX:+UseContainerSupport \
   -XX:MaxRAMPercentage=75.0 \
   -XX:+UseG1GC \
   -Djava.security.egd=file:/dev/./urandom \
   -Dspring.profiles.active=prod \
   -jar /app/app.jar"]
