# WordFlip

WordFlip 是一款 Android 单词卡片学习应用，采用 Spring Boot 单体后端与 OpenAPI 契约。当前产品基线为 **v7.0：单主词书、词书专属学习卡、学习计划、双层 FSRS**。

[任务清单](TASK.md) · [需求 v7](docs/wordflip/requirements.md) · [OpenAPI](wordflip-api/openapi.yaml) · [数据库设计](docs/wordflip/database-design.md) · [Agent 指令](AGENTS.md) · [贡献规范](CONTRIBUTING.md)

## 当前状态

v7 契约、v2 数据库基线、服务端主链路、Android Plan Gate 与 Apple 风格 UI 已进入仓库。当前重点是：

1. 构建并审核三本内置词书内容。
2. 在真实 MySQL 上建立全新 v2 数据库并完成发布。
3. 跑完数据库集成测试和服务端冒烟。
4. 真机走通首次选书、学习、测验、FSRS、统计和切书保留历史。
5. 完成生产配置、release build 与 MVP 演示材料。

详见 [TASK.md](TASK.md) 的 `V7-DB`、`V7-S`、`V7-A`、`V7-Q`。

## v7 核心模型

| 概念 | v7 规则 |
|------|---------|
| 当前主词书 | 用户可保留多本词书，但任一时刻只有一个 `activePlanId` |
| 学习计划 | 切换主词书等同切换学习计划；旧计划、分组和进度保留 |
| 学习卡 | 词书专属 `learning_card`；同一单词在不同词书可有不同考义 |
| 学习主键 | 进度、测验、图片和污渍使用 `cardId`；`wordKey` 只用于规范词形查询 |
| 卡片记忆 | `card_skill_memory`，当前词书考义的权威 FSRS 状态 |
| 词形熟悉度 | `lexeme_skill_memory`，只用于跨书诊断，不直接让新卡变为已掌握 |
| 记忆写入 | 只有服务端测验判题写双层记忆与 `review_events` |
| 内容来源 | ECDICT、WordNet 等通过 `sourceMaterials` 追溯，不是全局可切换词典 |

## 仓库结构

```text
.
├── docs/
│   ├── wordflip/                         # v7 产品、数据库、API 与架构
│   └── superpowers/                      # 已批准设计与实施计划
├── wordflip-api/                         # OpenAPI + pytest 契约测试
├── wordflip-server/                      # Spring Boot 3
│   ├── src/main/resources/db/migration-v2/
│   └── db-archive/migration-v1/          # 历史迁移，只读
├── wordflip-android/                     # Kotlin + Compose 多模块客户端
├── tools/content-pipeline/               # 内容 verify/build/publish
├── scripts/                              # v2 重建、冒烟与辅助脚本
├── docker/                               # MySQL 8 + Redis 7 + MinIO
├── prototypes/                           # 历史 UI/动效参考
└── wordflip-web/                         # 二期占位
```

完整目录规范见 [STRUCTURE.md](STRUCTURE.md)。

## 环境要求

- JDK 21：后端
- JDK 17+ 与 Android SDK 34：Android 构建
- Docker Desktop：MySQL、Redis、MinIO 与数据库集成测试
- Python 3.10+：契约测试和内容管线
- MySQL CLI：执行 v2 安全重建脚本时需要 `mysql` / `mysqldump`

## 本地快速启动

### 1. 基础设施

```powershell
cd docker
Copy-Item .env.example .env
```

新 v7 开发库应与 `wordflip-server/src/main/resources/application-dev.yml` 对齐。首次启动前编辑本地 `.env`：

```dotenv
MYSQL_DATABASE=wordflip_v2_dev
MINIO_ROOT_USER=admin
MINIO_ROOT_PASSWORD=admin123
```

然后启动：

```powershell
docker compose up -d
docker compose ps
```

> 如果 Docker volume 已经初始化为旧 `wordflip` 库，只改 `.env` 不会自动创建 v2 库。请走下方“内容与 v2 数据库”流程，不要删除旧 volume 或覆盖旧库。

### 2. 后端

```powershell
cd ..\wordflip-server
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

服务地址：

- Health：`http://localhost:8080/api/v1/health`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

启动时 Flyway 只读取 `db/migration-v2`。旧 V1–V24 已归档，不会参与 v7 启动。

### 3. Android

```powershell
cd ..\wordflip-android
.\gradlew.bat test :app:assembleDebug
```

真机 USB：

```powershell
.\scripts\adb-reverse.ps1
.\scripts\install-phone-debug.ps1
```

Debug 包通过 `http://127.0.0.1:8080/api/v1` 访问本机后端。模拟器可使用 `http://10.0.2.2:8080/api/v1`。

### 4. API 契约

```powershell
cd ..\wordflip-api
python -m pytest -q tests
```

## 内容与 v2 数据库

### 1. 验证并构建内容包

内容管线从归档的三本内置词书种子中抽取词条，生成 v2 学习卡、异常清单和确定性抽检样本。

```powershell
cd tools\content-pipeline
Copy-Item overrides.example.json overrides.json
$env:PYTHONPATH = "src"

python -m wordflip_content verify
python -m wordflip_content build `
  --book ielts=../../wordflip-server/db-archive/migration-v1/V3_1__seed_book_words_ielts.sql `
  --book cet4=../../wordflip-server/db-archive/migration-v1/V3_2__seed_book_words_cet4.sql `
  --book kaoyan=../../wordflip-server/db-archive/migration-v1/V3_3__seed_book_words_kaoyan.sql `
  --overrides overrides.json `
  --output out
```

执行发布前必须人工检查 `out` 中的异常清单与抽检样本。

### 2. 从旧库安全建立新库

脚本默认仅 dry-run。它会先备份旧库、拒绝覆盖已存在目标库，再执行 v2 Flyway 与内容发布。

```powershell
cd ..\..
$env:WORDFLIP_DB_PASSWORD = "<数据库密码>"

.\scripts\rebuild-wordflip-v2.ps1 `
  -SourceDatabase wordflip `
  -TargetDatabase wordflip_v2_dev

# 审核输出和参数后，才显式执行：
.\scripts\rebuild-wordflip-v2.ps1 `
  -SourceDatabase wordflip `
  -TargetDatabase wordflip_v2_dev `
  -Execute
```

详细说明见 [scripts/README-wordflip-v2.md](scripts/README-wordflip-v2.md) 和 [内容管线 README](tools/content-pipeline/README.md)。

### 3. 服务冒烟

服务启动并登录后：

```powershell
.\scripts\smoke-wordflip-v2.ps1 -AccessToken "<JWT access token>"
```

冒烟会检查词书目录、当前学习计划与 Today；无计划的新用户应由 Android 进入首次选书页。

## 验证基线

```powershell
cd wordflip-api
python -m pytest -q tests

cd ..\wordflip-server
.\mvnw.cmd test

cd ..\wordflip-android
.\gradlew.bat test :app:assembleDebug
```

Docker Desktop 可用时，后端结果不得因 Testcontainers/MySQL 不可用而跳过数据库集成测试。

## 文档阅读顺序

| 顺序 | 文档 | 用途 |
|------|------|------|
| 1 | [requirements.md](docs/wordflip/requirements.md) | v7 用户行为与业务规则 |
| 2 | [openapi.yaml](wordflip-api/openapi.yaml) | REST 契约 |
| 3 | [database-design.md](docs/wordflip/database-design.md) | v2 表、不变量与内容发布 |
| 4 | [api-modules.md](docs/wordflip/api-modules.md) | 服务边界与 FSRS 事务 |
| 5 | [architecture.md](docs/wordflip/architecture.md) | 技术架构；冲突时服从前四项 |
| 6 | [TASK.md](TASK.md) | 当前实施与验收 |
| 7 | [docs/superpowers/specs](docs/superpowers/specs) | 已批准局部设计 |
| 8 | [prototypes/wordflip-v5.html](prototypes/wordflip-v5.html) | 历史 UI/动效参考 |
| 9 | [WordFlip-PRD.md](docs/prd/WordFlip-PRD.md) | 历史资料，不作实施依据 |

## 开发规则摘要

- API 变更先改 OpenAPI，再同步 server 与 Android。
- 数据库变更只进入 `migration-v2`；不得修改 archive 伪造新迁移。
- 客户端不得计算 FSRS、判题、Today 或统计业务结果。
- 学习浏览不写双层记忆；只有测验判题写 `review_events`。
- 业务代码类/方法文档和关键逻辑使用简体中文注释。
- 不自动 commit/push，不提交 `.env`、密钥、备份、全量内容源或真实用户数据。

完整规则见 [AGENTS.md](AGENTS.md) 与各子目录 `AGENTS.md`。

## 下一步

按 [TASK.md](TASK.md) 顺序推进：

1. `V7-DB09~13`：内容构建、全新 v2 库、MySQL 集成。
2. `V7-S13~20`：服务端 E2E、事务、越权、安全和性能。
3. `V7-A10~17`：Android 真机完整链路。
4. `V7-Q04~13`：发布配置、release、演示与合并准备。
