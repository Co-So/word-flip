"""ECDICT 解析与核心虚词黄金样例。"""

from word_lexicon_cleaner.ecdict import from_ecdict_row, parse_translation_lines


def test_parse_be_like():
    senses = parse_translation_lines("v. 是, 表示, 在\n[计] 后端, 总线允许")
    assert senses
    assert senses[0].pos == "v."
    assert "是" in senses[0].cn


def test_from_ecdict_be():
    w = from_ecdict_row(
        word="be",
        phonetic="bi:",
        translation="v. 是, 表示, 在\n[计] 后端",
    )
    # [计] 仍会进列表；overlay 再过滤。此处至少有 v. 义项
    assert any(s.pos == "v." and "是" in s.cn for s in w.senses)
    assert w.quality in ("ok", "uncertain")


def test_have_prefers_vt_over_aux_when_picking():
    from word_lexicon_cleaner.ecdict import pick_primary
    from word_lexicon_cleaner.models import Sense

    senses = [
        Sense(cn="已经", pos="aux.", quality="ok", sort_order=0),
        Sense(cn="有，怀有", pos="vt.", quality="ok", sort_order=1),
    ]
    pick_primary(senses)
    primary = next(s for s in senses if s.is_primary)
    assert primary.pos == "vt."
    assert "有" in primary.cn


def test_a_prefers_article_gloss():
    w = from_ecdict_row(
        word="a",
        phonetic="ei",
        translation="第一个字母 A; 一个; 第一的\nart. [计] 累加器",
    )
    assert w.quality == "ok"
    assert w.primary() is not None
    assert "一个" in (w.primary().cn or "") or w.primary().cn == "一"


def test_on_prefers_prep():
    w = from_ecdict_row(
        word="on",
        phonetic="ɒn",
        translation="prep. 在...之上\nadv. ...上去\na. 正起作用的\n[计] 打开",
    )
    assert w.primary().pos == "prep."


def test_download_keeps_tech_gloss():
    w = from_ecdict_row(
        word="download",
        phonetic="",
        translation="[计] 下载, 下栽",
    )
    assert w.senses
    assert any("下载" in s.cn or "下栽" in s.cn for s in w.senses)
