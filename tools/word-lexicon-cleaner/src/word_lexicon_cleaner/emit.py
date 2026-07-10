"""生成 dict_* upsert SQL 草稿（Phase C 审阅用，勿直接当生产迁移）。"""

from __future__ import annotations

from pathlib import Path

from .models import CleanedWord


def _sql_str(s: str | None) -> str:
    if s is None:
        return "NULL"
    esc = s.replace("\\", "\\\\").replace("'", "''")
    return f"'{esc}'"


def emit_sql(words: list[CleanedWord], path: Path, only_ok: bool = True) -> int:
    """写出草稿 SQL。默认只 emit quality=ok 且有 primary 的词。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "-- WordFlip dict_* seed DRAFT — 由 word-lexicon-cleaner emit 生成",
        "-- 须人工审阅后再纳入 Flyway；禁止未审直接上生产",
        "SET NAMES utf8mb4;",
        "",
    ]
    n = 0
    for w in words:
        if only_ok and w.quality != "ok":
            continue
        primary = w.primary()
        if primary is None or primary.quality != "ok":
            continue
        n += 1
        lines.append(f"-- {w.word_key}")
        lines.append(
            "INSERT INTO dict_words (word_key, en, ph, ph_us) VALUES ("
            f"{_sql_str(w.word_key)}, {_sql_str(w.en)}, {_sql_str(w.ph)}, NULL)"
            " ON DUPLICATE KEY UPDATE en=VALUES(en), ph=VALUES(ph);"
        )
        for s in w.senses:
            if s.quality == "reject":
                continue
            lines.append(
                "INSERT INTO dict_senses (word_key, pos, cn, is_primary, sort_order, quality) VALUES ("
                f"{_sql_str(w.word_key)}, {_sql_str(s.pos)}, {_sql_str(s.cn)}, "
                f"{1 if s.is_primary else 0}, {s.sort_order}, {_sql_str(s.quality)}"
                ");"
            )
        lines.append("")
    lines.append(f"-- emitted_words={n}")
    path.write_text("\n".join(lines), encoding="utf-8")
    return n
