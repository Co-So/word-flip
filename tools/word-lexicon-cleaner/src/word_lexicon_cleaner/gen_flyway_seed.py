"""从 cleaned.jsonl 生成 Flyway 灌数 SQL（仅 quality=ok 且有 primary）。"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def sql_str(s: str | None) -> str:
    if s is None:
        return "NULL"
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def generate(cleaned: Path, out: Path, batch_size: int = 200) -> tuple[int, int]:
    words_rows: list[str] = []
    sense_rows: list[str] = []
    word_n = 0
    sense_n = 0

    with cleaned.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            d = json.loads(line)
            if d.get("quality") != "ok":
                continue
            senses = d.get("senses") or []
            primary_ok = any(
                s.get("is_primary") and s.get("quality") == "ok" for s in senses
            )
            if not primary_ok:
                continue
            wk = d["word_key"]
            en = d.get("en") or wk
            ph = d.get("ph")
            words_rows.append(f"({sql_str(wk)}, {sql_str(en)}, {sql_str(ph)}, NULL)")
            word_n += 1
            for i, s in enumerate(senses):
                if s.get("quality") == "reject":
                    continue
                cn = (s.get("cn") or "").strip()
                if not cn:
                    continue
                pos = s.get("pos")
                is_pri = 1 if s.get("is_primary") else 0
                q = s.get("quality") or "ok"
                if q not in ("ok", "uncertain", "reject"):
                    q = "ok"
                sort_order = int(s.get("sort_order", i))
                sense_rows.append(
                    f"({sql_str(wk)}, {sql_str(pos)}, {sql_str(cn)}, {is_pri}, {sort_order}, {sql_str(q)})"
                )
                sense_n += 1

    lines = [
        "-- ===========================================================================",
        "-- V14：从 word-lexicon-cleaner 产物灌入 dict_*（仅 ok + 合格 primary）",
        "-- 生成命令：python -m word_lexicon_cleaner.gen_flyway_seed",
        "-- 勿手改大段 VALUES；需重跑清洗后再重新生成本文件",
        "-- ===========================================================================",
        "SET NAMES utf8mb4;",
        "",
    ]

    for i in range(0, len(words_rows), batch_size):
        chunk = words_rows[i : i + batch_size]
        lines.append(
            "INSERT INTO dict_words (word_key, en, ph, ph_us) VALUES\n"
            + ",\n".join(chunk)
            + "\nON DUPLICATE KEY UPDATE en=VALUES(en), ph=VALUES(ph);"
        )
        lines.append("")

    # senses：先清再插，避免重复跑失败（Flyway 只跑一次；本地重跑需 repair）
    lines.append("-- 义项：按 word_key 批量插入（本迁移首次执行）")
    for i in range(0, len(sense_rows), batch_size):
        chunk = sense_rows[i : i + batch_size]
        lines.append(
            "INSERT INTO dict_senses (word_key, pos, cn, is_primary, sort_order, quality) VALUES\n"
            + ",\n".join(chunk)
            + ";"
        )
        lines.append("")

    lines.append(f"-- seed_words={word_n} seed_senses={sense_n}")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return word_n, sense_n


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument(
        "-i",
        "--input",
        default="out/cleaned.jsonl",
        help="cleaned.jsonl path",
    )
    p.add_argument(
        "-o",
        "--output",
        required=True,
        help="Flyway SQL output path",
    )
    args = p.parse_args()
    root = Path(__file__).resolve().parents[2]
    inp = Path(args.input)
    if not inp.is_absolute():
        inp = root / inp
    out = Path(args.output)
    if not out.is_absolute():
        out = Path.cwd() / out
    wn, sn = generate(inp, out)
    print(f"wrote {out} words={wn} senses={sn}")


if __name__ == "__main__":
    main()
