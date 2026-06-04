# =====================================================================
# Script de inicialización del entorno de desarrollo
# Uso: .\scripts\init-env.ps1
# Base de datos: MySQL en VM PUCP (1inf54-982-7g.inf.pucp.edu.pe:3306)
# =====================================================================

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Inicializando entorno de desarrollo"        -ForegroundColor Cyan
Write-Host "  (MySQL externo en VM PUCP)"                 -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# Verificar Docker
Write-Host "`n[1/3] Verificando Docker..." -ForegroundColor Yellow
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "  Docker encontrado." -ForegroundColor Green
} else {
    Write-Host "  Docker no encontrado. Instálalo desde https://www.docker.com/" -ForegroundColor Red
    exit 1
}

# Verificar Java 21
Write-Host "[2/3] Verificando Java 21..." -ForegroundColor Yellow
$javaVersion = java -version 2>&1 | Select-Object -First 1
if ($javaVersion -match "21") {
    Write-Host "  Java 21 encontrado." -ForegroundColor Green
} else {
    Write-Host "  Java 21 no encontrado. Version actual: $javaVersion" -ForegroundColor Red
    Write-Host "  Descarga desde: https://adoptium.net/" -ForegroundColor Yellow
}

# Verificar Maven
Write-Host "[3/3] Verificando Maven..." -ForegroundColor Yellow
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "  Maven encontrado." -ForegroundColor Green
} else {
    Write-Host "  Maven no encontrado. Instálalo o usa el wrapper ./mvnw" -ForegroundColor Red
}

Write-Host "`n=============================================" -ForegroundColor Cyan
Write-Host "  Entorno listo."                              -ForegroundColor Cyan
Write-Host "  La BD MySQL está en la VM PUCP:"            -ForegroundColor Cyan
Write-Host "  Host : 1inf54-982-7g.inf.pucp.edu.pe:3306"  -ForegroundColor White
Write-Host "  DB   : baseEquipo7G"                        -ForegroundColor White
Write-Host ""
Write-Host "  Para desarrollo local ejecuta:"             -ForegroundColor Cyan
Write-Host "  mvn spring-boot:run"                        -ForegroundColor White
Write-Host ""
Write-Host "  Para desplegar con Dokploy (en la VM):"     -ForegroundColor Cyan
Write-Host "  docker compose up -d --build"               -ForegroundColor White
Write-Host "=============================================" -ForegroundColor Cyan
