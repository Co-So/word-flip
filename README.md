# WordFlip

单词卡片学习应用（Android MVP + Spring Boot 后端）。本仓库为 **Monorepo**：设计文档、API 契约、交互原型与后续代码脚手架同仓维护。

**目录规范：** [STRUCTURE.md](STRUCTURE.md) · **任务清单：** [TASK.md](TASK.md) · **Git 规范：** [CONTRIBUTING.md](CONTRIBUTING.md) · **Agent 指令：** [AGENTS.md](AGENTS.md) · **注释规范：** [docs/wordflip/coding-standards.md](docs/wordflip/coding-standards.md)

## 目录结构

```
.
├── docs/                    # 设计与需求文档
│   ├── prd/                 # 产品需求原文
│   └── wordflip/            # 定稿技术/产品文档（v6）
├── prototypes/              # HTML 交互原型（v5 为 UI 参考）
├── assets/                  # 静态资源（PRD 导出页等）
├── wordflip-api/            # OpenAPI 契约（代码生成单一来源）
├── wordflip-server/         # Spring Boot 后端（骨架已就绪）
├── wordflip-android/        # Kotlin + Compose 客户端（骨架已就绪）
├── wordflip-web/            # React Web 二期（待初始化）
└── docker/                  # 本地 MySQL / Redis / MinIO
```

## 本地开发快速启动

### 1. 基础设施（需 Docker Desktop 运行中）

```powershell
cd docker
copy .env.example .env   # 默认 root/minioadmin 已与 application-dev.yml 对齐
docker compose up -d
docker compose ps
```

### 2. 后端

```powershell
cd wordflip-server
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
# 健康检查：http://localhost:8080/api/v1/health
# Swagger：  http://localhost:8080/swagger-ui.html
```

### 3. Android

```powershell
cd wordflip-android
.\gradlew.bat :app:assembleDebug
# 或：gradle :app:assembleDebug
```

模拟器访问本机 API：`http://10.0.2.2:8080/api/v1`

## 文档阅读顺序

| 顺序 | 文档 | 说明 |
|------|------|------|
| 1 | [docs/prd/WordFlip-PRD.md](docs/prd/WordFlip-PRD.md) | 产品规划原文 |
| 2 | [docs/wordflip/requirements.md](docs/wordflip/requirements.md) | 需求定稿 v6 |
| 3 | [docs/wordflip/architecture.md](docs/wordflip/architecture.md) | 技术架构 |
| 4 | [docs/wordflip/database-design.md](docs/wordflip/database-design.md) | 数据库设计 |
| 5 | [docs/wordflip/api-modules.md](docs/wordflip/api-modules.md) | API 模块划分 |
| 6 | [wordflip-api/openapi.yaml](wordflip-api/openapi.yaml) | REST 契约 |
| 7 | [docs/wordflip/android-ui-spec.md](docs/wordflip/android-ui-spec.md) | Android UI/UX |
| 8 | [prototypes/wordflip-v5.html](prototypes/wordflip-v5.html) | 交互原型（浏览器直接打开） |

## 本地预览原型

在浏览器中打开 `prototypes/wordflip-v5.html`。业务逻辑以 `docs/wordflip/requirements.md` 为准，原型仅作 UI/动效参考。

## 下一步

1. 启动 Docker Desktop → `docker compose up -d`（TASK I-07、S-18）  
2. 按 [TASK.md](TASK.md) **§P0** 实现 Auth / 词书 / 分组  
3. Android 接入 OpenAPI + Retrofit（TASK A-10～A-14）  
4. `wordflip-web` — 二期  

详见 [architecture.md](docs/wordflip/architecture.md) §16。
