# 多词典可选（Multi-Dict）

> 版本：v1.0  
> 日期：2026-07-11  
> 状态：**已落地（契约 + V21–V24 + Lookup/Quiz/Settings + 工具脚手架 + Android 选词典）**  
> 关联：[dict-quality.md](./dict-quality.md) · [lexicon-restructure.md](./lexicon-restructure.md) · TASK §P-MULTI-DICT

## 1. 目标

词书 = membership（wordKey 列表）；释义来自用户所选词典。支持多本词典切换。

## 2. 上架词典

| dict_id | 展示名 | locale | 默认 | 数据源 |
|---------|--------|--------|------|--------|
| `wordflip_curated` | WordFlip 精校 | zh | 是 | ECDICT + learning-primary |
| `wiktionary_zh` | 维基词典 | zh | 否 | Kaikki Wiktionary 英→中（首轮按词书 keys） |
| `wordflip_concise` | 简明学习版 | zh | 否 | curated 派生（≤2 义、cn≤40） |
| `wordnet` | WordNet 英英 | en | 否 | Princeton WordNet |

例句增强：Tatoeba（非独立词典）。

## 3. WordNet 规则

- `Sense.cn` 可空；`enGloss` 存英义
- WordNet 仅作为学习卡详情来源资料，不参与测验考义选择。
- 缺词回退：优先同 locale；WordNet 无英英释义时回退 curated 中文释义（浏览/学习兜底，测验仍按 locale 降级）
- 全量 WordNet 数据通过 `tools/import-wordnet/` 工具补充，回退为种子数据期的过渡兼容

## 4. 设置

该历史全局词典设置已取消；来源目录由学习卡详情的 `sourceMaterials` 返回。

## 5. 验收

- 切换词典后卡片释义变化
- WordNet 下默写可用、中文选词降级
- 关于/OpenAPI 含许可署名

## 6. 已知问题（已修复 ✅）

**P-MULTI-BUG-01（2026-07-11）WordNet 英英仍异常**

| 项 | 说明 |
|----|------|
| 根因 | ① V23 仅 10 词种子；② `resolveWordSummaries` 对 `locale=en` 不回退 curated/legacy；③ 客户端 DTO `cn` 为 `String` 非空，Gson 反序列化 null 时传参崩溃；④ Quiz 过滤后 pool 为空 → EMPTY_POOL |
| 修复 | **服务端**：`WordLookupService` 去掉 locale 限制，任何 locale 缺词均回退 curated；`resolveFromDict` 输出 null-safe（`cn` 永不为 null）；`QuizService` 过滤增加 WordNet 最小 fallback（en 非空即允许默写）。**Android**：`WordCard.cn` / `WordSummary.cn` / `FlipCard.cn` 改为 `String?`；`QuizPrompt.cn` / `QuizWrongWord.cn` 可空；`preferChinesePrompt` 接收 `String?`。 |
| 工具 | 新增 `tools/import-wordnet/`：NLTK WordNet 查全部 `book_words` wordKey，生成 `dict_words` / `dict_senses` 插入 SQL；运行后 V23 覆盖问题即解。 |
| 验证 | 切 WordNet → 学习/详情/测验全路径可用；回退 curated 中文释义作为浏览兜底；测验仅出 dictation（已降级）。 |
