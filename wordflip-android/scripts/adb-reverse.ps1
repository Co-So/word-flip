# 真机 USB 联调：将手机 localhost:8080 转发到电脑 8080（Spring Boot）
# 用法：手机开启 USB 调试并连接后，在 wordflip-android 目录执行：
#   .\scripts\adb-reverse.ps1

$ErrorActionPreference = "Stop"

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Error "未找到 adb。请安装 Android SDK Platform-Tools 并加入 PATH。"
}

$devices = & adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if (-not $devices) {
    Write-Error "未检测到已授权的设备。请 USB 连接手机并开启「USB 调试」。"
}

& adb reverse tcp:8080 tcp:8080
Write-Host "已设置端口转发：手机 127.0.0.1:8080 -> 电脑 localhost:8080"
Write-Host "请确认本机后端已启动：wordflip-server (profile dev) 监听 8080"
Write-Host "然后安装/启动 App：.\gradlew.bat :app:installDebug"
