"""义项 Schema 校验（入库前硬门槛）。"""

from __future__ import annotations

import re

from .models import Sense

_HAN = re.compile(r"[\u4e00-\u9fff]")
_TRAILING_POS_TAIL = re.compile(r"\([^)]*\)\s*$")


def has_han(text: str | None) -> bool:
    return bool(text and _HAN.search(text))


def validate_sense_cn(cn: str, en: str, word_key: str) -> tuple[bool, str]:
    """返回 (ok, reason)。"""
    text = (cn or "").strip()
    if not text:
        return False, "empty_cn"
    if not has_han(text):
        return False, "no_han"
    if text.lower() == (en or "").strip().lower():
        return False, "cn_eq_en"
    if text.lower() == (word_key or "").strip().lower():
        return False, "cn_eq_key"
    # 禁止词性尾巴残留
    if _TRAILING_POS_TAIL.search(text) and re.search(
        r"\((?i)n|v|vt|vi|adj|adv|prep)\.?\)\s*$", text
    ):
        return False, "pos_in_cn"
    # 整段几乎全是拉丁（英文充中文）
    han_count = len(_HAN.findall(text))
    latin_count = len(re.findall(r"[A-Za-z]", text))
    if latin_count > 0 and han_count < 1:
        return False, "latin_only"
    if latin_count >= 8 and han_count <= 2:
        return False, "mostly_latin"
    return True, "ok"


def ensure_primary(senses: list[Sense]) -> None:
    """保证至多一个 primary；优先第一个 quality=ok。"""
    if not senses:
        return
    for s in senses:
        s.is_primary = False
    for s in senses:
        if s.quality == "ok":
            s.is_primary = True
            return
    senses[0].is_primary = True
