# WordNet 灌数工具

> 用途：将 Princeton WordNet 英英释义灌入 `dict_words` / `dict_senses`（`dict_id='wordnet'`），按用户已勾选词书的 wordKey 覆盖。
> 
> 运行前须确保：MySQL 可连、WordNet 数据可用（NLTK 或 SQLite）。

## 环境

```bash
pip install nltk pymysql
# 首次下载 WordNet 数据
python -c "import nltk; nltk.download('wordnet')"
```

## 用法

```bash
python import_wordnet.py
```

脚本行为：
1. 连接 MySQL，读取 `user_book_selection` → `book_words` 的 **全部 distinct wordKey**；
2. 用 NLTK WordNet 查询每个 lemma 的 synsets（优先最常见名词/动词 sense）；
3. 生成 `INSERT` 并打印 SQL；可重定向到 `.sql` 文件后由 Flyway 或手动执行。

## 配置

修改脚本内 `DB_CONFIG`：

```python
DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "your_password",
    "database": "wordflip",
    "charset": "utf8mb4",
}
```

## 产出

- 直接输出 SQL 到 stdout（推荐重定向到 `V{n}__seed_wordnet_full.sql`）
- 也可设置 `EXECUTE_DIRECTLY = True` 直接写入数据库

## 说明

- 若 NLTK 找不到某词，该词**跳过**（不插入空释义）。
- `en_gloss` 取最长 500 字符的 synset definition；`pos` 映射为 `n.`/`v.`/`adj.`/`adv.`。
- 本工具为**离线批处理**，不加入服务端运行时热路径。
