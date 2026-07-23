# WordFlip v7 任务清单（TASK）

> 版本：v4.0
> 日期：2026-07-23
> 当前基线：**v7.0（单主词书 + 词书专属学习卡 + 双层 FSRS）**
> 用法：完成一项后将 `[ ]` 改为 `[x]`；只标记已有代码、测试或人工验收证据的任务。
> 权威依据：[requirements.md](docs/wordflip/requirements.md) · [openapi.yaml](wordflip-api/openapi.yaml) · [database-design.md](docs/wordflip/database-design.md) · [api-modules.md](docs/wordflip/api-modules.md)

---

## 1. 当前焦点

v7 契约、v2 数据库基线、服务端主链路和 Android 适配已经进入仓库。当前阶段不是继续扩展旧 v6 功能，而是完成 **v7 收敛验收**：

1. 在真实 MySQL 上建立全新 `wordflip_v2`，发布三本内置词书内容。
2. 补齐被 Docker 环境跳过的数据库集成测试。
3. 真机走通「注册 → 选主词书 → 学习计划 → 分组 → 学习 → 测验 → 统计」。
4. 验证切换主词书后旧计划、FSRS 记忆和审计事件仍保留。
5. 完成安全、生产配置、发布构建与 MVP 演示材料。

### 1.1 当前状态

| 轨道 | 状态 | 下一道门 |
|------|------|----------|
| v7 产品与 API 契约 | ✅ 已落地 | 契约变更继续遵守 OpenAPI-first |
| v2 数据库基线 | 🟡 文件已落地 | 真实 MySQL 重建与内容发布 |
| Spring Boot 主链路 | 🟡 单元测试通过 | Testcontainers/MySQL 集成与 E2E |
| Android v7 适配 | 🟡 构建和单测通过 | 真机完整流程 |
| 发布准备 | ⏳ 未完成 | Dockerfile、prod 配置、release、演示 |

### 1.2 最近验证证据（2026-07-22）

- `wordflip-api`: `python -m pytest -q tests` → **10 passed**
- `wordflip-server`: `.\mvnw.cmd test` → **32 tests，0 failures，2 skipped**
- `wordflip-android`: `.\gradlew.bat test :app:assembleDebug` → **BUILD SUCCESSFUL**
- 后端跳过的 2 个测试为 MySQL/Testcontainers 集成测试；Docker Desktop 不可用时不视为完成。

---

## 2. v7 核心验收不变量

所有任务必须同时满足：

- 用户任一时刻只有一个 `activePlanId`；切换主词书等同于切换学习计划，旧计划保留。
- 学习内容真相是词书中的已发布 `learning_card`；同一 `wordKey` 在不同词书可有不同考义。
- 用户学习进度、图片和污渍绑定 `cardId`；`wordKey` 只作为规范词形查询键。
- `card_skill_memory` 是当前词书考义的权威 FSRS 状态；`lexeme_skill_memory` 仅用于跨词书诊断。
- 默写与选择按 `skill` 独立调度；一条轨道的答题不得覆盖另一条。
- 只有服务端测验判题可以写双层记忆；客户端不得提交 FSRS rating，也不得本地改掌握度。
- 每次有效答题必须在同一事务写入 `review_events` 与新记忆状态；`requestId` 保证幂等。
- 卡片翻转、浏览、发音和 `POST /study/sessions` 不写记忆或 `review_events`。
- 今日、分组、学习、测验和统计都必须限定在当前学习计划。
- ECDICT、WordNet 等是可追溯的来源资料，不是用户可切换的全局展示词典。

---

## 3. V7-D 文档与契约

- [x] **V7-D01** `requirements.md` 定稿 v7：单主词书、词书专属学习卡、双层 FSRS
- [x] **V7-D02** `database-design.md` 对齐 v2 核心表与不变量
- [x] **V7-D03** `api-modules.md` 对齐学习计划、学习卡、FSRS 判题事务
- [x] **V7-D04** `openapi.yaml` 对齐 v7 路径与 schema
- [x] **V7-D05** `wordflip-api/tests/test_learning_card_contract.py` 覆盖关键契约
- [x] **V7-D06** 根/子目录 `AGENTS.md`、`TASK.md`、根 `README.md` 切换到 v7 基线
- [ ] **V7-D07** 修订 `architecture.md` 中残留的 v6 服务、分组和下一步章节
- [ ] **V7-D08** 修订 `android-ui-spec.md` 与当前 Apple 风格实现、Plan Gate 一致
- [ ] **V7-D09** 在历史 PRD 与 v6 计划顶部增加“已被 v7 取代”声明

---

## 4. V7-DB 数据库与内容管线

- [x] **V7-DB01** 建立 `migration-v2/V1__init_wordflip_v2.sql` 全新基线
- [x] **V7-DB02** 将旧 V1–V24 迁移移入 `wordflip-server/db-archive/migration-v1/`
- [x] **V7-DB03** 建立 `lexemes → book_items → learning_cards → senses/examples` 内容模型
- [x] **V7-DB04** 建立 `user_learning_plans`、`study_groups`、`study_group_cards`
- [x] **V7-DB05** 建立 `card_skill_memory`、`lexeme_skill_memory`、`review_events`
- [x] **V7-DB06** 建立 `tools/content-pipeline/` 的 verify/build/publish 流程
- [x] **V7-DB07** 提供安全重建脚本 `scripts/rebuild-wordflip-v2.ps1`
- [x] **V7-DB08** 提供服务冒烟脚本 `scripts/smoke-wordflip-v2.ps1`
- [ ] **V7-DB09** 运行内容管线 verify/build，人工审核异常清单和确定性抽检样本
- [ ] **V7-DB10** dry-run 检查后创建全新 `wordflip_v2`，禁止覆盖旧库
- [ ] **V7-DB11** 在新库发布 IELTS/CET4/考研三本词书，核对 published card 数量
- [ ] **V7-DB12** 运行 `WordFlipV2MySqlIntegrationTest`，确保 2 个测试均执行且通过
- [ ] **V7-DB13** 验证备份可恢复，并记录 v1 → v2 上线/回滚步骤

---

## 5. V7-S Spring Boot

### 5.1 已落地主链路

- [x] **V7-S01** `POST/GET/PATCH /learning-plans`：创建、读取和切换当前计划
- [x] **V7-S02** 首次选书原子创建计划并更新 `active_plan_id`
- [x] **V7-S03** Books/Groups/Today/Study/Quiz/Stats 查询限定当前计划
- [x] **V7-S04** 词书卡片、卡片详情与来源资料读路径
- [x] **V7-S05** 自定义分组使用当前计划未入组 `cardIds`
- [x] **V7-S06** `FsrsSchedulerService` 锁定 FSRS 参数与调度策略
- [x] **V7-S07** `FsrsReviewService` 同事务更新双层记忆并写 `review_events`
- [x] **V7-S08** `QuizService` 服务端判题、`requestId` 幂等与 session 审计
- [x] **V7-S09** Study session 只写浏览/打卡，不写 FSRS 记忆
- [x] **V7-S10** Stats summary/heatmap/achievements 使用当前计划
- [x] **V7-S11** 图片与污渍迁移到 `cardId`，并校验当前用户/计划资源
- [x] **V7-S12** Auth、学习计划、FSRS、内容 DTO 等单元/MockMvc 测试通过

### 5.2 待收敛

- [ ] **V7-S13** 在真实 v2 数据库启动 dev profile，Flyway validate 无误
- [ ] **V7-S14** 执行注册、登录、选书、分组、学习、测验、统计 API E2E
- [ ] **V7-S15** 验证重复 `requestId` 不重复写 `review_events`
- [ ] **V7-S16** 验证答题事务失败时事件与两层记忆同时回滚
- [ ] **V7-S17** 验证切换计划后旧计划资源不可被当前计划接口越权访问
- [ ] **V7-S18** 验证删除/归档导入词书不会破坏历史计划和审计
- [ ] **V7-S19** 上传白名单、5MB 上限、对象路径与用户隔离安全测试
- [ ] **V7-S20** 500 卡分组与 Today/Quiz 查询性能基准

---

## 6. V7-A Android

### 6.1 已落地主链路

- [x] **V7-A01** 登录后 Plan Gate 检查当前学习计划
- [x] **V7-A02** 无计划用户选择一本主词书并创建计划
- [x] **V7-A03** Books 页展示当前词书并支持加入/切换学习
- [x] **V7-A04** core-model/core-network 对齐 `planId`、`cardId`、FSRS snapshot
- [x] **V7-A05** Groups/Study/Quiz/Media 使用 `cardId`
- [x] **V7-A06** Quiz 每题生成稳定 `requestId`，重试不重复提交业务事件
- [x] **V7-A07** 全应用 Apple 风格层级、材质、排版与动效已落地
- [x] **V7-A08** 主题支持系统/浅色/深色，业务文案与结构图标不使用 emoji 导航
- [x] **V7-A09** Android 单元测试与 `:app:assembleDebug` 通过

### 6.2 待真机验收

- [ ] **V7-A10** 新用户注册后必须停在选主词书页，成功创建计划后进入今日
- [ ] **V7-A11** 词书详情加入学习、切换主词书、返回刷新与防重复提交
- [ ] **V7-A12** 当前计划的自动分组、自定义分组和未入组卡片池一致
- [ ] **V7-A13** 学习翻卡/发音/结束上报不改变 FSRS 热力
- [ ] **V7-A14** 默写与选择分别更新 skill，结果页与组详情/今日统计刷新
- [ ] **V7-A15** 图片上传、编辑、删除与污渍在学习/卡拍/组详情一致
- [ ] **V7-A16** 切换主词书后旧计划进度保留，当前页面不泄漏旧计划数据
- [ ] **V7-A17** Loading/Content/Empty/Error/Offline 与旋转、返回、键盘场景走查

---

## 7. V7-Q 集成、质量与发布

- [x] **V7-Q01** OpenAPI 契约测试通过（10 tests）
- [x] **V7-Q02** 后端测试基线通过（32 tests；其中 Docker 集成 2 skipped）
- [x] **V7-Q03** Android 单测与 Debug APK 构建通过
- [ ] **V7-Q04** Docker Desktop 环境下全量测试无 skipped
- [ ] **V7-Q05** `scripts/smoke-wordflip-v2.ps1` 对已登录用户通过
- [ ] **V7-Q06** 按 requirements 附录 B 完成逐页手工清单
- [ ] **V7-Q07** 编写 `wordflip-server/Dockerfile`
- [ ] **V7-Q08** 编写不含密钥的 `application-prod.yml` 模板
- [ ] **V7-Q09** Android release build、R8 与签名配置说明
- [ ] **V7-Q10** 清理 Gradle 10 兼容性弃用警告
- [ ] **V7-Q11** 完成 MVP 演示脚本与测试账号/样例词书说明
- [ ] **V7-Q12** 合并当前功能分支前完成 review、测试与变更说明
- [ ] **V7-Q13** 用户确认后创建 `v0.1.0-mvp` tag

---

## 8. 二期 Backlog（不阻塞 v7 MVP）

- [ ] **B-01** React Web 登录、当前计划与今日页
- [ ] **B-02** Room 离线缓存与离线只读学习
- [ ] **B-03** FCM 每日/到期复习提醒
- [ ] **B-04** 词书导出 CSV / Anki / Quizlet
- [ ] **B-05** 多设备同步与冲突策略
- [ ] **B-06** 个性化 FSRS 参数训练
- [ ] **B-07** 内容审核后台与版本回滚 UI

---

## 9. 实施顺序

```text
V7-D 入口文档收敛
  → V7-DB09~13 内容构建与真实新库
  → V7-S13~20 服务端集成、安全、性能
  → V7-A10~17 Android 真机
  → V7-Q04~13 发布准备
```

API 或持久化变更仍按以下顺序：

```text
requirements（行为变化时）
  → openapi.yaml
  → api-modules.md / database-design.md
  → migration-v2
  → server
  → Android core-model/core-network
  → 自动化与真机验收
```

---

## 10. 历史说明

- 旧 TASK v3.2 的 P0–P4、P-LEX、P-DICT-QUALITY、P-MULTI-DICT 记录描述的是 v6 迭代过程，不再作为当前实现任务入口。
- 旧迁移保存在 `wordflip-server/db-archive/migration-v1/`，仅供追溯和内容构建输入，禁止继续追加生产迁移。
- v7 不提供全局词典切换；ECDICT、WordNet 等通过 `sourceMaterials` 作为来源资料展示。
- v7 不使用固定 `[1, 2, 4, 7, 15, 30]` 间隔，也不以 `(userId, wordKey)` 保存权威学习进度。

---

## 11. 每次任务交付自检

- [ ] 行为符合 requirements v7 与 OpenAPI
- [ ] 当前计划资源隔离正确
- [ ] 学习进度绑定 `cardId + skill`
- [ ] 只有测验判题写双层记忆与 `review_events`
- [ ] 业务代码新增/修改处使用简体中文注释
- [ ] 相关自动化测试或真实联调已执行
- [ ] TASK 对应项有完成证据后才勾选
- [ ] 未提交密钥、`.env`、keystore 或真实用户数据
- [ ] 未自动 commit/push；除非用户明确要求
