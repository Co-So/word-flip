# 词库结构化改造计划（Lexicon Restructure）

> 版本：v1.0  
> 日期：2026-07-10  
> 状态：**Phase A–C + ECDICT 纠偏完成**；下一步 Phase D 服务端读路径切 dict  
> 关联：[requirements.md](../requirements.md) · [database-design.md](../database-design.md) · [api-modules.md](../api-modules.md) · [architecture.md](../architecture.md) · [TASK.md](../../../TASK.md)

---

## 1. 背景与目标

### 1.1 问题

当前 `book_words` / `user_word_lexicon` 为**扁平 1:1 行模型**：`cn` 把多义、词性尾巴甚至短语碎片拼成一串。导致：

- 测验选项/题干语言混乱（「无正确答案」「答错展示不对」）
- 详情无法按义项展示例句
- 运行时 `WordSenseNormalizer` 只能治标，不能当词典真相

### 1.2 目标（对齐主流背单词 App）

```text
Headword (word_key / en / 音标)
  └─ Sense[]          ← 1:n（pos + cn + is_primary + quality）
       └─ Example[]   ← 1:n（en + cn）

学习进度 / 图片 / 污渍 / 分组
  └─ 仍绑 (user_id, word_key)   ← 不变
```

原则：**不怕大改，怕不能用** —— 分阶段可回滚；测验只认合格 primary sense。

### 1.3 非目标（本计划不做）

- 原声音频 / Elasticsearch 语境检索（不背单词级语料，二期）
- 按「考义」sense 出题（MVP 只用 primary）
- 词根词缀 / 派生树
- 修改 `word_key = lower(trim(en))` 算法或清空用户进度

---

## 2. 文档放置与权威层级

| 本计划路径 | 说明 |
|------------|------|
| **`docs/wordflip/plans/lexicon-restructure.md`** | 本文件：实施计划单一入口 |
| 实施完成后 | 结论并入 `database-design` / `requirements` / `api-modules`；本文件保留为历史决策 |

冲突时仍遵守 AGENTS 权威序：requirements → openapi → database-design → api-modules → architecture → **本计划（实施期补充）**。

---

## 3. 需要修改的文档清单（完整）

> Phase A 已完成：下表契约文档与 openapi 已写入正式条款（不再仅「待修订」指针）。

### 3.1 必须修改（契约与行为）

| 文档 | 改什么 | 阶段 |
|------|--------|------|
| [requirements.md](../requirements.md) | 新增词条字段职责 REQ；多义 1:n；测验只用 primary；导入须结构化清洗 | A / D |
| [database-design.md](../database-design.md) | 新增 `dict_words` / `dict_senses` / `dict_examples`；`book_words` 演进为词表引用；lexicon 与 primary 同步策略 | A / C |
| [api-modules.md](../api-modules.md) | WordLookup 读 dict；Quiz 出题池过滤；导入拆 sense；出题质量规则升级 | A / D |
| [architecture.md](../architecture.md) | §4.6 单词模型改为 headword+senses；清洗工具离线管道 | A |
| [wordflip-api/openapi.yaml](../../../wordflip-api/openapi.yaml) | `Sense` / `Example` schema；`WordSummary`/`WordCard` 扩展 `senses`；说明 `cn/pos/ph`=primary | A / D |
| [android-ui-spec.md](../android-ui-spec.md) | 卡片背面=primary；详情抽屉=多义项+例句 | A / E |
| [TASK.md](../../../TASK.md) | 新增 **§P-LEX** 轨道与勾选项 | 本提交 |

### 3.2 应当修改（索引与规范）

| 文档 | 改什么 | 阶段 |
|------|--------|------|
| [docs/README.md](../../README.md) | 索引本计划与后续 tools 说明 | 本提交 |
| [STRUCTURE.md](../../../STRUCTURE.md) | `docs/wordflip/plans/`、`tools/` 目录约定 | 本提交 |
| [AGENTS.md](../../../AGENTS.md) | 不可违反规则补「释义真相来自 sense / primary」；链到本计划 | 本提交 / D |
| [coding-standards.md](../coding-standards.md) | 词条/清洗相关类注释术语（sense、primary、quality） | D |

### 3.3 可选 / 二期

| 文档 | 改什么 |
|------|--------|
| [user-design.md](../user-design.md) | 一般不改（账号无关） |
| [design-system/MASTER.md](../design-system/MASTER.md) | 仅当详情多义项有新 UI token 时 |
| `docs/prd/WordFlip-PRD.md` | **勿作实施依据**；可不改 |
| `prototypes/wordflip-v5.html` | 非数据源；可不改 |

### 3.4 新增产物（非 docs 内 SQL）

| 路径 | 说明 |
|------|------|
| `tools/word-lexicon-cleaner/` | 规则 + LLM 清洗工具（STRUCTURE 已约定） |
| `wordflip-server/.../db/migration/V13__*.sql` 等 | 建表与灌数（禁止放进 `docs/`） |

---

## 4. 目标数据模型

### 4.1 表（建议命名）

**全局词典**

| 表 | 关键列 | 说明 |
|----|--------|------|
| `dict_words` | `word_key` PK, `en`, `ph`, `ph_us` NULL | headword |
| `dict_senses` | `id`, `word_key`, `pos`, `cn`, `is_primary`, `sort_order`, `quality` ENUM(`ok`,`uncertain`,`reject`) | 义项 |
| `dict_examples` | `id`, `sense_id`, `en`, `cn`, `sort_order` | 例句挂义项 |

约束（应用层 + 尽量 DB）：

- 每个 `word_key` **恰好一个** `is_primary=1` 且 `quality=ok`（reject 词可无 primary，则禁止测验）
- `cn` 必须含汉字；**禁止**把词性写进 `cn`（词性只在 `pos`）

**词书**

| 演进 | 说明 |
|------|------|
| 过渡期 | `book_words` 保留旧 `cn/pos/ph/detail_json` **只读** |
| 目标 | `(book_id, word_key, sort_order)` 引用 `dict_words` |

**用户域**

| 表 | 说明 |
|----|------|
| `user_word_lexicon` | 仍 `(user_id, word_key)`；`cn/pos/ph` 作 **primary 冗余缓存**（由 dict 同步），避免旧客户端空窗 |
| 进度表 | `group_words` / `word_skill_progress` / images / stains **不改键** |

### 4.2 API 兼容

```text
WordSummary.cn / pos / ph  = primary sense（干净）
WordSummary.senses?        = 完整义项（详情）
Quiz                       = 只用 primary；reject / 无 primary → 不出题
```

---

## 5. 清洗工具设计

路径：`tools/word-lexicon-cleaner/`

```text
原始 book_words
  → 规则引擎（可单测）
  → ok / uncertain / reject
  → uncertain → 大模型结构化 JSON
  → Schema 校验
  → 生成 Flyway seed / upsert SQL
```

| 层级 | 职责 |
|------|------|
| 规则 | 剥尾部 `(n.)`、按 `；` 拆义、英文头检测、无汉字 reject |
| LLM | 仅 uncertain；离线批处理；不进请求热路径 |
| 校验 | primary 唯一、pos 枚举、cn 含汉字、禁止英文充中文 |

CLI 形态：`rules` → `llm` → `merge` → `emit` → `report`。  
黄金样例 ≥30 条进 CI。

---

## 6. 产品行为定稿（改造后）

| 场景 | 规则 |
|------|------|
| 卡片背面 | primary：`cn` + `pos` + `ph` |
| 详情抽屉 | 全部 senses + 各义项 examples |
| 默写题干 | primary.cn |
| 英选中 / 中选英 | primary 中英文；干扰项用其他词 primary |
| 判题键 | 仍 `en` / `wordKey`（进度维度是词） |
| reject / 无合格 primary | 可浏览，**禁止入测验池** |

---

## 7. 实施阶段

| 阶段 | 内容 | 出口 | 预估 |
|------|------|------|------|
| **A** | 契约：改本节 §3.1 所列定稿文档 + openapi | 三句话无歧义：表、API、测验只用 primary | 0.5–1d |
| **B** | 清洗工具 MVP：规则 + 报告 + LLM 适配（无 Key 可跳过） | 对 ~14k 词跑通；ok≥70% 目标 | 1–2d |
| **C** | Flyway 建表 + 灌数；lexicon 同步 primary | 任意词可查多 sense | 1–2d |
| **D** | WordLookup / Quiz / Import 切读路径 | 测验不依赖脏字符串 | 1–2d |
| **E** | Android 详情多义 + 模型 | 真机多义词可读 | 1d |
| **F** | 降级 Normalizer；废弃旧 cn 依赖；TASK 勾选 | 单一真相源 | 0.5–1d |

**合计约 5–9 人天**（不含全量 LLM 费用与人工抽检）。

---

## 8. 风险与回滚

| 风险 | 缓解 |
|------|------|
| 灌数错误 | 副本库先跑；保留旧列只读；配置 `lexicon.source=legacy\|dict` |
| LLM 胡编 | Schema 硬校验；宁 reject 不入库 |
| 旧 App | `WordSummary.cn` 继续填 primary |
| 进度丢失 | 不改 wordKey；不删 progress |
| 大 seed | 分包迁移或 CI artifact，禁止只靠巨型单文件难审 |

回滚点：Phase D 前只加表不切读路径。

---

## 9. 验收标准

1. 随机 50 题：无「（无释义）」正确项、无英文充中文、答错展示与正确项一致  
2. 多义词详情 ≥2 sense，例句不串义  
3. reject 在报告中且不进测验池  
4. 老用户分组 / 热力仍在  
5. 规则黄金样例 CI 通过  

---

## 10. 已拍板决策（实施默认）

| # | 决策 | 选择 |
|---|------|------|
| 1 | 词典表形态 | **全局 `dict_*`**（内置书共享）；非仅用户 lexicon 下拆表 |
| 2 | MVP 例句 | **要 `dict_examples`**（可先少、可空） |
| 3 | LLM | 工具支持可配置 API；**无 Key 时规则产物仍可交付**，uncertain 留队列 |
| 4 | 测验义项 | MVP **仅 primary** |
| 5 | 进度键 | **保持 wordKey** |

---

## 11. 修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-07-10 | v1.0 | 初版定稿：模型、文档清单、工具、阶段、验收；写入 `docs/wordflip/plans/` |
| 2026-07-10 | v1.1 | Phase A 完成：权威文档与 openapi Sense/Example 已写入；状态改为推进 B |
| 2026-07-10 | v1.2 | Phase B：`tools/word-lexicon-cleaner` 规则+LLM+报告；去重词 ok≈99.7% |
| 2026-07-10 | v1.3 | Phase C：V13–V15 dict 建表灌数 + lexicon primary 同步 |
| 2026-07-10 | v1.4 | **纠偏**：放弃「脏词书正则=词典」；ECDICT 覆盖 dict（V16）；be/have/to 等核心词可用 |
