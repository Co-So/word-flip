"""可选 LLM 适配器：仅处理 quality=uncertain。无 API Key 时跳过。"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from .models import CleanedWord, Sense
from .validate import ensure_primary, validate_sense_cn


def load_dotenv(path: Path | None = None) -> None:
    """极简 .env 加载（不依赖 python-dotenv）。"""
    env_path = path or Path(__file__).resolve().parents[2] / ".env"
    if not env_path.is_file():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if k and k not in os.environ:
            os.environ[k] = v


def llm_configured() -> bool:
    load_dotenv()
    return bool(os.environ.get("LEXICON_LLM_API_KEY", "").strip())


def _chat_json(prompt: str) -> dict[str, Any] | None:
    load_dotenv()
    api_key = os.environ.get("LEXICON_LLM_API_KEY", "").strip()
    if not api_key:
        return None
    base = os.environ.get("LEXICON_LLM_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    model = os.environ.get("LEXICON_LLM_MODEL", "gpt-4o-mini")
    body = {
        "model": model,
        "temperature": 0,
        "response_format": {"type": "json_object"},
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是英语词典结构化助手。只输出 JSON："
                    '{"senses":[{"pos":"n.|v.|adj.|adv.|prep.|null","cn":"纯中文释义"}],'
                    '"quality":"ok|uncertain|reject","reason":"..."}。'
                    "规则：cn 必须含汉字、禁止词性写进 cn、一词可多义、选最常用义为第一条。"
                ),
            },
            {"role": "user", "content": prompt},
        ],
    }
    req = urllib.request.Request(
        f"{base}/chat/completions",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
        content = payload["choices"][0]["message"]["content"]
        return json.loads(content)
    except (urllib.error.URLError, TimeoutError, KeyError, json.JSONDecodeError, IndexError):
        return None


def refine_uncertain(word: CleanedWord) -> CleanedWord:
    """对 uncertain 词调用 LLM；失败则保持 uncertain。"""
    if word.quality != "uncertain":
        return word
    if not llm_configured():
        out = CleanedWord(
            word_key=word.word_key,
            en=word.en,
            ph=word.ph,
            quality=word.quality,
            reason=word.reason + "|llm_skipped_no_key",
            senses=list(word.senses),
            source="llm_skip",
        )
        return out

    prompt = (
        f"word_key={word.word_key}\nen={word.en}\nph={word.ph}\n"
        f"current_reason={word.reason}\n"
        f"current_senses={json.dumps([s.to_dict() for s in word.senses], ensure_ascii=False)}"
    )
    data = _chat_json(prompt)
    if not data:
        return CleanedWord(
            word_key=word.word_key,
            en=word.en,
            ph=word.ph,
            quality="uncertain",
            reason=word.reason + "|llm_failed",
            senses=list(word.senses),
            source="llm",
        )

    senses: list[Sense] = []
    for i, sd in enumerate(data.get("senses") or []):
        cn = str(sd.get("cn") or "").strip()
        pos = sd.get("pos")
        if pos in ("null", "None", ""):
            pos = None
        ok, reason = validate_sense_cn(cn, word.en, word.word_key)
        senses.append(
            Sense(
                cn=cn,
                pos=str(pos).strip() if pos else None,
                is_primary=False,
                quality="ok" if ok else "uncertain",
                sort_order=i,
            )
        )
        if not ok:
            # 校验失败的义项不抬升整词为 ok
            pass

    if not senses or not any(s.quality == "ok" for s in senses):
        q = data.get("quality") if data.get("quality") in ("ok", "uncertain", "reject") else "uncertain"
        if q == "ok":
            q = "uncertain"
        return CleanedWord(
            word_key=word.word_key,
            en=word.en,
            ph=word.ph,
            quality=q,  # type: ignore[arg-type]
            reason=str(data.get("reason") or "llm_no_valid_sense"),
            senses=senses or list(word.senses),
            source="llm",
        )

    ensure_primary(senses)
    q = data.get("quality") if data.get("quality") in ("ok", "uncertain", "reject") else "ok"
    # 硬校验：有 ok primary 才允许 ok
    if q == "ok" and not any(s.is_primary and s.quality == "ok" for s in senses):
        q = "uncertain"
    return CleanedWord(
        word_key=word.word_key,
        en=word.en,
        ph=word.ph,
        quality=q,  # type: ignore[arg-type]
        reason=str(data.get("reason") or "llm_ok"),
        senses=senses,
        source="llm",
    )


def process_file(rows: list[CleanedWord]) -> list[CleanedWord]:
    out: list[CleanedWord] = []
    for w in rows:
        if w.quality == "uncertain":
            out.append(refine_uncertain(w))
        else:
            out.append(w)
    return out
