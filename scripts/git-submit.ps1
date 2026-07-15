[CmdletBinding()]
param(
    [string]$Message,
    [string[]]$Include = @(),
    [switch]$Yes
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = $null
$pushedLocation = $false
$commitCreated = $false

function Stop-WithError([string]$Text) {
    throw $Text
}

function Invoke-CheckedGit([string[]]$Arguments) {
    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git 命令执行失败：git $($Arguments -join ' ')"
    }
}

function Test-BlockedPath([string]$Path) {
    $normalized = $Path.Replace('\', '/').ToLowerInvariant()
    $leaf = [IO.Path]::GetFileName($normalized)

    return $leaf -eq ".env" -or
        $leaf -like "*.jks" -or
        $leaf -like "*.keystore" -or
        $leaf -like "*.pem" -or
        $leaf -like "*.p12" -or
        $leaf -like "*.pfx" -or
        $leaf -like "id_rsa*" -or
        $leaf -like "id_ed25519*" -or
        $leaf -eq "application-local.yml" -or
        $leaf -like "*.dex"
}

try {
    $rootOutput = & git rev-parse --show-toplevel 2>$null
    $rootExitCode = $LASTEXITCODE
    $root = $rootOutput | Select-Object -First 1
    if ($rootExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        Stop-WithError "当前目录不在 Git 工作树中。"
    }

    Push-Location $root
    $pushedLocation = $true

    $branchOutput = & git symbolic-ref --quiet --short HEAD 2>$null
    $branchExitCode = $LASTEXITCODE
    $branch = $branchOutput | Select-Object -First 1
    if ($branchExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($branch)) {
        Stop-WithError "当前处于 detached HEAD，请先切换到功能分支。"
    }
    if ($branch -eq "main") {
        Stop-WithError "禁止在 main 分支直接提交，请先创建功能分支。"
    }

    & git remote get-url origin *> $null
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "仓库未配置 origin 远程。"
    }

    foreach ($path in $Include) {
        if (Test-BlockedPath $path) {
            Stop-WithError "拒绝暂存危险或临时文件：$path"
        }
        if (-not (Test-Path -LiteralPath $path)) {
            Stop-WithError "指定的路径不存在：$path"
        }
    }

    # 默认只暂存已跟踪文件，未跟踪内容必须显式指定。
    Invoke-CheckedGit @("add", "-u")
    foreach ($path in $Include) {
        Invoke-CheckedGit @("add", "--", $path)
    }

    $staged = @(& git diff --cached --name-only --diff-filter=ACDMRTUXB)
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError "无法读取已暂存文件。"
    }
    if ($staged.Count -eq 0) {
        Stop-WithError "没有可提交的已暂存改动。"
    }
    foreach ($path in $staged) {
        if (Test-BlockedPath $path) {
            Stop-WithError "已暂存内容包含危险或临时文件：$path"
        }
    }

    if ([string]::IsNullOrWhiteSpace($Message)) {
        $Message = Read-Host "请输入 Conventional Commit 标题"
    }

    $pattern = "^(feat|fix|docs|refactor|test|chore|build|style)(\([a-z0-9-]+\))?: .+$"
    if ($Message.Length -gt 72 -or
        $Message -notmatch $pattern -or
        $Message.Contains("`n") -or
        $Message.Contains("`r")) {
        Stop-WithError "提交标题不符合 Conventional Commits 或超过 72 个字符。"
    }

    $description = $Message.Substring($Message.IndexOf(":") + 1)
    if ($description -notmatch "[\u4e00-\u9fff]") {
        Stop-WithError "提交标题说明必须包含简体中文。"
    }

    Write-Host "分支：$branch"
    Write-Host "远程：origin"
    Write-Host "提交：$Message"
    Write-Host "已暂存文件："
    $staged | ForEach-Object { Write-Host "  - $_" }

    if (-not $Yes) {
        $answer = Read-Host "确认提交并推送？输入 y 继续"
        if ($answer -notin @("y", "Y")) {
            Stop-WithError "已取消，已暂存内容保留。"
        }
    }

    Invoke-CheckedGit @("commit", "-m", $Message)
    $commitCreated = $true

    & git rev-parse --abbrev-ref --symbolic-full-name "@{upstream}" *> $null
    if ($LASTEXITCODE -eq 0) {
        $retryCommand = "git push"
        Invoke-CheckedGit @("push")
    }
    else {
        $retryCommand = "git push -u origin $branch"
        Invoke-CheckedGit @("push", "-u", "origin", $branch)
    }

    Write-Host "提交并推送完成：$branch"
}
catch {
    if ($commitCreated) {
        [Console]::Error.WriteLine("本地 commit 已保留，可重试：$retryCommand")
    }
    [Console]::Error.WriteLine("错误：$($_.Exception.Message)")
    exit 1
}
finally {
    if ($pushedLocation) {
        Pop-Location
    }
}
