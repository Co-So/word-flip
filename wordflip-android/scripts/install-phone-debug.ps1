# 真机 USB 联调：端口转发 + 安装 debug 包
$ErrorActionPreference = "Stop"
$androidRoot = Split-Path $PSScriptRoot -Parent
Set-Location $androidRoot

& "$PSScriptRoot\adb-reverse.ps1"
.\gradlew.bat :app:installDebug

Write-Host ""
Write-Host "安装完成。请确认 wordflip-server 已在电脑 8080 端口运行。"
