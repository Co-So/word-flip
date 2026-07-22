# Git 提交与推送脚本设计

> 日期：2026-07-15
> 状态：已完成交互设计，待用户审阅后实施

## 1. 目标

为 WordFlip Monorepo 提供一个 Windows PowerShell 脚本，用于安全地完成改动暂存、提交信息校验、本地提交和推送当前功能分支。脚本只简化 Git 提交流程，不代替测试、代码审查或 Pull Request 流程。

## 2. 使用入口

脚本位于 `scripts/git-submit.ps1`，支持以下参数：

- `-Message <string>`：完整的 commit 标题；未传入时交互输入。
- `-Include <string[]>`：需要额外纳入的未跟踪文件或目录。
- `-Yes`：跳过提交与推送前的最终交互确认。

示例：

```powershell
.\scripts\git-submit.ps1 `
  -Message "fix(android): 修复 WordNet 释义展示" `
  -Include wordflip-android/core-network/src/main/java/com/wordflip/core/network/api/WordsApi.kt
```

## 3. 处理流程

1. 确认当前目录位于 Git 工作树内，并定位仓库根目录。
2. 读取当前分支；如果是 `main` 或处于 detached HEAD，立即停止。
3. 默认执行 `git add -u`，仅暂存已跟踪文件的新增、修改和删除。
4. 对每个 `-Include` 路径进行存在性与安全检查，通过后使用 `git add -- <path>` 显式暂存。
5. 检查全部已暂存文件，若包含禁止类型则停止；若没有可提交内容也停止。
6. 读取并校验提交标题。
7. 展示当前分支、目标远程、提交标题和已暂存文件清单。
8. 除非传入 `-Yes`，否则要求用户输入确认。
9. 执行 `git commit -m <Message>`；提交失败时不推送。
10. 如果当前分支已有上游，执行 `git push`；否则执行 `git push -u origin <branch>`。
11. 推送失败时保留已创建的本地 commit，输出错误与可手工重试的命令。

## 4. 提交信息规则

标题必须符合项目 Conventional Commits 约定：

```text
<type>(<scope>): <简体中文说明>
```

- 允许的 type：`feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`build`、`style`。
- scope 可选，允许小写字母、数字和连字符。
- 冒号后必须有非空说明。
- 标题不得超过 72 个字符。
- 本版脚本只接收单行标题，不处理正文和 `TASK:` / `REQ:` 脚注，避免将简单提交器扩展为复杂编辑器。

## 5. 安全规则

脚本必须拒绝以下操作或内容：

- 在 `main` 分支上提交或推送。
- detached HEAD 状态下提交。
- `--amend`、`--force` 或 `--force-with-lease`。
- 默认纳入任意未跟踪文件。
- 暂存 `.env`、`*.jks`、`*.keystore`、私钥文件、`application-local.yml` 或 `*.dex`。
- 在仓库没有名为 `origin` 的远程时继续推送。

安全检查发生在 commit 之前。如果脚本在执行 `git add -u` 后因检查失败退出，已暂存状态保留，不自动 reset，避免破坏用户原有暂存内容。

## 6. 错误处理

- 所有 Git 命令都检查退出码，失败时以非零状态结束脚本。
- 错误信息使用简体中文，同时保留 Git 原始错误输出。
- 提交前失败不会创建 commit。
- 提交后推送失败不会删除、修改或 amend 本地 commit。

## 7. 测试设计

使用 PowerShell 集成测试脚本和临时 Git 仓库验证行为，不依赖真实远程或当前工作树。测试至少覆盖：

1. `main` 分支会被拒绝。
2. 默认只暂存已跟踪文件。
3. `-Include` 可纳入指定的安全新文件。
4. `.dex` 和密钥类路径会被拒绝。
5. 不合法的提交标题会被拒绝。
6. 没有已暂存改动时停止。
7. 已有上游与无上游分支会选择正确的 push 形式。
8. push 失败时本地 commit 仍然存在。

实施时遵循测试先行：先运行测试并确认因脚本缺失而失败，再编写最小实现使其通过。

## 8. 非目标

- 不自动创建功能分支。
- 不自动运行 Maven、Gradle 或真机测试。
- 不创建 Pull Request。
- 不自动生成提交信息。
- 不自动拆分多个原子 commit。
- 不清理工作树或删除未跟踪文件。

## 9. 验收标准

- 用户能在任意功能分支从仓库根目录运行脚本。
- 脚本不会默认提交未跟踪文件。
- 脚本不会对 `main` 执行 commit 或 push。
- 脚本能在无上游分支上创建 commit 并设置 `origin` 上游。
- 所有设计的临时仓库集成测试通过。
