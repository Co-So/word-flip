# 内置词书数据来源

WordFlip 三本内置词书（雅思 / 四级 / 考研）词条来自开源项目：

| 项目 | URL | 说明 |
|------|-----|------|
| KyleBing/english-vocabulary | https://github.com/KyleBing/english-vocabulary | 中英文词汇 JSON，含音标与例句（json-sentence 变体） |

## 文件映射

| book_id | 词书名称 | 源文件 |
|---------|----------|--------|
| 1 | 雅思核心词汇 3000 | `json_original/json-sentence/IELTS_2.json`, `IELTS_3.json` |
| 2 | 四级高频词汇 | `json/3-CET4-顺序.json` + json-sentence 补全音标/例句 |
| 3 | 考研英语核心词 | `json/5-考研-顺序.json` + json-sentence 补全音标/例句 |

## 许可说明

KyleBing/english-vocabulary 仓库未声明 SPDX license，README 标明为学习共享资源。WordFlip 仅将其作为 **内置 seed 数据** 使用，并在本文件保留 attribution。

## 重新生成

```powershell
cd wordflip-server/scripts/vocabulary
python build_builtin_books.py --all
```

生成物写入 `src/main/resources/db/migration/V3*.sql`；原始 JSON 下载至 `raw/`（已 gitignore）。

## 词频序（wordfreq / COCA）

| 项目 | URL | 许可 |
|------|-----|------|
| LuminosoInsight/wordfreq（经 aparrish/wordfreq-en-25000 导出） | https://github.com/aparrish/wordfreq-en-25000 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |

- 用于 `word_freq_ranks` 表，供 `GroupStrategy.frequency` 全局排序。
- 生成：`python build_word_freq_ranks.py` → `V8_1__seed_word_freq_ranks.sql`。
- Attribution：wordfreq 聚合 COCA、OpenSubtitles 等多语料；详见 [wordfreq 文档](https://github.com/LuminosoInsight/wordfreq#license)。
