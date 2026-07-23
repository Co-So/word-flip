# wordflip-android — Agent 指令

> 父级：[../AGENTS.md](../AGENTS.md)
> 产品规则：[../docs/wordflip/requirements.md](../docs/wordflip/requirements.md) v7

## 范围

Kotlin + Jetpack Compose Android MVP。客户端负责界面、交互、导航、设备能力和网络调用；服务端负责 FSRS、判题、分组、导入解析、Today 与统计计算。

## 模块结构

```text
app/                 # Application、Hilt、Plan Gate、主导航
feature-auth/
feature-today/
feature-books/
feature-groups/
feature-study/
feature-snapshot/
feature-quiz/
feature-stats/
feature-settings/
core-network/        # Retrofit、Auth、Repository
core-model/          # v7 API/展示模型
core-ui/             # Apple 语义主题、组件、动效
core-image/          # CameraX、相册、编辑器
```

依赖方向：`feature-* → core-*`；`core-*` 不可依赖 `feature-*`。

## v7 硬性规则

- **中文注释**：Composable、ViewModel、状态归约、导航和设备交互关键逻辑使用简体中文 KDoc/注释。
- **Plan Gate**：登录后先读取当前学习计划；无计划必须选一本主词书创建计划，再进入主导航。
- **唯一当前计划**：Today/Groups/Study/Quiz/Stats 展示服务端当前计划结果；切换成功后刷新受影响页面。
- **cardId 主键**：学习进度、分组成员、测验、图片、污渍使用 `cardId`；`wordKey` 只用于查询或展示。
- **客户端不算 FSRS**：不得本地计算 rating、dueAt、stability、mastery 或 Today 计数。
- **客户端不判业务正确性**：答案提交服务端；本地只做输入校验和反馈展示。
- **答题幂等**：同一道题的网络重试复用同一个 `requestId`，进入下一题后才生成新值。
- **浏览不改记忆**：翻卡、TTS、长按详情、图片/污渍和学习 session 上报不得伪造掌握度变化。
- **状态完整**：新增页面至少考虑 Loading / Content / Empty / Error；需要网络时补 Offline/Retry。
- **防重复提交**：创建计划、加入学习、导入确认和答题提交期间同步进入 submitting 状态。

## 视觉与交互基线

- 当前实现采用已批准的 **Apple 风格**层级、系统语义色、分组材质、排版和可中断动效。
- 复用 `core-ui` 的 Apple 语义原语和现有组件；Material 3 是实现基础，不代表回退到默认 Material 外观。
- 不用 v5 原型或旧 Natural Sage 文档覆盖当前已落地界面。
- 结构导航使用 Material 图标，不用 emoji。
- Light/Dark 跟随现有 `WordFlipTheme`；不要在 feature 内硬编码重复色值。
- 动效必须尊重 reduced motion，并避免阻塞输入或导航。

## 网络与模型

- Retrofit 路径必须与 `../wordflip-api/openapi.yaml` 一致。
- 学习计划：`POST/GET/PATCH /learning-plans`。
- 学习卡：`/books/{bookId}/cards`、`/learning/cards/{cardId}`。
- 媒体：`/learning/cards/{cardId}/image|stain`。
- 自定义分组提交 `cardIds`，不得重新发送 wordKey 作为成员主键。
- DTO 字段变化先改 OpenAPI，再同步 `core-model` 与 `core-network`。
- Repository 将 HTTP 细节转换为 `Result`；ViewModel 不直接依赖 Retrofit API。

## 导航要求

- 登录态路由：Auth → Plan Gate → Main。
- 主导航保留五 Tab；学习、测验、分组详情、卡拍和图片编辑器进入子栈。
- 每次进入测验都创建新 session；网络重试不得创建重复 session 或答题事件。
- 切换学习计划后清理仅属于旧计划的页面参数/缓存并重新拉取。
- 返回信号先消费再刷新，避免重复处理。

## 命令

```powershell
.\gradlew.bat test :app:assembleDebug
```

真机 USB：

```powershell
.\scripts\adb-reverse.ps1
.\scripts\install-phone-debug.ps1
```

debug API Base URL 为 `http://127.0.0.1:8080/api/v1`；模拟器使用 `http://10.0.2.2:8080/api/v1`。

## 测试重点

- Plan Gate 首次选书、防重复提交和错误恢复。
- 词书加入/切换计划后的返回刷新。
- Quiz 同题复用 `requestId`。
- ViewModel 的 Loading/Content/Error 与重试。
- Study 视图模式、翻卡和 session 上报不改本地 mastery。
- `:app:assembleDebug` 作为每次跨模块修改的最低构建门槛。

## 禁止

- 在客户端实现 FSRS、固定间隔、判题或掌握度写入。
- 使用 `(userId, wordKey)` 作为权威学习进度。
- 恢复 `activeDictId` 全局词典切换。
- 跨计划复用 groupId/cardId 而不重新拉取。
- feature 模块直接依赖另一个 feature 模块。
- 用 emoji 作为 Tab、工具栏或关键操作图标。
- 为修编译而删除中文业务注释或降低 DTO 非空约束。

## 参考

- [OpenAPI](../wordflip-api/openapi.yaml)
- [requirements v7](../docs/wordflip/requirements.md)
- [当前 Apple UI 设计](../docs/superpowers/specs/2026-07-17-wordflip-apple-today-study-design.md)
- [TASK §V7-A](../TASK.md)
