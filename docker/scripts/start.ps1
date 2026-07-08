# 启动 MySQL / Redis / MinIO
$ErrorActionPreference = "Stop"
$dockerRoot = Split-Path $PSScriptRoot -Parent
Set-Location $dockerRoot

if (-not (Test-Path ".env")) {
    Write-Error "缺少 docker\.env，请先复制 .env.example 为 .env"
}

Write-Host "启动 Docker 基础设施..."
docker compose up -d
docker compose ps
