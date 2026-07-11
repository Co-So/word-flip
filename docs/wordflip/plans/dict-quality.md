# 词库质量方案（Dict Quality）

> 版本：v1.0  
> 日期：2026-07-11  
> 状态：**已落地（Q0–Q5）**  
> 关联：[lexicon-restructure.md](./lexicon-restructure.md) · [requirements.md](../requirements.md) · TASK §P-DICT-QUALITY

## 1. 目标

对标商业背单词 App 的**词库内容质量**（非存储引擎）：

- 考义（learning primary）适合测验与卡片背面
- 导入 = membership（wordKey 列表），释义从全局 `dict_*` 补全
- 词书可绑定 `exam_sense_id`；缺省回退全局 primary
- 不合格义项不出题

## 2. 质量标准（REQ-LEX 补充）

| 项 | 标准 |
|----|------|
| primary.cn | 含汉字；建议 ≤40 字；无 `(n.)` 等词性尾巴 |
| primary.pos | 词性只在 pos 字段 |
| quality | 仅 `ok` 的 primary 可入测验池 |
| 虚词 | 以 `function_word_primary.json` 覆盖表为准 |
| 例句 | 每个 ok primary 尽量 ≥1 条 `dict_examples` |
| 导入 | 可仅英文；展示/测验用 dict（或 exam_sense） |

## 3. Learning primary 策略

1. 若 word_key 在虚词覆盖表 → 按表指定 pos/cn 模式选义  
2. 否则在 `quality=ok` 中按词性优先级：`adv` > `adj` > `v/n` > 其它 > `prep` > `conj`  
3. 同优先级取 `sort_order`、`id` 较小者  
4. 每词恰好一条 `is_primary=1`

## 4. 导入补全

```
用户文件（可仅 en）→ book_words.word_key
                    → exam_sense_id = 当前全局 primary
学习/测验 → WordLookup：优先 exam_sense（若有书上下文）否则 primary
```

## 5. 验收

- 虚词黄金样例 CI（only/but/just/as/go…）
- `QuizLexiconAcceptanceTest` + cleaner golden
- 抽检：题干「但是」不得指向 only
