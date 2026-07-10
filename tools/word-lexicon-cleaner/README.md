# word-lexicon-cleaner

离线词库建设工具：**ECDICT 覆盖（推荐）** + 规则/LLM 兜底（用户导入）。**不**进入 Android / Server 运行时热路径。

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
| `overlay-ecdict` | **推荐**：用 ECDICT SQLite 覆盖词书词头 → cleaned + 可选 V16 SQL |
| `gen_flyway_seed` | 从 cleaned.jsonl 生成 INSERT 段 |

### ECDICT 覆盖（释义真相）

1. 下载 [ecdict-sqlite-28.zip](https://github.com/skywind3000/ECDICT/releases/download/1.0.28/ecdict-sqlite-28.zip) 解压到 `data/ecdict-sqlite/stardict.db`（目录已 gitignore）
2. 运行：

```bash
python -m word_lexicon_cleaner overlay-ecdict --keys-from mysql -o out/cleaned_ecdict.jsonl \
  --sql ../../wordflip-server/src/main/resources/db/migration/V16__rebuild_dict_from_ecdict.sql
```

3. Flyway migrate（V16 清空并重灌 dict_*；V17 同步 lexicon）

规则清洗（`rules`）仅作**用户导入**兜底，不再作为内置词书释义真相。

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
