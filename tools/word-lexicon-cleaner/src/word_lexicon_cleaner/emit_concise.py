"""从 curated 派生 concise，或写出 SQL；也可连接 MySQL 直接写入。"""

from __future__ import annotations

import os
from pathlib import Path


def emit_concise_sql(out: Path) -> int:
    """写出与 V22 同逻辑的说明；实际迁移已在 Flyway V22。"""
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        "-- 简明学习版由 Flyway V22__seed_concise_from_curated.sql 生成。\n"
        "-- 本命令保留为文档入口；全量重建请重跑 V22 或调用 MySQL 拷贝逻辑。\n",
        encoding="utf-8",
    )
    return 0


def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(prog="emit-concise")
    p.add_argument("-o", "--output", default="out/concise_note.sql")
    args = p.parse_args(argv)
    emit_concise_sql(Path(args.output))
    print(f"wrote {args.output}")
    return 0
