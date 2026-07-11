"""learning-primary 重选与虚词黄金样例。"""

from __future__ import annotations

from word_lexicon_cleaner.learning_primary import (
    apply_reselect,
    load_overrides,
    pick_primary_id,
    pos_rank,
    shorten_cn,
)


def _sense(sid: int, pos: str, cn: str, *, primary: bool = False, quality: str = "ok", order: int = 0):
    return {
        "id": sid,
        "pos": pos,
        "cn": cn,
        "is_primary": primary,
        "quality": quality,
        "sort_order": order,
    }


def test_pos_rank_prefers_adv_over_conj():
    assert pos_rank("adv.") < pos_rank("conj.")
    assert pos_rank("adj.") < pos_rank("prep.")


def test_shorten_cn_keeps_first_segments():
    long_cn = "刚刚, 正好, 仅仅, 几乎, 恰好是"
    assert len(shorten_cn(long_cn, max_len=20)) <= 20
    assert "刚刚" in shorten_cn(long_cn, max_len=20)


def test_override_only_picks_adv_只有():
    senses = [
        _sense(1, "conj.", "但是, 不过", primary=True, order=0),
        _sense(2, "adj.", "唯一的, 仅有的", order=1),
        _sense(3, "adv.", "只有, 仅仅, 只能", order=2),
    ]
    overrides = load_overrides()
    assert pick_primary_id(senses, overrides["only"]) == 3


def test_override_but_picks_conj_但是():
    senses = [
        _sense(10, "prep.", "除了", primary=True),
        _sense(11, "conj.", "但是", order=1),
        _sense(12, "adv.", "仅仅", order=2),
    ]
    overrides = load_overrides()
    assert pick_primary_id(senses, overrides["but"]) == 11


def test_override_just_picks_adv():
    senses = [
        _sense(20, "adj.", "正直的, 合理的", primary=True),
        _sense(21, "adv.", "刚刚, 正好, 仅仅", order=1),
    ]
    overrides = load_overrides()
    assert pick_primary_id(senses, overrides["just"]) == 21


def test_override_go_picks_vi_去():
    senses = [
        _sense(30, "vt.", "使…变成", primary=True),
        _sense(31, "vi.", "去, 走", order=1),
    ]
    overrides = load_overrides()
    assert pick_primary_id(senses, overrides["go"]) == 31


def test_apply_reselect_shortens_primary():
    by = {
        "just": [
            _sense(1, "adj.", "正直的", primary=True),
            _sense(
                2,
                "adv.",
                "刚刚, 正好, 仅仅, 几乎不, 恰恰, 刚才, 方才, 刚好, 恰好如此而且非常长的尾巴",
                order=1,
            ),
        ]
    }
    picks = apply_reselect(by, load_overrides(), shorten_primary=True)
    assert len(picks) == 1
    key, pid, new_cn = picks[0]
    assert key == "just" and pid == 2
    assert new_cn is not None
    assert len(new_cn) <= 40


def test_default_prefers_adv_without_override():
    senses = [
        _sense(1, "conj.", "然而", primary=True),
        _sense(2, "adv.", "因此", order=1),
    ]
    assert pick_primary_id(senses, None) == 2
