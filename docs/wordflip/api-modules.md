# WordFlip API 模块划分说明

> 版本：v2.0
> 日期：2026-07-16
> 契约真相：[openapi.yaml](../../wordflip-api/openapi.yaml)

## 1. 模块边界

| 模块 | 主要职责 | 主要标识 |
|---|---|---|
| Auth | 注册、登录、刷新与资源隔离 | `userId` |
| Books | 公共/用户词书、导入预览与内容状态 | `bookId`, `lexemeId` |
| LearningPlans | 创建、切换唯一当前主词书，保留历史计划 | `planId`, `bookId` |
| Cards | 词书专属学习卡、考义和来源资料 | `cardId`, `lexemeId` |
| Groups | 当前计划内学习卡分组 | `planId`, `groupId`, `cardId` |
| Study | 浏览学习卡与学习日志；不写记忆 | `cardId`, `lexemeId` |
| Quiz | 服务端出题、判题与防重 | `sessionId`, `cardId` |
| Review | FSRS 调度、双层记忆与审计事务 | `cardId`, `lexemeId`, `skill` |
| Today | 当前计划的新卡、到期复习和任务排序 | `activePlanId`, `dueAt` |
| Media | 卡片图片与污渍 | `userId`, `cardId` |
| Stats | 学习日志、复习事件和成就聚合 | `userId`, `planId` |

系统不提供 Dicts 模块或全局词典设置。ECDICT、WordNet 等由 Cards 模块作为 `sourceMaterials` 返回。

## 2. 学习计划

- `POST /learning-plans` 为一本词书创建计划并激活；同用户同词书已有计划时返回或恢复该计划。
- `GET /learning-plans/current` 读取 `user_settings.active_plan_id`。
- `PATCH /learning-plans/current` 切换历史计划或更新计划参数。
- 切换必须锁定用户设置、校验计划归属并原子更新；旧计划数据不删除。
- Today、Groups、Study、Quiz 的服务层必须先解析当前计划，禁止客户端用任意 `bookId` 绕过当前计划。

## 3. 学习卡与来源资料

- `GET /books/{bookId}/cards` 只返回当前发布版卡片。
- `GET /learning/cards/{cardId}` 返回卡片考义、双 skill 进度和来源资料。
- `GET /words/{wordKey}` 返回当前计划中对应卡片与来源资料，不接受词典参数。
- 默认展示和测验始终使用学习卡考义；来源资料只在详情区展示。
- 未发布、待补充或没有合格主义项的卡片不得进入分组、今日任务或测验池。

## 4. 导入词书

导入分为 preview 与 confirm：

1. 服务端解析 JSON/CSV/TXT，规范化 `wordKey` 并保留用户原文。
2. 有中文释义时创建用户词书专属候选义项。
3. 只有英文时，从已发布公共内容生成候选，并记录来源。
4. 无可靠候选时标记 `review_required`，不进入测验池。
5. confirm 创建用户私有词书；用户释义绝不写入公共 `dictionary_senses`。

## 5. 分组

- 自动和手动分组都严格属于一个 `planId`。
- `study_group_cards.UNIQUE(plan_id, card_id)` 保证当前计划内一卡一组。
- 增量分组只追加当前词书中尚未入组的已发布卡。
- 重新分组只替换当前计划的自动分组成员，不删除双层记忆、复习事件、图片或污渍。

## 6. FSRS 与判题事务

配置固定为 FSRS 官方默认权重、目标保持率 `0.90`、最大间隔 `36500` 天，首版不训练个人参数。

题型映射：

| 题型 | skill |
|---|---|
| `dictation` | `dictation` |
| `choice_en_cn` | `choice` |
| `choice_cn_en` | `choice` |

评分映射：

| 判题结果 | FSRS rating |
|---|---|
| 错误 | `Again` |
| 正确 | `Good` |

`ReviewService.applyQuizResult` 是双层记忆唯一写入口，事务流程：

1. `QuizService` 校验会话、当前题、答案与 `requestId`。
2. 锁定或创建 `(userId, cardId, skill)` 卡片记忆和 `(userId, lexemeId, skill)` 词形记忆。
3. 服务端完成 rating 映射并计算新 FSRS 状态。
4. 写不可变 `review_events`，保存旧/新状态、题型、评分和算法版本。
5. 更新卡片记忆和词形熟悉度，关联 `quiz_answers.review_event_id`。
6. 任一步失败则整个事务回滚；重复 `requestId` 返回原结果，不重复调度。

卡片浏览、翻转、发音、详情展开和 `POST /study/sessions` 均不得调用该入口。

## 7. 跨词书诊断

- 新计划遇到已有 `lexeme_skill_memory` 的词形时，卡片仍保持 `new`，先加入诊断队列。
- 诊断题仍经正常判题与 FSRS 事务，结果初始化新 `card_skill_memory`。
- 词形熟悉度只能影响诊断优先级和题型，不能直接复制旧书卡片稳定性或 due 时间。

## 8. Today 与统计

- 所有查询限定当前 `activePlanId`。
- 到期复习由 `card_skill_memory.due_at <= now` 决定。
- 新卡来自当前词书已发布且未建立有效复习状态的卡片，并受每日新卡上限约束。
- 学习日志用于打卡和时长；`review_events` 用于正确率、复习量和算法审计。
- 统计与成就可按当前计划或用户全局聚合，但响应必须明确口径。

## 9. 内容管线

内容工具提供：

- `download`：仅在本地文件缺失或校验失败时从 manifest 官方地址下载。
- `verify`：校验 SHA-256、ZIP 完整性、SQLite `quick_check`、表结构、行数与中文覆盖。
- `build`：只抽取三本词书涉及的词条，保留原始数据，生成候选卡、异常报告和可版本控制覆盖文件。
- `publish`：将来源修订、词条、卡片和义项幂等发布到 MySQL；仅发布通过审核的卡。

异常包括缺中文、释义过长、多词性、技术标签、匹配歧义和无可靠候选。首版审核全部异常，并对每本书确定性抽检至少 100 条。
