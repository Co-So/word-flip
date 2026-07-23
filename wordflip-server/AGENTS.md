# wordflip-server — Agent 指令

> 父级：[../AGENTS.md](../AGENTS.md)
> 产品规则：[../docs/wordflip/requirements.md](../docs/wordflip/requirements.md) v7

## 范围

Spring Boot 3 单体后端：认证、学习计划、词书与学习卡、分组、Today、学习上报、测验/FSRS、媒体、统计。服务端是业务计算与数据一致性的唯一权威。

## 当前持久化结构

```text
com.wordflip/
├── controller/      # 参数校验、身份解析、DTO 转换
├── service/         # 业务编排、资源隔离、事务边界
├── repository/      # JPA Repository + JDBC Store
├── domain/          # User/UserSettings 与业务枚举
├── dto/             # API DTO
├── security/        # JWT / Refresh Token
├── storage/         # MinIO
└── exception/       # ErrorResponse + @ControllerAdvice
```

v7 学习域以 `JdbcTemplate`、`JdbcLearningPlanStore`、`JdbcFsrsReviewStore` 为主；Auth 用户模型仍使用 JPA。不要为了统一风格重写无关持久化代码。

## 硬性规则

- **中文注释**：业务类/方法 JavaDoc、事务分支、SQL 意图、Flyway 表/索引说明使用简体中文。
- **当前计划隔离**：Groups/Today/Study/Quiz/Stats/Media 必须同时校验 `userId` 与当前 `activePlanId`。
- **学习卡主键**：权威学习状态绑定 `cardId + skill`；`wordKey` 只用于规范词形查询。
- **唯一记忆写入口**：仅 `QuizService` 调用 `FsrsReviewService.applyQuizResult(FsrsReviewCommand)`。
- **同事务审计**：`review_events`、`card_skill_memory`、`lexeme_skill_memory` 必须同事务提交或回滚。
- **幂等**：`requestId` 唯一；重复答题请求不得重复写事件或重复推进 FSRS。
- **服务端评分**：客户端只提交答案；服务端将错误映射 `Again`、正确映射 `Good`。
- **浏览不改记忆**：Study session 只写浏览/打卡，不访问或更新双层记忆与 `review_events`。
- **切书保留历史**：切换计划只更新 `active_plan_id`；不得删除旧计划、分组、记忆或事件。
- **Flyway v2**：当前迁移只放 `src/main/resources/db/migration-v2/V{n}__*.sql`。
- **历史迁移只读**：`db-archive/migration-v1/` 只供追溯和内容管线读取，禁止追加或作为当前启动目录。
- **Controller 无业务逻辑**：判题、FSRS、分组、导入和资源隔离均在 Service/Store。

## 关键服务

| Service / Store | 职责 |
|-----------------|------|
| `LearningPlanService` / `LearningPlanStore` | 创建、读取、切换当前学习计划 |
| `LearningCardQueryService` | 词书卡片、卡片详情、来源资料 |
| `BookService` / `BookImportService` | 词书目录、导入预览/确认、归档约束 |
| `GroupService` | 当前计划分组、未入组卡片、自定义分组 |
| `FsrsSchedulerService` | 锁定 FSRS 参数并计算新状态 |
| `FsrsReviewService` / `FsrsReviewStore` | 双层记忆和 `review_events` 原子写入 |
| `QuizService` | 出题、服务端判题、幂等答题、结果 |
| `TodayService` / `StatsService` | 当前计划 Today 与统计聚合 |
| `StudyService` | 学习卡数据与无记忆副作用的 session 上报 |
| `ImageService` / `StainService` | `cardId` 媒体与当前计划权限 |

## 数据库与内容

- 当前 dev 数据库：`wordflip_v2_dev`。
- `application.yml` 固定 Flyway location 为 `classpath:db/migration-v2`。
- v2 V1 是全新基线，不是对旧 V1–V24 的原地升级。
- 内容通过 `../tools/content-pipeline` 的 verify/build/publish 进入 `lexemes`、`book_items`、`learning_cards` 等表。
- 重建使用 `../scripts/rebuild-wordflip-v2.ps1`，必须先 dry-run、备份并使用全新目标库。

## 命令

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Docker 可用时确认 `WordFlipV2MySqlIntegrationTest` 没有 skipped。服务启动后可运行：

```powershell
..\scripts\smoke-wordflip-v2.ps1 -AccessToken "<JWT access token>"
```

## 测试重点

- `FsrsSchedulerServiceTest`：Again/Good 与锁定参数。
- `FsrsReviewServiceTest`：双层记忆、审计事件、事务。
- `LearningPlanServiceTest`：创建/切换计划与唯一 active plan。
- `DatabaseBaselineContractTest`：v2 表与约束。
- `WordFlipV2MySqlIntegrationTest`：真实 MySQL/Flyway/约束。
- Controller 测试：认证、当前计划资源隔离和 DTO 契约。
- Bug 修复先写失败测试或最小复现。

## 禁止

- 恢复 `ReviewService.applyQuizResult(userId, wordKey, correct)`。
- 恢复 `word_mastery`、`review_plans` 或固定间隔数组。
- 让 Android 提交 FSRS rating、`dueAt`、stability 或 mastery。
- 在 Study/翻卡路径写记忆。
- 只按 `cardId` 查询却不校验用户与当前计划。
- 把 ECDICT/WordNet 当作用户可切换的全局展示词典。
- 在 archive 目录新增迁移或修改历史 SQL 来修生产问题。

## 参考

- [OpenAPI](../wordflip-api/openapi.yaml)
- [数据库设计](../docs/wordflip/database-design.md)
- [API 模块](../docs/wordflip/api-modules.md)
- [TASK §V7-S](../TASK.md)
