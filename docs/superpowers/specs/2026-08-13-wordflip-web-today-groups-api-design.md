# WordFlip Web 今日与当前计划分组 API 接入设计

## 目标

完成 `WEB-API03`：以 requirements v7 为行为真相、OpenAPI 为 REST 契约真相，统一今日任务与当前计划分组的服务端口径，并将 Web 今日页、分组列表、分组详情和自定义分组接入真实 API。

本阶段继续采用按模块端到端交付：先收敛契约，再修正 Spring Boot 行为，最后替换 Web 的 Today/Groups Repository。Android 暂缓，不修改 Android 页面、模型或网络层。学习卡片墙及学习结束上报属于紧随其后的 `WEB-API04`，但本阶段返回和传递的学习入口必须使用真实 `groupId`，不得继续依赖 `study-demo` 等模拟标识。

## 方案选择

采用“按模块端到端完成”的方案，依次交付 `WEB-API03 → WEB-API04 → WEB-API05`：

- `WEB-API03` 负责今日任务、当前计划分组、分组卡片只读快照和自定义分组。
- `WEB-API04` 负责 `GET /study/groups/{groupId}`、卡片墙和 `POST /study/sessions`。
- `WEB-API05` 负责双轨测验初始化、服务端判题、幂等答题与预计算结果。

不采用一次性改完全部后端再统一接 Web，因为联调和资源隔离问题会过晚暴露；不采用只打通 happy path 的方式，因为 Today/Groups 是后续学习与测验共用的当前计划入口，若在这里容忍 Mock 回退或错误 ID，会把不一致扩散到后续模块。

## 范围

本阶段端到端接入以下端点：

- `GET /today`
- `GET /groups`
- `GET /groups/{groupId}`
- `GET /groups/{groupId}/cards`
- `GET /learning/cards/unassigned`
- `POST /groups/custom`

`GET /groups/{groupId}/stains/batch`、学习 session、测验、统计和媒体不在本阶段实现。分组详情中的污渍与卡拍入口保持现有边界，不在 HTTP 模式伪造成功结果。

## 权威业务口径

### 当前计划隔离

Today、Groups、分组卡片和未入组候选池只使用认证用户的唯一 `activePlanId`。历史计划仍保留，但通过当前计划接口访问历史分组时统一返回 404；响应不得泄露该分组是否属于同一用户的历史计划或其他用户。

自定义分组的每个 `cardId` 必须来自当前计划词书的已发布学习卡，并且在当前计划内尚未入组。同一计划内一张学习卡只能属于一个分组；重复提交的 `cardId` 由服务端去重，已入组或越权卡片返回 409。

### 已掌握口径

今日统计与分组进度统一复用现有 Stats/Books 的权威条件，按默写轨去重统计：

- `skill = dictation`
- `state = review`
- `stability >= 80`
- `scheduled_days >= 30`
- 最近有效测验结果为成功

现有 `TodayService` 和 `GroupService` 使用 `stability >= 30` 作为已掌握条件，与 requirements、OpenAPI 及 `StatsService` 不一致，本阶段必须先用失败测试锁定差异再修正。

heat0–heat4 是只读展示分档，不等同于“已掌握”。现有卡片热力阈值 `0 / 3 / 15 / 30` 可继续由服务端统一计算；分组 `progress` 和 `completed` 状态必须使用上述完整已掌握条件，不能以 heat4 数量替代。

### 今日任务

服务端返回当前时区日期、连续打卡、三项统计、三类任务、推荐分组和最近学习分组：

- 新词：当前计划已入组且没有有效测验记录的卡片。
- 到期复习：当前计划中任一权威 skill 记忆 `due_at` 不晚于当前时间的去重卡片。
- 测验池：由服务端提供规模；具体重新抽题由 `WEB-API05` 的测验初始化负责。
- 最近学习：当前计划最近完成学习或测验的分组，最多 3 个。

所有计数由服务端计算。Web 不根据卡片列表、热力或本地时间重新推导任务规模、完成率或 FSRS 状态。

## OpenAPI 契约

现有路径保持不变，优先做向后兼容的契约收敛：

- 为 `GroupListResponse.groups`、`GroupCardsResponse.cards` 和必要分页字段补齐 `required`。
- 为 Today 的 `tasks.newWords`、`tasks.dueReview`、`tasks.quiz`，以及 `TodayTask.sources` 内的字段补齐 `required`。
- 明确 `X-Timezone` 使用 IANA 时区名；缺失或非法值采用服务端既定安全回退，不把原始异常暴露给客户端。
- 为受保护路径补齐 401；为无当前计划和当前计划资源不存在补齐 404；为自定义分组冲突补齐 409；非法分页、筛选或空选择补齐 400。
- `TodayDashboard.stats.masteredCount` 与 `GroupDetail.progress` 的 description 明确完整已掌握口径，避免继续把 heat4 或 `stability >= 30` 当作完成度。
- ID 在 OpenAPI 中继续使用 `int64`；Web 在 Repository 映射边界转为字符串，页面不得自行转换或拼接原始 DTO。

如果契约测试证明现有 schema 已满足某项，则不为追求 diff 而重复改写；只提交有行为或验证价值的最小契约变更。

## Spring Boot 实现

### TodayService

`TodayService.getDashboard` 保持只读事务，并完成以下收敛：

- 已掌握查询复用完整权威条件，并只统计当前计划的去重卡片。
- 新词、到期和测验池都显式按 `userId + activePlanId` 隔离。
- `dueReviewCount` 与 `tasks.dueReview.count` 必须来自同一口径。
- `completionPercent = round(masteredCount / 已入组去重卡片总数 × 100)`，分母为 0 时返回 0。
- `X-Timezone` 只影响本地日期和连续打卡边界，不改变数据库中 UTC 的 `due_at` 与事件时间。
- 最近学习只返回当前计划中的分组，按最后活动时间倒序并限制 3 条。

今日查询不得创建记忆、补写复习事件或更新到期时间。

### GroupService

`GroupService` 继续作为当前计划分组业务边界：

- 列表筛选只接受 `auto | custom`，排序只接受 `createdAt | name`，非法值返回 400，不能通过动态 SQL 接受任意排序文本。
- 列表和详情的 `progress`、`status` 使用完整已掌握条件；heat0–heat4 保持服务端展示分档。
- 分组卡片分页限制在当前计划，页码从 1 开始，size 上限 100；非法参数由契约定义的校验处理，不静默扩大请求。
- 未入组候选只包含当前词书已发布且尚未入组的卡片；搜索匹配英文词头或中文考义。
- 创建自定义分组在同一事务内锁定或再次验证候选、创建分组并写入成员；任何成员冲突时整体回滚，不产生空组或部分成员。
- 组名去除首尾空白，空名称使用“自定义分组 {序号}”，长度不超过 64。

分组查询和创建都不得写 `card_skill_memory`、`lexeme_skill_memory` 或 `review_events`。

### 数据库影响

本阶段不预期新增 Flyway 迁移。当前 v2 已具备学习计划、分组、分组成员、双轨记忆、复习事件和学习日志字段。若实现过程中发现必须改变持久化结构，应停止实现并先更新 database-design 与 OpenAPI 设计，不在本任务中临时追加隐藏迁移。

## Web 数据层

### HTTP 运行时

在现有共享认证 HTTP 客户端上新增 `HttpTodayRepository` 与 `HttpGroupRepository`。它们与 Auth、Books、Settings 共用 Token Store、401 单次刷新和 `AppError` 映射，不创建独立 Axios 客户端。

`VITE_DATA_SOURCE=mock` 继续返回完整 Mock Bundle；`http` 模式将 `today` 和 `groups` 替换为 HTTP Repository。已接入模块请求失败时不得回退 Mock，也不得在页面直接调用 Axios。

### Today Repository

`HttpTodayRepository.getSummary()` 请求 `/today`，发送浏览器解析出的 IANA 时区名，并将 API DTO 映射为页面领域模型。领域模型直接表达：

- `date`、`streakDays`
- `masteredCount`、`dueReviewCount`、`completionPercent`
- 新词、到期、测验三类任务及来源分组
- `recommendedStudy`
- 最多 3 条 `recentGroups`

现有 Mock `TodaySummary` 同步到相同结构，避免页面根据数据源分支。服务端未提供的 `reviewedCount` 和 `currentBookTitle` 不再由 HTTP Repository 猜测；页面按 requirements 展示三项统计和当前计划语义。

### Group Repository

调整 `GroupRepository` 以匹配服务端能力：

- `listGroups(filters?)`
- `getDetail(groupId)`
- `listCards(groupId, page, size)`
- `listUnassigned({ all, q, page, size })`
- `createCustomGroup({ name?, cardIds })`

移除只存在于 Mock、服务端没有对应能力的 `appendMembers`。DTO 映射集中处理数字 ID、枚举、分页和双轨进度；页面只使用字符串 ID 与领域模型。Mock Repository 同步实现新契约，保证演示模式和 HTTP 模式拥有同样的页面能力，但两者状态来源严格分开。

## Web 页面行为

### 今日页

- 使用服务端 `date` 和本地化星期，移除固定日期与 `MOCK DATA · READY`。
- 顶部展示连续打卡天数。
- 摘要区按 requirements 展示已掌握、待复习和计划完成度三项，不保留服务端无法权威提供的“今日完成”占位。
- “最近学习”移到任务区上方，最多展示 3 个真实分组和相对时间。
- 新词、到期和最近学习入口都携带服务端返回的真实 `groupId`；在 `WEB-API04` 落地前不启动 Mock 学习会话，当前模块可先进入真实分组详情。
- 测验任务不复用旧测验状态；其真实初始化和跳转在 `WEB-API05` 完成。
- 无任务、无最近学习和无推荐分组分别显示稳定空状态，不伪造默认组。

### 分组列表与详情

- 分组卡片展示名称、来源、状态、heat0–heat4、总词数和服务端进度。
- 点击卡片进入真实 `/groups/{groupId}`。
- 详情先读取分组元数据，再读取分页卡片；展示英文、中文考义、词性、服务端 heatLevel 和双 skill 只读快照。
- 不提供手动修改掌握度按钮。
- “开始学习”保留真实 `groupId` 作为唯一上下文；卡片墙加载和 session 上报由 `WEB-API04` 接管。

### 自定义分组

新增自定义分组入口和页面：

- 从 `/learning/cards/unassigned` 加载当前计划候选，支持搜索和分页/全量选择策略。
- 以 chip 或等价可访问控件切换选择，实时显示已选数量。
- 0 个选择时前端直接提示“请先选择单词”，不发送请求。
- 创建期间禁止重复提交；成功后提示词数、刷新 Groups/Today 相关数据，并按 REQ-CG-5 返回词书页。
- 409 时保留组名和用户选择，刷新候选池，移除已不再可选的卡片并提示冲突。
- 候选池为空时展示“当前词书没有未入组的已发布学习卡”。

## 数据流与缓存

页面只依赖 Repository：

```text
Today/Groups 页面
  → TodayRepository / GroupRepository
  → 共享 authenticatedClient
  → Spring Boot 当前计划服务
  → MySQL v2 权威数据
```

自定义分组创建成功后，失效分组列表、未入组候选、Today 和可能受影响的词书进度。计划切换成功后，现有 WEB-API02 流程必须让 Today/Groups 重新请求，不能保留旧计划页面快照。

如果当前页面正在加载旧计划数据时发生计划切换，晚到响应不得覆盖新计划状态。实现使用现有请求生命周期保护或查询键中的计划世代，不在 UI 中混合两套计划数据。

## 错误处理

- 400 / `VALIDATION_ERROR`：显示筛选、分页、组名或选择错误。
- 401：共享认证客户端刷新一次；刷新失败清理会话并交给登录守卫。
- 404 无当前计划：提供选择词书入口；分组资源 404 显示“当前学习计划中没有该分组”。
- 409 自定义分组冲突：保留表单、刷新候选并提示哪些选择已失效，不自动改成其它卡片。
- 网络错误和 5xx：保留已加载页面或用户表单，提供显式重试。
- DTO 缺失必填字段：按数据错误处理并进入错误状态，不使用 Mock 或零值掩盖服务端契约问题。

## 测试与验收

按 TDD 顺序完成：

1. OpenAPI 契约测试先断言 Today/Groups 必填字段、完整已掌握描述、分页与 400/401/404/409。
2. `TodayService` 测试覆盖完整已掌握条件、双轨去重、到期计数一致性、空计划、时区日期、连续打卡和最近分组限制。
3. `GroupService` 测试覆盖当前计划隔离、历史/他人分组 404、进度口径、筛选排序校验、分页边界、未入组候选和创建冲突整体回滚。
4. 增加只读保护断言：Today/Groups 调用前后 `card_skill_memory`、`lexeme_skill_memory` 与 `review_events` 数量和内容不变。
5. `HttpTodayRepository` 测试覆盖时区头、DTO 映射、空字段和错误映射。
6. `HttpGroupRepository` 测试覆盖列表、详情、卡片分页、候选搜索、创建请求、数字 ID 映射及 409。
7. 运行时装配测试确认 HTTP 模式替换 Today/Groups 并复用共享认证客户端，Mock 模式保持完整可用。
8. 页面测试覆盖真实统计、最近分组、任务入口、列表/详情、分页、自定义分组空选择、成功与冲突恢复。
9. Docker/MySQL 集成验收覆盖“注册/登录 → 当前计划 → Today → Groups → 创建自定义分组 → 分组详情”，并验证切换计划后旧分组不可访问。

完成后运行 API pytest、Spring Boot 全量测试、Web Vitest、Lint、生产构建和相关 Playwright。Docker 可用时不得接受数据库集成测试 skipped。Android 不在本阶段验证范围内。

## 安全与业务边界

- 所有资源按认证 `userId` 隔离，并进一步校验属于唯一当前 `activePlanId`。
- `cardId` 是分组成员、进度和后续学习入口的唯一学习主键；`wordKey` 只用于查询与展示。
- Web 不实现 FSRS、已掌握计算、任务计算、分组规则或统计聚合。
- Today、分组浏览和自定义分组不得写双层记忆或复习事件。
- 历史计划、分组、记忆与审计事件不得因切换计划或创建分组而删除。
- HTTP 错误不得静默回退 Mock，也不得暴露资源归属或内部 SQL 信息。

## 非目标

- 不修改 Android。
- 不实现卡片墙数据读取、学习结束上报或学习日志写入；这些属于 `WEB-API04`。
- 不实现测验初始化、判题、FSRS 写入或结果页；这些属于 `WEB-API05`。
- 不实现统计、热力图和媒体写接口。
- 不新增重新分组、移动已有分组成员或删除分组能力。
- 不新增数据库迁移，除非后续单独批准持久化设计变更。
- 不提交或推送代码，除非用户另行明确要求。
