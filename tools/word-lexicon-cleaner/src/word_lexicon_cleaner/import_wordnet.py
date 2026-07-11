"""导入 Princeton WordNet → dict_id=wordnet（生成 SQL 或写 MySQL）。

依赖：可选 nltk wordnet；无则写入种子说明，全量灌数需本机安装 WordNet。
"""

from __future__ import annotations

import argparse
from pathlib import Path


def _try_nltk_lemmas(limit: int | None = None) -> list[tuple[str, str, str]]:
    """返回 [(word_key, pos, en_gloss), ...]。"""
    try:
        from nltk.corpus import wordnet as wn  # type: ignore
    except Exception:
        return []

    rows: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    pos_map = {"n": "n.", "v": "v.", "a": "adj.", "s": "adj.", "r": "adv."}
    for syn in wn.all_synsets():
        gloss = (syn.definition() or "").strip()
        if not gloss:
            continue
        pos = pos_map.get(syn.pos(), "n.")
        for lemma in syn.lemmas():
            key = lemma.name().replace("_", " ").lower().strip()
            if not key or " " in key:
                continue
            if key in seen:
                continue
            seen.add(key)
            rows.append((key, pos, gloss[:512]))
            if limit and len(rows) >= limit:
                return rows
    return rows


def emit_sql(rows: list[tuple[str, str, str]], out: Path) -> None:
    lines = [
        "-- WordNet → dict_id=wordnet（工具生成）",
        "SET NAMES utf8mb4;",
    ]
    for key, pos, gloss in rows:
        esc_en = key.replace("'", "''")
        esc_gloss = gloss.replace("\\", "\\\\").replace("'", "''")
        lines.append(
            f"INSERT INTO dict_words (dict_id, word_key, en, created_at, updated_at) "
            f"VALUES ('wordnet', '{esc_en}', '{esc_en}', NOW(3), NOW(3)) "
            f"ON DUPLICATE KEY UPDATE updated_at = NOW(3);"
        )
        lines.append(
            f"INSERT INTO dict_senses (dict_id, word_key, pos, cn, en_gloss, is_primary, sort_order, quality, created_at) "
            f"SELECT 'wordnet', '{esc_en}', '{pos}', NULL, '{esc_gloss}', 1, 0, 'ok', NOW(3) "
            f"FROM DUAL WHERE NOT EXISTS ("
            f"SELECT 1 FROM dict_senses WHERE dict_id='wordnet' AND word_key='{esc_en}' AND is_primary=1);"
        )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="import-wordnet")
    p.add_argument("-o", "--output", default="out/wordnet_seed.sql")
    p.add_argument("--limit", type=int, default=500, help="最多词头数（0=不限，需 nltk）")
    args = p.parse_args(argv)
    limit = None if args.limit == 0 else args.limit
    rows = _try_nltk_lemmas(limit)
    if not rows:
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        Path(args.output).write_text(
            "-- 未检测到 nltk WordNet。请: pip install nltk && python -c \"import nltk; nltk.download('wordnet')\"\n"
            "-- 或使用 Flyway V23 最小种子。\n",
            encoding="utf-8",
        )
        print("nltk WordNet unavailable; wrote stub note")
        return 0
    emit_sql(rows, Path(args.output))
    print(f"wrote {len(rows)} lemmas → {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
