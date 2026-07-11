"""从 Kaikki/Wiktionary JSONL 抽取英→中，写入 wiktionary_zh（按 keys 过滤）。

首轮：仅处理 --keys-file 或 MySQL book_words 出现的 word_key。
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def load_keys(path: Path | None, from_mysql: bool) -> set[str]:
    keys: set[str] = set()
    if path and path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            k = line.strip().lower()
            if k:
                keys.add(k)
    if from_mysql:
        try:
            import os
            import pymysql

            conn = pymysql.connect(
                host=os.environ.get("MYSQL_HOST", "127.0.0.1"),
                port=int(os.environ.get("MYSQL_PORT", "3306")),
                user=os.environ.get("MYSQL_USER", "root"),
                password=os.environ.get("MYSQL_PASSWORD", "root"),
                database=os.environ.get("MYSQL_DATABASE", "wordflip"),
                charset="utf8mb4",
            )
            with conn.cursor() as cur:
                cur.execute("SELECT DISTINCT word_key FROM book_words")
                for (k,) in cur.fetchall():
                    keys.add(k)
            conn.close()
        except Exception as e:
            print(f"mysql keys skipped: {e}")
    return keys


def extract_from_kaikki(jsonl: Path, keys: set[str], limit: int) -> list[dict]:
    """极简抽取：word + senses with zh translations if present。"""
    out: list[dict] = []
    if not jsonl.exists():
        return out
    with jsonl.open(encoding="utf-8") as f:
        for line in f:
            if limit and len(out) >= limit:
                break
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            word = (obj.get("word") or "").strip().lower()
            if not word or (keys and word not in keys):
                continue
            if " " in word:
                continue
            senses = []
            for s in obj.get("senses") or []:
                glosses = s.get("glosses") or []
                # 英维基 gloss 多为英文；中文多在 translations
                cn = None
                for tr in s.get("translations") or []:
                    if (tr.get("lang") or tr.get("code")) in ("zh", "cmn", "yue", "Chinese"):
                        cn = (tr.get("word") or tr.get("sense") or "").strip()
                        if cn:
                            break
                if not cn and glosses:
                    # 无中文则跳过该义（本词典要求英汉）
                    continue
                if not cn:
                    continue
                pos = (obj.get("pos") or s.get("pos") or "")[:32]
                senses.append({"pos": pos, "cn": cn[:512]})
            if not senses:
                continue
            out.append({"word_key": word, "en": obj.get("word") or word, "senses": senses[:5]})
    return out


def emit_sql(words: list[dict], out: Path) -> None:
    lines = ["-- Wiktionary → wiktionary_zh", "SET NAMES utf8mb4;"]
    for w in words:
        key = w["word_key"].replace("'", "''")
        en = (w.get("en") or key).replace("'", "''")
        lines.append(
            f"INSERT INTO dict_words (dict_id, word_key, en, created_at, updated_at) "
            f"VALUES ('wiktionary_zh', '{key}', '{en}', NOW(3), NOW(3)) "
            f"ON DUPLICATE KEY UPDATE updated_at=NOW(3);"
        )
        for i, s in enumerate(w["senses"]):
            cn = (s.get("cn") or "").replace("\\", "\\\\").replace("'", "''")
            pos = (s.get("pos") or "").replace("'", "''")
            primary = 1 if i == 0 else 0
            lines.append(
                f"INSERT INTO dict_senses (dict_id, word_key, pos, cn, en_gloss, is_primary, sort_order, quality, created_at) "
                f"VALUES ('wiktionary_zh', '{key}', '{pos}', '{cn}', NULL, {primary}, {i}, 'ok', NOW(3));"
            )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="import-wiktionary")
    p.add_argument("--kaikki", default="data/kaikki-en.jsonl", help="Kaikki English JSONL")
    p.add_argument("--keys-file", default=None)
    p.add_argument("--keys-from-mysql", action="store_true")
    p.add_argument("-o", "--output", default="out/wiktionary_zh.sql")
    p.add_argument("--limit", type=int, default=2000)
    args = p.parse_args(argv)
    keys = load_keys(Path(args.keys_file) if args.keys_file else None, args.keys_from_mysql)
    words = extract_from_kaikki(Path(args.kaikki), keys, args.limit)
    if not words:
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        Path(args.output).write_text(
            "-- 无抽取结果。请下载 Kaikki English dump 到 --kaikki，并用 --keys-from-mysql。\n"
            "-- https://kaikki.org/dictionary/rawdata.html\n",
            encoding="utf-8",
        )
        print("no words extracted; wrote stub")
        return 0
    emit_sql(words, Path(args.output))
    print(f"wrote {len(words)} words → {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
