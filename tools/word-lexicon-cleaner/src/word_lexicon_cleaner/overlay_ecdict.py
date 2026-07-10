"""用 ECDICT 覆盖词书词头 → cleaned.jsonl / Flyway 重建 SQL。"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import subprocess
from pathlib import Path

from .ecdict import from_ecdict_row, parse_translation_lines, pick_primary
from .gen_flyway_seed import generate as generate_seed_sql
from .io_jsonl import dump_cleaned, load_raw
from .models import CleanedWord, Sense
from .validate import has_han

_TECH_LINE = re.compile(r"^\[(计|医|化|生|法|经|军|宗|地|气|物|数|乐|体|航|建|工)\]")


def _filter_senses(senses: list[Sense]) -> list[Sense]:
    """去掉已无中文的空义；技术义保留为 uncertain（parse 阶段已处理）。"""
    kept = [s for s in senses if (s.cn or "").strip() and has_han(s.cn)]
    for i, s in enumerate(kept):
        s.sort_order = i
    return kept


def load_word_keys_from_mysql() -> list[str]:
    sql = "SELECT DISTINCT word_key FROM book_words ORDER BY word_key"
    cmd = [
        "docker",
        "exec",
        "-e",
        "MYSQL_PWD=root",
        "wordflip-mysql",
        "mysql",
        "-uroot",
        "wordflip",
        "-N",
        "--batch",
        "--default-character-set=utf8mb4",
        "-e",
        sql,
    ]
    proc = subprocess.run(cmd, capture_output=True, check=False)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.decode("utf-8", errors="replace"))
    return [ln.strip() for ln in proc.stdout.decode("utf-8").splitlines() if ln.strip()]


def overlay(
    db_path: Path,
    word_keys: list[str],
) -> tuple[list[CleanedWord], dict]:
    con = sqlite3.connect(str(db_path))
    cur = con.cursor()
    out: list[CleanedWord] = []
    stats = {
        "total": len(word_keys),
        "hit": 0,
        "miss": 0,
        "ok": 0,
        "uncertain": 0,
        "reject": 0,
    }
    for key in word_keys:
        row = cur.execute(
            "SELECT word, phonetic, translation, definition, pos "
            "FROM stardict WHERE word = ? COLLATE NOCASE LIMIT 1",
            (key,),
        ).fetchone()
        if not row:
            stats["miss"] += 1
            out.append(
                CleanedWord(
                    word_key=key,
                    en=key,
                    ph=None,
                    quality="reject",
                    reason="ecdict_miss",
                    senses=[],
                    source="ecdict",
                )
            )
            continue
        stats["hit"] += 1
        word, phonetic, translation, definition, _pos = row
        cleaned = from_ecdict_row(
            word=word or key,
            phonetic=phonetic,
            translation=translation,
            definition=definition,
        )
        cleaned.senses = _filter_senses(cleaned.senses)
        if cleaned.senses:
            if not any(s.quality == "ok" for s in cleaned.senses):
                for s in cleaned.senses:
                    if has_han(s.cn):
                        s.quality = "ok"
            pick_primary(cleaned.senses)
            # 冠词补 pos
            for s in cleaned.senses:
                if s.is_primary and not s.pos and s.cn in ("一个", "一", "那", "这个"):
                    s.pos = "art."
            if any(s.is_primary and s.quality == "ok" for s in cleaned.senses):
                cleaned.quality = "ok"
                cleaned.reason = "ecdict_ok"
            else:
                cleaned.quality = "uncertain"
                cleaned.reason = "ecdict_filtered_no_primary"
        else:
            cleaned.quality = "reject"
            cleaned.reason = "ecdict_only_tech_or_empty"
            cleaned.senses = []
        stats[cleaned.quality] = stats.get(cleaned.quality, 0) + 1
        out.append(cleaned)
    con.close()
    return out, stats


def write_rebuild_sql(cleaned_path: Path, out_sql: Path) -> tuple[int, int]:
    """生成 V16：清空 dict_* 后灌入 ECDICT 覆盖结果。"""
    # 先用 gen 生成 INSERT 段到临时，再包一层 DELETE
    tmp = out_sql.with_suffix(".inserts.sql")
    wn, sn = generate_seed_sql(cleaned_path, tmp, batch_size=200)
    inserts = tmp.read_text(encoding="utf-8")
    # 去掉原文件头注释中的 V14 字样，保留 INSERT
    body_lines = []
    for line in inserts.splitlines():
        if line.startswith("-- ===") or line.startswith("-- V14") or line.startswith("-- 生成"):
            continue
        if line.startswith("-- 勿手改") or line.startswith("-- seed_"):
            continue
        body_lines.append(line)
    header = [
        "-- ===========================================================================",
        "-- V16：以 ECDICT 为释义真相重建 dict_*（词书词头覆盖）",
        "-- 生成：python -m word_lexicon_cleaner overlay-ecdict",
        "-- ===========================================================================",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        "DELETE FROM dict_examples;",
        "DELETE FROM dict_senses;",
        "DELETE FROM dict_words;",
        "SET FOREIGN_KEY_CHECKS = 1;",
        "",
    ]
    out_sql.write_text("\n".join(header) + "\n".join(body_lines) + "\n", encoding="utf-8")
    tmp.unlink(missing_ok=True)
    return wn, sn


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="ECDICT overlay for WordFlip dict_*")
    p.add_argument(
        "--db",
        default="data/ecdict-sqlite/stardict.db",
        help="ECDICT sqlite path",
    )
    p.add_argument(
        "--keys-from",
        choices=["mysql", "raw-jsonl"],
        default="mysql",
    )
    p.add_argument("--raw", default="out/raw.jsonl")
    p.add_argument("-o", "--output", default="out/cleaned_ecdict.jsonl")
    p.add_argument(
        "--sql",
        default=None,
        help="optional Flyway SQL output path",
    )
    p.add_argument("--report", default="out/report_ecdict.md")
    args = p.parse_args(argv)

    root = Path(__file__).resolve().parents[2]
    db = Path(args.db)
    if not db.is_absolute():
        db = root / db
    if not db.is_file():
        raise SystemExit(f"ECDICT db not found: {db}")

    if args.keys_from == "mysql":
        keys = load_word_keys_from_mysql()
    else:
        raw_path = Path(args.raw)
        if not raw_path.is_absolute():
            raw_path = root / raw_path
        keys = sorted({r.word_key for r in load_raw(raw_path) if r.word_key})

    words, stats = overlay(db, keys)
    out = Path(args.output)
    if not out.is_absolute():
        out = root / out
    dump_cleaned(out, words)

    rep = Path(args.report)
    if not rep.is_absolute():
        rep = root / rep
    rep.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# ECDICT 覆盖报告",
        "",
        f"- 词头总数：{stats['total']}",
        f"- ECDICT 命中：{stats['hit']}",
        f"- 未命中：{stats['miss']}",
        f"- ok：{stats.get('ok', 0)}",
        f"- uncertain：{stats.get('uncertain', 0)}",
        f"- reject：{stats.get('reject', 0)}",
        "",
        "> 释义真相改为 ECDICT；规则清洗仅用于用户导入兜底。",
        "",
    ]
    rep.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False))

    if args.sql:
        sql_path = Path(args.sql)
        if not sql_path.is_absolute():
            sql_path = Path.cwd() / sql_path
        wn, sn = write_rebuild_sql(out, sql_path)
        print(f"sql words={wn} senses={sn} → {sql_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
