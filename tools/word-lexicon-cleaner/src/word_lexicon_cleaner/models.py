"""词库清洗数据模型。"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any, Literal

Quality = Literal["ok", "uncertain", "reject"]


@dataclass
class Example:
    en: str
    cn: str | None = None
    sort_order: int = 0

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class Sense:
    cn: str
    pos: str | None = None
    is_primary: bool = False
    quality: Quality = "ok"
    sort_order: int = 0
    examples: list[Example] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        return d


@dataclass
class RawWord:
    """输入：扁平 book_words 行（去重后）。"""

    word_key: str
    en: str
    cn: str
    pos: str | None = None
    ph: str | None = None
    book_id: int | None = None

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> RawWord:
        return cls(
            word_key=str(d.get("word_key") or d.get("wordKey") or "").strip(),
            en=str(d.get("en") or "").strip(),
            cn=str(d.get("cn") or "").strip(),
            pos=(str(d["pos"]).strip() if d.get("pos") else None) or None,
            ph=(str(d["ph"]).strip() if d.get("ph") else None) or None,
            book_id=d.get("book_id"),
        )


@dataclass
class CleanedWord:
    """规则/LLM 输出：结构化义项。"""

    word_key: str
    en: str
    ph: str | None
    quality: Quality
    reason: str
    senses: list[Sense] = field(default_factory=list)
    source: str = "rules"  # rules | llm | merge

    def primary(self) -> Sense | None:
        for s in self.senses:
            if s.is_primary and s.quality == "ok":
                return s
        for s in self.senses:
            if s.is_primary:
                return s
        return self.senses[0] if self.senses else None

    def to_dict(self) -> dict[str, Any]:
        return {
            "word_key": self.word_key,
            "en": self.en,
            "ph": self.ph,
            "quality": self.quality,
            "reason": self.reason,
            "source": self.source,
            "senses": [s.to_dict() for s in self.senses],
        }

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> CleanedWord:
        senses: list[Sense] = []
        for i, sd in enumerate(d.get("senses") or []):
            examples = [
                Example(
                    en=e.get("en", ""),
                    cn=e.get("cn"),
                    sort_order=int(e.get("sort_order", j)),
                )
                for j, e in enumerate(sd.get("examples") or [])
            ]
            senses.append(
                Sense(
                    cn=str(sd.get("cn") or "").strip(),
                    pos=(str(sd["pos"]).strip() if sd.get("pos") else None) or None,
                    is_primary=bool(sd.get("is_primary", False)),
                    quality=sd.get("quality") or "ok",  # type: ignore[arg-type]
                    sort_order=int(sd.get("sort_order", i)),
                    examples=examples,
                )
            )
        return cls(
            word_key=str(d.get("word_key") or "").strip(),
            en=str(d.get("en") or "").strip(),
            ph=(str(d["ph"]).strip() if d.get("ph") else None) or None,
            quality=d.get("quality") or "reject",  # type: ignore[arg-type]
            reason=str(d.get("reason") or ""),
            senses=senses,
            source=str(d.get("source") or "rules"),
        )
