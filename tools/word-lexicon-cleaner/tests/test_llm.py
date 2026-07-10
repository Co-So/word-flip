"""LLM 适配器：无 Key 时跳过。"""

from word_lexicon_cleaner.llm import llm_configured, refine_uncertain
from word_lexicon_cleaner.models import CleanedWord, Sense


def test_llm_skip_without_key(monkeypatch):
    monkeypatch.delenv("LEXICON_LLM_API_KEY", raising=False)
    # 避免读到本地 .env
    monkeypatch.setenv("LEXICON_LLM_API_KEY", "")
    w = CleanedWord(
        word_key="in",
        en="in",
        ph=None,
        quality="uncertain",
        reason="function_word_debris",
        senses=[Sense(cn="为收款人", pos="prep.", is_primary=True, quality="uncertain")],
    )
    out = refine_uncertain(w)
    assert out.quality == "uncertain"
    assert "llm_skipped_no_key" in out.reason or out.source == "llm_skip"


def test_llm_configured_false_when_empty(monkeypatch):
    monkeypatch.setenv("LEXICON_LLM_API_KEY", "")
    assert llm_configured() is False
