# WordFlip — Agent 指令

> 本文件供 Cursor / Codex 等 AI Agent 在仓库内自动读取。人类读者请从 [README.md](README.md) 入门。

---

## 项目概览

**WordFlip** 是移动端单词卡片学习应用 Monorepo：Android MVP（Kotlin + Compose）+ Spring Boot 3 后端 + OpenAPI 契约。

| 项 | 说明 |
|----|------|
| 当前阶段 | 脚手架已就绪；**P-LEX 词库结构化**为当前质量优先项（见 [TASK.md](TASK.md) / [plans/lexicon-restructure.md](docs/wordflip/plans/lexicon-restructure.md)） |
| MVP 范围 | P0 登录/词书/分组 → P1 今日/学习 → P2 测验 → P3 卡拍/图片/污渍 → P4 统计/设置 |
| 不在 MVP | iOS、React Web（二期）、云备份、推送提醒、微服务拆分 |

---

## 文档权威层级（冲突时按此顺序）

1. **[docs/wordflip/requirements.md](docs/wordflip/requirements.md)** v6 — 产品行为定稿  
2. **[wordflip-api/openapi.yaml](wordflip-api/openapi.yaml)** — REST 契约单一来源  
3. **[docs/wordflip/database-design.md](docs/wordflip/database-design.md)** — 表结构与查询  
4. **[docs/wordflip/api-modules.md](docs/wordflip/api-modules.md)** — 模块与业务规则  
5. **[docs/wordflip/architecture.md](docs/wordflip/architecture.md)** — 技术架构  
6. **[docs/wordflip/plans/](docs/wordflip/plans/)** — 跨模块实施计划（实施期补充；结论须并入 1–5）  
7. **[prototypes/wordflip-v5.html](prototypes/wordflip-v5.html)** — UI/动效参考（**非**业务逻辑）  
8. **[docs/prd/WordFlip-PRD.md](docs/prd/WordFlip-PRD.md)** — 历史 PRD v2，**勿作实施依据**

目录与文件放置见 [STRUCTURE.md](STRUCTURE.md)。任务拆解见 [TASK.md](TASK.md)。

---

## 技术栈

| 层 | 选型 |
|----|------|
| 后端 | Java 21、Spring Boot 3、Spring Security + JWT、JPA、Flyway、Redis、MinIO SDK |
| Android | Kotlin、Jetpack Compose、Material 3、Hilt、Navigation Compose、Retrofit、CameraX |
| 数据 | MySQL 8（utf8mb4）、Redis 7、MinIO |
| 契约 | OpenAPI 3.0.3 |
| Web（二期） | React 18 + TypeScript + Vite |

---

## 不可违反的业务规则

实现任何功能前必须遵守：

| 规则 | 说明 |
|------|------|
| **掌握度仅测验写入** | 队列三态 `unlearned` / `fuzzy` / `unknown` + 稳定性 `stability`（S）；**按 skill 双轨**（`dictation` / `choice`）各一套热力与 SRS；唯一入口 `ReviewService.applyQuizResult`，由 `QuizService` 调用。**禁止** `PATCH /words/{wordKey}/mastery` 或客户端/local 改态 |
| **释义真相（改造中）** | 目标为 `dict_senses`：**一词多义 1:n**；展示/测验默认 **primary** sense；`cn` 不含词性（词性在 `pos`）。进度仍绑 `wordKey`。见 [plans/lexicon-restructure.md](docs/wordflip/plans/lexicon-restructure.md) |
| **学习翻转不改态** | 卡片浏览、`POST /study/sessions` 不更新三态与稳定性 S |
| **分组增量追加** | `PUT /settings` 仅对未入组词 append 新 groups；**禁止** DELETE/重建已有 groups |
| **一词一组** | `UNIQUE(user_id, word_key)` on `group_words` |
| **wordKey** | `en.trim().toLowerCase()`；用户域学习进度均绑 `(user_id, word_key)` |
| **取消勾选不撤词** | 取消词书勾选不删除已入组单词 |
| **服务端权威** | SRS 间隔、判题、导入解析、今日任务计数均在 **Spring Boot**；Android 不做业务计算 |
| **已掌握统计** | `stability >= 80` 且最近测验成功且建议间隔 ≥ 30 天（非用户可选状态）；组详情主展示 heatLevel |

SRS 间隔（天）：`[1, 2, 4, 7, 15, 30]`。详见 `api-modules.md` §2.2。

---

## 仓库结构（摘要）

```
docs/wordflip/          # 定稿设计文档
wordflip-api/           # openapi.yaml
wordflip-server/        # Spring Boot
wordflip-android/       # Compose MVP
wordflip-web/           # 二期占位
docker/                 # compose：MySQL + Redis + MinIO
prototypes/             # HTML 原型，勿作数据源
```

完整规范：[STRUCTURE.md](STRUCTURE.md)

---

## 开发与构建命令

> 脚手架初始化后更新本节；当前以 TASK.md 顺序为准。

### 基础设施

```bash
cd docker
docker compose up -d
```

### 后端（初始化后）

```bash
cd wordflip-server
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw test
./mvnw flyway:migrate
```

Swagger UI（计划）：`http://localhost:8080/swagger-ui.html`

### Android（初始化后）

```bash
cd wordflip-android
./gradlew :app:assembleDebug
./gradlew test
```

模拟器访问本机 API：`http://10.0.2.2:8080/api/v1`

### OpenAPI 代码生成（初始化后）

- Android DTO：从 `wordflip-api/openapi.yaml` 生成至 `wordflip-android/core-model`
- 变更 API 时 **先改 openapi.yaml**，再改 server / client

---

## 测试要求

| 类型 | 要求 |
|------|------|
| 后端单元测试 | `applyQuizResult` 状态机、`appendGroupsForNewWords` delta 逻辑必须有测试 |
| 后端集成测试 | Auth、Settings 至少 MockMvc 冒烟 |
| Android | 高价值 ViewModel 单测可选；MVP 以手工联调清单为主（TASK §Q） |
| 合并前 | 相关 `./mvnw test` 或 `./gradlew test` 通过；不跳过 hook 除非用户明确要求 |

Bug 修复：先写失败测试或最小复现，再修复。

---

## 代码风格与架构

### 通用

- **最小 diff**：只改任务相关文件；不重构无关代码  
- **中文注释（强制）**：业务代码的类/方法文档与关键逻辑行注释使用**简体中文**；标识符保持英文。详见 [docs/wordflip/coding-standards.md](docs/wordflip/coding-standards.md)  
- **禁止**提交 `.env`、密钥、keystore、JWT 私钥  

### 后端（wordflip-server）

```
controller/   # 参数校验、DTO 转换；无业务逻辑
service/      # 业务编排、事务边界
domain/       # JPA Entity
repository/   # Spring Data JPA
```

- JPA `ddl-auto: validate`；**仅 Flyway** 改表（`db/migration/V{n}__*.sql`）  
- Controller 路径与 `openapi.yaml` 一致  
- 掌握度变更 **只** 在 `ReviewService.applyQuizResult`  

### Android（wordflip-android）

```
feature-*/    # 可依赖 core-*
core-*/       # 不可依赖 feature-*
```

- UI：Natural Sage 主题见 `docs/wordflip/design-system/MASTER.md`（**非** v5 蓝色）  
- 图标：Material Symbols Outlined，不用 emoji 作结构导航  
- **禁止**在 Android 实现 SRS、判题、词书解析入库  

### API 契约（wordflip-api）

- 修改端点：同步更新 `description`、schema `required`、错误码  
- 同步 `docs/wordflip/api-modules.md` 若业务规则变化  

---

## 实现工作流

1. 在 [TASK.md](TASK.md) 中找到当前任务 ID（如 `P0-B02`）  
2. 阅读关联 REQ（requirements.md）与 openapi 端点  
3. 后端先于或并行 Android；API 就绪后再接客户端  
4. 完成后将 TASK 项 `[ ]` → `[x]`  
5. 若改 API/表结构：更新 openapi → Flyway → database-design.md（必要时）  

**API 变更顺序：** `openapi.yaml` → `api-modules.md` → server → android `core-model`

---

## Git 与 PR

完整规范见 **[CONTRIBUTING.md](CONTRIBUTING.md)**（Conventional Commits + Monorepo scope + PR 模板）。

Agent 额外约束：

- **不要**自动 `git commit` / `git push`，除非用户明确要求  
- Commit：`<type>(<scope>): 中文说明`，正文说明 why；**单一主题**；推荐脚注 `TASK:` / `REQ:`  
- **不要** `git commit --amend` 已推送 commit；**不要** force push `main`  
- PR 前：lint + test 通过；正文含 Summary / Test plan（见 CONTRIBUTING §3）  

---

## 安全

- 密码 BCrypt；JWT Access 15min + Refresh 7d（Redis）  
- 资源按 `userId` 隔离  
- 上传：白名单 image/jpeg、png、webp；上限约 5MB  
- 登录失败不泄露账号是否存在（REQ-AUTH-6）  

---

## Agent 必须做 / 禁止做

### 必须

- 动手执行命令验证，不只口头描述  
- 业务冲突以 requirements v6 为准  
- 用中文回复用户（用户规则）  
- **新增/修改业务代码时写简体中文注释**（JavaDoc/KDoc + 关键行注释；见 coding-standards.md）  
- 引用代码用 `startLine:endLine:filepath` 格式  
- 长任务对照 TASK.md 逐步推进  

### 禁止

- 用 v5 原型或 PRD v2 覆盖 v6 业务规则  
- 客户端手动改掌握度（记得/模糊按钮）  
- 保存词书时重建全部分组  
- 在 `docs/` 放 SQL 迁移或生产密钥  
- 根目录散落 `.html`、临时业务代码  
- 用英文注释业务逻辑（**必须中文**，openapi description 除外）  
- 注释与代码不一致或只改代码不更新注释  

---

## 子目录 Agent 指令

Monorepo 嵌套 `AGENTS.md`（靠近的文件优先）：

| 路径 | 说明 |
|------|------|
| [wordflip-server/AGENTS.md](wordflip-server/AGENTS.md) | Spring Boot 专项 |
| [wordflip-android/AGENTS.md](wordflip-android/AGENTS.md) | Compose 专项 |
| [wordflip-api/AGENTS.md](wordflip-api/AGENTS.md) | OpenAPI 变更流程 |

复杂 glob 规则（如仅 `*.kt`）可追加 `.cursor/rules/*.mdc`。

---

## 交付清单（每次任务结束自检）

- [ ] 新增/修改业务代码含**简体中文**注释（见 coding-standards 自检清单）  
- [ ] 行为符合 requirements v6 与 openapi  
- [ ] 未违反「掌握度仅测验 / 分组增量 append」  
- [ ] 相关测试或 curl/手动步骤已执行  
- [ ] TASK.md 对应项已打勾（若在本会话完成）  
- [ ] 未提交 secrets；diff 范围合理  
- [ ] 向用户说明验证命令与已知限制  

---

## 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-30 | v1.0 | 初版：Monorepo Agent 指令 |
| 2026-06-30 | v1.1 | 强制简体中文注释；关联 coding-standards.md 与 .cursor/rules |
