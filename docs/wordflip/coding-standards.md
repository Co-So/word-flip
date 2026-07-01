# WordFlip 代码注释规范（中文）

> 版本：v1.0  
> 日期：2026-06-30  
> 受众：**人类开发者 + AI Agent**（Cursor / Codex 等）  
> 关联：[AGENTS.md](../../AGENTS.md) · [requirements.md](./requirements.md)

本仓库业务域为中文产品（掌握度、SRS、词书、分组等）。**新增与修改的业务代码必须使用简体中文注释**，便于团队与 AI 在后续迭代中理解意图，并与 `requirements.md` / `api-modules.md` 术语一致。

---

## 1. 总原则

| 原则 | 说明 |
|------|------|
| **语言** | 注释、类/方法 JavaDoc·KDoc、Flyway 脚本内注释 → **简体中文** |
| **术语** | 与产品文档一致：未学习 / 模糊 / 不认识、增量追加、wordKey、掌握度、艾宾浩斯/SRS |
| **适度** | 自解释代码（getter、简单 DTO 字段映射）可省略；**业务规则、分支、算法、副作用** 必须注释 |
| **同步** | 改逻辑必改注释；禁止误导性或过时的中文注释 |
| **边界** | 用户可见 UI 文案走 string resources；注释不写进界面 |

**代码标识符**（类名、方法名、变量名、API 路径、JSON 字段）保持 **英文**，与 OpenAPI / 数据库一致；**不要用拼音命名**。

---

## 2. 必须写中文注释的场景

- 实现或变更 **REQ-*** / **api-modules** 中的业务规则  
- `Service` 内事务边界、调用链、与 Redis/MinIO 的副作用  
- 非显而易见的 `if/else`、循环、排序、去重、分页  
- 魔法数字（SRS 间隔、TTL、分页上限）→ 注释说明或提取命名常量并在注释中引用 REQ  
- 对外 API 的 Controller 方法（简要说明用途与主要错误码）  
- Flyway 迁移：表/索引/约束的业务含义  
- Android：`ViewModel` 状态机、导航条件、与后端的职责边界  

---

## 3. 不必写或少写的场景

- 纯样板：`getXxx` / `setXxx`、Lombok 生成代码  
- 与 openapi.yaml / Entity 字段一一对应的 DTO 字段（除非有业务含义）  
- 整文件重复描述类名已表达的信息  
- 用中文注释解释 **Java/Kotlin 语法本身**  

---

## 4. 分语言约定

### 4.1 Java（wordflip-server）

| 位置 | 格式 | 示例 |
|------|------|------|
| 类 | `/** ... */` 块注释 | 说明职责、对应 openapi Tag |
| public 方法 | JavaDoc + `@param` `@return` | 中文描述参数与返回值 |
| 业务方法内部 | `//` 行注释 | 关键步骤、为何这样分支 |
| 常量 | 行尾或上一行 | 单位、与 REQ 对应关系 |

```java
/**
 * 复习调度与掌握度状态机。
 * 掌握度三态的唯一写入口：{@link #applyQuizResult}（REQ-QUIZ-6、REQ-EBBING-2~4）。
 */
@Service
public class ReviewService {

    /** SRS 间隔（天），索引为 stage，见 REQ-EBBING-1 */
    private static final int[] INTERVALS = {1, 2, 4, 7, 15, 30};

    /**
     * 测验判题后更新掌握度与复习计划。
     *
     * @param userId  当前用户
     * @param wordKey 归一化英文键，见 wordKey 约定
     * @param correct 本次是否答对（忽略大小写判题在 QuizService 完成）
     */
    public void applyQuizResult(Long userId, String wordKey, boolean correct) {
        // 连续答错判定：写入本条答案前，查最近一条 quiz_answers
        ...
    }
}
```

### 4.2 Kotlin（wordflip-android）

| 位置 | 格式 |
|------|------|
| 类 / 公开函数 | KDoc `/** */` |
| Composable | 说明页面 REQ 章节、数据来源（只读/不写掌握度） |
| 复杂 LaunchedEffect / 状态 | `//` 说明触发条件 |

```kotlin
/**
 * 今日首页（REQ-TODAY-1~12）。
 * 任务计数来自服务端 GET /today，不在客户端计算 SRS。
 */
@Composable
fun TodayScreen(viewModel: TodayViewModel = hiltViewModel()) {
    // 默认展示「开始学习」固定底栏，不随列表滚动
    ...
}
```

### 4.3 SQL（Flyway）

- **表注释**：`CREATE TABLE ... COMMENT='表说明'`
- **字段注释**：重要列使用 `COMMENT '字段说明'`
- **脚本内**：模块分区用 `--` 中文说明；唯一索引/约束可在上一行注释业务含义

```sql
-- 一词一组：同一用户下 word_key 只能出现在一个分组（REQ-BOOK-21）
CREATE TABLE group_words (
    user_id  BIGINT UNSIGNED NOT NULL COMMENT '冗余：支撑 (user_id, word_key) 唯一',
    word_key VARCHAR(191)    NOT NULL COMMENT '归一化键 LOWER(TRIM(en))',
    ...
) COMMENT='分组单词（一词一组）';
```

查看注释：`SHOW FULL COLUMNS FROM group_words;` 或 `information_schema.TABLES/COLUMNS`。

### 4.4 TypeScript（wordflip-web，二期）

- 与 Java/Kotlin 相同：**中文 JSDoc**，标识符英文。

---

## 5. 术语对照（注释中统一用词）

| 中文（注释/UI） | 代码/API 枚举 |
|-----------------|---------------|
| 未学习 | `unlearned` |
| 模糊 | `fuzzy` |
| 不认识 | `unknown` |
| 已掌握（统计） | 非 level；`stage>=5` 且间隔 ≥30 天 |
| 增量追加分组 | `appendGroupsForNewWords`，非 rebuild |
| 单词键 | `wordKey` = `en.trim().toLowerCase()` |

完整业务规则见 [api-modules.md](./api-modules.md)。

---

## 6. AI Agent 自检清单

提交代码前确认：

- [ ] 新增/修改的 **Service、Controller、ViewModel、Repository 实现** 含中文类/方法说明  
- [ ] 业务分支与副作用有中文行注释  
- [ ] 未用大量英文注释替代中文（**openapi.yaml 的 description 除外，可中英并存，以中文业务描述为主**）  
- [ ] 注释与当前代码行为一致  
- [ ] 未注释显而易见的样板代码凑行数  

---

## 7. 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-30 | v1.0 | 初版：中文注释规范，服务/Android/SQL |
