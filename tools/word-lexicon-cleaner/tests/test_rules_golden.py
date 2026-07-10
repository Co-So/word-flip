"""黄金样例：规则引擎 ≥30 条（REQ-LEX / 对齐 WordSenseNormalizer）。"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from word_lexicon_cleaner.models import RawWord
from word_lexicon_cleaner.rules import clean_segment, clean_word, has_han
from word_lexicon_cleaner.validate import validate_sense_cn

FIXTURES = Path(__file__).parent / "fixtures" / "golden.jsonl"


def _load_golden() -> list[dict]:
    rows = []
    for line in FIXTURES.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def test_golden_count_at_least_30():
    rows = _load_golden()
    assert len(rows) >= 30


@pytest.mark.parametrize("case", _load_golden(), ids=lambda c: c["id"])
def test_golden_case(case: dict):
    raw = RawWord.from_dict(case["input"])
    out = clean_word(raw)
    assert out.quality == case["expect_quality"], (
        f"{case['id']}: quality want={case['expect_quality']} got={out.quality} reason={out.reason}"
    )
    if "expect_primary_cn" in case:
        primary = out.primary()
        assert primary is not None
        assert primary.cn == case["expect_primary_cn"], (
            f"{case['id']}: cn want={case['expect_primary_cn']!r} got={primary.cn!r}"
        )
    if "expect_primary_pos" in case:
        primary = out.primary()
        assert primary is not None
        assert primary.pos == case["expect_primary_pos"]
    if case.get("expect_min_senses"):
        assert len(out.senses) >= case["expect_min_senses"]
    if case["expect_quality"] == "ok":
        assert any(s.is_primary and s.quality == "ok" for s in out.senses)
        assert has_han(out.primary().cn)  # type: ignore[union-attr]


def test_clean_segment_strips_trailing_pos():
    cn, pos = clean_segment("突然地 (adv.)")
    assert cn == "突然地"
    assert pos == "adv."


def test_clean_segment_keeps_fullwidth_parens():
    cn, _ = clean_segment("吸收（液体、气体等） (v.)")
    assert cn == "吸收（液体、气体等）"


def test_validate_rejects_no_han():
    ok, reason = validate_sense_cn("hello", "hello", "hello")
    assert not ok
    assert reason in ("no_han", "cn_eq_en", "cn_eq_key")
