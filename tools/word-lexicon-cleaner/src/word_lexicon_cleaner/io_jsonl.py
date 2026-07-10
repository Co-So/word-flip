"""读写 JSONL。"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable, TypeVar

from .models import CleanedWord, RawWord

T = TypeVar("T")


def read_jsonl(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def write_jsonl(path: Path, rows: Iterable[dict]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    n = 0
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
            n += 1
    return n


def load_raw(path: Path) -> list[RawWord]:
    return [RawWord.from_dict(d) for d in read_jsonl(path)]


def load_cleaned(path: Path) -> list[CleanedWord]:
    return [CleanedWord.from_dict(d) for d in read_jsonl(path)]


def dump_cleaned(path: Path, words: Iterable[CleanedWord]) -> int:
    return write_jsonl(path, (w.to_dict() for w in words))
