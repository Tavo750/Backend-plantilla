# =====================================================================
# Script de inicialización del entorno de desarrollo
# Uso: .\scripts\init-env.ps1
# =====================================================================

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Inicializando entorno de desarrollo"        -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# Verificar Docker
Write-Host "`n[1/4] Verificando Docker..." -ForegroundColor Yellow
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "  Docker encontrado." -ForegroundColor Green
} else {
    Write-Host "  Docker no encontrado. Instálalo desde https://www.docker.com/" -ForegroundColor Red
    exit 1
}

# Verificar Java 21
Write-Host "[2/4] Verificando Java 21..." -ForegroundColor Yellow
$javaVersion = java -version 2>&1 | Select-Object -First 1
if ($javaVersion -match "21") {
    Write-Host "  Java 21 encontrado." -ForegroundColor Green
} else {
    Write-Host "  Java 21 no encontrado. Version actual: $javaVersion" -ForegroundColor Red
    Write-Host "  Descarga desde: https://adoptium.net/" -ForegroundColor Yellow
}

# Verificar Maven
Write-Host "[3/4] Verificando Maven..." -ForegroundColor Yellow
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "  Maven encontrado." -ForegroundColor Green
} else {
    Write-Host "  Maven no encontrado. Instálalo o usa el wrapper ./mvnw" -ForegroundColor Red
}

# Levantar PostgreSQL con Docker Compose
Write-Host "[4/4] Levantando PostgreSQL con Docker Compose..." -ForegroundColor Yellow
$projectRoot = Split-Path -Parent $PSScriptRoot
docker compose -f "$projectRoot\docker-compose.yml" up -d postgres

Write-Host "`n=============================================" -ForegroundColor Cyan
Write-Host "  Entorno listo. Ejecuta:" -ForegroundColor Cyan
Write-Host "  mvn spring-boot:run" -ForegroundColor White
Write-Host "=============================================" -ForegroundColor Cyan
