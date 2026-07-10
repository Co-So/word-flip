"""从 Docker MySQL 导出 book_words（按 word_key 去重，保留最短 cn 优先有汉字）。"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path


def export_book_words(
    out: Path,
    *,
    container: str = "wordflip-mysql",
    database: str = "wordflip",
    user: str = "root",
    password: str = "root",
) -> int:
    """导出去重词条为 JSONL。依赖本机 docker。"""
    sql = (
        "SELECT bw.word_key, bw.en, bw.cn, bw.pos, bw.ph, bw.book_id "
        "FROM book_words bw "
        "INNER JOIN ("
        "  SELECT word_key, MIN(id) AS id FROM book_words GROUP BY word_key"
        ") t ON bw.id = t.id"
    )
    cmd = [
        "docker",
        "exec",
        "-e",
        f"MYSQL_PWD={password}",
        container,
        "mysql",
        "-u",
        user,
        database,
        "--batch",
        "--raw",
        "--default-character-set=utf8mb4",
        "-e",
        sql,
    ]
    proc = subprocess.run(cmd, capture_output=True, check=False)
    if proc.returncode != 0:
        err = proc.stderr.decode("utf-8", errors="replace")
        raise RuntimeError(f"mysql export failed: {err}")
    # mysql --batch 输出 TSV；stdout 可能是 utf-8
    text = proc.stdout.decode("utf-8")
    lines = text.splitlines()
    if not lines:
        raise RuntimeError("empty mysql output")
    header = lines[0].split("\t")
    out.parent.mkdir(parents=True, exist_ok=True)
    n = 0
    with out.open("w", encoding="utf-8", newline="\n") as f:
        for line in lines[1:]:
            cols = line.split("\t")
            if len(cols) < len(header):
                continue
            row = dict(zip(header, cols))
            # mysql NULL → \N
            for k, v in list(row.items()):
                if v == "\\N":
                    row[k] = None
            if row.get("book_id") is not None:
                try:
                    row["book_id"] = int(row["book_id"])
                except ValueError:
                    row["book_id"] = None
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
            n += 1
    return n
