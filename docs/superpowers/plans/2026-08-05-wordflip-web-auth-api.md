# WordFlip Web Auth API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `WEB-API01`，让 Web 可按环境变量把认证模块切到真实 Spring Boot API，并正确处理会话恢复、令牌轮换、401 重试和登出。

**Architecture:** 页面继续只依赖 `AuthRepository`。HTTP 目录拆分为认证 DTO、版本化令牌存储、单飞刷新会话管理、Axios 客户端和 `HttpAuthRepository`；运行时工厂在完整 Mock Bundle 上仅替换认证模块。Spring Boot dev CORS 精确允许 Web 的 `127.0.0.1:5273` Origin。

**Tech Stack:** React 18、TypeScript 5.7、Axios 1.7、Vitest 2、Testing Library、Vite 6、Spring Boot 3.3、JUnit 5、AssertJ。

## Global Constraints

- OpenAPI `wordflip-api/openapi.yaml` 是认证请求和响应字段的唯一来源。
- 默认 `VITE_DATA_SOURCE=mock`；只有显式配置 `http` 才启用真实认证。
- `VITE_DATA_SOURCE=http` 只替换 `AuthRepository`，其它 Repository 继续使用 Mock。
- Access Token 只发送到 `VITE_API_BASE_URL` 创建的 Axios 实例。
- 每个 401 原请求最多重放一次；并发 401 只触发一次 Refresh Token 轮换。
- 注册、登录和刷新请求不进入自动 401 重试链。
- 登出无论远端结果如何都清理本地会话。
- 不修改 OpenAPI，不在浏览器实现 FSRS、判题、分组、今日任务或统计计算。
- 新增和修改的业务注释使用简体中文。
- 未经用户明确要求，不执行本计划中的 Git commit 或 push 命令。

---

## File Map

- `wordflip-web/src/domain/auth.ts`：页面可见的认证输入和会话契约。
- `wordflip-web/src/data/http/auth/authDtos.ts`：OpenAPI 认证 DTO，仅限 HTTP 层使用。
- `wordflip-web/src/data/http/auth/TokenStore.ts`：版本化 `localStorage` 令牌记录校验与读写。
- `wordflip-web/src/data/http/auth/AuthSessionManager.ts`：令牌落库、到期判断、单飞刷新和会话映射。
- `wordflip-web/src/data/http/errors/mapHttpError.ts`：Axios/后端错误到 `AppError` 的统一转换。
- `wordflip-web/src/data/http/createHttpClient.ts`：Bearer 注入、401 刷新和单次重放。
- `wordflip-web/src/data/http/auth/HttpAuthRepository.ts`：注册、登录、恢复会话和登出。
- `wordflip-web/src/data/runtime/createRepositoryBundle.ts`：Mock/HTTP 混合装配。
- `wordflip-web/src/main.tsx`：读取 Vite 环境变量并使用运行时工厂。
- `wordflip-server/src/main/resources/application-dev.yml`：本地 Web Origin 白名单。
- `wordflip-server/src/test/java/com/wordflip/config/DevCorsConfigurationTest.java`：运行真实 YAML 绑定和 CORS 配置。

---

### Task 1: 对齐认证领域模型、Mock 与页面表单

**Files:**
- Modify: `wordflip-web/src/domain/auth.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockAuthRepository.ts`
- Modify: `wordflip-web/src/features/auth/LoginPage.tsx`
- Modify: `wordflip-web/src/features/auth/RegisterPage.tsx`
- Modify: `wordflip-web/src/features/auth/AuthFlow.test.tsx`
- Modify: `wordflip-web/e2e/happy-path.spec.ts`

**Interfaces:**
- Produces: `SignInInput { account: string; password: string }`
- Produces: `RegisterInput { account: string; password: string }`
- Preserves: `AuthSession { userId: string; displayName: string; authenticated: boolean }`

- [ ] **Step 1: 写页面契约失败测试**

把 `AuthFlow.test.tsx` 的登录输入改为 `getByLabelText("邮箱或手机号")`，并把注册测试改成只填写账号和密码：

```tsx
test("注册按真实契约只提交账号与密码并进入首次设置", async () => {
  const user = userEvent.setup();
  renderScenarioApp("logged-out", "/register");

  expect(screen.queryByLabelText("昵称")).not.toBeInTheDocument();
  await user.type(screen.getByLabelText("邮箱或手机号"), "linmo@example.test");
  await user.type(screen.getByLabelText("密码"), "wordflip-demo");
  await user.click(screen.getByRole("button", { name: "创建账户" }));

  expect(await screen.findByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
});
```

能捕获的生产回归：页面继续提交 OpenAPI 不接受的 `displayName/email` 形状，或仍展示演示专属文案。

- [ ] **Step 2: 运行测试确认 RED**

Run: `cd wordflip-web; npm test -- src/features/auth/AuthFlow.test.tsx`

Expected: FAIL，因为当前页面没有“邮箱或手机号”，注册仍要求昵称并显示“创建演示账户”。

- [ ] **Step 3: 最小修改领域模型、Mock 和页面**

将 `auth.ts` 输入改为：

```ts
export interface SignInInput {
  account: string;
  password: string;
}

export type RegisterInput = SignInInput;
```

`MockAuthRepository` 使用 `input.account.trim().toLowerCase()` 作为注册键；登录演示账号仍返回“演示用户”，新注册账号的展示标签使用规范化账号。登录页和注册页均显示“邮箱或手机号”，输入使用 `autoComplete="username"`，注册页移除昵称及所有“仅本地演示”文案。

- [ ] **Step 4: 运行单元测试确认 GREEN**

Run: `cd wordflip-web; npm test -- src/features/auth/AuthFlow.test.tsx src/data/mock/repositories/MockOnboardingRepositories.test.ts`

Expected: 两个测试文件全部 PASS。

- [ ] **Step 5: 同步 Mock E2E 选择器并验证**

把 `happy-path.spec.ts` 的 `page.getByLabel("邮箱")` 改为 `page.getByLabel("邮箱或手机号")`。

Run: `cd wordflip-web; npx playwright test e2e/happy-path.spec.ts`

Expected: 1 test PASS，证明默认 Mock 流程保持可用。

---

### Task 2: 实现版本化令牌存储和认证会话管理

**Files:**
- Create: `wordflip-web/src/data/http/auth/authDtos.ts`
- Create: `wordflip-web/src/data/http/auth/TokenStore.ts`
- Create: `wordflip-web/src/data/http/auth/TokenStore.test.ts`
- Create: `wordflip-web/src/data/http/auth/AuthSessionManager.ts`
- Create: `wordflip-web/src/data/http/auth/AuthSessionManager.test.ts`

**Interfaces:**
- Produces: `AuthResponseDto`, `StoredAuthRecord`, `TokenStore`
- Produces: `AuthSessionManager.startSession(response)`, `getSession()`, `getAccessToken()`, `refreshAccessToken()`, `clearSession()`
- Consumes: injected `refresh(refreshToken: string): Promise<AuthResponseDto>` and `now(): number`

- [ ] **Step 1: 写 TokenStore 失败测试**

测试使用 JSDOM 的真实 `window.localStorage`，覆盖完整记录读写、清理以及损坏/旧版本数据：

```ts
test("损坏或旧版本令牌记录按未登录处理并从存储移除", () => {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ schemaVersion: 0 }));
  const store = new TokenStore(window.localStorage);

  expect(store.read()).toBeNull();
  expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
});
```

能捕获的生产回归：不完整令牌被当作有效会话、旧 schema 未失效或清理没有发生。

- [ ] **Step 2: 运行 TokenStore 测试确认 RED**

Run: `cd wordflip-web; npm test -- src/data/http/auth/TokenStore.test.ts`

Expected: FAIL，因为模块不存在。

- [ ] **Step 3: 最小实现认证 DTO 与 TokenStore**

`authDtos.ts` 定义 OpenAPI 字段：

```ts
export interface AuthResponseDto {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: { id: number; email: string | null; phone: string | null };
}
```

`TokenStore.ts` 使用固定键 `wordflip:web:auth:v1` 和 `schemaVersion: 1`，只接受非空 token、正数 `accessExpiresAt`、有限数字 user id 以及 `string|null` 的 email/phone。JSON 解析或校验失败时删除该键并返回 `null`。

- [ ] **Step 4: 运行 TokenStore 测试确认 GREEN**

Run: `cd wordflip-web; npm test -- src/data/http/auth/TokenStore.test.ts`

Expected: 全部 PASS。

- [ ] **Step 5: 写 AuthSessionManager 失败测试**

使用手写字面量响应，覆盖未过期恢复、过期刷新、并发单飞和刷新失败清理：

```ts
test("并发刷新只轮换一次令牌并让所有调用获得新 access token", async () => {
  let refreshCalls = 0;
  const manager = createExpiredManager(async () => {
    refreshCalls += 1;
    return refreshedResponse;
  });

  await expect(Promise.all([
    manager.refreshAccessToken(),
    manager.refreshAccessToken()
  ])).resolves.toEqual(["access-new", "access-new"]);
  expect(refreshCalls).toBe(1);
});
```

能捕获的生产回归：并发请求重复使用同一个已吊销 Refresh Token，或刷新失败后残留会话。

- [ ] **Step 6: 运行会话管理测试确认 RED**

Run: `cd wordflip-web; npm test -- src/data/http/auth/AuthSessionManager.test.ts`

Expected: FAIL，因为 `AuthSessionManager` 不存在。

- [ ] **Step 7: 最小实现 AuthSessionManager**

`startSession()` 以 `now() + expiresIn * 1000` 生成到期时间并原子写入完整记录；展示标签按 `email ?? phone ?? \`用户 ${id}\`` 映射。`refreshAccessToken()` 维护一个 `refreshPromise`，在 `finally` 中重置；缺失 Refresh Token 或刷新失败均清空存储。`getSession()` 在到期前返回本地会话，到期后尝试刷新，失败返回 `null`。

- [ ] **Step 8: 运行会话管理与存储测试确认 GREEN**

Run: `cd wordflip-web; npm test -- src/data/http/auth/TokenStore.test.ts src/data/http/auth/AuthSessionManager.test.ts`

Expected: 两个测试文件全部 PASS。

---

### Task 3: 实现 HTTP 错误映射和带刷新能力的 Axios 客户端

**Files:**
- Create: `wordflip-web/src/data/http/errors/mapHttpError.ts`
- Create: `wordflip-web/src/data/http/errors/mapHttpError.test.ts`
- Create: `wordflip-web/src/data/http/createHttpClient.ts`
- Create: `wordflip-web/src/data/http/createHttpClient.test.ts`

**Interfaces:**
- Produces: `mapHttpError(error: unknown): AppError`
- Produces: `createPublicHttpClient(baseURL: string): AxiosInstance`
- Produces: `createAuthenticatedHttpClient({ baseURL, sessions }): AxiosInstance`
- Consumes: `sessions.getAccessToken()`, `sessions.refreshAccessToken()`, `sessions.clearSession()`

- [ ] **Step 1: 写错误映射失败测试**

用 `AxiosError` 和手写 `AxiosResponse` 覆盖 400 details、401、404、409、500、无 response 网络错误和未知错误。关键断言：

```ts
expect(mapHttpError(validationAxiosError)).toEqual({
  kind: "validation",
  message: "Request validation failed",
  fieldErrors: { password: "密码至少 8 位" }
});
expect(mapHttpError(networkAxiosError)).toEqual({
  kind: "unavailable",
  message: "网络连接不可用，请稍后重试",
  retryable: true
});
```

能捕获的生产回归：页面收到 Axios 内部对象、后端字段错误丢失或 5xx 被错误归类为 validation。

- [ ] **Step 2: 运行错误映射测试确认 RED**

Run: `cd wordflip-web; npm test -- src/data/http/errors/mapHttpError.test.ts`

Expected: FAIL，因为模块不存在。

- [ ] **Step 3: 最小实现 mapHttpError**

先识别已经是 `AppError` 的对象并原样返回；再使用 `axios.isAxiosError` 读取 `response.status` 与符合 `{ code, message, details }` 的响应体。只把 `details` 中字符串值复制到 `fieldErrors`，不把任意对象泄漏给页面。

- [ ] **Step 4: 运行错误映射测试确认 GREEN**

Run: `cd wordflip-web; npm test -- src/data/http/errors/mapHttpError.test.ts`

Expected: 全部 PASS。

- [ ] **Step 5: 写 Axios 客户端失败测试**

给真实 Axios 实例设置自定义 `defaults.adapter`，不要断言 mock 是否存在；断言 adapter 实际观察到的请求行为：

```ts
test("401 后刷新一次并以新 Bearer token 重放原请求", async () => {
  const seenAuthorization: Array<string | undefined> = [];
  const client = createAuthenticatedHttpClient({ baseURL: "/api/v1", sessions });
  client.defaults.adapter = async (config) => {
    seenAuthorization.push(config.headers.get("Authorization")?.toString());
    if (seenAuthorization.length === 1) throw unauthorizedAxiosError(config);
    return okResponse(config, { value: "ok" });
  };

  await expect(client.get("/settings")).resolves.toMatchObject({ data: { value: "ok" } });
  expect(seenAuthorization).toEqual(["Bearer access-old", "Bearer access-new"]);
});
```

另测第二次 401 不再重放并清理会话，以及两个并发 401 共享 manager 的单飞刷新。

能捕获的生产回归：旧 token 被用于重放、无限 401 循环或 Authorization 未附加。

- [ ] **Step 6: 运行 Axios 客户端测试确认 RED**

Run: `cd wordflip-web; npm test -- src/data/http/createHttpClient.test.ts`

Expected: FAIL，因为客户端工厂不存在。

- [ ] **Step 7: 最小实现 Axios 客户端**

请求拦截器读取最新 access token 并设置 `Authorization: Bearer <token>`。响应拦截器只处理 status 401 且自定义 `_wordflipRetried !== true` 的请求；先标记、再等待 `refreshAccessToken()`、更新请求头并重放。刷新或重放失败时清理会话，最后通过 `mapHttpError` 拒绝。公开客户端只做 baseURL、JSON headers 和错误映射，不安装刷新拦截器。

- [ ] **Step 8: 运行 HTTP 基础设施测试确认 GREEN**

Run: `cd wordflip-web; npm test -- src/data/http/errors/mapHttpError.test.ts src/data/http/createHttpClient.test.ts`

Expected: 两个测试文件全部 PASS。

---

### Task 4: 实现 HttpAuthRepository

**Files:**
- Create: `wordflip-web/src/data/http/auth/HttpAuthRepository.ts`
- Create: `wordflip-web/src/data/http/auth/HttpAuthRepository.test.ts`

**Interfaces:**
- Consumes: public/authenticated `AxiosInstance`, `AuthSessionManager`
- Produces: `HttpAuthRepository implements AuthRepository`
- Produces: `createHttpAuthRepository({ baseURL, storage }): HttpAuthRepository`

- [ ] **Step 1: 写认证 Repository 失败测试**

通过真实 Axios 自定义 adapter 记录请求，分别测试：

```ts
test("邮箱注册按 OpenAPI 发送 email 而不是 account 或 displayName", async () => {
  const request = await registerThroughRecordingAdapter("linmo@example.test");
  expect(JSON.parse(request.data as string)).toEqual({
    email: "linmo@example.test",
    password: "wordflip-demo"
  });
});

test("E.164 手机号注册按 OpenAPI 发送 phone", async () => {
  const request = await registerThroughRecordingAdapter("+8613800138000");
  expect(JSON.parse(request.data as string)).toEqual({
    phone: "+8613800138000",
    password: "wordflip-demo"
  });
});
```

同时覆盖登录发送 `{ account, password }`、成功响应保存令牌、`getSession()` 委托会话管理，以及登出携带 refresh token 后即使网络失败也清理本地记录。

能捕获的生产回归：DTO 字段不符合 OpenAPI、手机号误发为 email、认证成功未建立会话、登出失败残留本地 token。

- [ ] **Step 2: 运行 Repository 测试确认 RED**

Run: `cd wordflip-web; npm test -- src/data/http/auth/HttpAuthRepository.test.ts`

Expected: FAIL，因为 Repository 不存在。

- [ ] **Step 3: 最小实现 HttpAuthRepository**

`signIn()` 调用 `POST /auth/login`；`register()` 用 `^\+[1-9]\d{1,14}$` 判断 phone，否则发送 email；二者成功后调用 `sessions.startSession(response.data)`。`signOut()` 读取当前 Refresh Token，用 authenticated client 调用 `POST /auth/logout`，并在 `finally` 清理会话；方法最终返回 `{ signedOut: true }`，使本地退出不被网络状态阻塞。

工厂创建 public client、TokenStore、AuthSessionManager 和 authenticated client。刷新闭包调用 public client 的 `/auth/refresh`，其成功 DTO 由 manager 原子落库。

- [ ] **Step 4: 运行 Repository 与 HTTP 基础设施测试确认 GREEN**

Run: `cd wordflip-web; npm test -- src/data/http/auth src/data/http/createHttpClient.test.ts src/data/http/errors/mapHttpError.test.ts`

Expected: 认证 HTTP 目录全部 PASS。

---

### Task 5: 实现运行时混合 Repository 装配

**Files:**
- Create: `wordflip-web/src/data/runtime/createRepositoryBundle.ts`
- Create: `wordflip-web/src/data/runtime/createRepositoryBundle.test.ts`
- Modify: `wordflip-web/src/main.tsx`
- Create: `wordflip-web/.env.example`
- Modify: `wordflip-web/README.md`

**Interfaces:**
- Produces: `createRepositoryBundle({ dataSource, apiBaseUrl, storage, demoStore }): RepositoryBundle`
- Consumes: `VITE_DATA_SOURCE`, `VITE_API_BASE_URL`

- [ ] **Step 1: 写运行时装配失败测试**

```ts
test("http 数据源只替换认证 Repository", () => {
  const repositories = createRepositoryBundle({
    dataSource: "http",
    apiBaseUrl: "http://127.0.0.1:8080/api/v1",
    storage: window.localStorage,
    demoStore
  });

  expect(repositories.auth).toBeInstanceOf(HttpAuthRepository);
  expect(repositories.books).toBeInstanceOf(MockBookRepository);
  expect(repositories.quiz).toBeInstanceOf(MockQuizRepository);
});
```

另测缺省/未知数据源使用完整 Mock，以及 http 缺失 API base URL 时抛出不包含 token 或密码的明确配置错误。

能捕获的生产回归：启用真实认证时误把未实现模块也切成 HTTP，或默认演示环境依赖后端。

- [ ] **Step 2: 运行装配测试确认 RED**

Run: `cd wordflip-web; npm test -- src/data/runtime/createRepositoryBundle.test.ts`

Expected: FAIL，因为工厂不存在。

- [ ] **Step 3: 最小实现工厂并替换 main.tsx 装配**

工厂先调用 `createMockRepositoryBundle(demoStore)`；只有 `dataSource === "http"` 时返回 `{ ...mockBundle, auth: createHttpAuthRepository(...) }`。`main.tsx` 保留演示场景 bootstrap 和 DemoStateStore 创建，但通过工厂读取：

```ts
const repositories = createRepositoryBundle({
  dataSource: import.meta.env.VITE_DATA_SOURCE,
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL,
  storage: window.localStorage,
  demoStore
});
```

`.env.example` 写入非敏感示例：

```dotenv
VITE_DATA_SOURCE=mock
VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1
```

README 说明复制为 `.env.local` 后把数据源改为 `http`，并明确当前只有认证走真实 API。

- [ ] **Step 4: 运行装配和认证页面回归确认 GREEN**

Run: `cd wordflip-web; npm test -- src/data/runtime/createRepositoryBundle.test.ts src/features/auth/AuthFlow.test.tsx`

Expected: 全部 PASS。

---

### Task 6: 对齐 Spring Boot dev CORS

**Files:**
- Create: `wordflip-server/src/test/java/com/wordflip/config/DevCorsConfigurationTest.java`
- Modify: `wordflip-server/src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: `wordflip.cors.allowed-origins` from the real `application-dev.yml`
- Verifies: `/api/**` CORS allows `http://127.0.0.1:5273` without wildcard origin

- [ ] **Step 1: 写运行真实 YAML 的 CORS 失败测试**

测试用 `YamlPropertySourceLoader` 加载 classpath `application-dev.yml`，通过 Spring `Binder` 绑定 `CorsProperties`，再运行 `CorsConfig.corsConfigurationSource()`：

```java
@Test
void devProfileAllowsCurrentWebOriginWithoutWildcard() throws IOException {
    CorsProperties properties = bindDevCorsProperties();
    CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(properties);
    MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");

    CorsConfiguration configuration = source.getCorsConfiguration(request);
    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowedOrigins())
            .contains("http://127.0.0.1:5273")
            .doesNotContain("*");
}
```

能捕获的生产回归：Vite 改端口/Host 后后端仍只允许旧 Origin，导致浏览器预检失败。

- [ ] **Step 2: 运行 CORS 测试确认 RED**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=DevCorsConfigurationTest test`

Expected: FAIL，因为 dev 白名单尚无 `http://127.0.0.1:5273`。

- [ ] **Step 3: 最小修改 dev 白名单**

在 `application-dev.yml` 的 `wordflip.cors.allowed-origins` 中新增：

```yaml
- http://127.0.0.1:5273
```

不删除既有 Origin，不增加通配符。

- [ ] **Step 4: 运行 CORS 测试确认 GREEN**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=DevCorsConfigurationTest test`

Expected: 1 test PASS。

---

### Task 7: 总体验证、真实联调和任务状态

**Files:**
- Modify: `TASK.md`
- Modify only if verification exposes defects: files from Tasks 1–6

**Interfaces:**
- Verifies: WEB-API01 behavior across Web build, Mock E2E, Spring config and optional live backend
- Produces: evidence before marking `WEB-API01` complete

- [ ] **Step 1: 运行 Web 认证相关测试**

Run: `cd wordflip-web; npm test -- src/features/auth src/data/http src/data/runtime/createRepositoryBundle.test.ts`

Expected: 全部 PASS，0 failures。

- [ ] **Step 2: 运行 Web 全量验证**

Run sequentially:

```powershell
cd wordflip-web
npm test
npm run lint
npm run build
npx playwright test e2e/happy-path.spec.ts
```

Expected: Vitest 0 failures；ESLint exit 0；TypeScript/Vite build exit 0；happy path 1 test PASS。

- [ ] **Step 3: 运行后端相关测试**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=AuthControllerTest,DevCorsConfigurationTest test`

Expected: 两个测试类全部 PASS。

- [ ] **Step 4: 尝试真实认证联调**

若 MySQL、Redis 和 Spring Boot dev server 可用，在 Web `.env.local` 不落库的前提下用进程环境变量启动：

```powershell
$env:VITE_DATA_SOURCE = "http"
$env:VITE_API_BASE_URL = "http://127.0.0.1:8080/api/v1"
npm run dev
```

在浏览器验证：注册或登录成功、刷新页面仍保持认证、过期/无效 Refresh Token 回到登录、登出清空会话。若基础设施不可用，记录具体阻塞项并保持 TASK 未勾选。

- [ ] **Step 5: 检查 diff 和安全边界**

Run from repository root:

```powershell
git diff --check
git status --short
rg -n "accessToken|refreshToken|password" wordflip-web/.env* wordflip-web/src --glob '!**/*.test.*'
```

Expected: diff 无空白错误；没有真实 token、密码或密钥；token 字段只出现在认证传输/存储代码，密码只在表单和请求 DTO 流程中短暂存在。

- [ ] **Step 6: 有完整证据后更新 TASK**

只有步骤 1～5 均满足且真实认证联调完成时，将 `TASK.md` 的 `WEB-API01` 改为 `[x]`。若真实联调因基础设施不可用而跳过，保持 `[ ]` 并在交付说明中列出自动化已通过、真实联调未执行。

- [ ] **Step 7: 提交（仅在用户明确要求时）**

```powershell
git add docs/superpowers/specs/2026-08-05-wordflip-web-auth-api-design.md docs/superpowers/plans/2026-08-05-wordflip-web-auth-api.md wordflip-web wordflip-server/src/main/resources/application-dev.yml wordflip-server/src/test/java/com/wordflip/config/DevCorsConfigurationTest.java TASK.md
git commit -m "feat(web): 接入真实认证接口与令牌生命周期"
```

Commit body 应说明：按模块混合切换；令牌轮换与 401 单飞重试；CORS 新增精确本地 Origin；是否完成真实后端联调。未经用户明确要求，不 push。
