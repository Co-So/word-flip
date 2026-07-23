# WordFlip Web Visual Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `wordflip-web/` 中交付桌面端优先、完整可点击、使用一致模拟数据且可逐模块切换真实 API 的 WordFlip Web 视觉演示版。

**Architecture:** React 页面按业务功能拆分，通过 Repository 接口读取数据；首版使用固定种子和版本化本地存储实现 `MockRepository`。普通页面运行在左侧导航 `AppShell` 中，学习和测验运行在收窄导航的 `FocusShell` 中；组件不直接依赖 OpenAPI DTO 或模拟 JSON。

**Tech Stack:** Node.js 20、npm、React 18.3.1、TypeScript 5.7、Vite 6、React Router 6、TanStack Query 5、Axios 1、Vitest 2、Testing Library 16、Playwright 1.49、CSS Modules + 全局设计 token。

## Global Constraints

- 产品和行为基线为 `docs/wordflip/requirements.md` v7 与 `wordflip-api/openapi.yaml`。
- 视觉基线为 `docs/superpowers/specs/2026-07-23-wordflip-web-visual-demo-design.md`。
- 只实现 Web 视觉演示版；Android 本计划不修改业务代码。
- 主要验收宽度为 1440px 和 1280px；窄屏必须可用且无页面级横向滚动。
- 学习浏览不得改变掌握度。
- 模拟层只回放预先定义的服务端结果，不实现真实 SRS、判题、导入解析或今日任务计算。
- 测验结果只能通过演示状态的统一 `applyQuizResult` 入口写入。
- 听写 `dictation` 与选择 `choice` 双轨互不覆盖。
- 新增词书只为未入组单词增量追加分组；取消词书不删除已有单词。
- 业务代码的类、方法文档与关键逻辑注释使用简体中文。
- Material Symbols Outlined 用于结构图标；禁止用 emoji 充当导航图标。
- 本计划先交付本地可运行演示，不创建生产部署；部署另立任务。
- 每个提交步骤仅是审查边界；未获得用户明确授权时不得实际执行 `git commit` 或 `git push`。

---

## File Structure

```text
wordflip-web/
  package.json
  package-lock.json
  tsconfig.json
  tsconfig.app.json
  tsconfig.node.json
  vite.config.ts
  vitest.config.ts
  playwright.config.ts
  eslint.config.js
  index.html
  src/
    main.tsx
    app/
      App.tsx
      AppProviders.tsx
      router.tsx
      routeGuards.tsx
    components/
      AsyncState/
      Button/
      EmptyState/
      FormField/
      Panel/
      StatusTag/
      Toast/
    design-system/
      tokens.css
      global.css
      motion.css
    layouts/
      AppShell/
      AuthShell/
      FocusShell/
    domain/
      auth.ts
      books.ts
      groups.ts
      learning.ts
      media.ts
      quiz.ts
      settings.ts
      stats.ts
      today.ts
    data/
      contracts/
        AppError.ts
        RepositoryBundle.ts
      mock/
        createDemoState.ts
        DemoStateStore.ts
        fixtures.ts
        repositories/
      runtime/
        RepositoryContext.tsx
    features/
      auth/
      onboarding/
      today/
      books/
      groups/
      study/
      quiz/
      media/
      stats/
      settings/
    test/
      renderApp.tsx
      setup.ts
      testIds.ts
  e2e/
    happy-path.spec.ts
    representative-states.spec.ts
    responsive.spec.ts
    visual-regression.spec.ts
  public/
    card-images/
```

The root app owns routing and providers. Features own page-specific UI and hooks. Shared components contain no business rules. Domain files contain stable view models. Repository contracts isolate pages from mock and future HTTP implementations.

---

### Task 1: React 18 工程、测试与路由基线

**Files:**
- Create: `wordflip-web/package.json`
- Create: `wordflip-web/package-lock.json`
- Create: `wordflip-web/tsconfig.json`
- Create: `wordflip-web/tsconfig.app.json`
- Create: `wordflip-web/tsconfig.node.json`
- Create: `wordflip-web/vite.config.ts`
- Create: `wordflip-web/vitest.config.ts`
- Create: `wordflip-web/playwright.config.ts`
- Create: `wordflip-web/eslint.config.js`
- Create: `wordflip-web/index.html`
- Create: `wordflip-web/src/main.tsx`
- Create: `wordflip-web/src/app/App.tsx`
- Create: `wordflip-web/src/app/AppProviders.tsx`
- Create: `wordflip-web/src/app/router.tsx`
- Create: `wordflip-web/src/test/setup.ts`
- Create: `wordflip-web/src/test/renderApp.tsx`
- Create: `wordflip-web/src/app/App.test.tsx`

**Interfaces:**
- Produces: `AppProviders({ children }: PropsWithChildren)`
- Produces: `AppRouter = ReturnType<typeof createBrowserRouter>`
- Produces: `createAppRouter(): AppRouter`
- Produces: `renderApp(route: string): RenderResult`
- Consumes: none

- [ ] **Step 1: 创建项目清单和脚本**

Create `wordflip-web/package.json` with pinned React 18 and test tooling:

```json
{
  "name": "wordflip-web",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:e2e": "playwright test",
    "test:e2e:update": "playwright test --update-snapshots"
  },
  "dependencies": {
    "@tanstack/react-query": "5.62.2",
    "axios": "1.7.9",
    "react": "18.3.1",
    "react-dom": "18.3.1",
    "react-router-dom": "6.28.1"
  },
  "devDependencies": {
    "@eslint/js": "9.17.0",
    "@playwright/test": "1.49.1",
    "@testing-library/jest-dom": "6.6.3",
    "@testing-library/react": "16.1.0",
    "@testing-library/user-event": "14.5.2",
    "@types/react": "18.3.18",
    "@types/react-dom": "18.3.5",
    "@vitejs/plugin-react": "4.3.4",
    "eslint": "9.17.0",
    "eslint-plugin-react-hooks": "5.1.0",
    "eslint-plugin-react-refresh": "0.4.16",
    "globals": "15.14.0",
    "jsdom": "25.0.1",
    "typescript": "5.7.2",
    "typescript-eslint": "8.18.1",
    "vite": "6.0.5",
    "vitest": "2.1.8"
  }
}
```

Run:

```bash
cd wordflip-web
npm install
```

Expected: exit 0 and a new `package-lock.json`.

- [ ] **Step 2: 配置 TypeScript、Vite、Vitest、ESLint 和 Playwright**

Use strict TypeScript, `@/` mapped to `src/`, jsdom for unit tests, and a Playwright web server on port 4173. `vitest.config.ts` must include:

```ts
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) }
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true
  }
});
```

Use this Playwright server configuration:

```ts
import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  use: {
    baseURL: "http://127.0.0.1:4173",
    trace: "retain-on-failure"
  },
  webServer: {
    command: "npm run build && npm run dev -- --host 127.0.0.1 --port 4173",
    port: 4173,
    reuseExistingServer: true
  },
  projects: [
    { name: "desktop-chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } }
  ]
});
```

- [ ] **Step 3: 写应用启动失败测试**

Create `src/app/App.test.tsx`:

```tsx
import { screen } from "@testing-library/react";
import { renderApp } from "@/test/renderApp";

test("根路由可以渲染应用启动状态", async () => {
  renderApp("/");
  expect(await screen.findByRole("status")).toHaveTextContent("正在加载 WordFlip");
});
```

- [ ] **Step 4: 运行测试并确认失败**

Run:

```bash
npm test -- src/app/App.test.tsx
```

Expected: FAIL because `renderApp` and `App` do not exist.

- [ ] **Step 5: 实现最小应用与测试 Provider**

`AppProviders` must create one `QueryClient` per mounted app:

```tsx
export function AppProviders({ children }: PropsWithChildren) {
  const [queryClient] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: false } } })
  );
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
```

`App` initially renders:

```tsx
export function App() {
  return <div role="status">正在加载 WordFlip</div>;
}
```

`renderApp(route)` uses `MemoryRouter`, `AppProviders`, and Testing Library `render`.

- [ ] **Step 6: 验证工程基线**

Run:

```bash
npm test
npm run lint
npm run build
```

Expected: all commands exit 0; Vitest reports 1 passing test.

- [ ] **Step 7: 提交检查点（需用户授权）**

```bash
git add wordflip-web
git commit -m "feat(web): 初始化 React 视觉演示工程"
```

---

### Task 2: Web 设计系统、通用组件与应用外壳

**Files:**
- Create: `wordflip-web/src/design-system/tokens.css`
- Create: `wordflip-web/src/design-system/global.css`
- Create: `wordflip-web/src/design-system/motion.css`
- Create: `wordflip-web/src/components/Button/Button.tsx`
- Create: `wordflip-web/src/components/Button/Button.module.css`
- Create: `wordflip-web/src/components/Panel/Panel.tsx`
- Create: `wordflip-web/src/components/Panel/Panel.module.css`
- Create: `wordflip-web/src/components/StatusTag/StatusTag.tsx`
- Create: `wordflip-web/src/components/EmptyState/EmptyState.tsx`
- Create: `wordflip-web/src/components/AsyncState/AsyncState.tsx`
- Create: `wordflip-web/src/components/CommandPalette/CommandPalette.tsx`
- Create: `wordflip-web/src/components/CommandPalette/CommandPalette.module.css`
- Create: `wordflip-web/src/layouts/AppShell/AppShell.tsx`
- Create: `wordflip-web/src/layouts/AppShell/AppShell.module.css`
- Create: `wordflip-web/src/layouts/AuthShell/AuthShell.tsx`
- Create: `wordflip-web/src/layouts/FocusShell/FocusShell.tsx`
- Create: `wordflip-web/src/layouts/FocusShell/FocusShell.module.css`
- Create: `wordflip-web/src/layouts/AppShell/AppShell.test.tsx`
- Modify: `wordflip-web/src/main.tsx`

**Interfaces:**
- Produces: `<AppShell />` with nested `<Outlet />`
- Produces: `<FocusShell title progress aside onExit />`
- Produces: `<Button variant="primary|secondary|ghost" />`
- Produces: `<Panel title action />`
- Produces: `<AsyncState status error onRetry />`
- Consumes: React Router navigation

- [ ] **Step 1: 写外壳导航失败测试**

```tsx
test("左侧导航只显示五个稳定入口", () => {
  render(
    <MemoryRouter>
      <AppShell />
    </MemoryRouter>
  );
  expect(screen.getAllByRole("link")).toHaveLength(5);
  for (const label of ["今日", "词书", "分组", "统计", "设置"]) {
    expect(screen.getByRole("link", { name: label })).toBeVisible();
  }
});

test("Ctrl K 打开全局命令入口", async () => {
  render(
    <MemoryRouter>
      <AppShell />
    </MemoryRouter>
  );
  await userEvent.keyboard("{Control>}k{/Control}");
  expect(screen.getByRole("dialog", { name: "搜索单词或功能" })).toBeVisible();
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/layouts/AppShell/AppShell.test.tsx`

Expected: FAIL because `AppShell` does not exist.

- [ ] **Step 3: 定义 Web 主题 token**

`tokens.css` must define:

```css
:root {
  --wf-canvas: #f8f5ef;
  --wf-sidebar: #eee9df;
  --wf-surface: #fdfbf7;
  --wf-border: #ded7cb;
  --wf-text: #302d29;
  --wf-text-muted: #7f776c;
  --wf-sage: #6f9038;
  --wf-sage-container: #b7d07a;
  --wf-terracotta: #c76a49;
  --wf-error: #c0392b;
  --wf-radius-control: 8px;
  --wf-radius-panel: 12px;
  --wf-sidebar-width: 216px;
  --wf-content-max: 1240px;
  --wf-motion-fast: 160ms;
  --wf-motion-normal: 220ms;
  --wf-motion-flip: 360ms;
}
```

`motion.css` disables nonessential transforms inside `@media (prefers-reduced-motion: reduce)`.

- [ ] **Step 4: 实现通用组件和布局**

`AppShell` owns navigation and layout. It must render five `NavLink` entries, a `CommandPalette`, and an `<Outlet />`. `CommandPalette` opens with `Ctrl/⌘ + K`, lists the same five destinations, closes with `Esc`, and restores focus to its trigger. `FocusShell` accepts:

```ts
export interface FocusShellProps {
  title: string;
  progress: string;
  aside: ReactNode;
  onExit: () => void;
  children: ReactNode;
}
```

The shell collapses navigation below 1024px and never introduces page-level horizontal scrolling.

- [ ] **Step 5: 验证视觉基础**

Run:

```bash
npm test -- src/layouts/AppShell/AppShell.test.tsx
npm run lint
npm run build
```

Expected: all exit 0 and the shell test passes.

- [ ] **Step 6: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src/components wordflip-web/src/design-system wordflip-web/src/layouts wordflip-web/src/main.tsx
git commit -m "feat(web): 建立暖纸张设计系统与应用外壳"
```

---

### Task 3: 领域模型、Repository 契约与版本化模拟状态

**Files:**
- Create: `wordflip-web/src/domain/auth.ts`
- Create: `wordflip-web/src/domain/books.ts`
- Create: `wordflip-web/src/domain/groups.ts`
- Create: `wordflip-web/src/domain/learning.ts`
- Create: `wordflip-web/src/domain/media.ts`
- Create: `wordflip-web/src/domain/quiz.ts`
- Create: `wordflip-web/src/domain/settings.ts`
- Create: `wordflip-web/src/domain/stats.ts`
- Create: `wordflip-web/src/domain/today.ts`
- Create: `wordflip-web/src/data/contracts/AppError.ts`
- Create: `wordflip-web/src/data/contracts/RepositoryBundle.ts`
- Create: `wordflip-web/src/data/mock/fixtures.ts`
- Create: `wordflip-web/src/data/mock/createDemoState.ts`
- Create: `wordflip-web/src/data/mock/DemoStateStore.ts`
- Create: `wordflip-web/src/data/mock/DemoStateStore.test.ts`
- Create: `wordflip-web/src/data/runtime/RepositoryContext.tsx`
- Modify: `wordflip-web/src/test/renderApp.tsx`

**Interfaces:**
- Produces: `RepositoryBundle`
- Produces: `DemoState`, `createDemoState()`
- Produces: `DemoStateStore.read()`, `write()`, `reset()`, `update()`, `applyQuizResult()`
- Produces: `useRepositories(): RepositoryBundle`
- Produces: `renderAuthenticatedApp(route): TestAppHandle`
- Produces: `renderScenarioApp(scenario, route): TestAppHandle`
- Consumes: browser `localStorage`

- [ ] **Step 1: 定义稳定领域类型和错误类型**

Use skill strings exactly:

```ts
export type QuizSkill = "dictation" | "choice";
export type QueueState = "unlearned" | "fuzzy" | "unknown";

export interface SkillProgress {
  skill: QuizSkill;
  state: QueueState;
  stability: number;
  heatLevel: 0 | 1 | 2 | 3;
  lastQuizSucceeded: boolean;
}

export type AppError =
  | { kind: "validation"; message: string; fieldErrors: Record<string, string> }
  | { kind: "unauthorized"; message: string }
  | { kind: "not-found"; message: string }
  | { kind: "conflict"; message: string }
  | { kind: "unavailable"; message: string; retryable: true }
  | { kind: "unknown"; message: string };
```

- [ ] **Step 2: 写业务边界失败测试**

```ts
test("学习完成不改变任一 skill 的掌握度", () => {
  const store = createStore();
  const before = store.read().cards.byWordKey.sustainable.progress;
  store.update((draft) => {
    draft.study.sessions.demo.status = "completed";
  });
  expect(store.read().cards.byWordKey.sustainable.progress).toEqual(before);
});

test("测验结果只更新指定 skill", () => {
  const store = createStore();
  const beforeChoice = store.read().cards.byWordKey.sustainable.progress.choice;
  store.applyQuizResult(FIXED_DICTATION_RESULT);
  expect(store.read().cards.byWordKey.sustainable.progress.choice).toEqual(beforeChoice);
  expect(store.read().cards.byWordKey.sustainable.progress.dictation.stability).toBe(30);
});

test("重置后恢复固定日期和标准统计", () => {
  const store = createStore();
  store.update((draft) => {
    draft.today.masteredCount = 999;
  });
  store.reset();
  expect(store.read().clock.today).toBe("2026-07-23");
  expect(store.read().today.masteredCount).toBe(126);
});
```

- [ ] **Step 3: 运行测试并确认失败**

Run: `npm test -- src/data/mock/DemoStateStore.test.ts`

Expected: FAIL because store and fixtures do not exist.

- [ ] **Step 4: 实现固定种子和版本化存储**

Use storage key `wordflip.web.demo.v1`. `DemoStateStore.read()` must validate `schemaVersion === 1`; otherwise call `reset()`. `applyQuizResult` accepts a precomputed result:

```ts
export type DemoScenario =
  | "logged-out"
  | "configured"
  | "empty-today"
  | "empty-books"
  | "quiz-complete"
  | "quiz-dictation"
  | "quiz-choice"
  | "after-quiz"
  | "mutated";

export interface PrecomputedQuizResult {
  wordKey: string;
  skill: QuizSkill;
  next: SkillProgress;
  dashboardSnapshot: TodaySummary;
  statsSnapshot: StatsSummary;
}
```

The method copies `next` and snapshots into state. It must not calculate correctness, intervals, stability, heat level, or dashboard counts.

- [ ] **Step 5: 定义 RepositoryBundle**

`RepositoryBundle` exposes exact members:

```ts
export interface RepositoryBundle {
  auth: AuthRepository;
  settings: SettingsRepository;
  books: BookRepository;
  groups: GroupRepository;
  today: TodayRepository;
  study: StudyRepository;
  quiz: QuizRepository;
  media: MediaRepository;
  stats: StatsRepository;
}
```

Each method returns a `Promise` and either resolves a domain model or rejects with `AppError`.

- [ ] **Step 6: 扩展统一测试渲染工具**

`src/test/renderApp.tsx` must define the handle used by all later tasks:

```ts
export interface TestAppHandle extends RenderResult {
  store: DemoStateStore;
  router: ReturnType<typeof createMemoryRouter>;
  navigate: (to: string) => Promise<void>;
}

export function renderAuthenticatedApp(route: string): TestAppHandle {
  return renderTestApp({ route, scenario: "configured", authenticated: true });
}

export function renderScenarioApp(scenario: DemoScenario, route: string): TestAppHandle {
  return renderTestApp({ route, scenario, authenticated: scenario !== "logged-out" });
}
```

`renderTestApp` creates one in-memory `DemoStateStore`, one mock `RepositoryBundle`, and one memory router, then returns Testing Library results plus the same store and router. `navigate(to)` calls `router.navigate(to)` inside Testing Library `act()`.

- [ ] **Step 7: 验证模拟状态**

Run:

```bash
npm test -- src/data/mock/DemoStateStore.test.ts
npm run lint
npm run build
```

Expected: all exit 0; the three boundary tests pass.

- [ ] **Step 8: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src/domain wordflip-web/src/data wordflip-web/src/test/renderApp.tsx
git commit -m "feat(web): 建立可切换数据契约与模拟状态"
```

---

### Task 4: 登录、注册、路由守卫与首次设置

**Files:**
- Create: `wordflip-web/src/data/mock/repositories/MockAuthRepository.ts`
- Create: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts`
- Create: `wordflip-web/src/features/auth/LoginPage.tsx`
- Create: `wordflip-web/src/features/auth/RegisterPage.tsx`
- Create: `wordflip-web/src/features/auth/auth.module.css`
- Create: `wordflip-web/src/features/auth/AuthFlow.test.tsx`
- Create: `wordflip-web/src/features/onboarding/OnboardingPage.tsx`
- Create: `wordflip-web/src/features/onboarding/OnboardingPage.test.tsx`
- Create: `wordflip-web/src/features/onboarding/onboarding.module.css`
- Create: `wordflip-web/src/app/routeGuards.tsx`
- Modify: `wordflip-web/src/app/router.tsx`

**Interfaces:**
- Consumes: `AuthRepository`, `SettingsRepository`, `DemoStateStore`
- Produces: `RequireAuth`, `RequireOnboarding`
- Produces: routes `/login`, `/register`, `/onboarding`

- [ ] **Step 1: 写登录失败与成功导航测试**

```tsx
test("错误密码保留输入并显示不泄露账号状态的提示", async () => {
  renderApp("/login");
  await userEvent.type(screen.getByLabelText("邮箱"), "demo@wordflip.local");
  await userEvent.type(screen.getByLabelText("密码"), "wrong");
  await userEvent.click(screen.getByRole("button", { name: "登录" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("邮箱或密码不正确");
  expect(screen.getByLabelText("邮箱")).toHaveValue("demo@wordflip.local");
});

test("演示账号登录后进入首次设置", async () => {
  renderApp("/login");
  await userEvent.type(screen.getByLabelText("邮箱"), "demo@wordflip.local");
  await userEvent.type(screen.getByLabelText("密码"), "wordflip-demo");
  await userEvent.click(screen.getByRole("button", { name: "登录" }));
  expect(await screen.findByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
});

test("注册后进入首次设置且不暴露生产认证行为", async () => {
  renderApp("/register");
  await userEvent.type(screen.getByLabelText("昵称"), "林默");
  await userEvent.type(screen.getByLabelText("邮箱"), "linmo@example.test");
  await userEvent.type(screen.getByLabelText("密码"), "wordflip-demo");
  await userEvent.click(screen.getByRole("button", { name: "创建演示账户" }));
  expect(await screen.findByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/features/auth/AuthFlow.test.tsx`

Expected: FAIL because auth pages and repositories do not exist.

- [ ] **Step 3: 实现认证仓储和路由守卫**

The demo account is `demo@wordflip.local` / `wordflip-demo`. Any other login credential rejects:

```ts
throw {
  kind: "validation",
  message: "邮箱或密码不正确",
  fieldErrors: {}
} satisfies AppError;
```

`RequireAuth` redirects unauthenticated users to `/login`. `RequireOnboarding` redirects authenticated users without a plan to `/onboarding`.

`MockAuthRepository.register()` accepts a non-empty nickname, an email-shaped string, and a password of at least 8 characters, then writes a local demo user with `onboardingCompleted: false`. It does not hash, upload, or claim to create a production account.

- [ ] **Step 4: 写首次设置与增量分组失败测试**

```tsx
test("首次设置保存词书、词典和分组大小", async () => {
  renderAuthenticatedApp("/onboarding");
  await userEvent.click(screen.getByLabelText("雅思核心词汇"));
  await userEvent.selectOptions(screen.getByLabelText("默认词典"), "wordflip_curated");
  await userEvent.clear(screen.getByLabelText("每组单词数"));
  await userEvent.type(screen.getByLabelText("每组单词数"), "25");
  await userEvent.click(screen.getByRole("button", { name: "完成设置" }));
  expect(await screen.findByRole("heading", { name: /今天继续前进/ })).toBeVisible();
});
```

- [ ] **Step 5: 实现首次设置**

`MockSettingsRepository.saveOnboarding()` copies a precomputed `afterOnboarding` snapshot into the store. The repository may append fixture groups but must not rebuild or delete existing groups.

- [ ] **Step 6: 验证账户与首次设置流程**

Run:

```bash
npm test -- src/features/auth src/features/onboarding
npm run lint
npm run build
```

Expected: all exit 0.

- [ ] **Step 7: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src/features/auth wordflip-web/src/features/onboarding wordflip-web/src/app wordflip-web/src/data/mock/repositories
git commit -m "feat(web): 完成演示登录与首次设置"
```

---

### Task 5: 今日、词书与分组页面

**Files:**
- Create: `wordflip-web/src/data/mock/repositories/MockTodayRepository.ts`
- Create: `wordflip-web/src/data/mock/repositories/MockBookRepository.ts`
- Create: `wordflip-web/src/data/mock/repositories/MockGroupRepository.ts`
- Create: `wordflip-web/src/features/today/TodayPage.tsx`
- Create: `wordflip-web/src/features/today/TodayPage.module.css`
- Create: `wordflip-web/src/features/today/TodayPage.test.tsx`
- Create: `wordflip-web/src/features/books/BooksPage.tsx`
- Create: `wordflip-web/src/features/books/BookDetailPage.tsx`
- Create: `wordflip-web/src/features/books/books.module.css`
- Create: `wordflip-web/src/features/books/BooksFlow.test.tsx`
- Create: `wordflip-web/src/features/groups/GroupsPage.tsx`
- Create: `wordflip-web/src/features/groups/GroupDetailPage.tsx`
- Create: `wordflip-web/src/features/groups/groups.module.css`
- Create: `wordflip-web/src/features/groups/GroupsFlow.test.tsx`
- Modify: `wordflip-web/src/app/router.tsx`

**Interfaces:**
- Consumes: `TodayRepository.getSummary()`
- Consumes: `BookRepository.list()`, `getDetail(bookId)`
- Consumes: `GroupRepository.list()`, `getDetail(groupId)`
- Produces: `/today`, `/books`, `/books/:bookId`, `/groups`, `/groups/:groupId`

- [ ] **Step 1: 写今日标准数据和空状态测试**

```tsx
test("今日页显示固定种子摘要", async () => {
  renderAuthenticatedApp("/today");
  expect(await screen.findByText("126")).toBeVisible();
  expect(screen.getByText("18")).toBeVisible();
  expect(screen.getByText("72%")).toBeVisible();
  expect(screen.getByText("雅思核心词汇")).toBeVisible();
});

test("无任务场景显示完成反馈", async () => {
  renderScenarioApp("empty-today", "/today");
  expect(await screen.findByRole("heading", { name: "今天的任务已完成" })).toBeVisible();
  expect(screen.getByRole("link", { name: "浏览词书" })).toHaveAttribute("href", "/books");
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/features/today/TodayPage.test.tsx`

Expected: FAIL because page and repository do not exist.

- [ ] **Step 3: 实现今日页面**

Render four summary cells, recent study panel, task list, and one primary action. Use `StatusTag` for `MOCK DATA · READY`; use `Panel` rather than redefining card chrome.

- [ ] **Step 4: 写词书和分组业务测试**

```tsx
test("取消词书后已有分组仍保留", async () => {
  const app = renderScenarioApp("configured", "/books");
  await userEvent.click(await screen.findByLabelText("取消雅思核心词汇"));
  await app.navigate("/groups");
  expect(await screen.findByText("第 12 组 · 城市与环境")).toBeVisible();
});

test("组详情以 heatLevel 为主展示", async () => {
  renderAuthenticatedApp("/groups/group-12");
  const row = await screen.findByRole("row", { name: /sustainable/ });
  expect(within(row).getByText("热力 2")).toBeVisible();
});
```

- [ ] **Step 5: 实现词书与分组页面**

Book selection writes a precomputed post-save snapshot. Group rows display `heatLevel`, not a user-editable mastery control. No page renders “记得/模糊” buttons that directly mutate progress.

- [ ] **Step 6: 验证三类管理页面**

Run:

```bash
npm test -- src/features/today src/features/books src/features/groups
npm run lint
npm run build
```

Expected: all exit 0.

- [ ] **Step 7: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src/features/today wordflip-web/src/features/books wordflip-web/src/features/groups wordflip-web/src/data/mock/repositories wordflip-web/src/app/router.tsx
git commit -m "feat(web): 完成今日词书与分组演示"
```

---

### Task 6: 翻卡学习工作台

**Files:**
- Create: `wordflip-web/src/data/mock/repositories/MockStudyRepository.ts`
- Create: `wordflip-web/src/features/study/StudyPage.tsx`
- Create: `wordflip-web/src/features/study/StudyCard.tsx`
- Create: `wordflip-web/src/features/study/StudySidebar.tsx`
- Create: `wordflip-web/src/features/study/StudyCompletePage.tsx`
- Create: `wordflip-web/src/features/study/study.module.css`
- Create: `wordflip-web/src/features/study/StudyFlow.test.tsx`
- Modify: `wordflip-web/src/app/router.tsx`

**Interfaces:**
- Consumes: `StudyRepository.getSession(sessionId)`
- Consumes: `StudyRepository.completeSession(sessionId)`
- Produces: `/study/:sessionId`, `/study/:sessionId/complete`
- Produces: `StudyCard({ card, isFlipped, onFlip })`

- [ ] **Step 1: 写翻卡键盘和业务边界测试**

```tsx
test("空格翻面且方向键切换单词", async () => {
  renderAuthenticatedApp("/study/study-demo");
  expect(await screen.findByRole("heading", { name: "sustainable" })).toBeVisible();
  await userEvent.keyboard(" ");
  expect(screen.getByText("可持续的")).toBeVisible();
  await userEvent.keyboard("{ArrowRight}");
  expect(await screen.findByRole("heading", { name: "infrastructure" })).toBeVisible();
});

test("完成学习不改变双轨掌握度", async () => {
  const { store } = renderAuthenticatedApp("/study/study-demo");
  const before = structuredClone(store.read().cards.byWordKey.sustainable.progress);
  await userEvent.click(await screen.findByRole("button", { name: "完成学习" }));
  expect(store.read().cards.byWordKey.sustainable.progress).toEqual(before);
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/features/study/StudyFlow.test.tsx`

Expected: FAIL because study workspace does not exist.

- [ ] **Step 3: 实现学习 Repository**

`completeSession()` copies a fixed `afterStudySession` dashboard snapshot and session status. It may update viewed-word counts; it must not write `SkillProgress`.

- [ ] **Step 4: 实现专注工作台与翻卡**

Use `FocusShell`. The center card renders word, phonetic, definition, example, image, and heat label. The right sidebar renders `18 / 25 WORDS`, queue summary, and keyboard help.

The flip animation uses `--wf-motion-flip`. Under reduced motion, switch content with opacity only.

- [ ] **Step 5: 验证学习工作台**

Run:

```bash
npm test -- src/features/study/StudyFlow.test.tsx
npm run lint
npm run build
```

Expected: all exit 0.

- [ ] **Step 6: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src/features/study wordflip-web/src/data/mock/repositories/MockStudyRepository.ts wordflip-web/src/app/router.tsx
git commit -m "feat(web): 完成翻卡学习工作台"
```

---

### Task 7: 听写与选择测验工作台

**Files:**
- Create: `wordflip-web/src/data/mock/repositories/MockQuizRepository.ts`
- Create: `wordflip-web/src/features/quiz/QuizSetupPage.tsx`
- Create: `wordflip-web/src/features/quiz/QuizPage.tsx`
- Create: `wordflip-web/src/features/quiz/DictationQuestion.tsx`
- Create: `wordflip-web/src/features/quiz/ChoiceQuestion.tsx`
- Create: `wordflip-web/src/features/quiz/QuizResultPage.tsx`
- Create: `wordflip-web/src/features/quiz/quiz.module.css`
- Create: `wordflip-web/src/features/quiz/QuizFlow.test.tsx`
- Modify: `wordflip-web/src/app/router.tsx`

**Interfaces:**
- Consumes: `QuizRepository.createSession(skill, scope)`
- Consumes: `QuizRepository.getSession(sessionId)`
- Consumes: `QuizRepository.submitAnswer(sessionId, questionId, answer)`
- Consumes: `QuizRepository.getResult(sessionId)`
- Produces: `/quiz`, `/quiz/:sessionId`, `/quiz/:sessionId/result`

- [ ] **Step 1: 写双轨与唯一写入口测试**

```tsx
test("听写提交只更新 dictation", async () => {
  const { store } = renderScenarioApp("quiz-dictation", "/quiz/quiz-dictation-1");
  const choiceBefore = structuredClone(store.read().cards.byWordKey.sustainable.progress.choice);
  await userEvent.type(await screen.findByLabelText("输入英文单词"), "sustainable");
  await userEvent.keyboard("{Enter}");
  expect(store.read().cards.byWordKey.sustainable.progress.choice).toEqual(choiceBefore);
  expect(store.read().cards.byWordKey.sustainable.progress.dictation.stability).toBe(30);
});

test("选择题提交不覆盖 dictation", async () => {
  const { store } = renderScenarioApp("quiz-choice", "/quiz/quiz-choice-1");
  const dictationBefore = structuredClone(store.read().cards.byWordKey.sustainable.progress.dictation);
  await userEvent.click(await screen.findByRole("radio", { name: "可持续的" }));
  await userEvent.click(screen.getByRole("button", { name: "提交答案" }));
  expect(store.read().cards.byWordKey.sustainable.progress.dictation).toEqual(dictationBefore);
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/features/quiz/QuizFlow.test.tsx`

Expected: FAIL because quiz pages and repository do not exist.

- [ ] **Step 3: 实现测验 Repository**

`submitAnswer()` looks up a fixture by `sessionId`, `questionId`, and normalized answer. The fixture contains a precomputed `PrecomputedQuizResult`; the repository passes it to `DemoStateStore.applyQuizResult()`. No client scoring or interval computation is allowed.

- [ ] **Step 4: 实现两类题目和结果页**

Use `FocusShell`. Dictation has a labeled text field and Enter submission. Choice uses a keyboard-reachable radio group. Result page displays separate dictation and choice summaries and the copy “本次变化来自测验结果；翻卡学习不会改变掌握度。”

- [ ] **Step 5: 验证测验工作台**

Run:

```bash
npm test -- src/features/quiz/QuizFlow.test.tsx
npm run lint
npm run build
```

Expected: all exit 0.

- [ ] **Step 6: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src/features/quiz wordflip-web/src/data/mock/repositories/MockQuizRepository.ts wordflip-web/src/app/router.tsx
git commit -m "feat(web): 完成双轨测验与结果演示"
```

---

### Task 8: 媒体、统计与设置

**Files:**
- Create: `wordflip-web/src/data/mock/repositories/MockMediaRepository.ts`
- Create: `wordflip-web/src/data/mock/repositories/MockStatsRepository.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts`
- Create: `wordflip-web/src/features/media/MediaPage.tsx`
- Create: `wordflip-web/src/features/media/ImageEditor.tsx`
- Create: `wordflip-web/src/features/media/media.module.css`
- Create: `wordflip-web/src/features/media/MediaPage.test.tsx`
- Create: `wordflip-web/src/features/stats/StatsPage.tsx`
- Create: `wordflip-web/src/features/stats/Heatmap.tsx`
- Create: `wordflip-web/src/features/stats/stats.module.css`
- Create: `wordflip-web/src/features/stats/StatsPage.test.tsx`
- Create: `wordflip-web/src/features/settings/SettingsPage.tsx`
- Create: `wordflip-web/src/features/settings/ResetDemoDialog.tsx`
- Create: `wordflip-web/src/features/settings/settings.module.css`
- Create: `wordflip-web/src/features/settings/SettingsPage.test.tsx`
- Create: `wordflip-web/public/card-images/sustainable.webp`
- Create: `wordflip-web/public/card-images/infrastructure.webp`
- Create: `wordflip-web/public/card-images/custom-placeholder.webp`
- Modify: `wordflip-web/src/app/router.tsx`

**Interfaces:**
- Consumes: `MediaRepository.getImage()`, `saveTransform()`, `clearImage()`
- Consumes: `StatsRepository.getSummary()`, `getHeatmap()`
- Consumes: `SettingsRepository.get()`, `save()`, `resetDemo()`
- Produces: `/media`, `/stats`, `/settings`

- [ ] **Step 1: 写媒体、统计和重置失败测试**

```tsx
test("图片变换保存后刷新仍存在", async () => {
  const app = renderAuthenticatedApp("/media");
  await userEvent.click(await screen.findByRole("button", { name: "向右旋转" }));
  await userEvent.click(screen.getByRole("button", { name: "保存图片位置" }));
  app.unmount();
  renderAuthenticatedApp("/media");
  expect(await screen.findByText("旋转 90°")).toBeVisible();
});

test("统计读取测验后的同一份状态", async () => {
  const { store } = renderScenarioApp("after-quiz", "/stats");
  expect(await screen.findByText(String(store.read().stats.masteredCount))).toBeVisible();
});

test("重置演示数据恢复 126 个已掌握单词", async () => {
  renderScenarioApp("mutated", "/settings");
  await userEvent.click(await screen.findByRole("button", { name: "重置演示数据" }));
  await userEvent.click(screen.getByRole("button", { name: "确认重置" }));
  expect(await screen.findByText("126")).toBeVisible();
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- src/features/media src/features/stats src/features/settings`

Expected: FAIL because the pages do not exist.

- [ ] **Step 3: 实现媒体页面**

Accept only JPEG, PNG, and WebP in the mock picker and reject files over 5MB with a field-level error. The selected file uses an object URL only for the current preview. Saving maps it to the fixed demo asset `/card-images/custom-placeholder.webp` and persists the filename plus transform metadata; no file bytes or object URL are written to `localStorage`, and the mock never uploads to MinIO.

- [ ] **Step 4: 实现统计页面**

Render four summary values, a 12-month heatmap, achievements, and separate dictation/choice progress. Heat cells include text or accessible labels; color is not the only information channel.

- [ ] **Step 5: 实现设置和重置**

Settings save a precomputed post-save snapshot. The reset dialog traps focus, requires explicit confirmation, calls `DemoStateStore.reset()`, clears Query caches, and navigates to `/today`.

- [ ] **Step 6: 验证三类页面**

Run:

```bash
npm test -- src/features/media src/features/stats src/features/settings
npm run lint
npm run build
```

Expected: all exit 0.

- [ ] **Step 7: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src/features/media wordflip-web/src/features/stats wordflip-web/src/features/settings wordflip-web/src/data/mock/repositories wordflip-web/public
git commit -m "feat(web): 完成媒体统计与设置演示"
```

---

### Task 9: 代表性状态、无障碍、响应式与端到端验收

**Files:**
- Create: `wordflip-web/src/components/FormField/FormField.tsx`
- Create: `wordflip-web/src/components/Toast/ToastProvider.tsx`
- Create: `wordflip-web/src/test/testIds.ts`
- Modify: `wordflip-web/src/components/AsyncState/AsyncState.tsx`
- Modify: `wordflip-web/src/design-system/global.css`
- Modify: `wordflip-web/src/design-system/motion.css`
- Modify: all feature page CSS modules as required
- Create: `wordflip-web/e2e/happy-path.spec.ts`
- Create: `wordflip-web/e2e/representative-states.spec.ts`
- Create: `wordflip-web/e2e/responsive.spec.ts`
- Create: `wordflip-web/e2e/visual-regression.spec.ts`

**Interfaces:**
- Consumes: all routes and scenario fixtures
- Produces: stable `data-testid="app-shell"` and `data-testid="page-content"` only where role queries cannot express layout checks
- Produces: Playwright acceptance suite

- [ ] **Step 1: 写完整主流程 E2E**

```ts
test("登录到统计变化的完整演示流程", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("邮箱").fill("demo@wordflip.local");
  await page.getByLabel("密码").fill("wordflip-demo");
  await page.getByRole("button", { name: "登录" }).click();
  await page.getByLabel("雅思核心词汇").check();
  await page.getByRole("button", { name: "完成设置" }).click();
  await page.getByRole("button", { name: "开始今日学习" }).click();
  await page.keyboard.press("Space");
  await page.keyboard.press("ArrowRight");
  await page.getByRole("button", { name: "完成学习" }).click();
  await page.goto("/quiz");
  await page.getByRole("button", { name: "开始听写测验" }).click();
  await page.getByLabel("输入英文单词").fill("sustainable");
  await page.keyboard.press("Enter");
  await page.getByRole("link", { name: "查看统计" }).click();
  await expect(page.getByText("听写进度")).toBeVisible();
  await page.reload();
  await expect(page.getByText("听写进度")).toBeVisible();
});
```

- [ ] **Step 2: 写代表性状态 E2E**

Use the development-only scenario control to visit login failure, empty today, empty books, and quiz result. Assert each screen has a heading, a recovery action, and no uncaught page error.

```ts
for (const scenario of ["empty-today", "empty-books", "quiz-complete"]) {
  test(`${scenario} 有明确恢复动作`, async ({ page }) => {
    await page.goto(`/__demo/scenario/${scenario}`);
    await expect(page.getByRole("main")).toBeVisible();
    await expect(page.getByRole("link").or(page.getByRole("button")).first()).toBeVisible();
  });
}
```

The internal `/__demo/scenario/:name` route is compiled only when `import.meta.env.DEV` is true.

- [ ] **Step 3: 写响应式 E2E**

```ts
for (const width of [1440, 1280, 1024, 768]) {
  test(`今日页在 ${width}px 无页面级横向滚动`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/__demo/scenario/configured?next=/today");
    const sizes = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth
    }));
    expect(sizes.scroll).toBeLessThanOrEqual(sizes.viewport);
  });
}
```

- [ ] **Step 4: 写视觉回归 E2E**

Add focused screenshot coverage:

```ts
for (const entry of [
  { name: "today", path: "/__demo/scenario/configured?next=/today" },
  { name: "study", path: "/__demo/scenario/configured?next=/study/study-demo" },
  { name: "quiz", path: "/__demo/scenario/quiz-dictation?next=/quiz/quiz-dictation-1" },
  { name: "empty-today", path: "/__demo/scenario/empty-today?next=/today" }
]) {
  test(`${entry.name} 视觉基线`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(entry.path);
    await expect(page.getByTestId("page-content")).toHaveScreenshot(`${entry.name}-1440.png`, {
      animations: "disabled"
    });
  });
}
```

Generate the initial reviewed baselines with `npm run test:e2e:update`; subsequent runs use `npm run test:e2e`.

- [ ] **Step 5: 运行 E2E 并确认失败**

Run: `npm run test:e2e`

Expected: FAIL until scenario route, focus management, and responsive fixes are implemented.

- [ ] **Step 6: 完成状态、焦点和响应式修正**

Requirements:

- Loading uses skeletons with stable dimensions.
- Field errors retain input and connect via `aria-describedby`.
- Retryable errors render a named retry button.
- Dialogs trap and restore focus.
- `Esc` exits focus workspaces after confirmation when progress would be lost.
- `prefers-reduced-motion` removes transform motion.
- AppShell collapses below 1024px.
- FocusShell stacks its aside below content below 900px.
- No page-level horizontal scroll at 768px and above.

- [ ] **Step 7: 运行完整验证**

Run:

```bash
npm test
npm run lint
npm run build
npm run test:e2e
```

Expected: all commands exit 0; unit/component tests report 0 failures; Playwright reports 0 failures across all four spec files.

- [ ] **Step 8: 提交检查点（需用户授权）**

```bash
git add wordflip-web/src wordflip-web/e2e
git commit -m "test(web): 完成状态响应式与端到端验收"
```

---

### Task 10: 文档同步与交付检查

**Files:**
- Modify: `TASK.md`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `STRUCTURE.md`
- Modify: `docs/wordflip/architecture.md`
- Modify: `docs/wordflip/design-system/MASTER.md`
- Modify: `wordflip-web/README.md`
- Modify: `docs/superpowers/specs/2026-07-23-wordflip-web-visual-demo-design.md` only if implementation reveals an approved clarification

**Interfaces:**
- Consumes: verified scripts and final directory structure from Tasks 1–9
- Produces: authoritative Web setup and phase documentation

- [ ] **Step 1: 更新任务主线**

Update `TASK.md` so the active phase is Web visual demo. Add checkboxes for Tasks 1–9 and mark only verified work complete. Keep Android tasks present but explicitly paused.

- [ ] **Step 2: 更新仓库入口与 Agent 指令**

`README.md` and `AGENTS.md` must state:

- Web is current priority.
- Android is paused, not deleted.
- Web first uses mock repositories.
- Business truth remains on the Spring Boot server.
- Web commands are `npm install`, `npm run dev`, `npm test`, `npm run build`, and `npm run test:e2e`.

- [ ] **Step 3: 更新架构和设计系统**

`docs/wordflip/architecture.md` must replace “Web 二期” with the approved phased data-source strategy. `docs/wordflip/design-system/MASTER.md` must add the Web-only warm paper and terracotta extension without changing Android token behavior.

- [ ] **Step 4: 更新 Web README 和结构清单**

Document:

````markdown
## 数据模式

- `MockRepository`：默认视觉演示模式，固定种子并持久化到本地存储。
- `HttpRepository`：后续按功能模块接入；页面和路由不重写。

## 验证

```bash
npm test
npm run lint
npm run build
npm run test:e2e
```
````

Update `STRUCTURE.md` with the actual `wordflip-web` directories.

- [ ] **Step 5: 运行最终验证和变更检查**

Run:

```bash
cd wordflip-web
npm test
npm run lint
npm run build
npm run test:e2e
cd ..
git diff --check
git status --short
```

Expected:

- All four Web validation commands exit 0.
- `git diff --check` exits 0 with no output.
- `git status --short` contains only planned Web and documentation files.
- No `.env`, credential, keystore, JWT key, generated report, or Playwright trace is staged.

- [ ] **Step 6: 提交最终文档检查点（需用户授权）**

```bash
git add TASK.md README.md AGENTS.md STRUCTURE.md docs/wordflip/architecture.md docs/wordflip/design-system/MASTER.md wordflip-web/README.md docs/superpowers
git commit -m "docs(web): 切换视觉演示版实施主线"
```

Do not push until the user explicitly requests it and the complete validation output has been reviewed.

---

## Execution Order and Review Gates

| Wave | Tasks | Independent deliverable |
|---|---|---|
| 1 | 1–3 | 可构建的应用外壳、设计系统和经过测试的模拟数据边界 |
| 2 | 4–5 | 可从登录走到今日、词书和分组的管理流程 |
| 3 | 6–7 | 不违反业务规则的学习与双轨测验工作台 |
| 4 | 8–9 | 媒体、统计、设置、代表性状态和完整 E2E |
| 5 | 10 | 文档、命令和交付状态与实际实现一致 |

At every wave gate:

1. Run the exact test commands in the completed tasks.
2. Inspect `git diff --check` and `git status --short`.
3. Review behavior against the design spec.
4. Obtain user authorization before any commit or push.
