#!/usr/bin/env python3
"""
wordfreq/COCA 词频 ETL：下载 wordfreq-en-25000 → 生成 Flyway V8_1 seed SQL。
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
RAW_DIR = SCRIPT_DIR / "raw"
MIGRATION_DIR = SCRIPT_DIR.parent.parent / "src" / "main" / "resources" / "db" / "migration"

WORDFREQ_URL = (
    "https://raw.githubusercontent.com/aparrish/wordfreq-en-25000/master/"
    "wordfreq-en-25000-log.json"
)
BATCH_SIZE = 500
# 与 book_words / word_key 规则一致，仅保留 ASCII 英文词
WORD_KEY_RE = re.compile(r"^[a-z0-9][a-z0-9\-']*$")


def download_wordfreq(force: bool) -> Path:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    dest = RAW_DIR / "wordfreq-en-25000-log.json"
    if dest.exists() and not force:
        return dest
    print(f"  下载 {WORDFREQ_URL}")
    req = urllib.request.Request(WORDFREQ_URL, headers={"User-Agent": "WordFlip-ETL/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            dest.write_bytes(resp.read())
    except urllib.error.URLError as e:
        raise RuntimeError(f"下载失败: {e}") from e
    return dest


def normalize_word_key(word: str) -> str | None:
    key = word.strip().lower()
    if not key or len(key) > 128 or not key.isascii():
        return None
    if not WORD_KEY_RE.match(key):
        return None
    return key


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def build_rows(path: Path) -> list[tuple[str, int]]:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError("wordfreq JSON 须为数组")

    rows: list[tuple[str, int]] = []
    seen: set[str] = set()
    for index, item in enumerate(data):
        if not isinstance(item, list) or len(item) < 1:
            continue
        word = item[0]
        if not isinstance(word, str):
            continue
        key = normalize_word_key(word)
        if key is None or key in seen:
            continue
        seen.add(key)
        rows.append((key, len(rows) + 1))
    return rows


def write_migration(rows: list[tuple[str, int]]) -> Path:
    MIGRATION_DIR.mkdir(parents=True, exist_ok=True)
    out = MIGRATION_DIR / "V8_1__seed_word_freq_ranks.sql"
    lines = [
        "-- wordfreq-en-25000（CC BY-SA 4.0，含 COCA 等语料）",
        f"-- 共 {len(rows)} 条",
        "",
    ]
    for i in range(0, len(rows), BATCH_SIZE):
        chunk = rows[i : i + BATCH_SIZE]
        lines.append("INSERT INTO word_freq_ranks (word_key, freq_rank, source) VALUES")
        values = [
            f"  ('{sql_escape(key)}', {rank}, 'wordfreq')"
            for key, rank in chunk
        ]
        lines.append(",\n".join(values) + ";")
        lines.append("")
    out.write_text("\n".join(lines), encoding="utf-8")
    return out


def coverage_stats(rows: list[tuple[str, int]]) -> None:
    """统计内置 book_words 覆盖率（若 processed 摘要存在）。"""
    processed = SCRIPT_DIR / "processed"
    book_keys: set[str] = set()
    for path in processed.glob("book_*.json"):
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
            items = data if isinstance(data, list) else data.get("words", [])
            for item in items:
                if not isinstance(item, dict):
                    continue
                key = item.get("word_key")
                if key:
                    book_keys.add(key)
        except (json.JSONDecodeError, OSError):
            continue
    if not book_keys:
        print("  （无 processed/book_*.json，跳过覆盖率统计）")
        return
    rank_map = {k: r for k, r in rows}
    covered = sum(1 for k in book_keys if k in rank_map)
    print(f"  内置词覆盖率: {covered}/{len(book_keys)} ({100 * covered / len(book_keys):.1f}%)")


def main() -> None:
    parser = argparse.ArgumentParser(description="生成 word_freq_ranks Flyway seed")
    parser.add_argument("--force", action="store_true", help="强制重新下载")
    args = parser.parse_args()

    path = download_wordfreq(force=args.force)
    rows = build_rows(path)
    if not rows:
        print("无有效词频行", file=sys.stderr)
        sys.exit(1)

    out = write_migration(rows)
    print(f"  生成 {out.name}，{len(rows)} 条")
    coverage_stats(rows)


if __name__ == "__main__":
    main()
