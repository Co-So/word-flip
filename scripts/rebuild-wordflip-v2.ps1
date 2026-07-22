[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$MySqlHost = "127.0.0.1",
    [int]$MySqlPort = 3306,
    [string]$AdminUser = "root",
    [string]$SourceDatabase = "wordflip",
    [string]$TargetDatabase = "wordflip_v2",
    [string]$BackupDirectory = (Join-Path $PSScriptRoot "..\backups"),
    [string]$ContentBuildDirectory = (Join-Path $PSScriptRoot "..\tools\content-pipeline\out"),
    [switch]$Execute
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

if (-not $Execute) {
    Write-Host "DRY-RUN：未改数据库。确认参数后追加 -Execute。"
}
if ($SourceDatabase -eq $TargetDatabase) {
    throw "目标库必须是新的空库，禁止直接覆盖现有 $SourceDatabase。"
}
if ([string]::IsNullOrWhiteSpace($env:WORDFLIP_DB_PASSWORD)) {
    throw "请先设置环境变量 WORDFLIP_DB_PASSWORD。"
}
if (-not (Test-Path -LiteralPath $ContentBuildDirectory)) {
    throw "内容构建目录不存在：$ContentBuildDirectory"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = Join-Path $BackupDirectory "$SourceDatabase-$timestamp.sql"
$commonArgs = @("--host=$MySqlHost", "--port=$MySqlPort", "--user=$AdminUser", "--protocol=TCP")

Write-Host "1/4 备份 $SourceDatabase -> $backupPath"
Write-Host "2/4 创建全新数据库 $TargetDatabase（已存在则终止）"
Write-Host "3/4 执行 migration-v2/V1 基线"
Write-Host "4/4 幂等发布三本词书内容"
if (-not $Execute) { return }

New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
$env:MYSQL_PWD = $env:WORDFLIP_DB_PASSWORD
try {
    & mysqldump @commonArgs --single-transaction --routines --triggers --default-character-set=utf8mb4 $SourceDatabase | Out-File -LiteralPath $backupPath -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw "mysqldump 失败，未继续重建。" }

    $exists = & mysql @commonArgs --batch --skip-column-names --execute="SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$TargetDatabase'"
    if ($LASTEXITCODE -ne 0) { throw "无法检查目标数据库。" }
    if ($exists) { throw "目标数据库 $TargetDatabase 已存在；脚本不会删除或清空它。" }
    & mysql @commonArgs --execute="CREATE DATABASE ``$TargetDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    if ($LASTEXITCODE -ne 0) { throw "创建目标数据库失败。" }

    $env:FLYWAY_URL = "jdbc:mysql://${MySqlHost}:$MySqlPort/$TargetDatabase?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
    $env:FLYWAY_USER = $AdminUser
    $env:FLYWAY_PASSWORD = $env:WORDFLIP_DB_PASSWORD
    $env:FLYWAY_LOCATIONS = "filesystem:$repoRoot/wordflip-server/src/main/resources/db/migration-v2"
    Push-Location (Join-Path $repoRoot "wordflip-server")
    try {
        & .\mvnw.cmd org.flywaydb:flyway-maven-plugin:10.10.0:migrate
        if ($LASTEXITCODE -ne 0) { throw "Flyway V1 执行失败。" }
    } finally {
        Pop-Location
    }

    $escapedPassword = [uri]::EscapeDataString($env:WORDFLIP_DB_PASSWORD)
    $env:WORDFLIP_CONTENT_DSN = "mysql://${AdminUser}:$escapedPassword@${MySqlHost}:$MySqlPort/$TargetDatabase"
    Push-Location (Join-Path $repoRoot "tools\content-pipeline")
    try {
        $env:PYTHONPATH = "src"
        & python -m wordflip_content publish --build-dir $ContentBuildDirectory
        if ($LASTEXITCODE -ne 0) { throw "内容发布失败。" }
    } finally {
        Pop-Location
    }
    Write-Host "完成。备份文件：$backupPath；新库：$TargetDatabase"
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Item Env:FLYWAY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:WORDFLIP_CONTENT_DSN -ErrorAction SilentlyContinue
}
