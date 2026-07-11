"""背单词 learning-primary：按虚词表 + 词性优先级重选 is_primary。"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

# 词性优先级：分数越小越优先（学习向）
_POS_RANK = [
    (re.compile(r"adv", re.I), 10),
    (re.compile(r"adj|^a\.|a&", re.I), 20),
    (re.compile(r"\bvi\b|^vi\.|vi\.", re.I), 30),
    (re.compile(r"\bvt\b|^vt\.|vt\.", re.I), 31),
    (re.compile(r"(^|\b)v\.|verb", re.I), 32),
    (re.compile(r"(^|\b)n\.|noun", re.I), 40),
    (re.compile(r"art|det", re.I), 50),
    (re.compile(r"aux|modal", re.I), 55),
    (re.compile(r"prep", re.I), 70),
    (re.compile(r"conj", re.I), 80),
]


def default_overrides_path() -> Path:
    return Path(__file__).resolve().parents[2] / "data" / "function_word_primary.json"


def load_overrides(path: Path | None = None) -> dict[str, dict[str, str]]:
    p = path or default_overrides_path()
    raw = json.loads(p.read_text(encoding="utf-8"))
    return {k: v for k, v in raw.items() if not k.startswith("_") and isinstance(v, dict)}


def pos_rank(pos: str | None) -> int:
    if not pos:
        return 60
    for pat, rank in _POS_RANK:
        if pat.search(pos):
            return rank
    return 60


def quality_rank(quality: str | None) -> int:
    q = (quality or "ok").lower()
    if q == "ok":
        return 0
    if q == "uncertain":
        return 1
    return 2


def pick_primary_id(
    senses: list[dict[str, Any]],
    override: dict[str, str] | None = None,
) -> int | None:
    """从义项列表选出应作为 primary 的 id；无合格项返回 None。"""
    okish = [s for s in senses if quality_rank(s.get("quality")) < 2]
    if not okish:
        okish = list(senses)
    if not okish:
        return None

    if override:
        pos_sub = (override.get("pos") or "").lower()
        cn_sub = override.get("cn_contains") or ""
        matched = [
            s
            for s in okish
            if (not pos_sub or pos_sub in (s.get("pos") or "").lower())
            and (not cn_sub or cn_sub in (s.get("cn") or ""))
        ]
        if matched:
            matched.sort(key=lambda s: (s.get("sort_order", 0), s.get("id", 0)))
            return int(matched[0]["id"])

    okish.sort(
        key=lambda s: (
            quality_rank(s.get("quality")),
            pos_rank(s.get("pos")),
            s.get("sort_order", 0),
            s.get("id", 0),
        )
    )
    return int(okish[0]["id"])


def shorten_cn(cn: str, max_len: int = 40) -> str:
    """精简释义：按逗号/分号截到长度上限，保留首段义核。"""
    text = (cn or "").strip()
    if not text:
        return text
    if len(text) <= max_len:
        return text
    for sep in ("；", ";", "，", ",", "、"):
        parts = [p.strip() for p in text.split(sep) if p.strip()]
        if len(parts) >= 2:
            acc = parts[0]
            for p in parts[1:]:
                cand = acc + sep + p
                if len(cand) > max_len:
                    break
                acc = cand
            return acc if acc else text[:max_len]
    return text[:max_len]


def apply_reselect(
    senses_by_key: dict[str, list[dict[str, Any]]],
    overrides: dict[str, dict[str, str]] | None = None,
    shorten_primary: bool = True,
) -> list[tuple[str, int, str | None]]:
    """
    返回 [(word_key, primary_id, shortened_cn_or_None), ...]。
    shortened_cn 仅当 shorten_primary 且需改写时非空。
    """
    overrides = overrides or {}
    result: list[tuple[str, int, str | None]] = []
    for key, senses in senses_by_key.items():
        pid = pick_primary_id(senses, overrides.get(key))
        if pid is None:
            continue
        new_cn = None
        if shorten_primary:
            for s in senses:
                if int(s["id"]) == pid:
                    shortened = shorten_cn(s.get("cn") or "")
                    if shortened and shortened != (s.get("cn") or "").strip():
                        new_cn = shortened
                    break
        result.append((key, pid, new_cn))
    return result
