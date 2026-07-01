# WordFlip 仓库目录结构规范

> 版本：v1.0  
> 日期：2026-06-30  
> 状态：**Monorepo 标准（当前 + 脚手架目标）**

本文档规定 WordFlip 仓库的**标准文件夹结构、命名约定与放置规则**。新增目录或移动文件前应遵循本文；详细技术说明见 [docs/wordflip/architecture.md](docs/wordflip/architecture.md)。

---

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| Monorepo | 文档、契约、原型、多端代码同仓；**业务逻辑只在服务端一份** |
| 契约优先 | API 以 `wordflip-api/openapi.yaml` 为单一来源，客户端 DTO 由 OpenAPI 生成 |
| 文档与代码分离 | 产品/设计文档在 `docs/`；运行时资源在 `assets/`；交互原型在 `prototypes/` |
| 按端分包 | `wordflip-server` / `wordflip-android` / `wordflip-web` 各自独立构建，不互相嵌套 |
| 基础设施集中 | 本地 MySQL / Redis / MinIO 统一放在根目录 `docker/` |
| 小步扩展 | 脚手架初始化前允许占位目录 + README；禁止在根目录堆临时文件 |

---

## 2. 仓库总览

### 2.1 当前结构（已存在）

```
.
├── STRUCTURE.md                 # 目录结构规范
├── TASK.md                      # 可打勾任务清单
├── AGENTS.md                    # AI Agent 指令（Cursor 自动读取）
├── .cursor/rules/               # Cursor 规则（如 chinese-comments.mdc）
├── README.md                    # 人类入口
├── .gitignore
│
├── docs/                        # 📄 文档（不参与构建）
├── prototypes/                  # 🖥️ HTML 交互原型
├── assets/                      # 🖼️ 静态参考资源
├── wordflip-api/                # 📜 OpenAPI 契约
├── wordflip-server/             # ☕ Spring Boot（占位 → 脚手架）
├── wordflip-android/            # 🤖 Android（占位 → 脚手架）
├── wordflip-web/                # ⚛️ React Web 二期（占位）
└── docker/                      # 🐳 本地依赖服务
```

### 2.2 目标结构（脚手架完成后）

```
.
├── …（同上）
│
├── wordflip-server/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/wordflip/
│       └── main/resources/db/migration/
│
├── wordflip-android/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── app/
│   ├── feature-*/
│   └── core-*/
│
├── wordflip-web/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│
└── docker/
    ├── docker-compose.yml
    ├── .env.example
    └── init/                    # 可选：MySQL 初始化脚本
```

---

## 3. 根目录

| 路径 | 用途 | 允许内容 | 禁止 |
|------|------|----------|------|
| `/` | Monorepo 根 | `README.md`、`STRUCTURE.md`、`.gitignore`、各一级子目录 | 散落的 `.html`、`.java`、业务代码、密钥文件 |
| `README.md` | 新人入口 | 项目简介、文档索引、启动指引 | 长篇设计细节（放 `docs/`） |
| `.gitignore` | 全局忽略 | 各端 build 产物、IDE、`.env` | — |

**命名：** 仓库根目录名可为 `WF原型制作`（本地）或 `wordflip`（推荐远程仓库名）；子项目目录**必须**使用 `wordflip-*` 前缀。

---

## 4. `docs/` — 文档

```
docs/
├── README.md                    # 文档索引
├── prd/                         # 历史 / 原始 PRD（只读参考）
│   └── WordFlip-PRD.md
└── wordflip/                    # ★ 定稿规格（实施依据）
    ├── requirements.md          # 产品需求 v6（权威）
    ├── architecture.md          # 技术架构
    ├── database-design.md       # 数据库设计
    ├── api-modules.md           # API 模块与业务规则
    ├── user-design.md           # 账号与登录
    ├── android-ui-spec.md       # Android UI/UX
    ├── coding-standards.md      # 代码中文注释规范
    └── design-system/
        └── MASTER.md            # Natural Sage 设计 tokens
```

| 规则 | 说明 |
|------|------|
| 新增设计文档 | 放在 `docs/wordflip/`，Markdown 格式，版本号写在文首 |
| PRD 修订 | **不**直接改 `docs/prd/WordFlip-PRD.md` 作为实施依据；变更写入 `requirements.md` |
| 禁止 | 在 `docs/` 放源码、SQL 迁移脚本（迁移放 `wordflip-server/.../db/migration/`） |
| 交叉引用 | 文档间用相对路径；OpenAPI 引用 `../../wordflip-api/openapi.yaml` |

---

## 5. `prototypes/` — 交互原型

```
prototypes/
├── README.md
├── wordflip-v5.html             # ★ 当前 UI/动效参考
├── wordflip-v4.html             # 历史版本（归档）
├── wordflip-v3.html
└── wordflip-v2.html
```

| 规则 | 说明 |
|------|------|
| 新版本原型 | `wordflip-v{N}.html`，在 README 标注是否仍为参考 |
| 业务逻辑 | 原型仅 UI 参考；行为以 `docs/wordflip/requirements.md` 为准 |
| 禁止 | 将原型作为生产构建入口；禁止在根目录新增 `.html` |

---

## 6. `assets/` — 静态资源

```
assets/
├── README.md
└── prd-pages/                   # PRD PDF 导出截图等
    └── page_*.png
```

| 规则 | 说明 |
|------|------|
| 用途 | 设计对照图、营销素材、不参与编译的静态文件 |
| 禁止 | 用户上传的图片、MinIO 对象、Android `res/` 资源（各端自有目录） |

---

## 7. `wordflip-api/` — API 契约

```
wordflip-api/
├── README.md
└── openapi.yaml                 # OpenAPI 3.0.3，单一来源
```

| 规则 | 说明 |
|------|------|
| 变更流程 | 先改 `openapi.yaml` → 同步 `docs/wordflip/api-modules.md` → 再改服务端/客户端 |
| 生成物 | OpenAPI Generator 输出**不提交**到本目录（生成到各端 `build/` 或指定 package） |
| 禁止 | 在本目录写 Java/Kotlin 实现代码 |

---

## 8. `wordflip-server/` — 后端

### 8.1 目标目录树

```
wordflip-server/
├── README.md
├── pom.xml
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/wordflip/
    │   │   ├── WordflipApplication.java
    │   │   ├── config/              # Security、Redis、MinIO、CORS、Flyway
    │   │   ├── controller/          # REST，对齐 openapi paths
    │   │   ├── service/             # 业务编排（Auth、Book、Group、Quiz…）
    │   │   ├── domain/              # JPA Entity、枚举
    │   │   ├── repository/          # Spring Data JPA
    │   │   ├── dto/                 # 请求/响应（可与 OpenAPI schema 对齐）
    │   │   ├── security/            # JWT、UserDetails
    │   │   ├── storage/             # MinIO 客户端封装
    │   │   └── exception/           # 全局异常、ErrorResponse
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       └── db/migration/        # Flyway：V1__init_schema.sql …
    └── test/
        └── java/com/wordflip/
```

### 8.2 包与文件命名

| 类型 | 约定 | 示例 |
|------|------|------|
| Controller | `{Resource}Controller` | `QuizController` |
| Service | `{Domain}Service` | `ReviewService` |
| Entity | 单数名词 | `WordMastery` |
| Repository | `{Entity}Repository` | `WordMasteryRepository` |
| Flyway | `V{序号}__{snake_case}.sql` | `V1__init_schema.sql` |
| 配置 | `application-{profile}.yml` | `application-dev.yml` |

### 8.3 禁止

- 在 `controller` 写 SRS / 判题等业务逻辑  
- 在仓库根或 `docs/` 放 SQL 迁移  
- 将 `docker-compose.yml` 放在 `wordflip-server/`（统一用根目录 `docker/`）

---

## 9. `wordflip-android/` — Android 客户端

### 9.1 目标模块树

```
wordflip-android/
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
├── app/                         # Application、MainActivity、导航壳
├── feature-auth/
├── feature-today/
├── feature-books/
├── feature-groups/
├── feature-study/               # 学习页、BottomSheet、引导
├── feature-snapshot/            # 卡拍
├── feature-quiz/
├── feature-stats/
├── feature-settings/
├── core-network/                # Retrofit、Auth 拦截器、Token 存储
├── core-model/                  # OpenAPI 生成 DTO
├── core-ui/                     # 主题、Flip 卡片、通用 Compose 组件
└── core-image/                  # 图片编辑器、CameraX
```

### 9.2 模块命名

| 前缀 | 含义 |
|------|------|
| `feature-*` | 面向用户的业务功能，可依赖 `core-*` |
| `core-*` | 跨 feature 共享，**不可**依赖 `feature-*` |
| `app` | 组装模块，依赖所有需要的 feature |

### 9.3 Feature 内结构（推荐）

```
feature-study/
└── src/main/java/com/wordflip/feature/study/
    ├── StudyScreen.kt
    ├── StudyViewModel.kt
    ├── StudyUiState.kt
    └── navigation/
```

### 9.4 禁止

- 在 Android 层实现艾宾浩斯计算、测验判题、词书文件解析入库  
- 把 `prototypes/*.html` 复制进 `assets/` 作为 WebView 主界面  
- 业务真相写入 Room 而不与服务端同步（Room 仅作可选缓存）

---

## 10. `wordflip-web/` — Web 客户端（二期）

```
wordflip-web/
├── README.md
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
└── src/
    ├── main.tsx
    ├── app/                     # 路由壳
    ├── pages/                   # 与 Android feature 对应
    ├── components/
    ├── api/                     # axios + OpenAPI 生成类型
    ├── hooks/
    └── styles/
```

| 规则 | 说明 |
|------|------|
| MVP | 可不初始化；目录保留占位 |
| 共享 | 仅共享 `wordflip-api/openapi.yaml`，UI 独立实现 |

---

## 11. `docker/` — 本地基础设施

```
docker/
├── README.md
├── docker-compose.yml           # MySQL 8 + Redis 7 + MinIO
├── .env.example                 # 端口、密码占位（不含真实密钥）
└── init/                        # 可选
    └── mysql/                   # 首次启动脚本
```

| 服务 | 容器名建议 | 端口 |
|------|------------|------|
| MySQL | `wordflip-mysql` | 3306 |
| Redis | `wordflip-redis` | 6379 |
| MinIO | `wordflip-minio` | 9000, 9001 |

| 规则 | 说明 |
|------|------|
| 数据卷 | 使用命名 volume 或 `docker/data/`（已在 `.gitignore`） |
| 禁止 | 提交含真实密码的 `.env` |

---

## 12. 文件放置速查

| 我要放… | 放在 |
|---------|------|
| 产品需求变更 | `docs/wordflip/requirements.md` |
| 新 REST 端点 | `wordflip-api/openapi.yaml` |
| 数据库表变更 | `wordflip-server/.../db/migration/V{n}__*.sql` + 更新 `database-design.md` |
| Android 新页面 | `wordflip-android/feature-*/` |
| 设计 token / 颜色 | `docs/wordflip/design-system/MASTER.md` |
| 新的 HTML 原型 | `prototypes/wordflip-v{N}.html` |
| 本地 compose | `docker/docker-compose.yml` |
| 密钥、Token | **不得入库**；用 `.env`（gitignore）或 CI Secret |

---

## 13. 禁止清单（全局）

- ❌ 根目录散落业务源码、构建产物、用户数据  
- ❌ 在多个端重复实现掌握度 / SRS / 判题逻辑  
- ❌ 未更新 OpenAPI 就直接改 Controller 路径  
- ❌ 把 `localStorage` 原型逻辑原样搬进 Android 作为主数据源  
- ❌ 在 `docs/prd/` 当作唯一需求源做开发（以 `requirements.md` v6 为准）  
- ❌ 提交 `.env`、`application-local.yml`、Keystore、JWT 私钥  

---

## 14. 初始化检查清单

脚手架搭建完成后，确认：

- [ ] `wordflip-server` 能 `mvn spring-boot:run` 且 Flyway 执行 `V1__init_schema.sql`
- [ ] `docker compose up -d` 启动三件套
- [ ] `wordflip-android` 模块树与 §9.1 一致
- [ ] OpenAPI 生成脚本指向 `wordflip-api/openapi.yaml`
- [ ] 根目录 `README.md` 与本文 §2 一致
- [ ] 无新增根目录 `.html` / 临时文件

---

## 15. 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-30 | v1.0 | 初版：Monorepo 标准结构（当前 + 脚手架目标） |
| 2026-06-30 | v1.1 | 关联 [TASK.md](TASK.md) 任务清单 |

---

## 16. 相关文档

- [README.md](README.md) — 快速入门  
- [TASK.md](TASK.md) — **可打勾任务清单（开发用）**  
- [AGENTS.md](AGENTS.md) — **AI Agent 指令（Cursor 自动读取）**  
- [docs/wordflip/coding-standards.md](docs/wordflip/coding-standards.md) — **代码中文注释规范**  
- [docs/wordflip/architecture.md](docs/wordflip/architecture.md) — 技术架构与 MVP 分期  
- [docs/wordflip/requirements.md](docs/wordflip/requirements.md) — 产品需求定稿  
