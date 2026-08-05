# WordFlip Web Settings Sign-out Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在设置页补齐符合 `REQ-AUTH-5` 与 `REQ-SETTINGS-6` 的退出登录入口，并通过真实认证 Repository 清理当前浏览器会话后返回登录页。

**Architecture:** `SettingsPage` 继续只依赖 `RepositoryBundle.auth`，不直接访问令牌存储或 Axios。专用 `SignOutDialog` 负责确认框的焦点、pending 和错误展示；页面负责调用 `auth.signOut()`、清空 TanStack Query 缓存并 replace 导航到 `/login`。

**Tech Stack:** React 18、TypeScript 5.7、React Router、TanStack Query、Testing Library、Vitest 2、Playwright、Spring Boot 3.3。

## Global Constraints

- 遵守 `REQ-AUTH-5` 与 `REQ-SETTINGS-6`：设置页提供“退出登录”，退出后清除本地会话并返回登录页。
- 页面只调用 `AuthRepository.signOut()`，不得导入 `TokenStore`、Axios 或 HTTP DTO。
- 退出只注销当前浏览器会话；不新增“全部设备退出”。
- 确认进行中只提交一次，取消和确认按钮禁用，Esc 不关闭。
- 成功后先清空 Query 缓存，再使用 replace 导航到 `/login`。
- 意外失败时保留对话框并显示安全错误，不输出令牌、密码或响应堆栈。
- 新增和修改的业务注释使用简体中文。
- 未经用户明确要求，不执行 Git commit 或 push。

---

### Task 1: 设置页退出登录交互与会话清理

**Files:**
- Create: `wordflip-web/src/features/settings/SignOutDialog.tsx`
- Modify: `wordflip-web/src/features/settings/SettingsPage.tsx`
- Modify: `wordflip-web/src/features/settings/settings.module.css`
- Test: `wordflip-web/src/features/settings/SettingsPage.test.tsx`

**Interfaces:**
- Consumes: `AuthRepository.signOut(): Promise<{ signedOut: true }>`
- Consumes: `QueryClient.clear(): void`
- Produces: `SignOutDialog({ error, onCancel, onConfirm, returnFocusRef, signingOut })`
- Produces: 设置页可访问的“账户”区域、“退出登录”按钮与“确认退出登录”对话框

- [ ] **Step 1: 写确认、取消与成功退出的失败测试**

在 `SettingsPage.test.tsx` 增加：

```tsx
test("退出登录需要确认，取消恢复焦点，确认后清理会话并返回登录页", async () => {
  const user = userEvent.setup();
  const app = renderScenarioApp("configured", "/settings");
  const trigger = await screen.findByRole("button", { name: "退出登录" });

  await user.click(trigger);
  const dialog = screen.getByRole("dialog", { name: "确认退出登录" });
  expect(dialog).toHaveAttribute("aria-modal", "true");
  expect(screen.getByRole("button", { name: "取消" })).toHaveFocus();

  await user.keyboard("{Escape}");
  expect(screen.queryByRole("dialog", { name: "确认退出登录" })).not.toBeInTheDocument();
  expect(trigger).toHaveFocus();

  await user.click(trigger);
  await user.click(screen.getByRole("button", { name: "确认退出" }));

  expect(await screen.findByRole("heading", { name: "登录 WordFlip" })).toBeVisible();
  expect(app.store.read().auth.session).toBeNull();
});
```

该测试捕获的生产回归：设置页缺少退出入口、未确认就清理会话、取消后焦点丢失、确认后未清理本地会话或未返回登录页。

- [ ] **Step 2: 运行测试确认 RED**

Run: `cd wordflip-web; npm test -- src/features/settings/SettingsPage.test.tsx`

Expected: FAIL，因为当前设置页找不到“退出登录”按钮。

- [ ] **Step 3: 写 pending 防重复与错误保留的失败测试**

在同一测试文件导入 `MockAuthRepository`，增加：

```tsx
test("退出 pending 时只提交一次，意外失败后保留确认框并允许重试", async () => {
  let rejectSignOut!: (reason: unknown) => void;
  const signOutPromise = new Promise<{ signedOut: true }>((_resolve, reject) => {
    rejectSignOut = reject;
  });
  const signOutSpy = vi.spyOn(MockAuthRepository.prototype, "signOut").mockReturnValue(signOutPromise);
  const user = userEvent.setup();
  renderScenarioApp("configured", "/settings");

  await user.click(await screen.findByRole("button", { name: "退出登录" }));
  const confirm = screen.getByRole("button", { name: "确认退出" });
  await user.dblClick(confirm);
  await user.keyboard("{Enter}");

  expect(signOutSpy).toHaveBeenCalledTimes(1);
  expect(confirm).toBeDisabled();
  expect(screen.getByRole("button", { name: "取消" })).toBeDisabled();
  await user.keyboard("{Escape}");
  expect(screen.getByRole("dialog", { name: "确认退出登录" })).toBeVisible();

  rejectSignOut(new Error("暂时无法退出登录"));
  expect(await screen.findByRole("alert")).toHaveTextContent("暂时无法退出登录");
  expect(confirm).toBeEnabled();
  expect(screen.getByRole("button", { name: "取消" })).toBeEnabled();
});
```

该测试捕获的生产回归：双击/键盘重复提交、pending 时仍可关闭对话框、异常后错误不可见或操作永久禁用。

- [ ] **Step 4: 运行测试确认第二个 RED**

Run: `cd wordflip-web; npm test -- src/features/settings/SettingsPage.test.tsx`

Expected: 两个新增测试均 FAIL，原因仍是退出登录 UI 尚不存在。

- [ ] **Step 5: 最小实现专用确认框**

创建 `SignOutDialog.tsx`。组件使用现有 `settings.module.css` 的 `backdrop`、`dialog`、`dialogActions` 和错误样式；挂载时聚焦取消按钮，限制 Tab 焦点，Esc 仅在非 pending 时取消，卸载时恢复触发按钮焦点：

```tsx
import { useEffect, useRef, type KeyboardEvent, type RefObject } from "react";
import { Button } from "@/components/Button/Button";
import styles from "./settings.module.css";

interface SignOutDialogProps {
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
  returnFocusRef: RefObject<HTMLElement>;
  signingOut: boolean;
}

export function SignOutDialog({ error, onCancel, onConfirm, returnFocusRef, signingOut }: SignOutDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const returnFocusTarget = returnFocusRef.current;
    cancelRef.current?.focus();
    return () => { returnFocusTarget?.focus(); };
  }, [returnFocusRef]);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (signingOut && (event.key === "Escape" || event.key === "Tab")) {
      event.preventDefault();
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      onCancel();
      return;
    }
    if (event.key !== "Tab") return;
    const first = cancelRef.current;
    const last = confirmRef.current;
    if (!first || !last) return;
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return <div className={styles.backdrop}>
    <div aria-labelledby="sign-out-title" aria-modal="true" className={styles.dialog} onKeyDown={handleKeyDown} role="dialog">
      <p className={styles.eyebrow}>ACCOUNT SIGN OUT</p>
      <h2 id="sign-out-title">确认退出登录</h2>
      <p>当前浏览器会退出 WordFlip，并返回登录页。</p>
      {error ? <p className={styles.signOutError} role="alert">{error}</p> : null}
      <div className={styles.dialogActions}>
        <Button disabled={signingOut} onClick={onCancel} ref={cancelRef} variant="secondary">取消</Button>
        <Button className={styles.dangerButton} disabled={signingOut} onClick={onConfirm} ref={confirmRef}>确认退出</Button>
      </div>
    </div>
  </div>;
}
```

- [ ] **Step 6: 最小接入 SettingsPage**

`SettingsPage` 同时读取 `{ auth, settings }`，增加触发按钮 ref、提交互斥 ref、对话框/pending/error 状态，以及以下处理函数：

```tsx
async function signOut() {
  if (signingOutRef.current) return;
  signingOutRef.current = true;
  setSigningOut(true);
  setSignOutError(null);
  try {
    await auth.signOut();
    queryClient.clear();
    setSignOutDialogOpen(false);
    navigate("/login", { replace: true });
  } catch (reason) {
    setSignOutError(reason instanceof Error ? reason.message : "暂时无法退出登录，请重试");
  } finally {
    signingOutRef.current = false;
    setSigningOut(false);
  }
}
```

在 `.workspace` 最后增加 `styles.accountPanel` 包裹的 `Panel title="账户"`，显示说明和“退出登录”按钮；页面末尾按状态渲染 `SignOutDialog`。CSS 增加：

```css
.accountPanel { grid-column: 1 / -1; }
.accountActions { align-items: center; display: flex; justify-content: space-between; gap: 16px; }
.dangerButton { border-color: var(--wf-error); color: var(--wf-error); }
.signOutError { color: var(--wf-error); }
```

- [ ] **Step 7: 运行设置页测试确认 GREEN**

Run: `cd wordflip-web; npm test -- src/features/settings/SettingsPage.test.tsx`

Expected: 设置页全部测试 PASS，0 failures。

- [ ] **Step 8: 运行认证与设置回归**

Run: `cd wordflip-web; npm test -- src/features/settings/SettingsPage.test.tsx src/features/auth/AuthFlow.test.tsx src/data/http/auth`

Expected: 所有相关测试 PASS，0 failures。

---

### Task 2: 全量验证、真实浏览器退出与任务状态

**Files:**
- Modify: `TASK.md`
- Modify only if verification exposes a defect: Task 1 files 或既有认证 HTTP 文件

**Interfaces:**
- Verifies: 设置页真实退出会调用 Spring Boot `/auth/logout`，清除 `TokenStore` 并返回 `/login`
- Produces: `WEB-API01` 完成证据与任务勾选

- [ ] **Step 1: 运行 Web 全量验证**

Run sequentially:

```powershell
cd wordflip-web
npm test
npm run lint
npm run build
npx playwright test e2e/happy-path.spec.ts e2e/representative-states.spec.ts
```

Expected: Vitest 0 failures；ESLint exit 0；Vite build exit 0；Playwright 7 tests PASS。

- [ ] **Step 2: 运行后端认证与 CORS 验证**

Run: `cd wordflip-server; .\mvnw.cmd "-Dtest=AuthControllerTest,DevCorsConfigurationTest" test`

Expected: 5 tests PASS，0 failures。

- [ ] **Step 3: 在 HTTP 模式执行真实浏览器退出**

保持 `VITE_DATA_SOURCE=http` 与 `VITE_API_BASE_URL=http://127.0.0.1:8080/api/v1`，在浏览器完成：注册测试账号 → 进入设置 → 点击退出登录 → 确认退出 → 验证 URL 为 `/login` → 刷新后仍为 `/login`。浏览器控制台不得出现 CORS 或未处理异常。

- [ ] **Step 4: 检查 diff 与安全边界**

Run from repository root:

```powershell
git diff --check
git status --short
rg -n "accessToken|refreshToken|password" wordflip-web/.env* wordflip-web/src --glob '!**/*.test.*'
```

Expected: 无空白错误；没有真实 token、密码、密钥或真实用户数据；改动只包含 WEB-API01 设计、计划、认证接入、退出登录 UI、CORS 测试与任务状态。

- [ ] **Step 5: 有完整证据后勾选任务**

只有 Task 1、Task 2 步骤 1～4 全部通过时，使用以下最小变更：

```diff
-- [ ] **WEB-API01** 身份认证与令牌生命周期
+- [x] **WEB-API01** 身份认证与令牌生命周期
```

修改后再次运行 `git diff --check TASK.md`。

- [ ] **Step 6: 提交（仅在用户明确要求时）**

```powershell
git add TASK.md docs/superpowers/specs/2026-08-05-wordflip-web-auth-api-design.md docs/superpowers/plans/2026-08-05-wordflip-web-auth-api.md docs/superpowers/plans/2026-08-05-wordflip-web-settings-sign-out.md wordflip-web wordflip-server/src/main/resources/application-dev.yml wordflip-server/src/test/java/com/wordflip/config/DevCorsConfigurationTest.java
git commit -m "feat(web): 接入真实认证接口与令牌生命周期"
```

未经用户明确要求，不执行本步骤，也不 push。
