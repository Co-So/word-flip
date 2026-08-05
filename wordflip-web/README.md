# wordflip-web

WordFlip 当前优先开发的 React Web 客户端。第一阶段是桌面端优先的完整可点击视觉演示，使用固定模拟数据覆盖登录、首次设置、今日、词书、分组、学习、双轨测验、媒体、统计、设置和代表性异常状态。

业务规则仍以 [requirements.md](../docs/wordflip/requirements.md) 与 [openapi.yaml](../wordflip-api/openapi.yaml) 为准。Web 不实现 FSRS、判题、分组、今日任务或统计算法。

## 启动

环境：Node.js 20、npm。

```powershell
npm install
npx playwright install chromium
npm run dev
```

默认使用完整 Mock 数据，不需要启动后端。若要把认证模块接到本地 Spring Boot，复制 `.env.example` 为 `.env.local`，并设置：

```dotenv
VITE_DATA_SOURCE=http
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
```

当前 `http` 模式只替换认证 Repository；设置、词书、今日、分组、学习、测验、统计与媒体仍使用 Mock，后续按模块迁移。

演示账号：

- 邮箱：`demo@wordflip.local`
- 密码：`wordflip-demo`

## 页面

稳定主导航为今日、词书、分组、统计和设置。学习、测验和媒体编辑使用上下文流程页面。

主要路由：

- `/login`、`/register`、`/onboarding`
- `/today`、`/books`、`/books/:bookId`
- `/groups`、`/groups/:groupId`
- `/study/:sessionId`、`/study/:sessionId/complete`
- `/quiz`、`/quiz/:sessionId`、`/quiz/:sessionId/result`
- `/media`、`/stats`、`/settings`

开发环境的 `/__demo/scenario/:name` 可稳定加载空任务、空词书、测验完成等视觉回归场景，不进入生产路由。

## 架构

```text
src/
├── app/             # 路由、Provider、守卫
├── components/      # 通用 UI
├── data/            # Repository 契约、模拟实现和运行时装配
├── design-system/   # Web token、全局样式与动效
├── domain/          # 稳定页面领域模型
├── features/        # 业务页面与组件
├── layouts/         # AppShell / AuthShell / FocusShell
└── test/            # Vitest 测试工具
```

页面只依赖 Repository 契约，不能直接读取模拟 JSON 或 OpenAPI DTO。

## 数据模式

- `MockRepository`：默认视觉演示模式，固定种子持久化到 `wordflip.web.demo.v1`，支持稳定场景和重置。
- `HttpRepository`：认证模块已支持按环境变量接入真实 API；其余模块按设置/词书、今日/分组、学习、测验、统计、媒体的顺序接入，页面和路由不重写。

模拟测验只回放预计算服务端结果，并通过统一 `applyQuizResult` 入口更新指定 skill；翻卡学习不会改变掌握度。

## 验证

```powershell
npm test
npm run lint
npm run build
npm run test:e2e
```

Playwright 覆盖完整主流程、代表性状态、1440/1280/1024/768 响应式、减少动态效果和视觉基线。

## 设计

Web 使用暖米白纸张表面、Natural Sage 学习语义和少量陶土橙，借鉴成熟桌面工具的克制留白、稳定工作台、等宽状态标签与快捷键表达。详细规格见 [Web 视觉演示设计](../docs/superpowers/specs/2026-07-23-wordflip-web-visual-demo-design.md)。
