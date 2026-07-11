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
- `activeDictId=wordnet` 时测验仅 `dictation`（中文选词题自动降级为默写）
- 缺词回退仅同 locale（en 不回退到 curated 中文）

## 4. 设置

`user_settings.active_dict_id` → `GET/PATCH` 的 `activeDictId`；`GET /dicts` 列目录。

## 5. 验收

- 切换词典后卡片释义变化
- WordNet 下默写可用、中文选词降级
- 关于/OpenAPI 含许可署名

## 6. 已知问题（挂起 → 明日）

**P-MULTI-BUG-01（2026-07-11）WordNet 英英仍异常**

- 用户侧：切 WordNet 后仍加载/展示出错
- 根因候选：① V23 仅 10 词种子、en 不回退；② 客户端/DTO `enGloss` 路径未完全闭环；③ 测验 EMPTY_POOL
- 明日动作：`import-wordnet` 灌词书覆盖 → 真机复现 → 修剩余 bug → 勾 TASK `P-MULTI-BUG-01`
