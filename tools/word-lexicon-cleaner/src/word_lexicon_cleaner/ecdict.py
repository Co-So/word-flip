"""从 ECDICT 解析 translation → Sense 列表。

ECDICT translation 常见形态（每行一条）：
  n. 名词释义
  vt. 及物动词释义
  vi. 不及物…
  prep. …
或无词性前缀的纯中文行。
"""

from __future__ import annotations

import re
from typing import Iterable

from .models import CleanedWord, Sense
from .validate import ensure_primary, has_han, validate_sense_cn

# 行首词性：n. / vt. / vi. / a. / adj. / adv. / prep. / conj. / art. / aux. / pron. / num. / int. / v.
_LINE_POS = re.compile(
    r"(?i)^\s*(?P<pos>"
    r"n|v|vt|vi|adj|adv|prep|pron|conj|art|num|int|aux|a|ad|abbr|pl|pref|suf|comb"
    r")\.?\s+"
)

_POS_CANON = {
    "n": "n.",
    "v": "v.",
    "vt": "vt.",
    "vi": "vi.",
    "adj": "adj.",
    "a": "adj.",
    "ad": "adv.",
    "adv": "adv.",
    "prep": "prep.",
    "pron": "pron.",
    "conj": "conj.",
    "art": "art.",
    "num": "num.",
    "int": "int.",
    "aux": "aux.",
    "abbr": "abbr.",
    "pl": "pl.",
    "pref": "pref.",
    "suf": "suf.",
    "comb": "comb.",
}

# 学习用 primary：实义动词优先于助动词（have 的「有」优先于「已经」）
_PRIMARY_POS_PRIORITY = (
    "art.",
    "v.",
    "vt.",
    "vi.",
    "prep.",
    "pron.",
    "conj.",
    "n.",
    "adj.",
    "adv.",
    "aux.",
    "num.",
    "int.",
)


def canonicalize_pos(raw: str | None) -> str | None:
    if not raw:
        return None
    key = raw.strip().lower().rstrip(".")
    return _POS_CANON.get(key, raw.strip() if raw.strip().endswith(".") else raw.strip() + ".")


def parse_translation_lines(translation: str | None) -> list[Sense]:
    """把 ECDICT translation 拆成义项。"""
    if not translation:
        return []
    text = translation.replace("\\n", "\n")
    senses: list[Sense] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        pos = None
        # 剥 [计] 等标签，保留后面中文（download/internet 等）
        tech = re.match(r"^\[([^\]]+)\]\s*", line)
        tech_tag = None
        if tech:
            tech_tag = tech.group(1)
            line = line[tech.end() :].strip()
            if not line:
                continue
        m = _LINE_POS.match(line)
        if m:
            pos = canonicalize_pos(m.group("pos"))
            line = line[m.end() :].strip()
        # 再剥一次行内 [计]
        if line.startswith("["):
            tech2 = re.match(r"^\[([^\]]+)\]\s*", line)
            if tech2:
                tech_tag = tech_tag or tech2.group(1)
                line = line[tech2.end() :].strip()
        # 分号拆多义（ECDICT 常见：第一个字母 A; 一个; 第一的）
        parts = [p.strip() for p in re.split(r"[；;]", line) if p.strip()] or [line]
        for part in parts:
            ok, _ = validate_sense_cn(part, "", "")
            if not ok and not has_han(part):
                continue
            # 技术标签义项标 uncertain，避免抢 primary；无其它义时仍可用
            quality = "uncertain" if tech_tag else ("ok" if ok else "uncertain")
            if ok and not tech_tag:
                quality = "ok"
            senses.append(
                Sense(
                    cn=part,
                    pos=pos,
                    is_primary=False,
                    quality=quality,  # type: ignore[arg-type]
                    sort_order=len(senses),
                )
            )
    return senses


def pick_primary(senses: list[Sense]) -> None:
    """按学习者词典习惯：优先常用词性族中第一条 ok 义项。"""
    if not senses:
        return
    for s in senses:
        s.is_primary = False

    # 冠词 a/an/the：优先「一个/那」类短义
    for s in senses:
        if s.quality != "ok":
            continue
        if s.pos == "art." or s.cn in ("一个", "一", "那", "这个"):
            s.is_primary = True
            return
        if s.cn.startswith("一个") or s.cn == "一":
            s.is_primary = True
            return

    for want in _PRIMARY_POS_PRIORITY:
        for s in senses:
            if s.quality == "ok" and (s.pos or "") == want:
                s.is_primary = True
                return
    for s in senses:
        if s.quality == "ok":
            s.is_primary = True
            return
    # 仅有 uncertain（如纯 [计] 词）时仍指定 primary，整词标 uncertain
    ensure_primary(senses)


def from_ecdict_row(
    *,
    word: str,
    phonetic: str | None,
    translation: str | None,
    definition: str | None = None,
) -> CleanedWord:
    """单条 ECDICT → CleanedWord。"""
    word_key = word.strip().lower()
    en = word.strip()
    ph = None
    if phonetic:
        p = phonetic.strip()
        if p and not p.startswith("/"):
            p = f"/{p}/"
        ph = p or None

    senses = parse_translation_lines(translation)
    if not senses:
        return CleanedWord(
            word_key=word_key,
            en=en,
            ph=ph,
            quality="reject",
            reason="ecdict_no_han_translation",
            senses=[],
            source="ecdict",
        )

    # 仅有 uncertain（如纯 [计]）：提升含汉字义项为 ok，避免核心科技词整词 reject
    if not any(s.quality == "ok" for s in senses):
        for s in senses:
            if has_han(s.cn):
                s.quality = "ok"

    pick_primary(senses)
    ok_primary = any(s.is_primary and s.quality == "ok" for s in senses)
    if not ok_primary:
        return CleanedWord(
            word_key=word_key,
            en=en,
            ph=ph,
            quality="uncertain",
            reason="ecdict_no_ok_primary",
            senses=senses,
            source="ecdict",
        )
    return CleanedWord(
        word_key=word_key,
        en=en,
        ph=ph,
        quality="ok",
        reason="ecdict_ok",
        senses=senses,
        source="ecdict",
    )


def merge_pos_frequency_hint(pos_field: str | None) -> str | None:
    """ECDICT pos 字段如 n:46/v:54 → 取最高频词性作为缺省提示。"""
    if not pos_field:
        return None
    best = None
    best_pct = -1.0
    for part in pos_field.split("/"):
        part = part.strip()
        if ":" not in part:
            continue
        tag, pct_s = part.split(":", 1)
        try:
            pct = float(pct_s)
        except ValueError:
            continue
        if pct > best_pct:
            best_pct = pct
            best = canonicalize_pos(tag)
    return best
