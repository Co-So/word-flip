"""规则引擎：扁平 cn → 义项列表 + ok/uncertain/reject。

对齐服务端 WordSenseNormalizer 的剥尾逻辑，并增加拆义与质量分流（REQ-LEX）。
"""

from __future__ import annotations

import re
from typing import Iterable

from .models import CleanedWord, RawWord, Sense
from .validate import ensure_primary, validate_sense_cn

# 尾部半角括号（词性或脏尾巴）；全角（…）释义保留
_TRAILING_POS = re.compile(r"\s*\([^)]*\)\s*$")
_LEADING_POS_ABBR = re.compile(
    r"(?i)^(?:n|v|vt|vi|adj|adv|prep|pron|conj|art|num|int|aux|a|ad)\.?\s*"
)
_LEADING_ENGLISH_CHUNK = re.compile(
    r"^[A-Za-z][A-Za-z'\-]*(?:\s*\([^)]*\))?\s*[；;，,]\s*"
)
# 段内尾部词性捕获：突然地 (adv.)
_EXTRACT_POS = re.compile(
    r"(?i)\s*\((?P<pos>n|v|vt|vi|adj|adv|prep|pron|conj|art|num|int|aux|a|ad|n&a|n&v|v&n)\.?\)\s*$"
)
_SPLIT_SENSES = re.compile(r"[；;]")
_HAN = re.compile(r"[\u4e00-\u9fff]")

_FUNCTION_WORDS = frozenset(
    {
        "a",
        "an",
        "the",
        "of",
        "to",
        "in",
        "on",
        "at",
        "for",
        "by",
        "with",
        "as",
        "or",
        "and",
        "is",
        "be",
        "are",
        "was",
        "were",
        "do",
        "does",
        "did",
        "have",
        "has",
        "had",
        "will",
        "would",
        "can",
        "could",
        "may",
        "might",
        "must",
        "shall",
        "should",
        "out",
        "up",
        "off",
        "go",
        "get",
    }
)

_POS_CANON = {
    "n": "n.",
    "n.": "n.",
    "v": "v.",
    "v.": "v.",
    "vt": "vt.",
    "vt.": "vt.",
    "vi": "vi.",
    "vi.": "vi.",
    "adj": "adj.",
    "adj.": "adj.",
    "a": "adj.",
    "a.": "adj.",
    "ad": "adv.",
    "ad.": "adv.",
    "adv": "adv.",
    "adv.": "adv.",
    "prep": "prep.",
    "prep.": "prep.",
    "pron": "pron.",
    "pron.": "pron.",
    "conj": "conj.",
    "conj.": "conj.",
    "art": "art.",
    "art.": "art.",
    "num": "num.",
    "num.": "num.",
    "int": "int.",
    "int.": "int.",
    "aux": "aux.",
    "aux.": "aux.",
    "n&a": "n&a.",
    "n&a.": "n&a.",
    "n&v": "n&v.",
    "n&v.": "n&v.",
    "v&n": "v&n.",
    "v&n.": "v&n.",
}


def normalize_word_key(en: str) -> str:
    return en.strip().lower()


def has_han(text: str | None) -> bool:
    if not text:
        return False
    return _HAN.search(text) is not None


def canonicalize_pos(pos: str | None) -> str | None:
    if not pos:
        return None
    p = pos.strip().lower().replace(" ", "")
    if p in _POS_CANON:
        return _POS_CANON[p]
    # 宽松：含已知族
    for key, canon in _POS_CANON.items():
        if key.rstrip(".") in p:
            return canon
    return pos.strip()


def clean_segment(raw: str) -> tuple[str, str | None]:
    """清洗单段释义，返回 (cn, extracted_pos)。"""
    text = (raw or "").strip()
    if not text:
        return "", None
    extracted: str | None = None
    m = _EXTRACT_POS.search(text)
    if m:
        extracted = canonicalize_pos(m.group("pos"))
        text = text[: m.start()].rstrip()

    prev = None
    while prev != text:
        prev = text
        text = _TRAILING_POS.sub("", text).strip()

    text = _LEADING_POS_ABBR.sub("", text).strip()
    text = _TRAILING_POS.sub("", text).strip()

    while True:
        m2 = _LEADING_ENGLISH_CHUNK.match(text)
        if not m2:
            break
        text = text[m2.end() :].strip()
        text = _TRAILING_POS.sub("", text).strip()

    # 从首个汉字起截（去掉残留拉丁前缀）
    for i, ch in enumerate(text):
        if "\u4e00" <= ch <= "\u9fff":
            if i > 0:
                text = text[i:].strip()
            break

    text = _TRAILING_POS.sub("", text).strip()
    text = re.sub(r"\s{2,}", " ", text).strip()
    return text, extracted


def looks_like_english_debris(raw_cn: str) -> bool:
    t = (raw_cn or "").strip()
    if not t:
        return True
    c = t[0]
    return ("A" <= c <= "Z") or ("a" <= c <= "z")


def is_function_word(word_key: str) -> bool:
    if word_key in _FUNCTION_WORDS:
        return True
    return len(word_key) <= 2


def split_raw_senses(cn: str) -> list[str]:
    parts = [p.strip() for p in _SPLIT_SENSES.split(cn or "") if p.strip()]
    return parts if parts else ([cn.strip()] if cn and cn.strip() else [])


def clean_word(raw: RawWord) -> CleanedWord:
    """对单条扁平词条跑规则，产出 CleanedWord。"""
    en = (raw.en or "").strip()
    word_key = (raw.word_key or normalize_word_key(en)).strip().lower()
    if not word_key and en:
        word_key = normalize_word_key(en)
    ph = raw.ph
    row_pos = canonicalize_pos(raw.pos)

    if not en or not word_key:
        return CleanedWord(
            word_key=word_key or "",
            en=en,
            ph=ph,
            quality="reject",
            reason="empty_en",
            senses=[],
            source="rules",
        )

    segments = split_raw_senses(raw.cn)
    senses: list[Sense] = []
    reasons: list[str] = []

    for i, seg in enumerate(segments):
        cn, extracted_pos = clean_segment(seg)
        pos = extracted_pos or row_pos
        ok_cn, cn_reason = validate_sense_cn(cn, en, word_key)
        if not ok_cn:
            # 段失败：记 uncertain 段或跳过
            if has_han(seg) or has_han(cn):
                senses.append(
                    Sense(
                        cn=cn or seg,
                        pos=pos,
                        is_primary=False,
                        quality="uncertain",
                        sort_order=i,
                    )
                )
                reasons.append(f"seg{i}:{cn_reason}")
            else:
                reasons.append(f"seg{i}:drop:{cn_reason}")
            continue
        senses.append(
            Sense(
                cn=cn,
                pos=pos,
                is_primary=False,
                quality="ok",
                sort_order=i,
            )
        )

    if not senses:
        return CleanedWord(
            word_key=word_key,
            en=en,
            ph=ph,
            quality="reject",
            reason=";".join(reasons) or "no_han_sense",
            senses=[],
            source="rules",
        )

    # 虚词 + 原文英文头 → uncertain（对齐测验池排除）
    if is_function_word(word_key) and looks_like_english_debris(raw.cn):
        for s in senses:
            s.quality = "uncertain"
        ensure_primary(senses)
        return CleanedWord(
            word_key=word_key,
            en=en,
            ph=ph,
            quality="uncertain",
            reason="function_word_debris",
            senses=senses,
            source="rules",
        )

    # 任一段 uncertain → 整词 uncertain
    if any(s.quality == "uncertain" for s in senses):
        ensure_primary(senses)
        return CleanedWord(
            word_key=word_key,
            en=en,
            ph=ph,
            quality="uncertain",
            reason=";".join(reasons) or "partial_uncertain",
            senses=senses,
            source="rules",
        )

    # 多义且某段仍含拉丁字母 → uncertain
    if len(senses) > 1 and any(re.search(r"[A-Za-z]{3,}", s.cn) for s in senses):
        for s in senses:
            if re.search(r"[A-Za-z]{3,}", s.cn):
                s.quality = "uncertain"
        ensure_primary(senses)
        return CleanedWord(
            word_key=word_key,
            en=en,
            ph=ph,
            quality="uncertain",
            reason="latin_in_multi_sense",
            senses=senses,
            source="rules",
        )

    ensure_primary(senses)
    return CleanedWord(
        word_key=word_key,
        en=en,
        ph=ph,
        quality="ok",
        reason="rules_ok",
        senses=senses,
        source="rules",
    )


def clean_many(rows: Iterable[RawWord]) -> list[CleanedWord]:
    return [clean_word(r) for r in rows]
