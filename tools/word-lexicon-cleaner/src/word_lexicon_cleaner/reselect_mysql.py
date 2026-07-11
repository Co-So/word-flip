"""从 MySQL dict_senses 重选 learning-primary，并可选写出 Flyway SQL。"""

from __future__ import annotations

import os
from collections import defaultdict
from pathlib import Path
from typing import Any

from .learning_primary import apply_reselect, load_overrides


def _connect():
    import pymysql

    return pymysql.connect(
        host=os.environ.get("MYSQL_HOST", "127.0.0.1"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        user=os.environ.get("MYSQL_USER", "root"),
        password=os.environ.get("MYSQL_PASSWORD", "root"),
        database=os.environ.get("MYSQL_DATABASE", "wordflip"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def fetch_senses_by_key(conn) -> dict[str, list[dict[str, Any]]]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, word_key, pos, cn, is_primary, sort_order, quality "
            "FROM dict_senses ORDER BY word_key, sort_order, id"
        )
        rows = cur.fetchall()
    by: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for r in rows:
        by[r["word_key"]].append(r)
    return by


def reselect_in_mysql(
    *,
    dry_run: bool = False,
    shorten_primary: bool = True,
    sql_out: Path | None = None,
) -> dict[str, int]:
    """重选 primary；返回统计。"""
    overrides = load_overrides()
    conn = _connect()
    try:
        by = fetch_senses_by_key(conn)
        picks = apply_reselect(by, overrides, shorten_primary=shorten_primary)
        lines: list[str] = [
            "-- 自动生成：learning-primary 重选（见 tools/word-lexicon-cleaner learning_primary）",
            "SET NAMES utf8mb4;",
            "UPDATE dict_senses SET is_primary = 0;",
        ]
        changed = 0
        shortened = 0
        with conn.cursor() as cur:
            if not dry_run:
                cur.execute("UPDATE dict_senses SET is_primary = 0")
            for word_key, pid, new_cn in picks:
                lines.append(f"UPDATE dict_senses SET is_primary = 1 WHERE id = {pid};")
                if new_cn:
                    esc = new_cn.replace("\\", "\\\\").replace("'", "''")
                    lines.append(
                        f"UPDATE dict_senses SET cn = '{esc}' WHERE id = {pid};"
                    )
                    shortened += 1
                if not dry_run:
                    cur.execute(
                        "UPDATE dict_senses SET is_primary = 1 WHERE id = %s", (pid,)
                    )
                    if new_cn:
                        cur.execute(
                            "UPDATE dict_senses SET cn = %s WHERE id = %s",
                            (new_cn, pid),
                        )
                changed += 1
            if not dry_run:
                # 同步 lexicon 冗余 primary
                cur.execute(
                    """
                    UPDATE user_word_lexicon u
                    INNER JOIN dict_senses s
                      ON s.word_key = u.word_key AND s.is_primary = 1 AND s.quality = 'ok'
                    SET u.cn = s.cn, u.pos = s.pos
                    """
                )
                conn.commit()
        if sql_out:
            lines.append("")
            lines.append(
                "-- 同步用户词典冗余 primary（REQ-LEX / V17 同逻辑）"
            )
            lines.append(
                "UPDATE user_word_lexicon u "
                "INNER JOIN dict_senses s "
                "ON s.word_key = u.word_key AND s.is_primary = 1 AND s.quality = 'ok' "
                "SET u.cn = s.cn, u.pos = s.pos;"
            )
            sql_out.parent.mkdir(parents=True, exist_ok=True)
            sql_out.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return {
            "words": len(by),
            "primaries_set": changed,
            "cn_shortened": shortened,
            "overrides": len(overrides),
        }
    finally:
        conn.close()
