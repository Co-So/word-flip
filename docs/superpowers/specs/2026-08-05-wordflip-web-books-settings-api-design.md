# WordFlip Web 学习计划、词书与设置 API 接入设计

## 目标

完成 `WEB-API02`：先以 OpenAPI 为单一来源补齐学习计划、词书进度与分组设置契约，再同步 Spring Boot 实现，最后将 Web 的首次设置、词书列表、词书详情、切书和设置保存接入真实 API。

本阶段保留既有页面、路由和视觉结构。Android 暂不修改；契约扩展必须保持向后兼容，使旧 Android DTO 可以忽略新增响应字段并继续发送原有请求。今日、分组、学习、测验、统计和媒体仍使用 Mock Repository，留待 `WEB-API03～07`。

## 方案选择

采用对现有端点做增量扩展的方案，不新增组合式首次设置端点，也不增加数据库表：

- `BookItem` 增加计划归属与学习进度，避免 Web 根据多个不完整响应猜测历史状态。
- `PATCH /settings/preferences` 增加分组大小与分组策略，继续由设置资源保存用户配置。
- Web 首次设置依次保存分组配置、创建学习计划；第二步失败时保留已保存配置，用户可安全重试。
- 保留 `BookItem.selected` 和现有请求字段，避免破坏 Android 及其它已有客户端。

不采用 Web-only 拼装，因为现有响应无法可靠区分历史计划与从未学习的词书，也无法提供真实进度。不新增首次设置组合端点，因为两步流程不存在数据丢失风险，新增端点会扩大契约与服务编排范围。

## OpenAPI 契约

### 词书与计划状态

`GET /books` 与 `GET /books/{bookId}` 继续返回 `BookItem`，并增加以下必需但可空字段：

- `planId`：当前用户为该词书建立的学习计划 ID；未建立时为 `null`。
- `planStatus`：计划状态 `active | paused | completed`；未建立时为 `null`。
- `progress`：计划存在时返回 `BookProgress`，否则为 `null`。

`BookProgress` 包含：

- `masteredCount`：当前计划已入组卡片中，默写轨 `stability >= 30` 的去重卡片数。
- `assignedCardCount`：当前计划已入组的去重卡片总数。
- `completionPercent`：`round(masteredCount / assignedCardCount × 100)`；分母为 0 时返回 0。

既有 `wordCount` 仍表示词书已发布学习卡数，`selected` 仍表示该计划是否为唯一当前计划。Web 以 `selected` 判定 `current`，以非空 `planId` 判定 `history`，否则判定 `available`。

### 分组设置

`PreferencesPatchRequest` 增加两个可选字段：

- `groupSize`：仅允许 `10 | 20 | 30 | 50`。
- `groupStrategy`：仅允许 `book_order | frequency | random`。

`PATCH /settings/preferences` 的语义扩展为“更新用户偏好与当前计划自动分组配置”。若这两个字段发生变化且用户存在当前计划，服务端在同一事务中保存设置并调用现有增量分组逻辑：先补齐最后一个未满自动分组，再按新配置追加尚未入组的卡片。它不得重建已有完整分组，也不得写入或删除卡片记忆、词形记忆和复习事件。

三种策略只决定本次尚未入组卡片的追加顺序：`book_order` 使用 `book_items.sort_order`；`frequency` 优先使用 `book_items.metadata_json.frequencyRank` 升序，缺失时回退书内顺序；`random` 使用 `(userId, planId)` 作为稳定种子。现有分组成员不得因策略变化而移动。

其它偏好字段保持原行为。请求没有任何字段、分组大小不在枚举内或字段值非法时返回 `VALIDATION_ERROR`。

## Spring Boot 实现

### 词书查询

`BookService` 在现有可见性约束下查询当前用户对每本词书的唯一计划，并聚合该计划的已入组卡片数和已掌握卡片数。查询必须按 `userId` 和 `planId` 隔离，不能把同一词书的其他用户记忆或分组计入结果。

列表和详情复用同一映射结构，避免两处对计划状态或进度采用不同口径。没有计划时返回显式 `null`，而不是伪造零进度计划。

### 设置事务

`SettingsService.patchPreferences` 继续作为事务边界。它先校验并写入设置；仅当 `groupSize` 或 `groupStrategy` 实际变化且 `activePlanId` 非空时调用 `GroupService.appendAutoGroups(userId, activePlanId)`。追加失败时整个事务回滚，避免设置值与实际追加行为不一致。

只修改自动分组配置不会触发“重新分组”。已有完整分组保持不动；如用户以后需要重排，使用独立重新分组能力处理，不隐式改变历史分组。

当前 `GroupService.appendAutoGroups` 尚未补齐末尾未满自动分组，也未读取 `groupStrategy`。本阶段必须先为这两个 v7 规则补充失败测试，再把排序与追加拆成可独立测试的纯逻辑和 JDBC 编排；这属于分组设置真实生效的必要前置，不扩展到分组页面 API。

### 数据库影响

本阶段不新增 Flyway 迁移。`group_size`、`group_strategy`、`active_plan_id`、学习计划、分组成员和双轨记忆均已存在于 v2 表结构中；实现只增加查询聚合和已有字段更新。

## Web 数据层

### 共享 HTTP 运行时

HTTP 模式创建一套共享的公开客户端、认证客户端、`TokenStore` 与 `AuthSessionManager`。认证、词书和设置 Repository 复用同一个认证客户端，因此 Bearer Token、并发刷新、401 单次重放与会话清理行为保持一致，不为每个业务模块创建独立令牌生命周期。

`VITE_DATA_SOURCE=mock` 仍返回完整 Mock Bundle；`http` 模式只替换 `auth`、`books` 和 `settings`，其余 Repository 保持 Mock。页面不得直接调用 Axios 或解析 OpenAPI DTO。

### HttpBookRepository

`HttpBookRepository` 实现现有 `BookRepository`：

- `listBooks()`：读取 `/books` 并映射首次设置所需的基础词书。
- `list()`：读取 `/books`，映射 `current | history | available` 与服务端进度。
- `getDetail(bookId)`：读取 `/books/{bookId}`。
- `getActivePlan()`：读取 `/learning-plans/current`；404 映射为 `null`。
- `switchActivePlan(planId)`：调用 `PATCH /learning-plans/current`。
- `activateBook(bookId)`：调用 `POST /learning-plans`；服务端复用该用户同书历史计划并原子激活。

DTO 到领域模型的转换集中在 mapper 中。页面只接收字符串化 ID 和现有领域状态，不感知服务端数字 ID、nullable 字段或枚举拼写。

### HttpSettingsRepository

`HttpSettingsRepository` 实现现有 `SettingsRepository`：

- `getSettings()`：从 `/settings` 映射 `autoSpeak` 与 `groupSize`；`reducedMotion` 从 Web 本地存储读取。
- `updateSettings()`：PATCH `autoSpeak`、`groupSize` 和固定的 `book_order` 策略，并在成功后保存本地 `reducedMotion`。
- `saveOnboarding()`：先 PATCH `groupSize/groupStrategy`，成功后 POST `/learning-plans`。计划创建失败时不回滚已保存的分组偏好，重试不会产生重复计划或重复分组成员。
- `supportsDemoReset()`：Mock Repository 返回 `true`，HTTP Repository 返回 `false`；设置页仅在返回 `true` 时展示“重置演示数据”区域。
- `resetDemo()`：继续只由 Mock 模式调用并重置版本化演示状态；HTTP Repository 不提供可触发的服务端数据清空行为。

`reducedMotion` 本阶段保持 Web 设备级偏好，不扩展服务端或 Android 契约。

## 页面行为与缓存

不重写首次设置、词书和设置页面。现有 Query/Mutation 成功回调继续负责失效相关查询：

- 首次设置成功后进入主应用，并刷新当前计划、词书和设置。
- 创建或切换计划后刷新词书列表、词书详情及当前计划查询。
- 设置保存成功后刷新设置；分组和今日仍为 Mock，本阶段不制造“真实设置已刷新 Mock 分组”的假象。

HTTP 请求期间沿用现有提交中状态，阻止重复创建计划、重复切书和重复保存。404 当前计划只在 `getActivePlan()` 中解释为“尚未设置”；其它 404 仍作为资源不存在错误。

## 错误处理

所有新 Repository 复用 WEB-API01 的 `AppError` 映射：

- 400 / `VALIDATION_ERROR`：显示可操作的设置或输入错误。
- 401：由共享认证客户端先刷新一次；刷新失败清理会话并交给登录门处理。
- 404：当前计划读取映射为 `null`；词书详情等其它资源返回 `not-found`。
- 409：显示计划切换或资源状态冲突。
- 网络错误与 5xx：保留当前页面状态并允许重试。

服务端错误不得导致 Web 回退到 Mock 数据；数据源只由启动配置决定，避免用户在真实模式看到混合伪造结果。

## 测试与验收

按 TDD 顺序完成：

1. OpenAPI 契约测试先断言 `BookItem` 新字段、`BookProgress` 口径，以及 `PreferencesPatchRequest` 的分组配置枚举。
2. `BookService` 测试覆盖当前、历史、未建立计划三种词书，并验证进度只统计目标用户和目标计划。
3. `SettingsService` 测试覆盖合法更新、非法分组大小、无当前计划、配置未变化不追加、配置变化增量追加及追加失败事务回滚。
4. Controller/序列化测试验证 nullable 字段、枚举、400 错误和响应 JSON。
5. `HttpBookRepository` 契约测试覆盖列表、详情、无当前计划、创建与切换请求及 DTO 映射。
6. `HttpSettingsRepository` 契约测试覆盖远端/本地设置合并、首次设置请求顺序、第二步失败后的可重试性，以及 HTTP 模式不伪造演示重置。
7. 运行时装配测试确认 Mock 模式完全不变，HTTP 模式只替换认证、词书和设置并共享认证客户端。
8. 页面回归测试覆盖首次设置、词书当前/历史/可选状态、切书、设置保存和失败重试。

完成后运行 API pytest、相关及全量 Spring Boot 测试、Web Vitest、Lint、生产构建和相关 Playwright。Docker/MySQL 可用时不得接受数据库集成测试 skipped；Android 不在本阶段验证范围内。

## 安全与业务边界

- 所有计划、进度与分组聚合严格按认证 `userId` 隔离。
- 学习进度只读取 `card_skill_memory`，不得由浏览、设置或切书操作写入。
- 只有测验判题链路可以写双层记忆与 `review_events`。
- 切书只切换唯一 `activePlanId`，不得删除旧计划、分组、记忆或审计事件。
- Web 不计算 FSRS、判题、分组顺序或进度；只展示服务端结果。
- HTTP 失败不静默回退 Mock，不泄露令牌或内部异常。

## 非目标

- 不修改 Android 页面、模型或网络层。
- 不接入 Today、Groups、Study、Quiz、Stats 或媒体 API。
- 不新增任意自定义分组大小、重新分组 UI 或新分组算法。
- 不新增数据库迁移或把分组配置迁移到学习计划表。
- 不重写现有页面视觉、侧栏或路由。
- 不实现生产部署、跨设备设置同步扩展或演示数据的服务端清空接口。
