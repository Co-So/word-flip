"""从三本词书和 ECDICT 构建可审核的学习卡候选。"""

from __future__ import annotations

from dataclasses import asdict, dataclass
import csv
import hashlib
import json
from pathlib import Path
import re
import sqlite3
from typing import Iterable


CHINESE_RE = re.compile(r"[\u3400-\u9fff]")
TECHNICAL_TAG_RE = re.compile(r"\b(?:CET4|CET6|IELTS|TOEFL|GRE|考研|高考)\b", re.IGNORECASE)
EXCHANGE_TYPES = {
    "p": "past",
    "d": "past_participle",
    "i": "present_participle",
    "3": "third_person",
    "r": "comparative",
    "t": "superlative",
    "s": "plural",
    "0": "lemma",
    "1": "variant",
}
FIELDS = (
    "id", "word", "sw", "phonetic", "definition", "translation", "pos",
    "collins", "oxford", "tag", "bnc", "frq", "exchange", "detail", "audio",
)


@dataclass(frozen=True)
class BuildReport:
    """内容构建统计。"""

    total_items: int
    source_entries: int
    published_cards: int
    review_required_cards: int


def normalize_word(value: object) -> str:
    """生成全系统统一的规范词形键。"""
    return str(value or "").strip().lower()


def build_content(
    sqlite_path: Path,
    books: dict[str, Path],
    output_dir: Path,
    overrides_path: Path | None = None,
) -> BuildReport:
    """构建来源原始记录、词书学习卡、异常清单与确定性抽样。"""
    book_items = {code: _load_book(path) for code, path in books.items()}
    all_keys = sorted({item["word_key"] for items in book_items.values() for item in items})
    source_by_key = _load_ecdict_entries(sqlite_path, all_keys)
    overrides = _load_overrides(overrides_path)

    output_dir.mkdir(parents=True, exist_ok=True)
    source_entries = [_source_record(source_by_key[key]) for key in all_keys if key in source_by_key]
    cards: list[dict[str, object]] = []
    anomalies: list[dict[str, object]] = []

    for book_code, items in book_items.items():
        for order, item in enumerate(items, start=1):
            source = source_by_key.get(item["word_key"])
            card = _build_card(book_code, order, item, source, overrides)
            cards.append(card)
            if card["anomalies"]:
                anomalies.append(
                    {
                        "book_code": book_code,
                        "word_key": card["word_key"],
                        "anomalies": card["anomalies"],
                        "status": card["status"],
                    }
                )

    _write_jsonl(output_dir / "source_entries.jsonl", source_entries)
    _write_jsonl(output_dir / "learning_cards.jsonl", cards)
    _write_jsonl(output_dir / "anomalies.jsonl", anomalies)
    samples = {
        code: _deterministic_sample([card for card in cards if card["book_code"] == code], 100)
        for code in books
    }
    (output_dir / "review_samples.json").write_text(
        json.dumps(samples, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    report = BuildReport(
        total_items=len(cards),
        source_entries=len(source_entries),
        published_cards=sum(card["status"] == "published" for card in cards),
        review_required_cards=sum(card["status"] != "published" for card in cards),
    )
    (output_dir / "build_report.json").write_text(
        json.dumps(asdict(report), ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return report


def _load_book(path: Path) -> list[dict[str, object]]:
    suffix = path.suffix.lower()
    if suffix == ".sql":
        raw_items = _load_legacy_sql_book(path)
    elif suffix in {".json", ".jsonl"}:
        if suffix == ".jsonl":
            raw_items = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
        else:
            value = json.loads(path.read_text(encoding="utf-8"))
            raw_items = value if isinstance(value, list) else value.get("words", value.get("items", value.get("data", [])))
    else:
        with path.open(encoding="utf-8-sig", newline="") as source:
            raw_items = list(csv.DictReader(source))

    result: list[dict[str, object]] = []
    seen: set[str] = set()
    for raw in raw_items:
        if isinstance(raw, str):
            raw = {"en": raw}
        word = raw.get("en") or raw.get("word") or raw.get("headword")
        key = normalize_word(word)
        if not key or key in seen:
            continue
        seen.add(key)
        result.append(
            {
                "word_key": key,
                "headword": str(word).strip(),
                "cn": _first_text(raw, "cn", "meaning", "translation", "definition_cn"),
                "pos": _first_text(raw, "pos", "part_of_speech"),
                "phonetic": _first_text(raw, "ph", "phonetic"),
                "raw_payload": raw,
            }
        )
    return result


SQL_VALUE = r"'(?:''|[^'])*'|NULL"
LEGACY_BOOK_ROW_RE = re.compile(
    rf"^\(\d+,\s*({SQL_VALUE}),\s*({SQL_VALUE}),\s*({SQL_VALUE}),\s*"
    rf"({SQL_VALUE}),\s*({SQL_VALUE}),\s*({SQL_VALUE}),\s*(\d+)\)[,;]$"
)


def _load_legacy_sql_book(path: Path) -> list[dict[str, object]]:
    """读取仓库历史 Flyway 中的真实词书资产，不执行 SQL。"""
    rows: list[dict[str, object]] = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        match = LEGACY_BOOK_ROW_RE.match(raw_line.strip())
        if not match:
            continue
        word_key, en, cn, pos, phonetic, detail, sort_order = match.groups()
        detail_value = _decode_sql_value(detail)
        try:
            detail_json = json.loads(detail_value) if detail_value else None
        except json.JSONDecodeError:
            detail_json = {"raw": detail_value}
        rows.append(
            {
                "word": _decode_sql_value(en) or _decode_sql_value(word_key),
                "cn": _decode_sql_value(cn),
                "pos": _decode_sql_value(pos),
                "phonetic": _decode_sql_value(phonetic),
                "sort_order": int(sort_order),
                "detail": detail_json,
            }
        )
    rows.sort(key=lambda item: int(item["sort_order"]))
    if not rows:
        raise ValueError(f"未从历史词书 SQL 解析到任何条目：{path}")
    return rows


def _decode_sql_value(value: str) -> str | None:
    """解码单引号 SQL 字面量；历史资产不包含反斜杠转义。"""
    if value == "NULL":
        return None
    return value[1:-1].replace("''", "'")


def _load_ecdict_entries(path: Path, keys: list[str]) -> dict[str, dict[str, object]]:
    connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    result: dict[str, dict[str, object]] = {}
    try:
        for start in range(0, len(keys), 500):
            chunk = keys[start : start + 500]
            placeholders = ",".join("?" for _ in chunk)
            rows = connection.execute(
                f"SELECT {', '.join(FIELDS)} FROM stardict WHERE lower(trim(word)) IN ({placeholders})",
                chunk,
            )
            for row in rows:
                key = normalize_word(row["word"])
                result.setdefault(key, dict(row))
    finally:
        connection.close()
    return result


def _build_card(
    book_code: str,
    sort_order: int,
    item: dict[str, object],
    source: dict[str, object] | None,
    overrides: dict[str, dict[str, object]],
) -> dict[str, object]:
    override = overrides.get(f"{book_code}:{item['word_key']}", {})
    book_cn = str(item.get("cn") or "").strip()
    source_cn = str((source or {}).get("translation") or "").strip()
    cn = str(override.get("cn") or book_cn or source_cn).strip()
    pos = str(override.get("pos") or item.get("pos") or (source or {}).get("pos") or "").strip() or None
    provenance = "人工覆盖" if override else ("词书原始考义" if book_cn else "ECDICT 候选")
    anomalies = _detect_anomalies(cn, pos, source is not None)
    approved = bool(override.get("approved", False))
    rejected = bool(override.get("rejected", False))
    status = "published" if cn and CHINESE_RE.search(cn) and (not anomalies or approved) and not rejected else "review_required"
    return {
        "book_code": book_code,
        "sort_order": sort_order,
        "word_key": item["word_key"],
        "headword": item["headword"],
        "phonetic": item.get("phonetic") or (source or {}).get("phonetic"),
        "status": status,
        "version": 1,
        "senses": [{"pos": pos, "cn": cn, "is_primary": True, "quality": "ok" if status == "published" else "uncertain"}],
        "provenance": provenance,
        "source_entry_key": str((source or {}).get("id")) if source else None,
        "anomalies": anomalies,
        "raw_book_item": item["raw_payload"],
    }


def _detect_anomalies(cn: str, pos: str | None, matched: bool) -> list[str]:
    flags: list[str] = []
    if not matched:
        flags.append("ecdict_missing")
    if not cn or not CHINESE_RE.search(cn):
        flags.append("missing_chinese")
    if len(cn) > 80:
        flags.append("meaning_too_long")
    if len({part for part in re.split(r"[,;/、\s]+", pos or "") if part}) > 2:
        flags.append("multiple_parts_of_speech")
    if TECHNICAL_TAG_RE.search(cn):
        flags.append("technical_tag")
    return flags


def _source_record(row: dict[str, object]) -> dict[str, object]:
    payload = {field: row.get(field) for field in FIELDS}
    cn = str(row.get("translation") or "").strip() or None
    en_gloss = str(row.get("definition") or "").strip() or None
    pos = str(row.get("pos") or "").strip() or None
    senses = []
    if cn or en_gloss:
        senses.append(
            {
                "pos": pos,
                "cn": cn,
                "en_gloss": en_gloss,
                "quality": "ok" if cn and CHINESE_RE.search(cn) else "uncertain",
                "derivation": {
                    "rule": "ecdict_raw_fields",
                    "raw_fields": ["pos", "translation", "definition"],
                },
            }
        )
    return {
        "source": "ecdict",
        "source_key": str(row["id"]),
        "word_key": normalize_word(row["word"]),
        "raw_definition": row.get("definition"),
        "raw_translation": row.get("translation"),
        "raw_payload": payload,
        "senses": senses,
        "word_forms": _parse_exchange(str(row.get("exchange") or "")),
    }


def _parse_exchange(exchange: str) -> list[dict[str, str]]:
    """将 ECDICT exchange 字段转换为可查询词形，同时保留原始行用于追溯。"""
    result: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for part in exchange.split("/"):
        code, separator, raw_form = part.partition(":")
        form_type = EXCHANGE_TYPES.get(code.strip())
        form = raw_form.strip()
        form_key = normalize_word(form)
        key = (form_key, form_type or "")
        if not separator or not form_type or not form_key or key in seen:
            continue
        seen.add(key)
        result.append({"form": form, "form_key": form_key, "form_type": form_type})
    return result


def _load_overrides(path: Path | None) -> dict[str, dict[str, object]]:
    if path is None or not path.exists():
        return {}
    value = json.loads(path.read_text(encoding="utf-8"))
    return value if isinstance(value, dict) else {}


def _deterministic_sample(cards: list[dict[str, object]], limit: int) -> list[dict[str, object]]:
    ranked = sorted(cards, key=lambda card: hashlib.sha256(str(card["word_key"]).encode()).hexdigest())
    return ranked[:limit]


def _write_jsonl(path: Path, rows: Iterable[dict[str, object]]) -> None:
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def _first_text(value: dict[str, object], *keys: str) -> str:
    for key in keys:
        candidate = value.get(key)
        if candidate is not None and str(candidate).strip():
            return str(candidate).strip()
    return ""
