# 停止占用 8080 的 wordflip-server（本机开发用）
$ErrorActionPreference = "Stop"

$pids = @(
    netstat -ano |
        Select-String ":8080\s+.*LISTENING" |
        ForEach-Object {
            ($_.Line -split '\s+')[-1]
        } |
        Sort-Object -Unique
)

if (-not $pids) {
    Write-Host "8080 端口未被占用，无需停止。"
    exit 0
}

foreach ($procId in $pids) {
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Host "停止进程 PID=$procId ($($proc.ProcessName))..."
        Stop-Process -Id $procId -Force
    }
}

Write-Host "8080 已释放。"
