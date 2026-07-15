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
    if ($LASTEXITCODE -ne 0) { throw "无法创建临时 bare 远程仓库。" }
    & git init -b $Branch $root | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "无法创建临时工作仓库。" }
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
    finally { Pop-Location }
}

try {
    $mainRepo = New-TestRepository
    Set-Content -LiteralPath (Join-Path $mainRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    $mainResult = Invoke-Submit $mainRepo.Root @("-Message", "fix(test): 不应提交主分支", "-Yes")
    Assert-True ($mainResult.ExitCode -ne 0) "main 分支应被拒绝"

    $detachedRepo = New-TestRepository "fix/test-detached"
    Invoke-Git $detachedRepo.Root @("checkout", "--detach") | Out-Null
    Set-Content -LiteralPath (Join-Path $detachedRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    $detachedResult = Invoke-Submit $detachedRepo.Root @("-Message", "fix(test): 拒绝游离提交", "-Yes")
    Assert-True ($detachedResult.ExitCode -ne 0) "detached HEAD 应被拒绝"

    $emptyRepo = New-TestRepository "fix/test-empty"
    $emptyResult = Invoke-Submit $emptyRepo.Root @("-Message", "fix(test): 拒绝空提交", "-Yes")
    Assert-True ($emptyResult.ExitCode -ne 0) "没有改动时应停止"

    $stageRepo = New-TestRepository "fix/test-stage"
    Set-Content -LiteralPath (Join-Path $stageRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $stageRepo.Root "untracked.txt") -Value "new" -Encoding UTF8
    $stageResult = Invoke-Submit $stageRepo.Root @("-Message", "fix(test): 验证默认暂存", "-Yes")
    Assert-True ($stageResult.ExitCode -eq 0) "功能分支提交应成功。输出：$($stageResult.Output)"
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

    $englishRepo = New-TestRepository "fix/test-chinese-message"
    Set-Content -LiteralPath (Join-Path $englishRepo.Root "tracked.txt") -Value "changed" -Encoding UTF8
    $englishResult = Invoke-Submit $englishRepo.Root @("-Message", "fix(test): english only", "-Yes")
    Assert-True ($englishResult.ExitCode -ne 0) "不含中文的提交说明应被拒绝"

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
    Assert-True ($failureResult.Output -match "本地 commit 已保留") "push 失败时应给出恢复提示"

    Write-Host "ALL_GIT_SUBMIT_TESTS_PASSED"
}
finally {
    foreach ($path in $tempRoots) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
    }
}
