# WordFlip — Agent 指令

> 本文件供 Cursor / Codex 等 AI Agent 在仓库内自动读取。人类读者请从 [README.md](README.md) 入门。

---

## 项目概览

**WordFlip** 是移动端单词卡片学习应用 Monorepo：Android（Kotlin + Compose）+ Spring Boot 3 后端 + OpenAPI 契约 + MySQL 内容/学习数据。

| 项 | 说明 |
|----|------|
| 当前基线 | **v7.0：单主词书 + 词书专属学习卡 + 双层 FSRS** |
| 当前阶段 | **V7 收敛验收**：真实 v2 数据库、内容发布、服务端集成、Android 真机、发布准备 |
| 当前任务入口 | [TASK.md](TASK.md) |
| MVP 范围 | 登录、主词书/学习计划、分组、今日、学习、测验、图片/污渍、统计、设置 |
| 不在 MVP | iOS、React Web、推送、云备份、多设备同步、微服务拆分、个性化 FSRS 训练 |

---

## 文档权威层级

发生冲突时按以下顺序处理：

1. **[docs/wordflip/requirements.md](docs/wordflip/requirements.md)** v7 — 用户行为与业务规则
2. **[wordflip-api/openapi.yaml](wordflip-api/openapi.yaml)** — REST 契约单一来源
3. **[docs/wordflip/database-design.md](docs/wordflip/database-design.md)** — v2 数据模型与不变量
4. **[docs/wordflip/api-modules.md](docs/wordflip/api-modules.md)** — 服务边界与事务规则
5. **[docs/wordflip/architecture.md](docs/wordflip/architecture.md)** — 技术架构；残留 v6 内容不得覆盖前四项
6. **[docs/superpowers/specs/](docs/superpowers/specs/)** / **[plans/](docs/superpowers/plans/)** — 已批准的局部设计与实施记录
7. **[prototypes/wordflip-v5.html](prototypes/wordflip-v5.html)** — 历史 UI/动效参考，非业务或数据真相
8. **[docs/prd/WordFlip-PRD.md](docs/prd/WordFlip-PRD.md)** — 历史 PRD，勿作实施依据

目录放置见 [STRUCTURE.md](STRUCTURE.md)。旧 v1 数据库迁移仅供追溯和内容构建输入。

---

## 技术栈

| 层 | 当前选型 |
|----|----------|
| 后端 | Java 21、Spring Boot 3.3、Spring Security + JWT、Spring JDBC/JPA、Flyway |
| Android | Kotlin、Jetpack Compose、Material 3、Hilt、Navigation Compose、Retrofit、CameraX |
| 数据 | MySQL 8、Redis 7、MinIO |
| 内容 | Python 内容管线：verify / build / publish |
| 契约 | OpenAPI 3.0.3 + pytest 契约测试 |
| Web（二期） | React + TypeScript + Vite |

---

## v7 不可违反的业务规则

实现或评审任何功能前必须遵守：

| 规则 | 说明 |
|------|------|
| **唯一当前计划** | 用户可保留多本词书和历史计划，但任一时刻只有一个 `activePlanId`；Today/Groups/Study/Quiz/Stats 只使用当前计划 |
| **学习卡是真相** | 学习内容来自词书的已发布 `learning_cards`；同一词形在不同词书可有不同考义 |
| **cardId 是学习主键** | 进度、图片、污渍、测验与分组成员绑定 `cardId`；`wordKey` 只用于规范词形查询 |
| **双层记忆** | `card_skill_memory` 是当前词书考义的权威 FSRS；`lexeme_skill_memory` 仅用于跨书诊断，不能直接把新卡标成已掌握 |
| **仅测验写记忆** | 唯一写入口是 `QuizService` 调用 `FsrsReviewService.applyQuizResult(FsrsReviewCommand)`；客户端不得提交 FSRS rating 或本地改掌握度 |
| **答题事务与幂等** | 有效答题在同一事务写 `review_events` 与双层记忆；`requestId` 唯一，重试不得重复产生事件 |
| **skill 双轨** | `dictation` 与 `choice` 各自维护 FSRS；不得互相覆盖 |
| **浏览不改记忆** | 翻卡、长按详情、发音、图片操作和 `POST /study/sessions` 不写双层记忆或 `review_events` |
| **切书保留历史** | 切换主词书等同切换学习计划；旧计划、分组、记忆和审计事件不得删除 |
| **来源资料非全局词典** | ECDICT、WordNet 等通过 `sourceMaterials` 提供追溯资料；v7 不提供 `activeDictId` 全局切换 |
| **服务端权威** | FSRS、判题、导入解析、分组、今日任务和统计计算均在 Spring Boot；Android 只展示结果和提交用户动作 |

FSRS 参数锁定为目标保持率 `0.90`、最大间隔 `36500` 天；首版不训练个人权重。固定 `[1, 2, 4, 7, 15, 30]` 间隔属于历史实现。

---

## 仓库结构

```text
docs/wordflip/                         # v7 权威产品/架构文档
docs/superpowers/                      # 已批准设计与实施计划
wordflip-api/                          # OpenAPI 契约与契约测试
wordflip-server/                       # Spring Boot
  src/main/resources/db/migration-v2/  # 当前 Flyway 基线与后续迁移
  db-archive/migration-v1/             # 历史迁移，只读
wordflip-android/                       # Compose Android
tools/content-pipeline/                # v2 内容构建与发布
scripts/                               # v2 重建、冒烟与提交辅助脚本
docker/                                # MySQL + Redis + MinIO
wordflip-web/                          # 二期占位
prototypes/                            # 历史原型
```

---

## 开发与验证命令

### 基础设施

```powershell
cd docker
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

### 后端

```powershell
cd wordflip-server
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

开发配置默认连接 `wordflip_v2_dev`。当前 Flyway 目录为 `classpath:db/migration-v2`。

### Android

```powershell
cd wordflip-android
.\gradlew.bat test :app:assembleDebug
```

模拟器 API：`http://10.0.2.2:8080/api/v1`。真机 USB 调试使用 `scripts/adb-reverse.ps1` 后访问 `http://127.0.0.1:8080/api/v1`。

### API 契约

```powershell
cd wordflip-api
python -m pytest -q tests
```

### v2 内容与数据库

```powershell
cd tools/content-pipeline
$env:PYTHONPATH = "src"
python -m wordflip_content verify

cd ../..
$env:WORDFLIP_DB_PASSWORD = "<数据库密码>"
.\scripts\rebuild-wordflip-v2.ps1 -SourceDatabase wordflip -TargetDatabase wordflip_v2
# 审核 dry-run 后才能显式追加 -Execute
```

重建脚本必须使用新目标库；禁止把源库与目标库设为同名，也禁止手工删除旧库来绕过保护。

---

## 测试要求

| 类型 | 最低要求 |
|------|----------|
| 后端单元测试 | FSRS Again/Good、双层记忆、`review_events` 事务、学习计划原子切换、判题幂等 |
| 后端集成测试 | Auth、当前计划隔离、v2 Flyway、内容发布、MySQL 约束 |
| API 契约 | learning-plans、learning cards、`cardId` 媒体、quiz `requestId` |
| Android | Plan Gate、防重复提交、高价值 ViewModel/纯状态测试；Debug 构建 |
| 真机 | 首次选书、切换计划、分组、学习不改记忆、测验写 FSRS、媒体一致性 |
| 合并前 | 相关测试通过；Docker 可用时不得接受数据库集成测试 skipped |

Bug 修复先写失败测试或最小复现，再修复。仅文档变更至少执行链接检查、冲突扫描和 `git diff --check`。

---

## 代码风格与架构

### 通用

- **最小 diff**：只修改任务相关文件，不顺手重构。
- **中文注释（强制）**：业务类/方法文档与关键逻辑行使用简体中文；标识符保持英文。见 [coding-standards.md](docs/wordflip/coding-standards.md)。
- 禁止提交 `.env`、数据库备份、内容全量源文件、密钥、keystore、JWT 私钥或真实用户数据。

### 后端

```text
controller/   # 参数校验、身份解析、DTO 转换
service/      # 业务编排与事务边界
repository/   # JPA Repository 与 JDBC Store
domain/       # 用户实体和业务枚举
dto/          # API DTO
storage/      # MinIO
```

- Controller 不实现判题、FSRS、分组或资源隔离逻辑。
- v7 主业务读写以 `JdbcTemplate` / JDBC Store 为主；Auth 用户模型仍使用 JPA。
- JPA `ddl-auto: validate`；仅 Flyway 修改生产表。
- 当前迁移放在 `src/main/resources/db/migration-v2/V{n}__*.sql`；禁止向 `db-archive/migration-v1` 追加。
- 记忆更新只经 `FsrsReviewService`；不得重新引入 `ReviewService` 或 wordKey 级权威进度。

### Android

```text
feature-*/    # 可依赖 core-*
core-*/       # 不可依赖 feature-*
app/          # Hilt 装配、Plan Gate、导航
```

- 当前视觉基线为已批准并落地的 Apple 风格界面；不要用 v5 或旧 Natural Sage 文档覆盖现有组件。
- Material 3 仅作为实现载体；颜色、材质、间距和动效优先复用 `core-ui` 的 Apple 语义原语。
- 结构导航使用 Material 图标，不用 emoji。
- 禁止在 Android 实现 FSRS、判题、词书解析入库或统计业务计算。
- 网络与 UI 模型使用 `cardId`；`wordKey` 只用于查询和展示。

### API 契约

- API/DTO 变更先改 `openapi.yaml`，再同步文档、server 与 Android。
- 修改端点时同步 `description`、schema `required`、错误码和契约测试。
- 行为变化先更新 requirements；持久化变化同步 database-design 与 Flyway。

---

## 实现工作流

1. 在 [TASK.md](TASK.md) 选择 `V7-*` 任务。
2. 阅读关联 requirements REQ、OpenAPI path/schema 与数据库不变量。
3. API 变更遵循 OpenAPI-first；数据库变更只能进入 migration-v2。
4. 先写测试或最小复现，再做实现。
5. 执行相关自动化与必要的真实数据库/真机验收。
6. 有证据后才将 TASK 项改为 `[x]`。

变更顺序：

```text
requirements（行为变化时）
  → openapi.yaml
  → api-modules.md / database-design.md
  → migration-v2
  → server
  → Android core-model/core-network
  → 自动化与真机验收
```

---

## Git 与 PR

完整规范见 [CONTRIBUTING.md](CONTRIBUTING.md)。

- 不自动 `git commit` / `git push`，除非用户明确要求。
- Commit：`<type>(<scope>): 中文说明`，单一主题，正文说明 why。
- 不 amend 已推送 commit；不 force push `main`。
- 不回退或覆盖用户的未提交改动。
- PR 前运行相关测试并写明 Summary / Test plan / 数据迁移影响。

---

## 安全

- BCrypt 密码；JWT Access 15min + Refresh 7d。
- 所有用户资源按 `userId` 隔离，并进一步校验其属于当前 `activePlanId`。
- 上传只允许 image/jpeg、png、webp；服务端上限约 5MB。
- 登录失败不得泄露账号是否存在。
- `requestId` 既用于幂等，也必须与认证用户和 quiz session 一起校验。
- 数据库重建先备份、只写新库；不得直接覆盖 v1 数据库。

---

## Agent 必须做 / 禁止做

### 必须

- 用中文回复用户。
- 动手执行命令验证，不只口头描述。
- 业务冲突以 requirements v7 为准。
- 新增/修改业务代码时写简体中文注释。
- 引用代码使用 `startLine:endLine:filepath`。
- 长任务按 TASK 跟踪并报告测试、跳过项和已知限制。

### 禁止

- 用 v5 原型、PRD 或 v6 计划覆盖 v7 业务规则。
- 恢复全局可切换词典 `activeDictId`。
- 以 `(userId, wordKey)` 保存权威学习进度。
- 客户端提交 FSRS rating 或手动改掌握度。
- 浏览/学习 session 写双层记忆或 `review_events`。
- 切换主词书时删除旧计划、分组、记忆或审计事件。
- 在历史 migration-v1 目录追加新迁移。
- 在 `docs/` 放 SQL 迁移、数据库备份或生产密钥。
- 用英文注释业务逻辑，或留下与代码不一致的注释。

---

## 子目录 Agent 指令

| 路径 | 说明 |
|------|------|
| [wordflip-server/AGENTS.md](wordflip-server/AGENTS.md) | Spring Boot / v2 数据 / FSRS 专项 |
| [wordflip-android/AGENTS.md](wordflip-android/AGENTS.md) | Compose / Plan Gate / cardId 专项 |
| [wordflip-api/AGENTS.md](wordflip-api/AGENTS.md) | OpenAPI-first 与契约测试 |

靠近目标文件的 `AGENTS.md` 优先，但不得违反根规则和 v7 权威文档。

---

## 每次交付自检

- [ ] 行为符合 requirements v7 与 OpenAPI
- [ ] 当前计划资源隔离正确
- [ ] 进度、图片、污渍使用 `cardId`
- [ ] 只有测验判题写双层记忆与 `review_events`
- [ ] 新增/修改业务代码包含简体中文注释
- [ ] 相关测试、真实数据库或真机步骤已执行
- [ ] TASK 只勾选有证据的完成项
- [ ] 未提交 secrets、备份、全量内容源或真实用户数据
- [ ] diff 范围合理，未自动 commit/push
- [ ] 向用户说明验证命令、skipped 项和已知限制

---

## 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-30 | v1.0 | 初版 Monorepo Agent 指令 |
| 2026-06-30 | v1.1 | 强制简体中文注释 |
| 2026-07-23 | v2.0 | 全面切换到 v7 学习计划、词书专属学习卡、双层 FSRS 与 v2 数据库基线 |
