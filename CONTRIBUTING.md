# WordFlip — 贡献与 Git 规范

> 人类开发者与 AI Agent 共用。Agent 专项约束另见 [AGENTS.md](AGENTS.md)。

---

## 1. 分支

| 项 | 约定 |
|----|------|
| 主分支 | `main`（新仓库默认；自 `master` 迁移时一次性 rename 即可） |
| 功能分支 | `<type>/<scope>-<简短描述>`，小写、连字符分词 |
| 生命周期 | 从 `main` 拉出 → PR 合并 → 删除远程分支 |

**分支名示例**

```
feat/server-auth-jwt
feat/android-login-screen
fix/server-group-append-delta
docs/requirements-quiz-req
chore/docker-compose-redis
```

**禁止**

- 直接在 `main` 上开发业务功能（热修除外，且需尽快 PR 补录）
- `force push` 到 `main`
- 长期不合并的「万能分支」（如 `dev`、`wip` 堆积数周）

---

## 2. Commit Message

采用 **[Conventional Commits](https://www.conventionalcommits.org/) 结构**，标题与正文使用**简体中文完整句**，并说明 **why**（为何改、解决什么问题）。

### 2.1 格式

```
<type>(<scope>): <简短说明>

<可选正文：行为变化、边界条件、与上一 commit 的依赖关系>

<可选脚注>
TASK: P0-B02
REQ: REQ-AUTH-6
BREAKING CHANGE: <破坏性变更说明>
```

- **标题（第一行）**：≤ 72 字符；祈使/陈述均可，须能独立读懂
- **正文**：换行后写 why；跨端改动须说明依赖顺序（如「依赖上一 commit 的 openapi schema」）
- **脚注**：`TASK:`、`REQ:` 推荐填写，便于与 [TASK.md](TASK.md)、[requirements.md](docs/wordflip/requirements.md) 追溯

### 2.2 Type

| type | 用途 |
|------|------|
| `feat` | 新功能、新 API 行为、新界面流程 |
| `fix` | Bug 修复 |
| `docs` | 仅文档（requirements、architecture、注释性说明等） |
| `refactor` | 行为不变的重构 |
| `test` | 测试用例、测试基建 |
| `chore` | 脚手架、依赖升级、仓库杂项 |
| `build` | Gradle / Maven 构建脚本、打包配置 |
| `style` | 格式化、import 整理（**无**逻辑变更） |

**类型选择**

- 改 `openapi.yaml` **且**同步改 server/android 实现 → 用 `feat` 或 `fix`，**不要**标成 `docs`
- 纯契约或文档、无运行时变更 → `docs` 或 `feat(api)` 视是否新增端点而定

### 2.3 Scope

与 Monorepo 目录对齐：

| scope | 路径 / 场景 |
|-------|-------------|
| `api` | `wordflip-api/openapi.yaml` |
| `server` | `wordflip-server/` |
| `android` | `wordflip-android/` |
| `web` | `wordflip-web/` |
| `docker` | `docker/` |
| `docs` | `docs/`、`TASK.md`、`AGENTS.md`、`STRUCTURE.md` |
| `db` | Flyway 迁移、`database-design.md` 表结构 |
| `proto` | `prototypes/`（UI 参考，非业务数据源） |

跨 scope 时：**优先拆成多个 commit**；同一 PR 内可保留「api → server → android」顺序。

### 2.4 单一主题（原子 commit）

每个 commit 只做一件事，便于 review 与 revert：

| 适合单独 commit | 应拆分 |
|-----------------|--------|
| 仅 openapi 契约 | openapi + server + android 混在一个 commit |
| 仅 Flyway + 对应 Entity | 顺手全库 format + 功能改动 |
| 仅某一 Android feature 模块 | 无关模块 refactor 与 feat 捆绑 |
| 仅 requirements / TASK 文档 | |

**API 变更推荐顺序**（可对应多个 commit，同一 PR）：

`openapi.yaml` → `api-modules.md`（若规则变）→ Flyway（若表变）→ `server` → `android core-model`

### 2.5 示例

```
feat(api): 新增 POST /auth/login 与 refresh 契约

对齐 requirements v6 登录流程；掌握度仍仅经测验写入。
TASK: P0-B02
```

```
feat(server): 实现 JWT 登录与 Redis refresh 存储

登录失败统一返回 401，不泄露账号是否存在 (REQ-AUTH-6)。
依赖同 PR 内 openapi 契约 commit。
TASK: P0-B02
REQ: REQ-AUTH-6
```

```
fix(android): 修复模拟器 baseUrl 未指向 10.0.2.2

联调时无法访问本机 Spring Boot。
```

```
docs: 明确分组增量 append 规则

更新 requirements 与 api-modules；无运行时行为变更。
TASK: P0-S03
```

```
chore: 初始化 WordFlip Monorepo 脚手架

纳入文档、OpenAPI、server/android 骨架与 docker compose。
```

---

## 3. Pull Request

### 3.1 标题

与 squash 后的 commit 标题同格式，例如：

```
feat(server): P0 登录与 JWT
fix(android): 修复登录页错误态未展示
```

### 3.2 正文模板

```markdown
## Summary
- <做了什么、为什么，1～3 条>

## Test plan
- [ ] wordflip-server: `.\mvnw.cmd test`（或 `./mvnw test`）
- [ ] wordflip-android: `.\gradlew.bat test`（如适用）
- [ ] 手动：<curl / 模拟器步骤>

## 关联
- TASK: P0-B02
- REQ: REQ-AUTH-6（如适用）
```

### 3.3 合并前检查

- [ ] 相关 `./mvnw test` 或 `./gradlew test` 通过
- [ ] 未提交 secrets（`.env`、keystore、JWT 私钥等，见 [STRUCTURE.md](STRUCTURE.md)）
- [ ] 行为符合 [requirements.md](docs/wordflip/requirements.md) v6 与 [openapi.yaml](wordflip-api/openapi.yaml)
- [ ] 未违反「掌握度仅测验写入 / 分组增量 append」等 [AGENTS.md](AGENTS.md) 硬规则
- [ ] 业务代码含简体中文注释（见 [coding-standards.md](docs/wordflip/coding-standards.md)）
- [ ] 若在本 PR 完成任务，[TASK.md](TASK.md) 对应项已 `[x]`

### 3.4 合并策略

| 策略 | 适用 |
|------|------|
| **Squash merge**（推荐） | 个人或小团队；PR 内多个 commit 压成一条干净历史 |
| **Merge commit** | 需保留「api → server → android」分步历史；每个 commit 仍须符合本文规范 |

合并后删除源分支。

---

## 4. 安全与禁止事项

- **不要** `git commit --amend` 已推送到远程的 commit（除非明确协作约定且未被人基于该 commit 开发）
- **不要** `force push` 到 `main`
- **不要** 提交 `.env`、`application-local.yml`、Keystore、JWT 私钥
- AI Agent：**不要**自动 `git commit` / `git push`，除非用户明确要求（见 [AGENTS.md](AGENTS.md)）

---

## 5. 工具链（可选，后续启用）

当前以**文档约定 + code review** 为主。团队扩大或接入 CI 后可考虑：

| 层级 | 方案 |
|------|------|
| 本地 | `commit-msg` hook 或 [commitlint](https://commitlint.js.org/) |
| CI | PR 标题与 commit message lint |
| 模板 | `.gitmessage` 粘贴 §2.1 格式说明 |

首几个 commit 建议人工示范，再决定是否强制校验。

---

## 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-07-01 | v1.0 | 初版：分支、Conventional Commits、PR 模板、Monorepo scope |
