# WordFlip Web 今日与当前计划分组 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `WEB-API03`，让 Web 今日页、当前计划分组、分组详情与自定义分组使用 Spring Boot/MySQL 的真实权威数据，并为下一步真实卡片墙提供稳定的 `groupId` 入口。

**Architecture:** 按 OpenAPI-first 顺序先锁定 Today/Groups 契约，再用 Spring JDBC 修正当前计划隔离、掌握口径与分组事务，最后通过共享认证 Axios 客户端注入 `HttpTodayRepository` 和 `HttpGroupRepository`。页面只消费领域模型；Mock 与 HTTP 实现遵守同一 Repository 契约，HTTP 失败不回退 Mock。

**Tech Stack:** OpenAPI 3.0.3 + pytest、Java 21 + Spring Boot 3.3 + Spring JDBC + JUnit 5/Mockito/Testcontainers MySQL、React 18 + TypeScript 5.7 + Axios + Vitest/Testing Library + Playwright。

## Global Constraints

- requirements v7 高于 OpenAPI、数据库设计和局部计划；冲突时不得沿用旧 v6/v5 行为。
- Today、Groups、Study、Quiz、Stats 只使用唯一当前 `activePlanId`；历史计划资源通过当前计划接口访问时统一返回 404。
- `cardId` 是学习进度和分组成员主键；`wordKey` 只用于查询与展示。
- 已掌握必须同时满足 `skill='dictation'`、`state='review'`、`stability >= 80`、`scheduled_days >= 30`、最近对应 skill 测验 `correct=true`。
- heat0–heat4 是展示分档；heat4 不等于已掌握。
- Today/Groups/自定义分组不得写 `card_skill_memory`、`lexeme_skill_memory` 或 `review_events`。
- HTTP 模式中已接入模块失败时不得回退 Mock；页面不得直接调用 Axios。
- 新增或修改业务代码必须包含简体中文注释。
- Android 暂缓；本计划不修改 `wordflip-android/`。
- 本计划不新增 Flyway 迁移。
- Bug 修复和行为变化先写失败测试，再写最小实现。
- 执行过程中不自动 `git commit` 或 `git push`；每个任务末尾只记录建议提交点，必须获得用户明确授权后才能执行。

---

## File Structure Map

### API

- Create: `wordflip-api/tests/test_today_groups_contract.py` — 锁定 Today/Groups required、错误码、分页和掌握口径。
- Modify: `wordflip-api/openapi.yaml` — 收敛现有路径和 schema，不新增替代端点。

### Server

- Create: `wordflip-server/src/test/java/com/wordflip/service/TodayServiceTest.java` — Today SQL 口径、时区与只读行为单测。
- Create: `wordflip-server/src/test/java/com/wordflip/util/UserTimeZoneUtilTest.java` — IANA 时区解析与非法值安全回退。
- Create: `wordflip-server/src/test/java/com/wordflip/service/GroupServiceTest.java` — Groups 隔离、进度、校验与事务边界单测。
- Modify: `wordflip-server/src/main/java/com/wordflip/service/TodayService.java` — 完整已掌握条件、去重任务和当前计划聚合。
- Modify: `wordflip-server/src/main/java/com/wordflip/service/GroupService.java` — 完整进度条件、参数校验、事务安全。
- Modify: `wordflip-server/src/main/java/com/wordflip/controller/GroupController.java` — 保持控制器仅转发参数，错误由服务层标准化。
- Modify: `wordflip-server/src/test/java/com/wordflip/controller/GroupControllerTest.java` — 锁定真实路径和 cardId 请求。
- Create: `wordflip-server/src/test/java/com/wordflip/database/TodayGroupsMySqlIntegrationTest.java` — 用真实 MySQL 验证隔离、只读副作用和自定义分组回滚。

### Web Data

- Modify: `wordflip-web/src/domain/today.ts` — 与 `/today` 对齐的领域模型。
- Modify: `wordflip-web/src/domain/groups.ts` — 分组、分页卡片、自定义分组领域契约。
- Create: `wordflip-web/src/data/http/today/todayDtos.ts` — Today API DTO。
- Create: `wordflip-web/src/data/http/today/todayMappers.ts` — 数字 ID 到字符串 ID 的纯映射。
- Create: `wordflip-web/src/data/http/today/HttpTodayRepository.ts` — `/today` 请求。
- Create: `wordflip-web/src/data/http/today/HttpTodayRepository.test.ts` — 时区头、映射与错误测试。
- Create: `wordflip-web/src/data/http/groups/groupDtos.ts` — Groups、卡片进度与分页 DTO。
- Create: `wordflip-web/src/data/http/groups/groupMappers.ts` — 分组和只读卡片映射。
- Create: `wordflip-web/src/data/http/groups/HttpGroupRepository.ts` — Groups 六个端点请求。
- Create: `wordflip-web/src/data/http/groups/HttpGroupRepository.test.ts` — 路径、参数、ID 和 409 测试。
- Modify: `wordflip-web/src/data/mock/repositories/MockTodayRepository.ts` — 实现新 Today 领域契约。
- Modify: `wordflip-web/src/data/mock/repositories/MockGroupRepository.ts` — 实现新 Groups 契约，不再暴露 `appendMembers`。
- Modify: `wordflip-web/src/data/mock/createDemoState.ts` — 版本化固定快照迁移到新领域结构。
- Modify: `wordflip-web/src/data/mock/DemoStateStore.ts` — v5 持久化键与新 Today/Groups 运行时校验。
- Modify: `wordflip-web/src/data/mock/DemoStateStore.test.ts` — v4 失效与 v5 恢复测试。
- Modify: `wordflip-web/src/data/mock/quizFixtures.ts` — 测验预计算快照迁移到新 Today 结构。
- Modify: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts` — 新计划的 Today/Groups 初始快照。
- Modify: `wordflip-web/src/data/mock/repositories/MockOnboardingRepositories.test.ts` — 新快照结构断言。
- Modify: `wordflip-web/src/data/runtime/createRepositoryBundle.ts` — HTTP 模式替换 Today/Groups。
- Modify: `wordflip-web/src/data/http/createHttpRuntime.test.ts` — 锁定共享认证客户端请求。

### Web UI

- Modify: `wordflip-web/src/features/today/TodayPage.tsx` — 真实日期、三项统计、最近分组和真实 groupId 入口。
- Modify: `wordflip-web/src/features/today/TodayPage.module.css` — 最近分组置前与三栏摘要。
- Modify: `wordflip-web/src/features/today/TodayPage.test.tsx` — 删除固定 Mock 断言，覆盖 v7 页面行为。
- Modify: `wordflip-web/src/features/books/BooksFlow.test.tsx` — 适配 v5 演示状态中的 Today/Groups 字段。
- Modify: `wordflip-web/src/features/study/StudyFlow.test.tsx` — 学习完成后的 Today 快照断言迁移。
- Modify: `wordflip-web/src/features/settings/SettingsPage.test.tsx` — 重置演示数据断言迁移。
- Modify: `wordflip-web/src/features/media/MediaPage.test.tsx` — 损坏快照恢复断言迁移。
- Modify: `wordflip-web/src/features/stats/StatsPage.test.tsx` — 持久化快照断言迁移。
- Modify: `wordflip-web/src/features/groups/GroupsPage.tsx` — 状态、热力、进度和新建入口。
- Modify: `wordflip-web/src/features/groups/GroupDetailPage.tsx` — 元数据与分页卡片只读展示。
- Create: `wordflip-web/src/features/groups/CustomGroupPage.tsx` — 未入组卡片选择与创建。
- Modify: `wordflip-web/src/features/groups/groups.module.css` — 分组统计、分页与 chips。
- Modify: `wordflip-web/src/features/groups/GroupsFlow.test.tsx` — 列表、详情、自定义分组和错误恢复。
- Modify: `wordflip-web/src/app/App.tsx` — 增加 `/groups/new` 静态路由。

### Tracking and Acceptance

- Modify: `wordflip-web/e2e/happy-path.spec.ts` — Mock 模式保持完整可点击回归，使用真实 groupId 语义。
- Modify: `TASK.md` — 只有全部证据通过后勾选 `WEB-API03`。

---

### Task 1: Lock the Today/Groups OpenAPI contract

**Files:**
- Create: `wordflip-api/tests/test_today_groups_contract.py`
- Modify: `wordflip-api/openapi.yaml:414`
- Modify: `wordflip-api/openapi.yaml:512`
- Modify: `wordflip-api/openapi.yaml:1560`

**Interfaces:**
- Consumes: requirements `REQ-TODAY-1～13`、`REQ-CG-1～5`、`REQ-GROUP-1～5`、`REQ-GDETAIL-1～10`。
- Produces: 现有六个端点的稳定 OAS schema；后端 DTO 和 Web DTO 必须按这些字段实现。

- [ ] **Step 1: Write the failing contract tests**

```python
class TodayGroupsContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        path = Path(__file__).resolve().parents[1] / "openapi.yaml"
        cls.spec = yaml.safe_load(path.read_text(encoding="utf-8"))
        cls.schemas = cls.spec["components"]["schemas"]

    def test_today_requires_complete_dashboard(self) -> None:
        dashboard = self.schemas["TodayDashboard"]
        self.assertTrue(
            {"date", "streakDays", "stats", "tasks", "recommendedStudy", "recentGroups"}
            .issubset(dashboard["required"])
        )
        tasks = dashboard["properties"]["tasks"]
        self.assertEqual(
            ["newWords", "dueReview", "quiz"],
            tasks["required"],
        )

    def test_group_envelopes_and_page_cards_are_required(self) -> None:
        self.assertEqual(["groups"], self.schemas["GroupListResponse"]["required"])
        self.assertIn("cards", self.schemas["GroupCardsResponse"]["allOf"][1]["required"])
        self.assertIn("cards", self.schemas["UnassignedCardsResponse"]["allOf"][1]["required"])

    def test_mastery_descriptions_lock_authoritative_rule(self) -> None:
        serialized = yaml.safe_dump(
            {"today": self.schemas["TodayDashboard"], "group": self.schemas["GroupDetail"]},
            allow_unicode=True,
        )
        for fragment in ("dictation", "state='review'", "stability >= 80", "scheduled_days >= 30", "correct=true"):
            self.assertIn(fragment, serialized)

    def test_protected_operations_declare_expected_errors(self) -> None:
        paths = self.spec["paths"]
        expected = {
            ("/today", "get"): {"200", "401", "404"},
            ("/groups", "get"): {"200", "400", "401", "404"},
            ("/groups/{groupId}", "get"): {"200", "401", "404"},
            ("/groups/{groupId}/cards", "get"): {"200", "400", "401", "404"},
            ("/learning/cards/unassigned", "get"): {"200", "400", "401", "404"},
            ("/groups/custom", "post"): {"201", "400", "401", "404", "409"},
        }
        for (path, method), statuses in expected.items():
            self.assertTrue(statuses.issubset(paths[path][method]["responses"]))
```

- [ ] **Step 2: Run the contract test and verify it fails**

Run:

```powershell
cd wordflip-api
python -m pytest -q tests/test_today_groups_contract.py
```

Expected: FAIL on missing `required` fields, mastery description, or declared error responses.

- [ ] **Step 3: Make the minimal OpenAPI changes**

Add/adjust these concrete contract elements:

```yaml
GroupListResponse:
  type: object
  required: [groups]

TodayDashboard:
  type: object
  required: [date, streakDays, stats, tasks, recommendedStudy, recentGroups]
  properties:
    tasks:
      type: object
      required: [newWords, dueReview, quiz]
```

For `recommendedStudy`, retain optional object content but mark the property `nullable: true`. Add `required: [groupId, groupName, count]` to `TodayTask.sources.items`, and document the full mastery condition on both `masteredCount` and `GroupDetail.progress`. Add reusable 400/401/404/409 responses to each operation listed in the failing test.

- [ ] **Step 4: Verify the API contract**

```powershell
cd wordflip-api
python -c "import yaml; yaml.safe_load(open('openapi.yaml', encoding='utf-8'))"
python -m pytest -q tests
```

Expected: YAML parse succeeds and every API test passes.

- [ ] **Step 5: Record the review checkpoint**

Review only `openapi.yaml` and `test_today_groups_contract.py`. Suggested commit after explicit authorization: `test(api): 锁定今日与分组接口契约`.

---

### Task 2: Correct TodayService authoritative calculations

**Files:**
- Create: `wordflip-server/src/test/java/com/wordflip/service/TodayServiceTest.java`
- Create: `wordflip-server/src/test/java/com/wordflip/util/UserTimeZoneUtilTest.java`
- Modify: `wordflip-server/src/main/java/com/wordflip/service/TodayService.java:31`

**Interfaces:**
- Consumes: `TodayDashboard`, `TodayStats`, `TodayTasks`, `TaskSource`, `RecommendedStudy`, `RecentGroupDto` existing records.
- Produces: `TodayService#getDashboard(Long userId, ZoneId zoneId)` with complete mastery and current-plan-only queries.

- [ ] **Step 1: Write failing SQL-boundary tests**

Use Mockito to capture every `JdbcTemplate.queryForObject` SQL call, following `StatsServiceTest`. The mastery assertion must require:

```java
assertThat(normalizedSql).contains(
        "sgc.plan_id=?",
        "m.user_id=?",
        "m.skill='dictation'",
        "m.state='review'",
        "m.stability>=80",
        "m.scheduled_days>=30",
        "r.correct=TRUE",
        "ORDERBYr2.answered_atDESC,r2.idDESCLIMIT1"
);
```

Add separate tests that assert:

```java
assertThat(response.stats().dueReviewCount())
        .isEqualTo(response.tasks().dueReview().count());
assertThat(response.stats().completionPercent()).isEqualTo(67); // mastered=2,total=3
assertThat(response.recentGroups()).hasSizeLessThanOrEqualTo(3);
```

The recent-group SQL test must require both activity sources:

```java
assertThat(normalizedRecentSql).contains(
        "FROMstudy_logssl",
        "FROMquiz_sessionsqs",
        "JOINquiz_questionsqq",
        "JOINstudy_group_cardssgc",
        "qs.status='completed'",
        "LIMIT3"
);
```

Add `UserTimeZoneUtilTest` with exact expectations:

```java
assertThat(UserTimeZoneUtil.resolveZone("Europe/Paris"))
        .isEqualTo(ZoneId.of("Europe/Paris"));
assertThat(UserTimeZoneUtil.resolveZone("invalid/timezone"))
        .isEqualTo(ZoneId.of("Asia/Shanghai"));
assertThat(UserTimeZoneUtil.resolveZone("   "))
        .isEqualTo(ZoneId.of("Asia/Shanghai"));
```

Also capture table counts before and after `getDashboard` in the later MySQL test; unit tests do not pretend to prove read-only side effects.

- [ ] **Step 2: Run the focused test and verify the old `>=30` query fails**

```powershell
cd wordflip-server
.\mvnw.cmd -Dtest=TodayServiceTest test
```

Expected: FAIL because the current mastery SQL lacks `state`, `scheduled_days` and latest successful review conditions.

- [ ] **Step 3: Implement the minimal TodayService query correction**

Replace the mastery query with a current-plan, latest-review query equivalent to:

```sql
SELECT COUNT(DISTINCT sgc.card_id)
  FROM study_group_cards sgc
  JOIN card_skill_memory m
    ON m.card_id=sgc.card_id AND m.user_id=? AND m.skill='dictation'
  JOIN review_events r
    ON r.user_id=m.user_id AND r.plan_id=sgc.plan_id
   AND r.card_id=m.card_id AND r.skill=m.skill
   AND r.id=(
       SELECT r2.id FROM review_events r2
        WHERE r2.user_id=? AND r2.plan_id=sgc.plan_id
          AND r2.card_id=m.card_id AND r2.skill=m.skill
        ORDER BY r2.answered_at DESC, r2.id DESC LIMIT 1
   )
 WHERE sgc.plan_id=? AND m.state='review'
   AND m.stability>=80 AND m.scheduled_days>=30 AND r.correct=TRUE
```

Compute due once and pass the same integer to `TodayStats` and `TodayTask`. Keep `LocalDate.now(zoneId)` for the display/streak date and `Instant.now()` for UTC due comparisons. Add Chinese comments explaining why the latest review tie-breaks on `id`.

Replace `recentGroups` with a read-only union of grouped study and completed quiz activity. A completed quiz has no direct `group_id`, so derive its groups through `quiz_questions.card_id → study_group_cards(plan_id, card_id)`:

```sql
SELECT g.id, g.name, MAX(activity.activity_at) AS last_studied
  FROM (
        SELECT sl.group_id, sl.created_at AS activity_at
          FROM study_logs sl
         WHERE sl.user_id=? AND sl.plan_id=? AND sl.group_id IS NOT NULL
        UNION ALL
        SELECT sgc.group_id, qs.completed_at AS activity_at
          FROM quiz_sessions qs
          JOIN quiz_questions qq ON qq.session_id=qs.id
          JOIN study_group_cards sgc
            ON sgc.plan_id=qs.plan_id AND sgc.card_id=qq.card_id
         WHERE qs.user_id=? AND qs.plan_id=?
           AND qs.status='completed' AND qs.completed_at IS NOT NULL
       ) activity
  JOIN study_groups g ON g.id=activity.group_id
 WHERE g.plan_id=?
 GROUP BY g.id, g.name
 ORDER BY last_studied DESC
 LIMIT 3
```

- [ ] **Step 4: Run TodayService and related stats tests**

```powershell
cd wordflip-server
.\mvnw.cmd -Dtest=TodayServiceTest,StatsServiceTest,BookServiceTest,UserTimeZoneUtilTest test
```

Expected: all selected tests pass.

- [ ] **Step 5: Record the review checkpoint**

Suggested commit after explicit authorization: `fix(server): 统一今日页权威掌握口径`.

---

### Task 3: Correct GroupService progress, validation, and current-plan behavior

**Files:**
- Create: `wordflip-server/src/test/java/com/wordflip/service/GroupServiceTest.java`
- Modify: `wordflip-server/src/main/java/com/wordflip/service/GroupService.java:35`
- Modify: `wordflip-server/src/main/java/com/wordflip/controller/GroupController.java:34`
- Modify: `wordflip-server/src/test/java/com/wordflip/controller/GroupControllerTest.java`

**Interfaces:**
- Consumes: `GroupService#listGroups`, `getGroup`, `listGroupCards`, `listUnassignedCards`, `createCustomGroup`.
- Produces: the same public signatures; invalid filter/page values throw `WordflipException("VALIDATION_ERROR", ...)` before SQL.

- [ ] **Step 1: Write failing service tests**

Add tests with concrete assertions:

```java
assertThatThrownBy(() -> service.listGroups(USER_ID, "other", "createdAt"))
        .isInstanceOfSatisfying(WordflipException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR"));
assertThatThrownBy(() -> service.listGroups(USER_ID, null, "sql"))
        .isInstanceOfSatisfying(WordflipException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR"));
assertThatThrownBy(() -> service.listGroupCards(USER_ID, GROUP_ID, 0, 20))
        .isInstanceOfSatisfying(WordflipException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR"));
assertThatThrownBy(() -> service.listUnassignedCards(USER_ID, false, null, 1, 101))
        .isInstanceOfSatisfying(WordflipException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR"));
```

Capture `loadGroup` SQL and require the complete mastery predicate while retaining these heat buckets:

```java
assertThat(sql).contains("m.stability>=80", "m.scheduled_days>=30", "r.correct=TRUE");
assertThat(sql).contains("m.stability<3", "m.stability<15", "m.stability<30");
```

Add a transaction-oriented unit test for duplicate `cardIds` verifying the insert batch receives each ID once; real rollback proof belongs to Task 4.

- [ ] **Step 2: Run the focused tests and verify they fail**

```powershell
cd wordflip-server
.\mvnw.cmd -Dtest=GroupServiceTest,GroupControllerTest test
```

Expected: FAIL because invalid values are currently clamped/accepted and progress uses `stability >= 30`.

- [ ] **Step 3: Add explicit service validation**

Use private validation methods with exact accepted values:

```java
private void validateListOptions(String source, String sort) {
    if (source != null && !Set.of("auto", "custom").contains(source)) {
        throw new WordflipException("VALIDATION_ERROR", "source 只允许 auto 或 custom");
    }
    if (!Set.of("createdAt", "name").contains(sort)) {
        throw new WordflipException("VALIDATION_ERROR", "sort 只允许 createdAt 或 name");
    }
}

private void validatePage(int page, int size) {
    if (page < 1 || size < 1 || size > 100) {
        throw new WordflipException("VALIDATION_ERROR", "page 须从 1 开始且 size 须在 1–100");
    }
}
```

For `all=true`, keep the existing internal fetch cap of 5000 while still validating the caller-supplied page and size. Controller stays free of grouping logic.

- [ ] **Step 4: Correct group progress without changing heat semantics**

Join the latest dictation `review_events` row exactly as in Task 2. Calculate `mastered` only with the full authority rule; calculate heat0–heat4 from stability thresholds. Derive:

```java
String status = reviewed == 0 ? "not_started"
        : mastered == total && total > 0 ? "completed" : "learning";
float progress = total == 0 ? 0 : (float) mastered / total;
```

Ensure `requireOwnedGroup` continues joining `user_settings.active_plan_id`, so history and other-user groups both return the same `NOT_FOUND` error.

- [ ] **Step 5: Run group tests**

```powershell
cd wordflip-server
.\mvnw.cmd -Dtest=GroupServiceTest,GroupServiceAppendTest,GroupControllerTest test
```

Expected: all selected tests pass and append-auto behavior remains unchanged.

- [ ] **Step 6: Record the review checkpoint**

Suggested commit after explicit authorization: `fix(server): 收敛当前计划分组口径`.

---

### Task 4: Prove Today/Groups behavior against real MySQL

**Files:**
- Create: `wordflip-server/src/test/java/com/wordflip/database/TodayGroupsMySqlIntegrationTest.java`

**Interfaces:**
- Consumes: real Flyway v2 schema, `TodayService`, `GroupService`, `JdbcTemplate`.
- Produces: Docker-backed evidence for current-plan isolation, side-effect freedom, mastery count and custom-group rollback.

- [ ] **Step 1: Build a minimal deterministic fixture**

Create two users, one active and one historical/other plan, four published cards, one active group, one inaccessible group, dictation/choice memories, latest correct/incorrect review events, one completed quiz session with questions from the active group, and one unassigned card. Use test IDs in a dedicated `8_805_xxx` range and a `@Testcontainers(disabledWithoutDocker = true)` MySQL 8.4 container.

The fixture must include these mastery cases:

```text
card A: dictation review, stability 90, scheduled 40, latest correct => mastered
card B: dictation review, stability 90, scheduled 20, latest correct => not mastered
card C: dictation review, stability 90, scheduled 40, latest incorrect => not mastered
card D: choice review only => not mastered
```

- [ ] **Step 2: Write failing integration tests**

```java
@Test
void todayAndGroupsUseSameMasteryRuleWithoutMemoryWrites() {
    long cardMemoryBefore = countRows("card_skill_memory");
    long lexemeMemoryBefore = countRows("lexeme_skill_memory");
    long eventsBefore = countRows("review_events");

    TodayDashboard today = todayService.getDashboard(USER_ID, ZoneId.of("Asia/Shanghai"));
    GroupDetail group = groupService.getGroup(USER_ID, ACTIVE_GROUP_ID);

    assertThat(today.stats().masteredCount()).isEqualTo(1);
    assertThat(today.recentGroups()).extracting(RecentGroupDto::groupId)
            .contains(ACTIVE_GROUP_ID);
    assertThat(group.progress()).isEqualTo(0.25f);
    assertThat(countRows("card_skill_memory")).isEqualTo(cardMemoryBefore);
    assertThat(countRows("lexeme_skill_memory")).isEqualTo(lexemeMemoryBefore);
    assertThat(countRows("review_events")).isEqualTo(eventsBefore);
}

@Test
void historicalAndOtherUserGroupsAreIndistinguishableNotFound() {
    assertNotFound(HISTORY_GROUP_ID);
    assertNotFound(OTHER_USER_GROUP_ID);
}
```

Add a custom group test that submits one valid unassigned card plus one already-assigned card, expects `CONFLICT`, then asserts no new `study_groups` or `study_group_cards` rows remain. Add a success test verifying duplicate valid IDs produce one membership row.

- [ ] **Step 3: Run the integration test and verify failure before fixes are complete**

```powershell
cd wordflip-server
.\mvnw.cmd -Dtest=TodayGroupsMySqlIntegrationTest test
```

Expected: no skipped tests with Docker running; initial run fails on old mastery or rollback behavior.

- [ ] **Step 4: Make only fixture-driven corrections needed by the failing test**

If a concurrent/constraint conflict escapes as `DataIntegrityViolationException`, keep the service transaction intact and let `GlobalExceptionHandler` map it to 409. Do not catch and continue after a member insert. Do not add schema changes.

- [ ] **Step 5: Run server verification**

```powershell
cd wordflip-server
.\mvnw.cmd test
```

Expected: every server test passes; `TodayGroupsMySqlIntegrationTest` and existing MySQL tests are not skipped while Docker is available.

- [ ] **Step 6: Record the review checkpoint**

Suggested commit after explicit authorization: `test(server): 验证今日与分组真实数据库行为`.

---

### Task 5: Add the HTTP Today repository and shared runtime wiring

**Files:**
- Modify: `wordflip-web/src/domain/today.ts`
- Create: `wordflip-web/src/data/http/today/todayDtos.ts`
- Create: `wordflip-web/src/data/http/today/todayMappers.ts`
- Create: `wordflip-web/src/data/http/today/HttpTodayRepository.ts`
- Create: `wordflip-web/src/data/http/today/HttpTodayRepository.test.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockTodayRepository.ts`
- Modify: `wordflip-web/src/data/mock/createDemoState.ts`
- Modify: `wordflip-web/src/data/mock/DemoStateStore.ts`
- Modify: `wordflip-web/src/data/mock/DemoStateStore.test.ts`
- Modify: `wordflip-web/src/data/mock/quizFixtures.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockOnboardingRepositories.test.ts`
- Modify: `wordflip-web/src/features/books/BooksFlow.test.tsx`
- Modify: `wordflip-web/src/features/study/StudyFlow.test.tsx`
- Modify: `wordflip-web/src/features/settings/SettingsPage.test.tsx`
- Modify: `wordflip-web/src/features/media/MediaPage.test.tsx`
- Modify: `wordflip-web/src/features/stats/StatsPage.test.tsx`
- Modify: `wordflip-web/src/data/runtime/createRepositoryBundle.ts`
- Modify: `wordflip-web/src/data/http/createHttpRuntime.test.ts`

**Interfaces:**
- Consumes: authenticated Axios instance from `createHttpRuntime` and `GET /today` DTO.
- Produces: `TodayRepository#getSummary(): Promise<TodaySummary>` shared by Mock and HTTP.

- [ ] **Step 1: Define the exact Today domain contract**

```ts
export interface TodayTaskSource {
  groupId: string;
  groupName: string;
  count: number;
}

export interface TodayTask {
  count: number;
  label: string;
  sources: TodayTaskSource[];
}

export interface TodaySummary {
  date: string;
  streakDays: number;
  stats: { masteredCount: number; dueReviewCount: number; completionPercent: number };
  tasks: { newWords: TodayTask; dueReview: TodayTask; quiz: TodayTask };
  recommendedStudy: {
    groupId: string;
    groupName: string;
    wordCount: number;
    reason: "new_words" | "due_review" | "mixed";
  } | null;
  recentGroups: Array<{ groupId: string; name: string; lastStudiedAt: string }>;
}
```

Keep `TodayRepository` unchanged except for the new return shape.

- [ ] **Step 2: Write failing repository tests**

Create an Axios adapter harness matching `HttpBookRepository.test.ts`. Assert:

```ts
expect(requests[0]).toMatchObject({ method: "get", url: "/today" });
expect(requests[0].headers.get("X-Timezone")).toBe("Asia/Shanghai");
await expect(repository.getSummary()).resolves.toMatchObject({
  date: "2026-08-13",
  stats: { masteredCount: 12, dueReviewCount: 4, completionPercent: 60 },
  recommendedStudy: { groupId: "91" },
  recentGroups: [{ groupId: "91", name: "第 1 组", lastStudiedAt: "2026-08-13T08:00:00Z" }]
});
```

Inject `getTimeZone: () => "Asia/Shanghai"` into the repository constructor so tests do not depend on the runner locale. Also assert malformed required payloads reject with `{ kind: "unknown", message: "今日接口返回数据不完整" }` rather than filling zero values.

- [ ] **Step 3: Run the focused test and verify it fails**

```powershell
cd wordflip-web
npm test -- src/data/http/today/HttpTodayRepository.test.ts
```

Expected: FAIL because the repository and DTO files do not exist.

- [ ] **Step 4: Implement DTO mapping and repository**

```ts
export class HttpTodayRepository implements TodayRepository {
  constructor(
    private readonly client: AxiosInstance,
    private readonly getTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone
  ) {}

  async getSummary(): Promise<TodaySummary> {
    const response = await this.client.get<TodayDashboardDto>("/today", {
      headers: { "X-Timezone": this.getTimeZone() || "UTC" }
    });
    return mapTodaySummary(response.data);
  }
}
```

The shared authenticated client already converts Axios failures to `AppError`; the Repository must not remap them a second time. `mapTodaySummary` converts every numeric `groupId` with `String(id)`, preserves nullable `recommendedStudy`, copies arrays, and validates required object branches before reading them. Its validation helper throws the concrete AppError `{ kind: "unknown", message: "今日接口返回数据不完整" }`.

- [ ] **Step 5: Migrate the fixed Mock snapshot and wire HTTP mode**

Update every `TodaySummary` literal in `createDemoState.ts`, `quizFixtures.ts` and `MockSettingsRepository.ts` to the new structure. Add a focused `fixedTodaySummary(...)` builder in `quizFixtures.ts` so repeated precomputed quiz snapshots supply the same `date/streakDays/tasks/recentGroups` shape instead of duplicating object literals.

Because this changes persisted JSON shape, bump `DemoState.schemaVersion` from 4 to 5, change `DEMO_STORAGE_KEY` to `wordflip.web.demo.v5`, keep `wordflip.web.demo.v4` as the single legacy key to remove, and rewrite `isTodaySnapshot` to validate the new nested fields. Existing v4 data must reset to the deterministic v5 seed rather than being partially interpreted. Update all direct test access from `today.masteredCount` to `today.stats.masteredCount`, from `today.dueCount` to `today.stats.dueReviewCount`, and from the old task array to the named `tasks` object.

In `createRepositoryBundle`, add:

```ts
today: new HttpTodayRepository(runtime.authenticatedClient),
```

Keep non-integrated study/quiz/stats/media repositories untouched. Extend runtime tests to assert `/today` uses the same Bearer token and refresh lifecycle as `/books` and `/settings`.

- [ ] **Step 6: Run data-layer tests**

```powershell
cd wordflip-web
npm test -- src/data/http/today/HttpTodayRepository.test.ts src/data/http/createHttpRuntime.test.ts
npm test -- src/data/mock/DemoStateStore.test.ts src/data/mock/repositories/MockOnboardingRepositories.test.ts src/features/books/BooksFlow.test.tsx src/features/study/StudyFlow.test.tsx src/features/settings/SettingsPage.test.tsx src/features/media/MediaPage.test.tsx src/features/stats/StatsPage.test.tsx
```

Expected: both files pass.

- [ ] **Step 7: Record the review checkpoint**

Suggested commit after explicit authorization: `feat(web): 接入今日任务真实接口`.

---

### Task 6: Add the HTTP Groups repository and shared Mock contract

**Files:**
- Modify: `wordflip-web/src/domain/groups.ts`
- Create: `wordflip-web/src/data/http/groups/groupDtos.ts`
- Create: `wordflip-web/src/data/http/groups/groupMappers.ts`
- Create: `wordflip-web/src/data/http/groups/HttpGroupRepository.ts`
- Create: `wordflip-web/src/data/http/groups/HttpGroupRepository.test.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockGroupRepository.ts`
- Modify: `wordflip-web/src/data/mock/createDemoState.ts`
- Modify: `wordflip-web/src/data/mock/DemoStateStore.ts`
- Modify: `wordflip-web/src/data/mock/DemoStateStore.test.ts`
- Modify: `wordflip-web/src/data/mock/repositories/MockSettingsRepository.ts`
- Modify: `wordflip-web/src/features/books/BooksFlow.test.tsx`
- Modify: `wordflip-web/src/data/runtime/createRepositoryBundle.ts`
- Modify: `wordflip-web/src/data/http/createHttpRuntime.test.ts`

**Interfaces:**
- Consumes: six Groups endpoints and the shared authenticated client.
- Produces:

```ts
export interface GroupRepository {
  listGroups(options?: { source?: "auto" | "custom"; sort?: "createdAt" | "name" }): Promise<WordGroup[]>;
  getDetail(groupId: string): Promise<WordGroup>;
  listCards(groupId: string, page?: number, size?: number): Promise<GroupCardPage>;
  listUnassigned(options?: { all?: boolean; q?: string; page?: number; size?: number }): Promise<GroupCardPage>;
  createCustomGroup(input: { name?: string; cardIds: string[] }): Promise<WordGroup>;
}
```

- [ ] **Step 1: Define Groups domain models**

```ts
export interface GroupStats {
  heat0: number; heat1: number; heat2: number; heat3: number; heat4: number; total: number;
}

export interface WordGroup {
  groupId: string;
  name: string;
  source: "auto" | "custom";
  status: "not_started" | "learning" | "completed";
  createdAt: string | null;
  stats: GroupStats;
  progress: number;
}

export interface GroupCard {
  cardId: string;
  lexemeId: string;
  headword: string;
  phonetic: string | null;
  primaryPos: string | null;
  primaryDefinition: string;
  displayHeatLevel: 0 | 1 | 2 | 3 | 4;
  progress: {
    dictation: FsrsSkillSnapshot;
    choice: FsrsSkillSnapshot;
  };
}

export interface FsrsSkillSnapshot {
  state: "new" | "learning" | "review" | "relearning";
  dueAt: string;
  stability: number;
  difficulty: number;
  reps: number;
  lapses: number;
}

export interface GroupCardPage {
  page: number; size: number; totalElements: number; totalPages: number; cards: GroupCard[];
}
```

Do not reuse `StudySession` or invent `lastQuizSucceeded`; Groups only displays the server read snapshot.

Keep Mock-only membership separate from the public Repository response:

```ts
export interface DemoWordGroup extends WordGroup {
  cardIds: string[];
}

export interface PlanDemoState {
  groups: { items: DemoWordGroup[] };
  // existing cards/study/quiz/media/stats fields remain unchanged
}
```

`HttpGroupRepository` returns `WordGroup` without fake member IDs. `MockGroupRepository.listGroups/getDetail/createCustomGroup` strips `cardIds` before returning, while `listCards/listUnassigned` use the internal `DemoWordGroup.cardIds` membership.

- [ ] **Step 2: Write failing HTTP repository tests**

Cover every operation and exact request shape:

```ts
expect(requests[0]).toMatchObject({ method: "get", url: "/groups" });
expect(requests[1]).toMatchObject({ method: "get", url: "/groups/12" });
expect(requests[2]).toMatchObject({ method: "get", url: "/groups/12/cards" });
expect(requests[3]).toMatchObject({ method: "get", url: "/learning/cards/unassigned" });
expect(requests[4]).toMatchObject({ method: "post", url: "/groups/custom" });
expect(JSON.parse(String(requests[4].data))).toEqual({ name: "重点", cardIds: [31, 32] });
```

Reuse the strict positive integer validation behavior from `HttpBookRepository`: reject whitespace, leading zero, exponent, unsafe integer, zero, negative and decimal IDs before HTTP. Assert 409 maps to `{ kind: "conflict" }` and does not mutate the input array.

- [ ] **Step 3: Run the focused test and verify it fails**

```powershell
cd wordflip-web
npm test -- src/data/http/groups/HttpGroupRepository.test.ts
```

Expected: FAIL because Groups HTTP files do not exist.

- [ ] **Step 4: Implement DTOs, mappers and repository**

The card mapper selects the sense with `primary=true`, falling back to the first sense only when none is primary:

```ts
const primarySense = dto.senses.find((sense) => sense.primary) ?? dto.senses[0];
if (!primarySense) throw invalidPayload("学习卡缺少考义");
return {
  cardId: String(dto.cardId),
  lexemeId: String(dto.lexemeId),
  headword: dto.en,
  phonetic: dto.phonetic,
  primaryPos: primarySense.pos,
  primaryDefinition: primarySense.cn,
  displayHeatLevel: dto.progress.displayHeatLevel,
  progress: structuredClone(dto.progress)
};
```

`createCustomGroup` validates 1–500 unique positive IDs, converts them to numbers, trims an optional name, posts `/groups/custom`, and maps the returned group.

- [ ] **Step 5: Update Mock behavior and runtime wiring**

Remove `appendMembers`. Implement `listCards`, `listUnassigned` and `createCustomGroup` against the active plan partition. A successful Mock create removes no card records; membership alone makes a card disappear from the computed unassigned pool. Use a stable mock group ID such as `custom-group-${nextIndex}`.

Update `DemoStateStore.isPlanState` to validate all public group fields plus the internal `cardIds`. Update tests that previously called `appendMembers` to create a custom group or assert membership through `listCards`. Add the new required group fields to fixtures created by `MockSettingsRepository` and `BooksFlow.test.tsx`:

```ts
{
  groupId: "group-core-01",
  name: "第 1 组",
  source: "auto",
  status: "not_started",
  createdAt: "2026-07-23T00:00:00Z",
  stats: { heat0: 1, heat1: 0, heat2: 0, heat3: 0, heat4: 0, total: 1 },
  progress: 0,
  cardIds: ["card-sustainable"]
}
```

Wire:

```ts
groups: new HttpGroupRepository(runtime.authenticatedClient),
```

Extend runtime tests to verify Groups requests share the refreshed token and that mock mode still returns `MockGroupRepository`.

- [ ] **Step 6: Run Groups data tests**

```powershell
cd wordflip-web
npm test -- src/data/http/groups/HttpGroupRepository.test.ts src/data/http/createHttpRuntime.test.ts
npm test -- src/data/mock/DemoStateStore.test.ts src/data/mock/repositories/MockOnboardingRepositories.test.ts src/features/books/BooksFlow.test.tsx
```

Expected: all selected tests pass.

- [ ] **Step 7: Record the review checkpoint**

Suggested commit after explicit authorization: `feat(web): 接入当前计划分组接口`.

---

### Task 7: Rebuild TodayPage around the real dashboard model

**Files:**
- Modify: `wordflip-web/src/features/today/TodayPage.tsx`
- Modify: `wordflip-web/src/features/today/TodayPage.module.css`
- Modify: `wordflip-web/src/features/today/TodayPage.test.tsx`

**Interfaces:**
- Consumes: `TodayRepository#getSummary()` from Task 5.
- Produces: Today UI with three stats, recent groups before tasks, and no `study-demo` link.

- [ ] **Step 1: Replace old UI expectations with failing v7 tests**

```ts
test("展示三项权威统计、连续打卡和最多三个最近分组", async () => {
  renderAuthenticatedApp("/today");
  const summary = await screen.findByRole("list", { name: "今日摘要" });
  expect(within(summary).getAllByRole("listitem")).toHaveLength(3);
  expect(screen.getByText(/连续学习 14 天/)).toBeVisible();
  expect(screen.queryByText("MOCK DATA · READY")).not.toBeInTheDocument();
  expect(screen.queryByText("今日完成")).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "开始今日学习" })).not.toBeInTheDocument();
});

test("最近学习和推荐任务使用真实 groupId", async () => {
  renderAuthenticatedApp("/today");
  expect(await screen.findByRole("link", { name: /第 12 组/ }))
    .toHaveAttribute("href", "/groups/group-12");
  expect(screen.queryByText("study-demo")).not.toBeInTheDocument();
});
```

Update the empty scenario test to keep the “浏览词书” recovery link.

- [ ] **Step 2: Run TodayPage tests and verify failure**

```powershell
cd wordflip-web
npm test -- src/features/today/TodayPage.test.tsx
```

Expected: FAIL against the old four-stat Mock page.

- [ ] **Step 3: Implement the page with server-owned values**

Format `summary.date` with `Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", weekday: "long" })`. Render recent groups before the task panel. For new/due tasks, use the first source `groupId`, falling back to `recommendedStudy.groupId`; link to `/groups/{groupId}` until `WEB-API04` owns direct study startup. Render quiz as a non-link row with “测验接口接入后可开始”，so HTTP mode never opens a Mock quiz session.

Keep the existing `AsyncState` retry/error style and clear stale error when a later request succeeds.

- [ ] **Step 4: Run Today page and accessibility-focused tests**

```powershell
cd wordflip-web
npm test -- src/features/today/TodayPage.test.tsx src/app/routeGuards.test.tsx
```

Expected: selected tests pass.

- [ ] **Step 5: Record the review checkpoint**

Suggested commit after explicit authorization: `feat(web): 展示真实今日任务与最近分组`.

---

### Task 8: Rebuild Groups pages and add custom group creation

**Files:**
- Modify: `wordflip-web/src/features/groups/GroupsPage.tsx`
- Modify: `wordflip-web/src/features/groups/GroupDetailPage.tsx`
- Create: `wordflip-web/src/features/groups/CustomGroupPage.tsx`
- Modify: `wordflip-web/src/features/groups/groups.module.css`
- Modify: `wordflip-web/src/features/groups/GroupsFlow.test.tsx`
- Modify: `wordflip-web/src/app/App.tsx`

**Interfaces:**
- Consumes: `GroupRepository` from Task 6.
- Produces: `/groups`, `/groups/:groupId`, `/groups/new` UI flows.

- [ ] **Step 1: Write failing list/detail tests**

```ts
test("分组卡展示来源、状态、五档热力与服务端进度", async () => {
  renderAuthenticatedApp("/groups");
  const group = await screen.findByRole("article", { name: "第 12 组 · 城市与环境" });
  expect(within(group).getByText("自动分组")).toBeVisible();
  expect(within(group).getByText("学习中")).toBeVisible();
  expect(within(group).getByText(/已掌握 25%/)).toBeVisible();
  expect(within(group).getAllByText(/热力 [0-4]/)).toHaveLength(5);
});

test("详情分页卡片只读展示服务端双轨快照", async () => {
  renderAuthenticatedApp("/groups/group-12");
  const row = await screen.findByRole("row", { name: /sustainable/ });
  expect(within(row).getByText("默写")).toBeVisible();
  expect(within(row).getByText("选择")).toBeVisible();
  expect(within(row).queryByRole("button", { name: /记得|模糊|不认识/ })).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Write failing custom group tests**

Use `userEvent` and assert:

```ts
expect(screen.getByRole("link", { name: "新建自定义分组" }))
  .toHaveAttribute("href", "/groups/new");
await user.click(screen.getByRole("button", { name: "保存分组" }));
expect(screen.getByText("请先选择单词")).toBeVisible();
await user.click(screen.getByRole("checkbox", { name: /sustainable/ }));
expect(screen.getByText("已选 1 个")).toBeVisible();
await user.click(screen.getByRole("button", { name: "保存分组" }));
expect(await screen.findByText("已创建包含 1 张卡片的自定义分组")).toBeVisible();
```

Provide a repository stub returning a conflict once, then success. Verify the entered name remains after 409 and invalid cards are removed after candidate refresh.

- [ ] **Step 3: Run Groups flow tests and verify failure**

```powershell
cd wordflip-web
npm test -- src/features/groups/GroupsFlow.test.tsx
```

Expected: FAIL because status/stats/pagination/custom route are absent.

- [ ] **Step 4: Implement list and detail pages**

Render source/status labels from closed maps:

```ts
const sourceLabel = { auto: "自动分组", custom: "自定义分组" } as const;
const statusLabel = { not_started: "未开始", learning: "学习中", completed: "已完成" } as const;
```

Render progress as `Math.round(group.progress * 100)` only for visual percentage formatting; do not recompute mastered count. The detail page independently requests `getDetail(groupId)` and `listCards(groupId, page, 20)`, resets both data and error state when `groupId` changes, and exposes previous/next buttons only when pages exist.

- [ ] **Step 5: Implement `/groups/new` and creation recovery**

Add the static route before the parameter route:

```tsx
{ path: "/groups/new", element: <CustomGroupPage /> },
{ path: "/groups/:groupId", element: <GroupDetailPage /> },
```

On page load call `listUnassigned({ all: true, page: 1, size: 100 })`. Use accessible checkboxes styled as chips. Enforce 500 selected cards in UI. On success show the exact count message and navigate to `/books` after the feedback is observable; on 409 retain `name`, refetch candidates, intersect `selectedIds` with returned card IDs, and announce the conflict.

- [ ] **Step 6: Run Groups UI tests**

```powershell
cd wordflip-web
npm test -- src/features/groups/GroupsFlow.test.tsx src/app/router.test.tsx
```

Expected: all selected tests pass.

- [ ] **Step 7: Record the review checkpoint**

Suggested commit after explicit authorization: `feat(web): 完成真实分组与自定义分组页面`.

---

### Task 9: End-to-end verification and task tracking

**Files:**
- Modify: `wordflip-web/e2e/happy-path.spec.ts`
- Modify: `TASK.md:63`

**Interfaces:**
- Consumes: every deliverable from Tasks 1–8.
- Produces: reproducible verification evidence and completed `WEB-API03` task status.

- [ ] **Step 1: Update the browser regression path**

Replace any Today assertion that expects `study-demo` with the real group route. Add a Mock-mode browser flow:

```ts
await page.goto("/today");
await page.getByRole("link", { name: /第 12 组/ }).first().click();
await expect(page).toHaveURL(/\/groups\/group-12$/);
await expect(page.getByRole("heading", { name: "第 12 组 · 城市与环境" })).toBeVisible();
```

Add a custom-group route smoke assertion without creating permanent external data; mutation semantics are already covered by Vitest and MySQL integration tests.

- [ ] **Step 2: Run API verification**

```powershell
cd wordflip-api
python -c "import yaml; yaml.safe_load(open('openapi.yaml', encoding='utf-8'))"
python -m pytest -q tests
```

Expected: all tests pass.

- [ ] **Step 3: Run server verification with Docker available**

```powershell
cd wordflip-server
.\mvnw.cmd test
```

Expected: all tests pass; Docker-backed v2, Books/Settings and Today/Groups integration tests report executed, not skipped.

- [ ] **Step 4: Run Web verification**

```powershell
cd wordflip-web
npm test
npm run lint
npm run build
npm run test:e2e
```

Expected: Vitest, ESLint, TypeScript/Vite build and Playwright all pass.

- [ ] **Step 5: Perform a real HTTP smoke run**

Start the backend with dev profile and Web with HTTP mode:

```powershell
cd wordflip-server
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

cd ..\wordflip-web
$env:VITE_DATA_SOURCE="http"
$env:VITE_API_BASE_URL="http://127.0.0.1:8080/api/v1"
npm run dev -- --host 127.0.0.1
```

In the browser verify: login → Today shows server date → Groups list → group detail cards → custom group candidate page. Confirm DevTools requests use `/api/v1/today` and `/api/v1/groups*`, never Mock fallbacks.

- [ ] **Step 6: Mark the task complete only after all evidence passes**

Change exactly:

```markdown
- [x] **WEB-API03** 今日任务与当前计划分组
```

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only WEB-API03-related files are modified. Report test counts, skipped tests, real HTTP smoke result and known boundaries (`WEB-API04` study, `WEB-API05` quiz).

- [ ] **Step 7: Record the final review checkpoint**

Suggested commits after explicit authorization:

```text
test(web): 覆盖今日与分组真实入口
docs(task): 标记 WEB-API03 完成
```

Do not commit or push until the user explicitly requests it.
