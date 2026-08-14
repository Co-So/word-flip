# Task 7 · TodayPage 真实 Dashboard

## 完成内容

- 今日页改为展示服务端快照的中文日期、连续打卡与三项统计：已掌握、待复习、计划完成度。
- 最近学习分组置于今日任务之前，最多显示 3 条，并使用真实 `groupId` 进入分组详情。
- 新词与到期复习优先进入任务首个来源分组；无来源时才回退推荐分组。测验保持非链接的下一阶段提示，已移除 `study-demo` 入口。
- 无任务保留浏览词书空态；加载失败可重试，重试成功会清除旧错误。

## TDD 与验证

- RED：`npm test -- TodayPage.test.tsx`，7 项中 5 项按预期失败（旧页面缺少新 Dashboard 行为）。
- GREEN：`npm test -- TodayPage.test.tsx`，7/7 通过。
- 聚焦：`npm test -- TodayPage.test.tsx routeGuards.test.tsx`，2 files / 8 tests 通过。
- 全量：`npm test`，29 files / 274 tests 通过。
- `npm run lint` 通过。
- `npm run build` 通过。
- `git diff --check` 通过。

## 范围与限制

- 仅修改 `TodayPage.tsx`、对应样式和测试；本报告为交付记录。
- 未执行 E2E；本任务未修改路由、Repository、Mock/HTTP 映射或服务端逻辑。
