# 内置词书 ETL

从 [KyleBing/english-vocabulary](https://github.com/KyleBing/english-vocabulary) 下载 JSON，清洗后生成 Flyway 迁移 SQL。

## 前置

- Python 3.10+
- 网络（首次下载 raw JSON）

## 一键生成

```powershell
cd wordflip-server/scripts/vocabulary
python build_builtin_books.py --all
```

步骤：

1. 下载源 JSON → `raw/`（gitignore）
2. 清洗、去重、映射 `book_words` 字段
3. 输出 `processed/stats.json` 与 `../../src/main/resources/db/migration/V3*.sql`

## 应用迁移

```powershell
cd wordflip-server
.\mvnw.cmd flyway:migrate
# 或重启 start-dev.ps1
```

## 已有用户数据

替换 `book_words` 不会自动更新 `user_word_lexicon` / `group_words`。开发验证时需重新勾选词书并 **保存设置** 以增量入组新词。

详见 [SOURCES.md](./SOURCES.md)。

## 词频序（GroupStrategy.frequency）

```powershell
python build_word_freq_ranks.py
```

生成 `V8_1__seed_word_freq_ranks.sql`（约 25000 条，来源 wordfreq/COCA）。详见 SOURCES.md §词频。
