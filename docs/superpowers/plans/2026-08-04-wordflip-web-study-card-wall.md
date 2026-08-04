# WordFlip Web 学习页卡片墙实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用可独立翻面的响应式卡片墙直接替换 Web 学习页的单卡上一词/下一词浏览方式。

**Architecture:** `StudyPage` 继续加载同一 `StudySessionView`，但改用 `cardId` 集合保存每张卡的翻面状态，并根据 DOM 中的学习卡顺序处理左右方向键焦点移动。`StudyCard` 保持纯展示按钮，CSS 与 `FocusShell` 提供更宽的自适应网格；Repository、模拟数据和完成会话流程不变。

**Tech Stack:** React 18、TypeScript、CSS Modules、Vitest、Testing Library、Playwright、Vite。

## Global Constraints

- 当前卡片墙直接替换单卡页面，不新增视图切换。
- 翻卡浏览不得写入双层记忆、掌握度或 `review_events`。
- 学习状态继续以 `cardId` 为主键，`wordKey` 只用于展示与查询。
- 不修改 OpenAPI、服务端、Android、Repository 接口或模拟数据结构。
- 保留退出确认、空/错/加载态、完成会话及完成页跳转。
- 只修改任务相关文件，不覆盖工作树中的既有未提交改动。
- 按仓库规则不自动执行 `git commit` 或 `git push`。

---

### Task 1: 卡片墙渲染与独立翻面状态

**Files:**
- Modify: `wordflip-web/src/features/study/StudyFlow.test.tsx:5-41`
- Modify: `wordflip-web/src/features/study/StudyPage.tsx:20-169`
- Modify: `wordflip-web/src/features/study/StudyCard.tsx:4-49`

**Interfaces:**
- Consumes: `StudySessionView.cards: LearningCard[]`、现有 `StudyCardProps.card` 与 `StudyRepository.getSession(sessionId)`。
- Produces: `StudyPage` 中以 `Set<string>` 表示的独立翻面状态，以及带 `data-study-card` 的卡片按钮 DOM 顺序。

- [ ] **Step 1: 写出同时显示全部卡片且移除单卡控制的失败测试**

在 `StudyFlow.test.tsx` 中把旧的单卡切词用例替换为：

```tsx
test("学习会话以卡片墙同时展示全部词汇", async () => {
  renderAuthenticatedApp("/study/study-demo");

  expect(await screen.findByRole("heading", { name: "sustainable" })).toBeVisible();
  expect(screen.getByRole("heading", { name: "infrastructure" })).toBeVisible();
  expect(screen.getByRole("heading", { name: "urban" })).toBeVisible();
  expect(screen.getByTestId("study-card-wall")).toBeVisible();
  expect(screen.queryByRole("button", { name: "上一词" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "下一词" })).not.toBeInTheDocument();
  expect(screen.queryByText("1 / 3")).not.toBeInTheDocument();
});
```

- [ ] **Step 2: 运行测试并确认因仍是单卡页面而失败**

Run: `npm test -- src/features/study/StudyFlow.test.tsx -t "学习会话以卡片墙同时展示全部词汇"`

Expected: FAIL，页面找不到 `infrastructure` 或 `study-card-wall`。

- [ ] **Step 3: 写出卡片独立翻面的失败测试**

```tsx
test("每张卡片独立翻面且不会重置其他卡片", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const sustainable = await screen.findByRole("button", {
    name: "翻转 sustainable 学习卡查看释义"
  });
  const infrastructure = screen.getByRole("button", {
    name: "翻转 infrastructure 学习卡查看释义"
  });

  await user.click(sustainable);
  expect(screen.getByText("可持续的")).toBeVisible();
  expect(screen.queryByText("基础设施")).not.toBeInTheDocument();

  await user.click(infrastructure);
  expect(screen.getByText("可持续的")).toBeVisible();
  expect(screen.getByText("基础设施")).toBeVisible();
});
```

- [ ] **Step 4: 运行测试并确认第二张卡尚未渲染**

Run: `npm test -- src/features/study/StudyFlow.test.tsx -t "每张卡片独立翻面且不会重置其他卡片"`

Expected: FAIL，找不到 `infrastructure` 卡片按钮。

- [ ] **Step 5: 用最小实现替换单卡索引状态**

在 `StudyPage.tsx` 中：

```tsx
const [flippedCardIds, setFlippedCardIds] = useState<Set<string>>(() => new Set());

const flipCard = useCallback((cardId: string) => {
  setFlippedCardIds((current) => {
    const next = new Set(current);
    if (next.has(cardId)) next.delete(cardId);
    else next.add(cardId);
    return next;
  });
}, []);
```

加载新会话时清空集合，然后使用网格渲染全部卡片：

```tsx
<div className={styles.cardWall} data-testid="study-card-wall">
  {session.cards.map((card) => (
    <StudyCard
      card={card}
      isFlipped={flippedCardIds.has(card.cardId)}
      key={card.cardId}
      onFlip={() => flipCard(card.cardId)}
    />
  ))}
</div>
```

删除 `cardIndex`、`moveCard`、`.controls` JSX 和单卡计数器。

在 `StudyCard.tsx` 中让无障碍名称体现当前动作：

```tsx
aria-label={isFlipped
  ? `将 ${card.headword} 学习卡翻回正面`
  : `翻转 ${card.headword} 学习卡查看释义`}
```

- [ ] **Step 6: 运行学习页测试并处理旧断言**

Run: `npm test -- src/features/study/StudyFlow.test.tsx`

Expected: 新用例 PASS；旧的上一词/下一词断言需要在 Task 2 中改为卡片焦点语义，其余完成与退出流程保持 PASS。

---

### Task 2: 卡片墙键盘焦点与侧栏说明

**Files:**
- Modify: `wordflip-web/src/features/study/StudyFlow.test.tsx:28-159`
- Modify: `wordflip-web/src/features/study/StudyPage.tsx:69-106`
- Modify: `wordflip-web/src/features/study/StudySidebar.tsx:25-29`

**Interfaces:**
- Consumes: 带 `data-study-card` 的原生按钮、卡片的 DOM 顺序、`isInteractiveTarget(target)`。
- Produces: `moveCardFocus(target: EventTarget | null, direction: -1 | 1): void`，只移动焦点而不翻卡或循环。

- [ ] **Step 1: 写出方向键移动焦点与边界停止的失败测试**

```tsx
test("左右方向键在卡片墙中移动焦点并停在边界", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const cards = await screen.findAllByRole("button", { name: /学习卡/ });

  cards[0].focus();
  await user.keyboard("{ArrowRight}");
  expect(cards[1]).toHaveFocus();
  await user.keyboard("{ArrowLeft}");
  expect(cards[0]).toHaveFocus();
  await user.keyboard("{ArrowLeft}");
  expect(cards[0]).toHaveFocus();

  cards.at(-1)!.focus();
  await user.keyboard("{ArrowRight}");
  expect(cards.at(-1)).toHaveFocus();
});
```

- [ ] **Step 2: 运行测试并确认方向键仍执行旧单卡切词逻辑或无焦点移动**

Run: `npm test -- src/features/study/StudyFlow.test.tsx -t "左右方向键在卡片墙中移动焦点并停在边界"`

Expected: FAIL，第二张卡没有获得焦点。

- [ ] **Step 3: 实现基于 DOM 顺序的焦点移动**

在 `StudyPage.tsx` 中增加：

```tsx
function moveCardFocus(target: EventTarget | null, direction: -1 | 1) {
  if (!(target instanceof HTMLElement)) return;
  const currentCard = target.closest<HTMLButtonElement>("[data-study-card]");
  if (!currentCard) return;
  const cards = Array.from(
    document.querySelectorAll<HTMLButtonElement>("[data-study-card]")
  );
  const currentIndex = cards.indexOf(currentCard);
  const nextIndex = Math.min(Math.max(currentIndex + direction, 0), cards.length - 1);
  cards[nextIndex]?.focus();
}
```

全局键盘监听只保留 `Escape` 和卡片聚焦时的左右方向键；空格与 `Enter` 交给按钮原生激活，不再对页面主体执行全局翻面。

- [ ] **Step 4: 更新侧栏和旧键盘测试**

把侧栏说明更新为：

```tsx
<li><kbd>Space</kbd><span>翻转当前卡片</span></li>
<li><kbd>←</kbd><span>聚焦上一张</span></li>
<li><kbd>→</kbd><span>聚焦下一张</span></li>
```

更新旧的卡片按钮名称断言，并把“下一词与退出按钮”测试收窄为退出按钮原生键盘激活。保留输入控件聚焦、完成按钮、退出取消恢复焦点等回归断言。

- [ ] **Step 5: 运行完整学习页单元测试**

Run: `npm test -- src/features/study/StudyFlow.test.tsx`

Expected: 全部 PASS，无 React 警告或未处理 Promise。

---

### Task 3: 响应式卡片墙视觉与浏览器验收

**Files:**
- Modify: `wordflip-web/src/features/study/study.module.css:1-183`
- Modify: `wordflip-web/src/layouts/FocusShell/FocusShell.module.css:6`
- Modify: `wordflip-web/e2e/responsive.spec.ts:91-105`

**Interfaces:**
- Consumes: `.workspace`、`.cardWall`、`.card`、`.cardContent`、FocusShell `.main`。
- Produces: 桌面 3～4 列、平板 2～3 列、手机 2 列、极窄屏 1 列的无横向溢出布局。

- [ ] **Step 1: 写出桌面和手机列数的失败 E2E 测试**

在 `responsive.spec.ts` 增加：

```ts
test("学习卡片墙在桌面和手机使用响应式列数", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(scenarios.study);
  const wall = page.getByTestId("study-card-wall");
  await expect(wall).toBeVisible();
  const desktopColumns = await wall.evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(" ").length
  );
  expect(desktopColumns).toBeGreaterThanOrEqual(3);
  await expectNoHorizontalOverflow(page);

  await page.setViewportSize({ width: 390, height: 844 });
  const mobileColumns = await wall.evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(" ").length
  );
  expect(mobileColumns).toBe(2);
  await expectNoHorizontalOverflow(page);
});
```

- [ ] **Step 2: 运行 E2E 并确认卡片墙样式尚未满足列数**

Run: `npm run test:e2e -- e2e/responsive.spec.ts -g "学习卡片墙在桌面和手机使用响应式列数"`

Expected: FAIL，找不到 `study-card-wall` 或桌面/手机列数不符合预期。

- [ ] **Step 3: 实现卡片墙与紧凑卡片样式**

在 `study.module.css` 中增加网格并调整现有卡片尺寸：

```css
.cardWall {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  width: 100%;
}

.card {
  min-height: 320px;
  padding: 24px 18px;
}
```

同步缩小图片、标题、释义、例句和提示字号，确保英文长词能换行。把 FocusShell `.main` 的 `max-width` 从 `720px` 扩大至 `1040px`。手机媒体查询使用两列与更紧凑的卡片；在约 `340px` 以下降为单列。保留 `prefers-reduced-motion` 规则和清晰的 `:focus-visible` 样式。

- [ ] **Step 4: 运行响应式和代表性状态 E2E**

Run: `npm run test:e2e -- e2e/responsive.spec.ts e2e/representative-states.spec.ts`

Expected: 全部 PASS，学习页无横向溢出，退出确认框仍为实体表面。

---

### Task 4: 全量 Web 验证与差异审计

**Files:**
- Verify only: all modified files above

**Interfaces:**
- Consumes: Task 1～3 的最终实现。
- Produces: 可交付的测试、lint、构建和差异检查证据。

- [ ] **Step 1: 运行完整 Vitest**

Run: `npm test`

Expected: 全部测试 PASS。

- [ ] **Step 2: 运行 lint**

Run: `npm run lint`

Expected: exit code 0。

- [ ] **Step 3: 运行生产构建**

Run: `npm run build`

Expected: Vite 构建成功。

- [ ] **Step 4: 运行学习页相关 Playwright 验收**

Run: `npm run test:e2e -- e2e/happy-path.spec.ts e2e/representative-states.spec.ts e2e/responsive.spec.ts`

Expected: 全部 PASS；如环境无法运行，记录具体失败与未覆盖范围。

- [ ] **Step 5: 检查变更边界与格式**

Run: `git diff --check`

Run: `git status --short`

Expected: 无 diff 格式错误；只出现本任务文件和用户已有未提交文件，不执行 commit 或 push。
