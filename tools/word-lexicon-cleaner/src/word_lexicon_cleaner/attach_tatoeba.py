"""为缺例句的 ok primary 挂 Tatoeba EN-ZH（生成 SQL）。

需本机 Tatoeba 句对文件（tab：eng\\tcmn）或 --pairs。
"""

from __future__ import annotations

import argparse
from pathlib import Path


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="attach-tatoeba")
    p.add_argument("--pairs", default="data/tatoeba_eng_cmn.tsv")
    p.add_argument("-o", "--output", default="out/tatoeba_examples.sql")
    p.add_argument("--dict-id", default="wordflip_curated")
    p.add_argument("--limit", type=int, default=500)
    args = p.parse_args(argv)
    pairs_path = Path(args.pairs)
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    if not pairs_path.exists():
        out.write_text(
            "-- 未找到 Tatoeba 句对。下载后转为 TSV(eng\\tcmn) 再运行。\n"
            "-- https://tatoeba.org/en/downloads （CC BY）\n",
            encoding="utf-8",
        )
        print("pairs missing; wrote stub")
        return 0
    # 简化：按英文句中出现的单词粗匹配，仅作脚手架
    lines = [
        f"-- Tatoeba examples for {args.dict_id} (scaffold; prefer curated matching)",
        "SET NAMES utf8mb4;",
    ]
    count = 0
    for raw in pairs_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        if count >= args.limit:
            break
        parts = raw.split("\t")
        if len(parts) < 2:
            continue
        en, cn = parts[0].strip(), parts[1].strip()
        if not en or not cn:
            continue
        # 取句中第一个小写词作弱关联（生产应做更好的对齐）
        token = "".join(ch if ch.isalpha() else " " for ch in en.lower()).split()
        if not token:
            continue
        key = token[0]
        esc_en = en.replace("\\", "\\\\").replace("'", "''")[:512]
        esc_cn = cn.replace("\\", "\\\\").replace("'", "''")[:512]
        lines.append(
            f"INSERT INTO dict_examples (sense_id, en, cn, sort_order) "
            f"SELECT s.id, '{esc_en}', '{esc_cn}', 10 FROM dict_senses s "
            f"WHERE s.dict_id='{args.dict_id}' AND s.word_key='{key}' AND s.is_primary=1 "
            f"AND NOT EXISTS (SELECT 1 FROM dict_examples e WHERE e.sense_id=s.id AND e.en='{esc_en}') "
            f"LIMIT 1;"
        )
        count += 1
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {count} example inserts → {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
