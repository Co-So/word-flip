# WordFlip v7 Documentation Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `TASK.md`、根与子目录 `AGENTS.md`、根 `README.md` 全面同步到已落地的 WordFlip v7 基线。

**Architecture:** 以 `requirements.md` v7、`openapi.yaml`、`database-design.md` 和现有实现为事实来源，重写入口文档中的阶段、业务不变量、开发命令与验收路径。历史 v6、多词典、固定间隔 SRS 和 `(userId, wordKey)` 学习进度规则不再作为当前实现依据。

**Tech Stack:** Markdown、PowerShell、Git、OpenAPI 3.0.3、Spring Boot 3、Android Compose。

## Global Constraints

- 只修改项目入口文档与本实施计划，不修改业务代码或权威产品规格。
- 需求冲突以 `docs/wordflip/requirements.md` v7 为准。
- 不自动提交或推送 Git。
- 所有业务规则说明使用简体中文。

---

### Task 1: 重建 v7 任务清单

**Files:**
- Modify: `TASK.md`

**Interfaces:**
- Consumes: v7 requirements、OpenAPI、数据库基线、现有测试和脚本状态。
- Produces: 后续开发与验收唯一任务入口。

- [x] **Step 1: 替换旧 P0–P4 任务视图**

将当前焦点改为 v7 收敛，按文档契约、数据库内容、服务端、Android、验收发布五条轨道组织。

- [x] **Step 2: 标记已落地与待验收项**

保留可证实的完成项；将真实 MySQL、内容发布、端到端真机和发布准备保留为未完成。

- [x] **Step 3: 扫描旧业务规则**

Run: `rg -n "requirements v6|P-MULTI|activeDictId|applyQuizResult\\(userId, wordKey|\\[1, 2, 4, 7, 15, 30\\]" TASK.md`

Expected: 无匹配。

### Task 2: 同步 Agent 指令

**Files:**
- Modify: `AGENTS.md`
- Modify: `wordflip-server/AGENTS.md`
- Modify: `wordflip-android/AGENTS.md`
- Modify: `wordflip-api/AGENTS.md`

**Interfaces:**
- Consumes: v7 核心不变量和当前包/API 结构。
- Produces: Agent 在各目录内执行任务时的强制约束。

- [x] **Step 1: 更新根规则**

写明单一当前学习计划、词书专属学习卡、`cardId + skill`、双层记忆、测验唯一写入、服务端 FSRS 和 v2 迁移目录。

- [x] **Step 2: 更新后端规则**

改为 JDBC/JPA 混合持久化、`FsrsReviewService`、`review_events` 同事务与当前计划资源隔离。

- [x] **Step 3: 更新 Android 规则**

改为 v7 Plan Gate、`cardId` 媒体/进度、服务端权威、Apple 风格组件与当前 Material 语义主题。

- [x] **Step 4: 更新 API 规则**

替换 `PUT /settings` 与全局词典规则，写明 learning-plans、learning cards、requestId 和契约测试。

- [x] **Step 5: 扫描冲突**

Run: `rg -n "requirements v6|P-MULTI|activeDictId|ReviewService\\.applyQuizResult|PUT /settings|group_words|word_mastery" AGENTS.md wordflip-*/AGENTS.md`

Expected: 无当前规则误用；若出现，只能用于明确标注历史废弃。

### Task 3: 重写项目 README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: 当前目录结构、v2 重建脚本、内容管线、构建与冒烟命令。
- Produces: 人类开发者的 v7 入门与下一步入口。

- [x] **Step 1: 更新产品与架构摘要**

说明当前主词书、学习计划、词书专属学习卡、双层 FSRS 和 Apple 风格 Android。

- [x] **Step 2: 更新本地启动与 v2 数据流程**

保留 Docker/server/Android 命令，增加内容构建、新库重建和冒烟脚本说明。

- [x] **Step 3: 更新文档阅读顺序与下一步**

将 requirements v7 放在历史 PRD 之前；下一步改为真实数据库和真机验收。

### Task 4: 全量验证

**Files:**
- Verify: `TASK.md`
- Verify: `AGENTS.md`
- Verify: `wordflip-server/AGENTS.md`
- Verify: `wordflip-android/AGENTS.md`
- Verify: `wordflip-api/AGENTS.md`
- Verify: `README.md`

**Interfaces:**
- Consumes: Tasks 1–3 输出。
- Produces: 无旧基线冲突、链接有效、范围清晰的最终 diff。

- [x] **Step 1: 检查 Markdown 链接目标**

解析六个目标文件中的相对链接并验证本地目标存在。

- [x] **Step 2: 全文冲突扫描**

Run: `rg -n "requirements v6|多词典可选|activeDictId|固定间隔|P0-B|P2\\.5" TASK.md AGENTS.md README.md wordflip-*/AGENTS.md`

Expected: 无当前基线误用。

- [x] **Step 3: 检查 Git diff**

Run: `git diff --check`

Expected: 无错误。

- [x] **Step 4: 汇报但不提交**

列出修改文件、验证命令和仍待执行的真实数据库/真机限制。
