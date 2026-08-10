# WordFlip Web Books, Plans, and Settings API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `WEB-API02`，让 OpenAPI、Spring Boot 与 Web 共同支持真实的学习计划、词书进度、分组配置和设置保存，同时保持 Mock 模式与 Android 旧客户端兼容。

**Architecture:** 以增量 OpenAPI 变更扩展现有 `BookItem` 和 `PATCH /settings/preferences`；后端在事务中保存分组配置并增量补组，词书查询返回用户隔离的计划进度；Web 通过共享认证客户端实现 `HttpBookRepository` 与 `HttpSettingsRepository`。HTTP 模式仅替换认证、词书和设置，其他模块仍使用版本化 Mock。

**Tech Stack:** OpenAPI 3.0.3、Python `unittest` + PyYAML、Java 21、Spring Boot 3.3、Spring JDBC/JPA、JUnit 5 + Mockito + Testcontainers MySQL、React 18、TypeScript、Axios、TanStack Query、Vitest、Testing Library、Playwright。

## Global Constraints

- 业务规则以 `docs/wordflip/requirements.md` v7 为最高权威，尤其是 REQ-BOOK-2/3/4/12/13/15/17～27。
- API/DTO 必须先修改 `wordflip-api/openapi.yaml`，再同步 Spring Boot 与 Web。
- Android 本阶段不修改、不构建；所有契约字段均为增量扩展，保留 `BookItem.selected` 和原请求字段。
- 不新增 Flyway 迁移；使用 v2 已有的 `group_size`、`group_strategy`、计划、分组和记忆表。
- `masteredCount` 只统计默写轨 `stability >= 30`；完成度分母是当前计划已入组去重卡片数。
- 设置与分组操作不得写或删除 `card_skill_memory`、`lexeme_skill_memory`、`review_events`。
- Web 不计算 FSRS、分组顺序或学习进度，只映射服务端权威响应。
- 新增或修改的业务注释使用简体中文；标识符保持英文。
- 保留未跟踪的 `wordflip-web/.vite/`，不得删除、提交或覆盖。
- 仓库禁止自动提交：每个任务仅记录建议 commit message；只有用户再次明确授权时才执行 `git commit` 或 `git push`。

---

## File Map

### OpenAPI

- Modify: `wordflip-api/openapi.yaml` — 扩展词书进度与设置请求契约，修正 v7 词频策略描述。
- Create: `wordflip-api/tests/test_books_settings_contract.py` — 锁定 WEB-API02 新契约和兼容字段。

### Spring Boot

- Modify: `wordflip-server/pom.xml` — 将 Testcontainers 锁定为兼容 Docker Engine 29 的 1.21.4。
- Modify: `wordflip-server/src/main/java/com/wordflip/dto/book/BookListResponse.java` — 增加计划状态和进度 DTO。
- Modify: `wordflip-server/src/main/java/com/wordflip/service/BookService.java` — 查询并映射用户隔离的计划进度。
- Create: `wordflip-server/src/test/java/com/wordflip/service/BookServiceTest.java` — 覆盖 current/history/available 与 SQL 隔离参数。
- Modify: `wordflip-server/src/main/java/com/wordflip/dto/settings/PreferencesPatchRequest.java` — 接收分组大小和策略。
- Modify: `wordflip-server/src/main/java/com/wordflip/service/SettingsService.java` — 事务保存配置并按需追加分组。
- Modify: `wordflip-server/src/test/java/com/wordflip/service/SettingsServiceTest.java` — 覆盖合法、非法、未变化和追加行为。
- Create: `wordflip-server/src/main/java/com/wordflip/service/AutoGroupCardOrderer.java` — 三种增量分组排序的纯逻辑。
- Create: `wordflip-server/src/test/java/com/wordflip/service/AutoGroupCardOrdererTest.java` — 锁定书序、词频回退和稳定随机。
- Modify: `wordflip-server/src/main/java/com/wordflip/service/GroupService.java` — 先补末尾未满组，再创建新组。
- Create: `wordflip-server/src/test/java/com/wordflip/service/GroupServiceAppendTest.java` — 验证末尾补齐和新组插入编排。
- Create: `wordflip-server/src/test/java/com/wordflip/database/BookSettingsMySqlIntegrationTest.java` — 用真实 MySQL 验证查询、事务回滚和不写记忆。
- Modify: `wordflip-server/src/main/java/com/wordflip/controller/SettingsController.java` — 更新中文契约注释。

### Web

- Create: `wordflip-web/src/data/http/createHttpRuntime.ts` — 一次创建共享客户端、令牌存储和会话管理器。
- Create: `wordflip-web/src/data/http/createHttpRuntime.test.ts` — 验证业务请求共享刷新状态。
- Modify: `wordflip-web/src/data/http/auth/HttpAuthRepository.ts` — 允许运行时工厂复用现有认证 Repository。
- Create: `wordflip-web/src/data/http/books/bookDtos.ts` — OpenAPI 词书/计划 DTO。
- Create: `wordflip-web/src/data/http/books/bookMappers.ts` — DTO 到领域模型转换。
- Create: `wordflip-web/src/data/http/books/HttpBookRepository.ts` — 真实词书与学习计划调用。
- Create: `wordflip-web/src/data/http/books/HttpBookRepository.test.ts` — 请求和映射契约测试。
- Create: `wordflip-web/src/data/http/settings/settingsDtos.ts` — 设置 DTO。
- Create: `wordflip-web/src/data/http/settings/LocalSettingsStore.ts` — 只持久化 Web 设备级 `reducedMotion`。
- Create: `wordflip-web/src/data/http/settings/LocalSettingsStore.test.ts` — 本地设置版本与损坏数据测试。
- Create: `wordflip-web/src/data/http/settings/HttpSettingsRepository.ts` — 真实设置和首次设置编排。
- Create: `wordflip-web/src/data/http/settings/HttpSettingsRepository.test.ts` — 请求顺序、合并和重试测试。
- Modify: `wordflip-web/src/domain/books.ts` — 使用准确的计划进度字段名。
- Modify: `wordflip-web/src/domain/settings.ts` — 增加 `supportsDemoReset()` 能力。
- Modify: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts` — 明确支持演示重置并适配进度字段。
- Modify: `wordflip-web/src/data/mock/createDemoState.ts` — 迁移词书进度字段。
- Modify: `wordflip-web/src/data/mock/DemoStateStore.ts` — 校验新进度字段。
- Modify: `wordflip-web/src/features/books/BooksPage.tsx` — 展示服务端完成度字段。
- Modify: `wordflip-web/src/features/books/BookDetailPage.tsx` — 展示掌握数/已入组数。
- Modify: `wordflip-web/src/features/settings/SettingsPage.tsx` — HTTP 模式隐藏演示重置区并补保存错误状态。
- Modify: `wordflip-web/src/features/settings/SettingsPage.test.tsx` — 覆盖能力开关和保存失败。
- Modify: `wordflip-web/src/data/runtime/createRepositoryBundle.ts` — HTTP 模式替换 auth/books/settings。
- Modify: `wordflip-web/src/data/runtime/createRepositoryBundle.test.ts` — 锁定模块替换范围。
- Modify: `TASK.md` — 只在全部验证通过后勾选 WEB-API02。

---

### Task 0: Restore Docker 29 integration-test compatibility

**Files:**
- Modify: `wordflip-server/pom.xml`

**Interfaces:**
- Produces: Testcontainers `1.21.4` test runtime required by Tasks 4 and 8.

- [ ] **Step 1: Preserve the RED baseline evidence**

The unmodified project resolved Testcontainers `1.19.8`; against Docker Engine `29.6.1`, `WordFlipV2MySqlIntegrationTest` reported `Tests run: 2, Skipped: 2` because docker-java requested an API older than Docker 29's minimum supported API.

- [ ] **Step 2: Add the minimal dependency-management override**

```xml
<properties>
    <!-- Docker Engine 29 要求更高的 API 版本；1.21.4 保持 1.x API 并恢复 Testcontainers 兼容。 -->
    <testcontainers.version>1.21.4</testcontainers.version>
</properties>
```

Do not change MySQL images, test annotations, Spring Boot, or production dependencies.

- [ ] **Step 3: Verify the resolved dependency versions**

Run: `cd wordflip-server; .\mvnw.cmd dependency:tree '-Dincludes=org.testcontainers:*,com.github.docker-java:*'`

Expected: all Testcontainers modules resolve to `1.21.4`; no `1.19.8` remains.

- [ ] **Step 4: Verify the real MySQL tests execute GREEN**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=WordFlipV2MySqlIntegrationTest test`

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Record the suggested checkpoint**

Suggested commit: `test(server): 兼容 Docker 29 集成测试`

Do not commit unless the user explicitly authorizes it.

---

### Task 1: Lock the additive OpenAPI contract

**Files:**
- Create: `wordflip-api/tests/test_books_settings_contract.py`
- Modify: `wordflip-api/openapi.yaml:1325-1467`

**Interfaces:**
- Produces: `BookProgress`, extended `BookItem`, and extended `PreferencesPatchRequest` used verbatim by server and Web DTOs.

- [ ] **Step 1: Write the failing contract tests**

```python
"""WEB-API02 词书、计划与设置契约测试。"""

from pathlib import Path
import unittest
import yaml


class BooksSettingsContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        path = Path(__file__).resolve().parents[1] / "openapi.yaml"
        cls.schemas = yaml.safe_load(path.read_text(encoding="utf-8"))["components"]["schemas"]

    def test_book_item_exposes_nullable_plan_and_progress(self) -> None:
        book = self.schemas["BookItem"]
        self.assertTrue({"planId", "planStatus", "progress"}.issubset(book["required"]))
        self.assertTrue(book["properties"]["planId"]["nullable"])
        self.assertTrue(book["properties"]["progress"]["nullable"])
        progress = self.schemas["BookProgress"]
        self.assertEqual(
            {"masteredCount", "assignedCardCount", "completionPercent"},
            set(progress["required"]),
        )

    def test_preferences_patch_accepts_only_supported_group_configuration(self) -> None:
        request = self.schemas["PreferencesPatchRequest"]["properties"]
        self.assertEqual("#/components/schemas/GroupSize", request["groupSize"]["$ref"])
        self.assertEqual("#/components/schemas/GroupStrategy", request["groupStrategy"]["$ref"])

    def test_book_item_keeps_android_compatibility_fields(self) -> None:
        required = self.schemas["BookItem"]["required"]
        self.assertTrue({"selected", "wordCount", "canDelete"}.issubset(required))
```

- [ ] **Step 2: Run the contract test and verify RED**

Run: `cd wordflip-api; python -m pytest -q tests/test_books_settings_contract.py`

Expected: FAIL because `BookProgress`, `planId`, `planStatus`, `progress`, `groupSize`, and `groupStrategy` are absent.

- [ ] **Step 3: Add the minimal OpenAPI schemas**

```yaml
PreferencesPatchRequest:
  type: object
  minProperties: 1
  properties:
    groupSize:
      $ref: '#/components/schemas/GroupSize'
    groupStrategy:
      $ref: '#/components/schemas/GroupStrategy'

BookProgress:
  type: object
  required: [masteredCount, assignedCardCount, completionPercent]
  properties:
    masteredCount: { type: integer, minimum: 0 }
    assignedCardCount: { type: integer, minimum: 0 }
    completionPercent: { type: integer, minimum: 0, maximum: 100 }

BookItem:
  required: [id, name, source, wordCount, selected, canDelete, planId, planStatus, progress]
  properties:
    planId: { type: integer, format: int64, nullable: true }
    planStatus:
      type: string
      enum: [active, paused, completed]
      nullable: true
    progress:
      allOf:
        - $ref: '#/components/schemas/BookProgress'
      nullable: true
```

Also replace the stale `word_freq_ranks` text in `GroupStrategy` with `book_items.metadata_json.frequencyRank`; document missing-rank fallback to `book_items.sort_order` and stable random seed `(userId, planId)`.

- [ ] **Step 4: Run all API contract tests and verify GREEN**

Run: `cd wordflip-api; python -m pytest -q tests`

Expected: all tests pass with no unresolved `$ref`.

- [ ] **Step 5: Record the suggested checkpoint**

Suggested commit: `feat(api): 补齐词书进度与分组设置契约`

Do not commit unless the user explicitly authorizes it.

---

### Task 2: Make automatic group appending obey v7

**Files:**
- Create: `wordflip-server/src/main/java/com/wordflip/service/AutoGroupCardOrderer.java`
- Create: `wordflip-server/src/test/java/com/wordflip/service/AutoGroupCardOrdererTest.java`
- Modify: `wordflip-server/src/main/java/com/wordflip/service/GroupService.java:149-204`
- Create: `wordflip-server/src/test/java/com/wordflip/service/GroupServiceAppendTest.java`

**Interfaces:**
- Produces: `AutoGroupCardOrderer.order(List<Candidate>, GroupStrategy, long userId, long planId)`.
- Produces: corrected `GroupService.appendAutoGroups(Long userId, Long planId)` for Task 3.

- [ ] **Step 1: Write failing pure ordering tests**

```java
@Test
void ordersFrequencyRanksFirstAndFallsBackToBookOrder() {
    var cards = List.of(
            new Candidate(11L, 0, null),
            new Candidate(12L, 1, 20),
            new Candidate(13L, 2, 10)
    );
    assertThat(orderer.order(cards, GroupStrategy.frequency, 7L, 19L))
            .extracting(Candidate::cardId)
            .containsExactly(13L, 12L, 11L);
}

@Test
void randomOrderIsStableForTheSameUserAndPlan() {
    var first = orderer.order(cards(), GroupStrategy.random, 7L, 19L);
    var second = orderer.order(cards(), GroupStrategy.random, 7L, 19L);
    assertThat(second).isEqualTo(first);
}
```

- [ ] **Step 2: Run the ordering test and verify RED**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=AutoGroupCardOrdererTest test`

Expected: compilation FAIL because `AutoGroupCardOrderer` does not exist.

- [ ] **Step 3: Implement the pure orderer**

```java
/** 只决定本次未入组卡片的追加顺序，不移动已有成员。 */
final class AutoGroupCardOrderer {
    record Candidate(long cardId, int bookOrder, Integer frequencyRank) {}

    List<Candidate> order(List<Candidate> source, GroupStrategy strategy, long userId, long planId) {
        List<Candidate> ordered = new ArrayList<>(source);
        if (strategy == GroupStrategy.frequency) {
            ordered.sort(Comparator
                    .comparing(Candidate::frequencyRank, Comparator.nullsLast(Integer::compareTo))
                    .thenComparingInt(Candidate::bookOrder));
        } else if (strategy == GroupStrategy.random) {
            Collections.shuffle(ordered, new Random(Objects.hash(userId, planId)));
        } else {
            ordered.sort(Comparator.comparingInt(Candidate::bookOrder));
        }
        return ordered;
    }
}
```

- [ ] **Step 4: Write the failing append orchestration test**

Use a mocked `JdbcTemplate` to return: owned plan, `groupSize=3`, `book_order`, last auto group `(groupId=41, size=2, sortOrder=0)`, and unassigned cards `[101, 102, 103]`. Capture updates and assert card `101` is inserted into group `41` with `sort_order=2`, then cards `102/103` enter one new group. Also assert no SQL touches `card_skill_memory`, `lexeme_skill_memory`, or `review_events`.

```java
verify(jdbc).update(
        contains("INSERT INTO study_group_cards"),
        eq(41L), eq(19L), eq(101L), eq(2)
);
assertThat(recordedSql).noneMatch(sql ->
        sql.contains("card_skill_memory") || sql.contains("review_events"));
```

- [ ] **Step 5: Run the append test and verify RED**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=GroupServiceAppendTest test`

Expected: FAIL because the current implementation always creates a new group and ignores `group_strategy`.

- [ ] **Step 6: Implement minimal JDBC orchestration**

Update `appendAutoGroups` to:

1. read both `group_size` and `group_strategy`;
2. select candidates as `card_id`, `book_order`, and nullable `frequency_rank` from `JSON_EXTRACT(bi.metadata_json, '$.frequencyRank')`;
3. order through `AutoGroupCardOrderer`;
4. fill only the last `source='auto'` group when it has spare capacity;
5. create new auto groups for the remaining cards;
6. keep the unique `(plan_id, card_id)` guard as final idempotency protection.

Core insertion helper:

```java
private void appendCards(long groupId, long planId, int startOrder, List<Candidate> cards) {
    for (int index = 0; index < cards.size(); index++) {
        jdbc.update(
                "INSERT INTO study_group_cards(group_id, plan_id, card_id, sort_order) VALUES (?, ?, ?, ?)",
                groupId, planId, cards.get(index).cardId(), startOrder + index
        );
    }
}
```

- [ ] **Step 7: Run both group tests and verify GREEN**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=AutoGroupCardOrdererTest,GroupServiceAppendTest test`

Expected: all group ordering and append tests pass.

- [ ] **Step 8: Record the suggested checkpoint**

Suggested commit: `fix(server): 按分组配置增量补齐学习卡`

Do not commit unless the user explicitly authorizes it.

---

### Task 3: Persist group configuration transactionally

**Files:**
- Modify: `wordflip-server/src/main/java/com/wordflip/dto/settings/PreferencesPatchRequest.java`
- Modify: `wordflip-server/src/main/java/com/wordflip/service/SettingsService.java`
- Modify: `wordflip-server/src/main/java/com/wordflip/controller/SettingsController.java`
- Modify: `wordflip-server/src/test/java/com/wordflip/service/SettingsServiceTest.java`

**Interfaces:**
- Consumes: corrected `GroupService.appendAutoGroups(userId, planId)` from Task 2.
- Produces: PATCH request getters/setters `getGroupSize()` and `getGroupStrategy()` used by Jackson and service tests.

- [ ] **Step 1: Expand failing settings service tests**

Add a `@Mock GroupService groupService` and construct `new SettingsService(repository, groupService)`. Cover:

```java
@Test
void changedGroupConfigurationSavesThenAppendsCurrentPlan() {
    UserSettings settings = settings(7L, 19L, 20, GroupStrategy.book_order);
    when(repository.findById(7L)).thenReturn(Optional.of(settings));
    PreferencesPatchRequest request = new PreferencesPatchRequest();
    request.setGroupSize(30);
    request.setGroupStrategy(GroupStrategy.frequency);

    var response = service.patchPreferences(7L, request);

    assertThat(response.getGroupSize()).isEqualTo(30);
    assertThat(response.getGroupStrategy()).isEqualTo(GroupStrategy.frequency);
    InOrder order = inOrder(repository, groupService);
    order.verify(repository).save(settings);
    order.verify(groupService).appendAutoGroups(7L, 19L);
}
```

Also test `groupSize=25` returns `WordflipException` with code `VALIDATION_ERROR`, unchanged values do not append, and changed values with `activePlanId=null` only save.

- [ ] **Step 2: Run the settings test and verify RED**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=SettingsServiceTest test`

Expected: compilation/behavior FAIL because request and service do not support grouping fields.

- [ ] **Step 3: Add DTO fields and transactional service logic**

```java
private Integer groupSize;
private GroupStrategy groupStrategy;

public boolean hasAnyField() {
    return groupSize != null || groupStrategy != null
            || autoSpeak != null || themeMode != null
            || heatDisplayMode != null || quizLaunchMode != null
            || defaultQuestionLimit != null;
}
```

In `SettingsService`, compute `groupingChanged` before mutation, validate membership in `Set.of(10, 20, 30, 50)`, save, then call `appendAutoGroups` only for a changed configuration with a current plan. Keep the whole method under the existing `@Transactional` annotation.

- [ ] **Step 4: Update controller/service comments**

Replace “仅更新偏好、不触发 append” with Chinese documentation that group configuration updates may append unassigned cards but never regroup existing members or write memories.

- [ ] **Step 5: Run settings and learning-plan tests**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=SettingsServiceTest,LearningPlanServiceTest test`

Expected: PASS; existing learning plan activation still calls append once.

- [ ] **Step 6: Record the suggested checkpoint**

Suggested commit: `feat(server): 支持保存当前计划分组配置`

Do not commit unless the user explicitly authorizes it.

---

### Task 4: Return per-book plan status and progress

**Files:**
- Modify: `wordflip-server/src/main/java/com/wordflip/dto/book/BookListResponse.java`
- Modify: `wordflip-server/src/main/java/com/wordflip/service/BookService.java`
- Create: `wordflip-server/src/test/java/com/wordflip/service/BookServiceTest.java`
- Create: `wordflip-server/src/test/java/com/wordflip/database/BookSettingsMySqlIntegrationTest.java`

**Interfaces:**
- Produces: `BookListResponse.BookProgress(int masteredCount, int assignedCardCount, int completionPercent)`.
- Produces: `BookItem.planId()`, `planStatus()`, and nullable `progress()` matching Task 1.

- [ ] **Step 1: Write failing BookService unit tests**

Use a mocked `JdbcTemplate` and `ResultSet` to exercise the row mapper for:

```java
assertThat(current.planId()).isEqualTo(19L);
assertThat(current.planStatus()).isEqualTo("active");
assertThat(current.selected()).isTrue();
assertThat(current.progress()).isEqualTo(new BookProgress(3, 5, 60));

assertThat(available.planId()).isNull();
assertThat(available.planStatus()).isNull();
assertThat(available.progress()).isNull();
```

Capture the SQL and arguments and assert the memory join contains `m.user_id=?`, `m.skill='dictation'`, the plan join contains `p.user_id=?`, and every invocation passes the authenticated user ID for both constraints.

- [ ] **Step 2: Run BookServiceTest and verify RED**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=BookServiceTest test`

Expected: compilation FAIL because the DTO lacks plan/progress fields.

- [ ] **Step 3: Add DTO and query aggregation**

```java
public record BookProgress(
        int masteredCount,
        int assignedCardCount,
        int completionPercent
) {}

public record BookItem(
        long id,
        String name,
        String source,
        int wordCount,
        Integer declaredCount,
        boolean selected,
        boolean canDelete,
        Long planId,
        String planStatus,
        BookProgress progress
) {}
```

Build the progress aggregation from `study_group_cards` and a user-filtered dictation memory join. Use `COUNT(DISTINCT sgc.card_id)` for assigned cards and `COUNT(DISTINCT CASE WHEN m.stability>=30 THEN sgc.card_id END)` for mastered cards. Return `progress=null` when `plan_id` is SQL NULL; otherwise compute `Math.round(mastered * 100f / assigned)` with zero fallback.

- [ ] **Step 4: Add a real MySQL integration test**

Create a Testcontainers class using the same datasource properties as `WordFlipV2MySqlIntegrationTest`. Seed two users, three public books, two plans, group members, and dictation/choice memories. Autowire `BookService` and the proxied `SettingsService`; replace only `GroupService` with Spring `@MockBean` so `doThrow(...)` can verify that the surrounding settings transaction rolls back while the real JPA repository and MySQL connection remain active. Assert:

```java
var books = bookService.listBooks(userOneId).books();
assertThat(books).extracting(BookItem::planStatus)
        .containsExactly("active", "paused", null);
assertThat(books.get(0).progress())
        .isEqualTo(new BookProgress(1, 2, 50));
```

The second user's mastered memory for the same card must not increase user one's result. In the rollback test, configure `doThrow(new IllegalStateException("append failed"))` for `groupService.appendAutoGroups(userOneId, planId)`, call the proxied `settingsService.patchPreferences`, then query MySQL in a new transaction and assert `group_size` remains unchanged. Assert memory/event row counts are unchanged by settings patch.

- [ ] **Step 5: Run unit and MySQL tests**

Run: `cd wordflip-server; .\mvnw.cmd -Dtest=BookServiceTest,BookSettingsMySqlIntegrationTest test`

Expected: PASS with `BookSettingsMySqlIntegrationTest` executed, not skipped. If Docker is unavailable, stop and report instead of accepting a skip.

- [ ] **Step 6: Record the suggested checkpoint**

Suggested commit: `feat(server): 返回词书计划状态与学习进度`

Do not commit unless the user explicitly authorizes it.

---

### Task 5: Share one authenticated HTTP runtime in Web

**Files:**
- Create: `wordflip-web/src/data/http/createHttpRuntime.ts`
- Create: `wordflip-web/src/data/http/createHttpRuntime.test.ts`
- Modify: `wordflip-web/src/data/http/auth/HttpAuthRepository.ts`

**Interfaces:**
- Produces: `HttpRuntime { publicClient, authenticatedClient, sessions }`.
- Consumed by: Tasks 6–8.

- [ ] **Step 1: Write the failing runtime test**

```ts
test("运行时只创建一套会话并供多个业务请求共享刷新", async () => {
  const runtime = createHttpRuntime({ baseURL: "/api/v1", storage: window.localStorage });
  expect(runtime.publicClient).toBeDefined();
  expect(runtime.authenticatedClient).toBeDefined();
  expect(runtime.sessions).toBeDefined();
});
```

Extend the test with two concurrent requests (`/books`, `/settings`) that both receive the old-token 401 and assert the refresh adapter is called once, reusing the existing `createHttpClient.test.ts` adapter pattern.

- [ ] **Step 2: Run and verify RED**

Run: `cd wordflip-web; npm test -- --run src/data/http/createHttpRuntime.test.ts`

Expected: FAIL because `createHttpRuntime` does not exist.

- [ ] **Step 3: Implement the shared runtime**

```ts
export function createHttpRuntime({ baseURL, storage, now }: HttpRuntimeOptions): HttpRuntime {
  const publicClient = createPublicHttpClient(baseURL);
  const tokens = new TokenStore(storage);
  const sessions = new AuthSessionManager(tokens, async (refreshToken) => {
    const response = await publicClient.post<AuthResponseDto>("/auth/refresh", { refreshToken });
    return response.data;
  }, now);
  return {
    publicClient,
    authenticatedClient: createAuthenticatedHttpClient({ baseURL, sessions }),
    sessions
  };
}
```

Keep `createHttpAuthRepository(options)` as a compatibility wrapper that calls this factory, while exporting the `HttpAuthRepository` constructor for bundle composition.

- [ ] **Step 4: Run HTTP/auth tests and verify GREEN**

Run: `cd wordflip-web; npm test -- --run src/data/http/createHttpRuntime.test.ts src/data/http/createHttpClient.test.ts src/data/http/auth/HttpAuthRepository.test.ts`

Expected: all tests pass; no regression in single-refresh behavior.

- [ ] **Step 5: Record the suggested checkpoint**

Suggested commit: `refactor(web): 共享认证 HTTP 运行时`

Do not commit unless the user explicitly authorizes it.

---

### Task 6: Implement HttpBookRepository and accurate progress models

**Files:**
- Create: `wordflip-web/src/data/http/books/bookDtos.ts`
- Create: `wordflip-web/src/data/http/books/bookMappers.ts`
- Create: `wordflip-web/src/data/http/books/HttpBookRepository.ts`
- Create: `wordflip-web/src/data/http/books/HttpBookRepository.test.ts`
- Modify: `wordflip-web/src/domain/books.ts`
- Modify: `wordflip-web/src/data/mock/createDemoState.ts`
- Modify: `wordflip-web/src/data/mock/DemoStateStore.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts`
- Modify: `wordflip-web/src/features/books/BooksPage.tsx`
- Modify: `wordflip-web/src/features/books/BookDetailPage.tsx`

**Interfaces:**
- Consumes: `AxiosInstance` from Task 5 and Task 1 DTO shapes.
- Produces: `HttpBookRepository implements BookRepository`.

- [ ] **Step 1: Rename the domain progress contract with compile-time RED**

```ts
export interface BookProgress {
  masteredCount: number;
  assignedCardCount: number;
  completionPercent: number;
}
```

Update tests first to expect `126 / 300` through these names, then run the focused books tests before fixing fixtures.

Run: `cd wordflip-web; npm test -- --run src/features/books/BooksFlow.test.tsx`

Expected: TypeScript/Vitest FAIL until fixtures and pages use the new names.

- [ ] **Step 2: Migrate only book-progress fixtures and views**

Replace `bookProgress.learnedCount/publishedCardCount/completionRate` with `masteredCount/assignedCardCount/completionPercent`. Do not rename the unrelated Today domain's `completionRate`.

```tsx
<strong>{book.progress.completionPercent}%</strong>
<span>{book.progress.masteredCount} / {book.progress.assignedCardCount} 张已掌握</span>
```

Update `DemoStateStore` validation and all `planState.bookProgress` snapshots.

- [ ] **Step 3: Write failing HttpBookRepository tests**

Use an authenticated Axios adapter and assert:

```ts
await expect(repository.list()).resolves.toEqual([
  expect.objectContaining({
    bookId: "8",
    planId: "19",
    planStatus: "current",
    progress: { masteredCount: 3, assignedCardCount: 5, completionPercent: 60 }
  }),
  expect.objectContaining({ bookId: "9", planStatus: "history" }),
  expect.objectContaining({ bookId: "10", planStatus: "available", progress: null })
]);
```

Also assert exact calls for `GET /books`, `GET /books/8`, `POST /learning-plans` with `{ bookId: 8 }`, and `PATCH /learning-plans/current` with `{ planId: 19 }`. A 404 from current-plan GET must resolve `null`; a 404 from book detail must reject.

- [ ] **Step 4: Run repository test and verify RED**

Run: `cd wordflip-web; npm test -- --run src/data/http/books/HttpBookRepository.test.ts`

Expected: FAIL because DTOs, mapper, and Repository do not exist.

- [ ] **Step 5: Implement DTOs, mapper, and Repository**

```ts
export function mapBookOverview(dto: BookItemDto): BookOverview {
  return {
    bookId: String(dto.id),
    title: dto.name,
    cardCount: dto.wordCount,
    planId: dto.planId === null ? null : String(dto.planId),
    planStatus: dto.selected ? "current" : dto.planId === null ? "available" : "history",
    progress: dto.progress && {
      masteredCount: dto.progress.masteredCount,
      assignedCardCount: dto.progress.assignedCardCount,
      completionPercent: dto.progress.completionPercent
    }
  };
}
```

Keep numeric parsing strict: IDs must be finite positive integers before requests; invalid domain IDs reject with a `validation` AppError rather than producing `/books/NaN`.

- [ ] **Step 6: Run books and mock regression tests**

Run: `cd wordflip-web; npm test -- --run src/data/http/books/HttpBookRepository.test.ts src/features/books/BooksFlow.test.tsx src/data/mock/repositories/MockOnboardingRepositories.test.ts`

Expected: PASS with unchanged Mock visual assertions.

- [ ] **Step 7: Record the suggested checkpoint**

Suggested commit: `feat(web): 接入真实词书与学习计划接口`

Do not commit unless the user explicitly authorizes it.

---

### Task 7: Implement HttpSettingsRepository and device-local motion preference

**Files:**
- Create: `wordflip-web/src/data/http/settings/settingsDtos.ts`
- Create: `wordflip-web/src/data/http/settings/LocalSettingsStore.ts`
- Create: `wordflip-web/src/data/http/settings/LocalSettingsStore.test.ts`
- Create: `wordflip-web/src/data/http/settings/HttpSettingsRepository.ts`
- Create: `wordflip-web/src/data/http/settings/HttpSettingsRepository.test.ts`
- Modify: `wordflip-web/src/domain/settings.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts`

**Interfaces:**
- Consumes: shared authenticated `AxiosInstance` and the learning-plan mapper from Task 6.
- Produces: `HttpSettingsRepository implements SettingsRepository` and `supportsDemoReset(): boolean`.

- [ ] **Step 1: Write failing LocalSettingsStore tests**

```ts
test("只保存 reducedMotion 并忽略损坏记录", () => {
  const store = new LocalSettingsStore(window.localStorage);
  expect(store.readReducedMotion()).toBe(false);
  store.writeReducedMotion(true);
  expect(store.readReducedMotion()).toBe(true);
  window.localStorage.setItem(LOCAL_SETTINGS_KEY, "broken");
  expect(store.readReducedMotion()).toBe(false);
});
```

- [ ] **Step 2: Run the local store test and verify RED**

Run: `cd wordflip-web; npm test -- --run src/data/http/settings/LocalSettingsStore.test.ts`

Expected: FAIL because the store does not exist.

- [ ] **Step 3: Implement the versioned local record**

Use the key `wordflip.web.settings.v1` and exact shape `{ version: 1, reducedMotion: boolean }`. `storage=null`, invalid JSON, wrong version, or wrong type returns `false`; writes must not include server settings or tokens.

- [ ] **Step 4: Write failing HttpSettingsRepository tests**

Cover:

```ts
await expect(repository.getSettings()).resolves.toEqual({
  soundEnabled: true,
  reducedMotion: true,
  groupSize: 20
});

await repository.updateSettings({ soundEnabled: false, reducedMotion: true, groupSize: 30 });
expect(patchBody).toEqual({ autoSpeak: false, groupSize: 30, groupStrategy: "book_order" });

await repository.saveOnboarding({ bookId: "8", groupSize: 30, groupStrategy: "book_order" });
expect(requests.map((request) => request.url)).toEqual([
  "/settings/preferences",
  "/learning-plans"
]);
```

Reject the second onboarding request once, retry, and assert the second attempt sends the same two idempotent operations. Assert `supportsDemoReset()` is false and `resetDemo()` rejects without a network request.

- [ ] **Step 5: Run repository tests and verify RED**

Run: `cd wordflip-web; npm test -- --run src/data/http/settings/HttpSettingsRepository.test.ts`

Expected: FAIL because the Repository and capability method do not exist.

- [ ] **Step 6: Implement settings mapping and onboarding sequence**

```ts
async saveOnboarding(input: OnboardingInput): Promise<LearningPlan> {
  await this.client.patch("/settings/preferences", {
    groupSize: input.groupSize,
    groupStrategy: input.groupStrategy
  });
  const response = await this.client.post<LearningPlanDto>("/learning-plans", {
    bookId: parsePositiveId(input.bookId, "bookId")
  });
  return mapLearningPlan(response.data);
}
```

`updateSettings` writes local `reducedMotion` only after the server PATCH succeeds, so a rejected server save leaves both the displayed snapshot and local preference unchanged.

- [ ] **Step 7: Make MockSettingsRepository advertise reset support**

```ts
supportsDemoReset(): boolean {
  return true;
}
```

Keep existing `resetDemo()` semantics and tests unchanged.

- [ ] **Step 8: Run settings repository tests and verify GREEN**

Run: `cd wordflip-web; npm test -- --run src/data/http/settings/LocalSettingsStore.test.ts src/data/http/settings/HttpSettingsRepository.test.ts src/data/mock/repositories/MockOnboardingRepositories.test.ts`

Expected: PASS.

- [ ] **Step 9: Record the suggested checkpoint**

Suggested commit: `feat(web): 接入真实设置与首次学习计划`

Do not commit unless the user explicitly authorizes it.

---

### Task 8: Wire HTTP mode, preserve Mock mode, and verify the UI

**Files:**
- Modify: `wordflip-web/src/data/runtime/createRepositoryBundle.ts`
- Modify: `wordflip-web/src/data/runtime/createRepositoryBundle.test.ts`
- Modify: `wordflip-web/src/features/settings/SettingsPage.tsx`
- Modify: `wordflip-web/src/features/settings/SettingsPage.test.tsx`
- Modify: `wordflip-web/src/features/onboarding/OnboardingPage.test.tsx`
- Modify: `wordflip-web/src/features/books/BooksFlow.test.tsx`
- Modify: `TASK.md`

**Interfaces:**
- Consumes: `createHttpRuntime`, `HttpBookRepository`, and `HttpSettingsRepository`.
- Produces: complete WEB-API02 runtime behavior.

- [ ] **Step 1: Update runtime test to fail on the old bundle**

```ts
test("http 数据源替换认证、词书和设置，其余模块保留 Mock", () => {
  const repositories = createRepositoryBundle(httpOptions());
  expect(repositories.auth).toBeInstanceOf(HttpAuthRepository);
  expect(repositories.books).toBeInstanceOf(HttpBookRepository);
  expect(repositories.settings).toBeInstanceOf(HttpSettingsRepository);
  expect(repositories.quiz).toBeInstanceOf(MockQuizRepository);
});
```

- [ ] **Step 2: Run runtime test and verify RED**

Run: `cd wordflip-web; npm test -- --run src/data/runtime/createRepositoryBundle.test.ts`

Expected: FAIL because books/settings are still Mock.

- [ ] **Step 3: Compose all three HTTP repositories from one runtime**

```ts
const runtime = createHttpRuntime({ baseURL: normalizedBaseUrl, storage });
return {
  ...repositories,
  auth: new HttpAuthRepository(runtime.publicClient, runtime.authenticatedClient, runtime.sessions),
  books: new HttpBookRepository(runtime.authenticatedClient),
  settings: new HttpSettingsRepository(
    runtime.authenticatedClient,
    new LocalSettingsStore(storage)
  )
};
```

Update the Chinese comment from WEB-API01 to WEB-API02 and keep the missing-base-URL error unchanged.

- [ ] **Step 4: Write failing SettingsPage capability/error tests**

Render once with a Repository whose `supportsDemoReset()` returns false and assert “演示数据” and “重置演示数据” are absent. Reject `updateSettings` and assert an accessible alert is shown while the form remains editable and no success status appears.

```ts
expect(screen.queryByRole("button", { name: "重置演示数据" })).not.toBeInTheDocument();
expect(await screen.findByRole("alert")).toHaveTextContent("暂时无法保存设置");
```

- [ ] **Step 5: Implement the minimal page behavior**

Guard the demo panel and dialog with `settings.supportsDemoReset()`. Wrap `save()` in `try/catch`, clear the prior error before submission, and display save errors through the existing accessible error style. Do not change the layout of Mock mode.

- [ ] **Step 6: Run focused Web flows**

Run: `cd wordflip-web; npm test -- --run src/data/runtime/createRepositoryBundle.test.ts src/features/settings/SettingsPage.test.tsx src/features/onboarding/OnboardingPage.test.tsx src/features/books/BooksFlow.test.tsx`

Expected: PASS for Mock regression and HTTP capability behavior.

- [ ] **Step 7: Run complete automated verification**

```powershell
cd wordflip-api
python -m pytest -q tests

cd ..\wordflip-server
.\mvnw.cmd test

cd ..\wordflip-web
npm test -- --run
npm run lint
npm run build
npm run test:e2e
```

Expected:

- API tests all pass.
- Spring Boot tests all pass; Testcontainers MySQL tests report executed, not skipped.
- Web Vitest, ESLint, production build, and Playwright all pass.
- No Android command is run.

- [ ] **Step 8: Perform a real local smoke test**

Start Spring Boot with the dev profile and Web with:

```powershell
$env:VITE_DATA_SOURCE='http'
$env:VITE_API_BASE_URL='http://127.0.0.1:8080/api/v1'
npm run dev
```

Verify in the browser:

1. A user without an active plan sees onboarding and real books.
2. Saving group size 30 sends settings first, then creates the plan and enters Today.
3. Books shows current/history/available states and real progress.
4. Switching away and back retains the old plan and progress.
5. Settings reloads remote sound/group size and local reduced-motion state.
6. HTTP settings hides the demo-reset panel.
7. A forced backend failure displays an error and never substitutes Mock data.

- [ ] **Step 9: Mark the task complete only with evidence**

Change `TASK.md`:

```markdown
- [x] **WEB-API02** 学习计划、词书与设置
```

Do not mark it if the real MySQL test was skipped or the smoke test was not completed; instead report the missing evidence.

- [ ] **Step 10: Check the final diff**

Run:

```powershell
git status --short
git diff --check
git diff --stat
```

Confirm `.vite/` remains untracked and absent from the diff, no Android file changed, no migration was added, and no secrets or `.env` files are staged.

- [ ] **Step 11: Record the suggested final checkpoint**

Suggested commit: `feat(web): 接入学习计划词书与设置接口`

Do not commit or push unless the user explicitly authorizes it.
