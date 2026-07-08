# 启动 wordflip-server（dev profile，端口 8080）
# 用法：在 wordflip-server 目录执行  .\scripts\start-dev.ps1

$ErrorActionPreference = "Stop"
$serverRoot = Split-Path $PSScriptRoot -Parent
Set-Location $serverRoot

$listening = netstat -ano | Select-String ":8080\s+.*LISTENING"
if ($listening) {
    Write-Host "8080 已在监听，后端可能已运行，无需重复启动。"
    Write-Host "若要重启，请先执行：.\scripts\stop-dev.ps1"
    exit 0
}

Write-Host "启动 wordflip-server（profile=dev，默认已在 application.yml 配置）..."
Write-Host "停止服务：Ctrl+C"
Write-Host ""

# PowerShell 下不要写 -Dspring-boot.run.profiles=dev（会被错误解析）
.\mvnw.cmd spring-boot:run
