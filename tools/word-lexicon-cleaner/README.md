# word-lexicon-cleaner

离线词库清洗工具（规则优先 + 可选 LLM）。**不**进入 Android / Server 运行时热路径。

计划：[docs/wordflip/plans/lexicon-restructure.md](../../docs/wordflip/plans/lexicon-restructure.md)

## 环境

```bash
cd tools/word-lexicon-cleaner
python -m venv .venv
# Windows: .venv\Scripts\activate
pip install -e ".[dev]"   # 需要 Python ≥3.10
```

LLM（可选）：复制 `.env.example` → `.env`，设置 `LEXICON_LLM_API_KEY` 等。无 Key 时 `llm` 子命令跳过。

## CLI

```text
raw JSONL → rules → (llm) → merge → emit / report
```

| 命令 | 说明 |
|------|------|
| `export-mysql` | 从本机 Docker MySQL 导出 `book_words`（去重 word_key） |
| `rules` | 规则引擎 → `ok` / `uncertain` / `reject` |
| `llm` | 仅处理 `uncertain`（无 Key 则跳过并原样写出） |
| `merge` | 合并 rules + llm 产物 |
| `report` | 统计报告（Markdown + JSON） |
| `emit` | 生成 `dict_*` upsert SQL 草稿（供 Phase C 审阅） |
| `gen_flyway_seed` | 从 `cleaned.jsonl` 生成正式 `V14__seed_dict_from_cleaner.sql` |

示例：

```bash
python -m word_lexicon_cleaner export-mysql -o out/raw.jsonl
python -m word_lexicon_cleaner rules -i out/raw.jsonl -o out/rules.jsonl
python -m word_lexicon_cleaner llm -i out/rules.jsonl -o out/llm.jsonl
python -m word_lexicon_cleaner merge -r out/rules.jsonl -l out/llm.jsonl -o out/cleaned.jsonl
python -m word_lexicon_cleaner report -i out/cleaned.jsonl -o out/report.md
python -m word_lexicon_cleaner.gen_flyway_seed -i out/cleaned.jsonl -o ../../wordflip-server/src/main/resources/db/migration/V14__seed_dict_from_cleaner.sql
```

## 测试

```bash
pytest -q
```

黄金样例 ≥30 条，覆盖剥词性、拆义、reject、虚词碎片等。

## 产物

| 路径 | 是否入库 |
|------|----------|
| `out/report.md` / `out/report.json` | 可提交摘要 |
| `out/*.jsonl`、`out/*draft.sql` | **gitignore**（体积大） |
| `.env` | **禁止提交** |

## 质量分流

| quality | 含义 |
|---------|------|
| `ok` | 规则可确定；有唯一 primary；`cn` 含汉字且无词性尾巴 |
| `uncertain` | 需 LLM / 人工（多义歧义、残留拉丁、虚词脏数据等） |
| `reject` | 无汉字、空释义、英文充中文等；禁止入测验池 |
