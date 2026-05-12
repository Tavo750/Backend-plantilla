# =====================================================================
# Etapa 1: Compilación con Maven
# =====================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# =====================================================================
# Etapa 2: Imagen de ejecución ligera
# =====================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear usuario no-root por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/target/*.jar app.jar

# Copiar archivo de entorno si existe
COPY .env* ./

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar"]
