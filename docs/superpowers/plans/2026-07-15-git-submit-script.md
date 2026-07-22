# Git Submit Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一个阻止 `main`、默认只暂存已跟踪文件、校验提交信息并安全推送当前功能分支的 PowerShell 脚本。

**Architecture:** 生产脚本集中在 `scripts/git-submit.ps1`，将验证、暂存、commit 和 push 串成一个明确的失败即停流程。集成测试在系统临时目录创建普通仓库和本地 bare remote，通过子 PowerShell 进程验证真实 Git 行为，完全隔离当前 WordFlip 工作树。

**Tech Stack:** PowerShell 7/Windows PowerShell 5.1 兼容语法、Git CLI、PowerShell 自包含集成测试。

## Global Constraints

- 不在 `main` 或 detached HEAD 上提交。
- 默认仅执行 `git add -u`；未跟踪文件必须经 `-Include` 显式纳入。
- 禁止 `.env`、`*.jks`、`*.keystore`、`*.pem`、`*.p12`、`*.pfx`、`id_rsa*`、`id_ed25519*`、`application-local.yml` 和 `*.dex`。
- 提交标题必须匹配 Conventional Commits，且不超过 72 个字符。
- 不运行 Maven、Gradle 或真机测试。
- 不 amend，不 force push，不自动 reset 已暂存内容。
- 当前仓库中不执行真实 commit 或 push；这些行为只在临时测试仓库中执行。

---

### Task 1: 分支、暂存与提交信息安全门

**Files:**
- Create: `scripts/git-submit.ps1`
- Create: `scripts/tests/git-submit.Tests.ps1`

**Interfaces:**
- Consumes: Git CLI；参数 `-Message [string]`、`-Include [string[]]`、`-Yes [switch]`。
- Produces: 成功时退出码 `0`；任一安全门失败时输出简体中文错误并返回非零退出码。

- [ ] **Step 1: 创建首批失败集成测试**

在 `scripts/tests/git-submit.Tests.ps1` 中创建可独立执行的测试器，完整内容如下：

```powershell
param()

$ErrorActionPreference = "Stop"
$submitScript = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\git-submit.ps1"))
$tempRoots = [Collections.Generic.List[string]]::new()

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "断言失败：$Message" }
}

function Invoke-Git([string]$Repo, [string[]]$Arguments) {
    $output = & git -C $Repo @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') 失败：$output" }
    return $output
}

function New-TestRepository([string]$Branch = "main") {
    $root = Join-Path ([IO.Path]::GetTempPath()) ("wordflip-git-submit-" + [guid]::NewGuid())
    $remote = "$root-remote.git"
    $tempRoots.Add($root)
    $tempRoots.Add($remote)
    & git init --bare $remote | Out-Null
    & git init -b $Branch $root | Out-Null
    Invoke-Git $root @("config", "user.name", "WordFlip Test") | Out-Null
    Invoke-Git $root @("config", "user.email", "wordflip-test@example.invalid") | Out-Null
    Set-Content -LiteralPath (Join-Path $root "tracked.txt") -Value "initial" -Encoding UTF8
    Invoke-Git $root @("add", "tracked.txt") | Out-Null
    Invoke-Git $root @("commit", "-m", "chore(test): 初始化临时仓库") | Out-Null
    Invoke-Git $root @("remote", "add", "origin", $remote) | Out-Null
    return [pscustomobject]@{ Root = $root; Remote = $remote }
}

function Invoke-Submit([string]$Repo, [string[]]$Arguments) {
    $hostExe = (Get-Process -Id $PID).Path
    Push-Location $Repo
    try {
        $output = & $hostExe -NoProfile -File $submitScript @Arguments 2>&1 | Out-String
        return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
    }
    finally {
        Pop-Location
    }
}

try {
    $mainRepo = New-TestRepository
    Set-Content -LiteralPath (Join-Path $mainRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    $mainResult = Invoke-Submit $mainRepo.Root @("-Message", "fix(test): 不应提交主分支", "-Yes")
    Assert-True ($mainResult.ExitCode -ne 0) "main 分支应被拒绝"

    $stageRepo = New-TestRepository "fix/test-stage"
    Set-Content -LiteralPath (Join-Path $stageRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $stageRepo.Root "untracked.txt") -Value "new" -Encoding UTF8
    $stageResult = Invoke-Submit $stageRepo.Root @("-Message", "fix(test): 验证默认暂存", "-Yes")
    Assert-True ($stageResult.ExitCode -eq 0) "功能分支提交应成功"
    $committed = Invoke-Git $stageRepo.Root @("show", "--name-only", "--format=", "HEAD") | Out-String
    Assert-True ($committed -match "tracked\.txt") "已跟踪文件应被提交"
    Assert-True ($committed -notmatch "untracked\.txt") "未跟踪文件不应被默认提交"

    $includeRepo = New-TestRepository "feat/test-include"
    Set-Content -LiteralPath (Join-Path $includeRepo.Root "safe-new.txt") -Value "new" -Encoding UTF8
    $includeResult = Invoke-Submit $includeRepo.Root @("-Message", "feat(test): 纳入指定新文件", "-Include", "safe-new.txt", "-Yes")
    Assert-True ($includeResult.ExitCode -eq 0) "-Include 应纳入安全新文件"
    $includeCommit = Invoke-Git $includeRepo.Root @("show", "--name-only", "--format=", "HEAD") | Out-String
    Assert-True ($includeCommit -match "safe-new\.txt") "指定新文件应出现在 commit 中"

    $dangerRepo = New-TestRepository "fix/test-danger"
    Set-Content -LiteralPath (Join-Path $dangerRepo.Root "payload.dex") -Value "binary" -Encoding UTF8
    $dangerResult = Invoke-Submit $dangerRepo.Root @("-Message", "fix(test): 拒绝临时产物", "-Include", "payload.dex", "-Yes")
    Assert-True ($dangerResult.ExitCode -ne 0) ".dex 应被拒绝"

    $messageRepo = New-TestRepository "fix/test-message"
    Set-Content -LiteralPath (Join-Path $messageRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    $messageResult = Invoke-Submit $messageRepo.Root @("-Message", "bad message", "-Yes")
    Assert-True ($messageResult.ExitCode -ne 0) "非 Conventional Commit 标题应被拒绝"

    Write-Host "TASK1_TESTS_PASSED"
}
finally {
    foreach ($path in $tempRoots) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
    }
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```powershell
pwsh -NoProfile -File .\scripts\tests\git-submit.Tests.ps1
```

Expected: FAIL，原因为 `scripts/git-submit.ps1` 尚不存在，不得是测试脚本语法错误。

- [ ] **Step 3: 编写最小安全实现**

在 `scripts/git-submit.ps1` 写入以下完整实现：

```powershell
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

function Stop-WithError([string]$Text) {
    Write-Error $Text
    exit 1
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
    return $leaf -eq '.env' -or
        $leaf -like '*.jks' -or $leaf -like '*.keystore' -or
        $leaf -like '*.pem' -or $leaf -like '*.p12' -or $leaf -like '*.pfx' -or
        $leaf -like 'id_rsa*' -or $leaf -like 'id_ed25519*' -or
        $leaf -eq 'application-local.yml' -or $leaf -like '*.dex'
}

try {
    $root = (& git rev-parse --show-toplevel 2>$null | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        Stop-WithError "当前目录不在 Git 工作树中。"
    }
    Push-Location $root
    $pushedLocation = $true

    $branch = (& git symbolic-ref --quiet --short HEAD 2>$null | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branch)) {
        Stop-WithError "当前处于 detached HEAD，请先切换到功能分支。"
    }
    if ($branch -eq 'main') {
        Stop-WithError "禁止在 main 分支直接提交，请先创建功能分支。"
    }

    & git remote get-url origin *> $null
    if ($LASTEXITCODE -ne 0) { Stop-WithError "仓库未配置 origin 远程。" }

    foreach ($path in $Include) {
        if (Test-BlockedPath $path) { Stop-WithError "拒绝暂存危险或临时文件：$path" }
        if (-not (Test-Path -LiteralPath $path)) { Stop-WithError "指定的路径不存在：$path" }
    }

    Invoke-CheckedGit @('add', '-u')
    foreach ($path in $Include) { Invoke-CheckedGit @('add', '--', $path) }

    $staged = @(& git diff --cached --name-only --diff-filter=ACDMRTUXB)
    if ($LASTEXITCODE -ne 0) { Stop-WithError "无法读取已暂存文件。" }
    if ($staged.Count -eq 0) { Stop-WithError "没有可提交的已暂存改动。" }
    foreach ($path in $staged) {
        if (Test-BlockedPath $path) { Stop-WithError "已暂存内容包含危险或临时文件：$path" }
    }

    if ([string]::IsNullOrWhiteSpace($Message)) { $Message = Read-Host "请输入 Conventional Commit 标题" }
    $pattern = '^(feat|fix|docs|refactor|test|chore|build|style)(\([a-z0-9-]+\))?: .+$'
    if ($Message.Length -gt 72 -or $Message -notmatch $pattern -or $Message.Contains("`n") -or $Message.Contains("`r")) {
        Stop-WithError "提交标题不符合 Conventional Commits 或超过 72 个字符。"
    }
    $description = $Message.Substring($Message.IndexOf(':') + 1)
    if ($description -notmatch '[\u4e00-\u9fff]') {
        Stop-WithError "提交标题说明必须包含简体中文。"
    }

    Write-Host "分支：$branch"
    Write-Host "远程：origin"
    Write-Host "提交：$Message"
    Write-Host "已暂存文件："
    $staged | ForEach-Object { Write-Host "  - $_" }

    if (-not $Yes) {
        $answer = Read-Host "确认提交并推送？输入 y 继续"
        if ($answer -notin @('y', 'Y')) { Stop-WithError "已取消，已暂存内容保留。" }
    }

    Invoke-CheckedGit @('commit', '-m', $Message)

    & git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' *> $null
    if ($LASTEXITCODE -eq 0) {
        $retryCommand = 'git push'
    } else {
        $retryCommand = "git push -u origin $branch"
    }
    try {
        if ($retryCommand -eq 'git push') {
            Invoke-CheckedGit @('push')
        } else {
            Invoke-CheckedGit @('push', '-u', 'origin', $branch)
        }
    }
    catch {
        Write-Host "本地 commit 已保留，可重试：$retryCommand"
        throw
    }
    Write-Host "提交并推送完成：$branch"
}
catch {
    Write-Error $_.Exception.Message
    exit 1
}
finally {
    if ($pushedLocation) { Pop-Location }
}
```

- [ ] **Step 4: 运行测试并确认 GREEN**

Run:

```powershell
pwsh -NoProfile -File .\scripts\tests\git-submit.Tests.ps1
```

Expected: 输出 `TASK1_TESTS_PASSED`，退出码 `0`。

### Task 2: 上游分支、push 失败与回归验证

**Files:**
- Modify: `scripts/tests/git-submit.Tests.ps1`
- Modify: `scripts/git-submit.ps1`

**Interfaces:**
- Consumes: Task 1 的 `-Message`、`-Include`、`-Yes` 参数和 `origin` 远程。
- Produces: 无上游时设置 `origin/<branch>`；已有上游时直接 push；push 失败时保留本地 commit。

- [ ] **Step 1: 追加失败场景测试**

在 `scripts/tests/git-submit.Tests.ps1` 的 `Write-Host "TASK1_TESTS_PASSED"` 之前追加：

```powershell
    $upstream = Invoke-Git $stageRepo.Root @("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}") | Out-String
    Assert-True ($upstream.Trim() -eq "origin/fix/test-stage") "首次推送应设置 origin 上游"

    Set-Content -LiteralPath (Join-Path $stageRepo.Root "tracked.txt") -Value "changed-again" -Encoding UTF8
    $secondPush = Invoke-Submit $stageRepo.Root @("-Message", "fix(test): 验证已有上游推送", "-Yes")
    Assert-True ($secondPush.ExitCode -eq 0) "已有上游时应直接推送"

    $failureRepo = New-TestRepository "fix/test-push-failure"
    Set-Content -LiteralPath (Join-Path $failureRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    Invoke-Git $failureRepo.Root @("remote", "set-url", "origin", (Join-Path $failureRepo.Root "missing-remote.git")) | Out-Null
    $beforeCount = [int](Invoke-Git $failureRepo.Root @("rev-list", "--count", "HEAD") | Select-Object -First 1)
    $failureResult = Invoke-Submit $failureRepo.Root @("-Message", "fix(test): 保留推送失败提交", "-Yes")
    $afterCount = [int](Invoke-Git $failureRepo.Root @("rev-list", "--count", "HEAD") | Select-Object -First 1)
    Assert-True ($failureResult.ExitCode -ne 0) "push 失败时脚本应返回非零"
    Assert-True ($afterCount -eq ($beforeCount + 1)) "push 失败时本地 commit 应保留"

    Write-Host "ALL_GIT_SUBMIT_TESTS_PASSED"
```

- [ ] **Step 2: 运行追加测试**

Run:

```powershell
pwsh -NoProfile -File .\scripts\tests\git-submit.Tests.ps1
```

Expected: 如 Task 1 实现已正确覆盖上游和 push 失败语义，直接输出 `ALL_GIT_SUBMIT_TESTS_PASSED`。如果失败，仅修正 `scripts/git-submit.ps1` 中对应的上游判定或错误传播，不改变测试期望。

- [ ] **Step 3: 执行语法和完整回归验证**

Run:

```powershell
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    (Resolve-Path .\scripts\git-submit.ps1),
    [ref]$null,
    [ref]$errors
) | Out-Null
if ($errors.Count -gt 0) { $errors; exit 1 }
pwsh -NoProfile -File .\scripts\tests\git-submit.Tests.ps1
```

Expected: PowerShell 解析错误数为 `0`，测试输出 `ALL_GIT_SUBMIT_TESTS_PASSED`，最终退出码 `0`。

- [ ] **Step 4: 只读检查 WordFlip 工作树**

Run:

```powershell
git status --short --branch
git diff -- scripts/git-submit.ps1 scripts/tests/git-submit.Tests.ps1 docs/superpowers/specs/2026-07-15-git-submit-script-design.md docs/superpowers/plans/2026-07-15-git-submit-script.md
```

Expected: 只显示新增的脚本、测试和设计/计划文档；当前 WordFlip 仓库没有产生新 commit，也没有发生 push。
