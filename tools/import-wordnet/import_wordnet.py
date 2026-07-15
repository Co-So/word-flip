#!/usr/bin/env python3
"""
WordNet 灌数工具：按词书 wordKey 查 NLTK WordNet，生成 dict_words / dict_senses 插入 SQL。

用法：
    python import_wordnet.py > seed_wordnet_full.sql
    # 或直接写入 DB：
    EXECUTE_DIRECTLY = True  # 脚本内修改
"""

import re
from collections import defaultdict

# ---------------------------------------------------------------------------
# 配置（请按需修改）
# ---------------------------------------------------------------------------
DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",       # docker/.env 默认值
    "database": "wordflip",
    "charset": "utf8mb4",
}

EXECUTE_DIRECTLY = False   # True = 直接写库；False = 只打印 SQL
DICT_ID = "wordnet"
BATCH_SIZE = 500

# ---------------------------------------------------------------------------
# WordNet 查询
# ---------------------------------------------------------------------------

def normalize_word_key(key: str) -> str:
    """wordKey = en.trim().toLowerCase()"""
    return key.strip().lower()


def pos_to_label(pos: str) -> str:
    """NLTK pos → 展示词性"""
    return {
        "n": "n.",
        "v": "v.",
        "a": "adj.",
        "s": "adj.",
        "r": "adv.",
    }.get(pos, "")


def query_wordnet(word_key: str):
    """
    查询 NLTK WordNet；返回 [(pos_label, gloss)] 列表。
    优先取最常见词性（名词 > 动词 > 形容词 > 副词），每个 synset 一条。
    """
    try:
        from nltk.corpus import wordnet as wn
    except ImportError:
        raise RuntimeError("请先安装 NLTK: pip install nltk")

    lemma = word_key.replace(" ", "_")  # WordNet 用下划线连接多词
    synsets = wn.synsets(lemma)
    if not synsets:
        # 尝试去掉连字符
        if "-" in lemma:
            synsets = wn.synsets(lemma.replace("-", "_"))
        if not synsets:
            return []

    # 按词性分组，每组取最佳定义
    by_pos = defaultdict(list)
    for s in synsets:
        pos_label = pos_to_label(s.pos())
        if not pos_label:
            continue
        gloss = s.definition()
        if not gloss:
            continue
        # 截断到 500 字符
        if len(gloss) > 500:
            gloss = gloss[:497] + "..."
        by_pos[pos_label].append(gloss)

    # 每个词性取第一条（最常见 sense）
    results = []
    for pos_label in ["n.", "v.", "adj.", "adv."]:
        if pos_label in by_pos:
            results.append((pos_label, by_pos[pos_label][0]))
    return results


# ---------------------------------------------------------------------------
# SQL 生成
# ---------------------------------------------------------------------------

def escape_sql(s: str) -> str:
    if s is None:
        return "NULL"
    return "'" + s.replace("'", "''") + "'"


def build_inserts(word_key: str, senses: list) -> list:
    """为单个 wordKey 生成 INSERT dict_words + dict_senses SQL"""
    sqls = []
    sqls.append(
        f"INSERT INTO dict_words (dict_id, word_key, en, ph, ph_us, created_at, updated_at) "
        f"VALUES ({escape_sql(DICT_ID)}, {escape_sql(word_key)}, {escape_sql(word_key)}, NULL, NULL, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)) "
        f"ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3);"
    )
    for idx, (pos, gloss) in enumerate(senses):
        is_primary = 1 if idx == 0 else 0
        sqls.append(
            f"INSERT INTO dict_senses (dict_id, word_key, pos, cn, en_gloss, is_primary, sort_order, quality, created_at) "
            f"VALUES ({escape_sql(DICT_ID)}, {escape_sql(word_key)}, {escape_sql(pos)}, NULL, {escape_sql(gloss)}, {is_primary}, {idx}, 'ok', CURRENT_TIMESTAMP(3)) "
            f"ON DUPLICATE KEY UPDATE en_gloss = {escape_sql(gloss)}, updated_at = CURRENT_TIMESTAMP(3);"
        )
    return sqls


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def fetch_word_keys() -> set:
    """从 MySQL 读取全部已入 book_words 的 distinct wordKey"""
    import pymysql
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT DISTINCT word_key FROM book_words")
            return {row[0] for row in cur.fetchall()}
    finally:
        conn.close()


def main():
    # 确保 NLTK wordnet 已下载
    try:
        from nltk.corpus import wordnet as wn
        wn.synsets("test")
    except Exception:
        print("-- 首次运行：正在下载 WordNet 数据...", flush=True)
        import nltk
        nltk.download("wordnet", quiet=True)

    word_keys = fetch_word_keys()
    print(f"-- 共 {len(word_keys)} 个 distinct wordKey 待查询", flush=True)

    all_sqls = []
    found = 0
    missing = 0

    for wk in sorted(word_keys):
        senses = query_wordnet(wk)
        if senses:
            all_sqls.extend(build_inserts(wk, senses))
            found += 1
        else:
            missing += 1

    print(f"-- 查询完成：命中 {found}，缺失 {missing}", flush=True)

    if EXECUTE_DIRECTLY:
        import pymysql
        conn = pymysql.connect(**DB_CONFIG)
        try:
            with conn.cursor() as cur:
                for sql in all_sqls:
                    cur.execute(sql)
            conn.commit()
            print(f"-- 已写入 {len(all_sqls)} 条 SQL")
        finally:
            conn.close()
    else:
        # 输出 SQL 文件头
        print(f"-- WordNet 灌数脚本（{found} 词，{len(all_sqls)} 条 SQL）")
        print(f"-- 生成时间: {__import__('datetime').datetime.now().isoformat()}")
        print("SET NAMES utf8mb4;")
        print()
        for sql in all_sqls:
            print(sql)


if __name__ == "__main__":
    main()
