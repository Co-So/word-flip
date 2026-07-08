#!/usr/bin/env python3
"""
内置词书 ETL：下载 KyleBing JSON → 清洗 → 生成 Flyway V3 迁移 SQL。
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:
    print("需要 PyYAML: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

SCRIPT_DIR = Path(__file__).resolve().parent
RAW_DIR = SCRIPT_DIR / "raw"
PROCESSED_DIR = SCRIPT_DIR / "processed"
MIGRATION_DIR = SCRIPT_DIR.parent.parent / "src" / "main" / "resources" / "db" / "migration"

WORD_KEY_RE = re.compile(r"^[a-z0-9][a-z0-9\-']*$")
POS_MAP = {
    "n": "n.",
    "v": "v.",
    "adj": "adj.",
    "adv": "adv.",
    "prep": "prep.",
    "conj": "conj.",
    "pron": "pron.",
    "art": "art.",
    "num": "num.",
    "int": "int.",
    "interj": "int.",
}


def load_config() -> dict[str, Any]:
    with open(SCRIPT_DIR / "config.yaml", encoding="utf-8") as f:
        return yaml.safe_load(f)


def download_file(base_url: str, rel_path: str, force: bool) -> Path:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    safe_name = rel_path.replace("/", "__")
    dest = RAW_DIR / safe_name
    if dest.exists() and not force:
        return dest
    # 路径含中文时需百分号编码
    encoded_path = "/".join(urllib.parse.quote(part, safe="/") for part in rel_path.split("/"))
    url = f"{base_url.rstrip('/')}/{encoded_path}"
    print(f"  下载 {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "WordFlip-ETL/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            dest.write_bytes(resp.read())
    except urllib.error.URLError as e:
        raise RuntimeError(f"下载失败 {url}: {e}") from e
    return dest


def load_json_array(path: Path) -> list[dict[str, Any]]:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError(f"{path} 不是 JSON 数组")
    return data


def normalize_pos(raw: str | None) -> str | None:
    if not raw:
        return None
    t = raw.strip().lower().rstrip(".")
    if t in POS_MAP:
        return POS_MAP[t]
    if len(t) <= 8:
        return t + "." if not t.endswith(".") else t
    return None


def format_phonetic(uk: str | None, us: str | None) -> str | None:
    raw = (uk or us or "").strip()
    if not raw:
        return None
    if not raw.startswith("/"):
        raw = f"/{raw}/"
    return raw[:64]


def truncate(text: str, max_len: int) -> str:
    if len(text) <= max_len:
        return text
    return text[: max_len - 1] + "…"


def merge_cn(translations: list[dict[str, Any]]) -> tuple[str, str | None]:
    parts: list[str] = []
    first_pos: str | None = None
    for i, tr in enumerate(translations):
        t = (tr.get("translation") or "").strip()
        if not t:
            continue
        pos = normalize_pos(tr.get("type"))
        if i == 0:
            first_pos = pos
        if pos:
            parts.append(f"{t} ({pos})")
        else:
            parts.append(t)
    return "；".join(parts), first_pos


def build_examples(entry: dict[str, Any], max_examples: int = 3) -> list[str]:
    examples: list[str] = []
    for s in entry.get("sentences") or []:
        if not isinstance(s, dict):
            continue
        en = (s.get("sentence") or "").strip()
        cn = (s.get("translation") or "").strip()
        if en and cn:
            examples.append(f"{en} — {cn}")
        elif en:
            examples.append(en)
        if len(examples) >= max_examples:
            return examples
    for p in entry.get("phrases") or []:
        if not isinstance(p, dict):
            continue
        phrase = (p.get("phrase") or "").strip()
        tr = (p.get("translation") or "").strip()
        if phrase and tr:
            examples.append(f"{phrase} — {tr}")
        if len(examples) >= max_examples:
            break
    return examples


def build_detail_json(cn: str, entry: dict[str, Any]) -> str | None:
    examples = build_examples(entry)
    if not examples and cn == (entry.get("_primary_cn") or cn):
        return None
    detail = {"meaning": cn, "examples": examples, "etymology": None}
    return json.dumps(detail, ensure_ascii=False, separators=(",", ":"))


def is_valid_word_key(word_key: str) -> bool:
    if not word_key or len(word_key) > 191:
        return False
    return bool(WORD_KEY_RE.match(word_key))


def enrich_index(paths: list[str], base_url: str, force: bool) -> dict[str, dict[str, Any]]:
    """按 word 建索引，用于从 json-sentence 补音标/例句。"""
    index: dict[str, dict[str, Any]] = {}
    for rel in paths:
        path = download_file(base_url, rel, force)
        for entry in load_json_array(path):
            word = (entry.get("word") or "").strip()
            if not word:
                continue
            key = word.lower()
            if key not in index:
                index[key] = entry
    return index


def transform_entry(
    entry: dict[str, Any],
    book_id: int,
    sort_order: int,
    enrich: dict[str, dict[str, Any]] | None,
) -> dict[str, Any] | None:
    en = (entry.get("word") or "").strip()
    if not en:
        return None
    word_key = en.lower()
    if not is_valid_word_key(word_key):
        return None

    merged = dict(entry)
    if enrich and word_key in enrich:
        extra = enrich[word_key]
        for field in ("uk", "us", "sentences", "phrases"):
            if not merged.get(field) and extra.get(field):
                merged[field] = extra[field]
        if not merged.get("translations") and extra.get("translations"):
            merged["translations"] = extra["translations"]

    translations = merged.get("translations") or []
    if not translations:
        return None
    cn, pos = merge_cn(translations)
    if not cn:
        return None
    cn = truncate(cn, 512)
    ph = format_phonetic(merged.get("uk"), merged.get("us"))
    merged["_primary_cn"] = cn
    detail_json = build_detail_json(cn, merged)

    return {
        "book_id": book_id,
        "word_key": word_key,
        "en": en[:191],
        "cn": cn,
        "pos": pos,
        "ph": ph,
        "detail_json": detail_json,
        "sort_order": sort_order,
    }


def process_book(book_cfg: dict[str, Any], base_url: str, force: bool) -> tuple[list[dict], dict]:
    book_id = book_cfg["id"]
    seen: set[str] = set()
    rows: list[dict] = []
    stats = {"book_id": book_id, "name": book_cfg["name"], "loaded": 0, "kept": 0, "skipped_dup": 0, "skipped_invalid": 0}

    enrich_paths = book_cfg.get("enrich_from") or []
    enrich = enrich_index(enrich_paths, base_url, force) if enrich_paths else None

    sort_order = 0
    for rel in book_cfg["sources"]:
        path = download_file(base_url, rel, force)
        entries = load_json_array(path)
        stats["loaded"] += len(entries)
        for entry in entries:
            word_key = (entry.get("word") or "").strip().lower()
            if word_key in seen:
                stats["skipped_dup"] += 1
                continue
            row = transform_entry(entry, book_id, 0, enrich)
            if row is None:
                stats["skipped_invalid"] += 1
                continue
            seen.add(word_key)
            sort_order += 1
            row["sort_order"] = sort_order
            rows.append(row)
            stats["kept"] += 1

    stats["samples"] = rows[:5]
    return rows, stats


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def row_to_sql_values(row: dict[str, Any]) -> str:
    ph = f"'{sql_escape(row['ph'])}'" if row.get("ph") else "NULL"
    pos = f"'{sql_escape(row['pos'])}'" if row.get("pos") else "NULL"
    detail = f"'{sql_escape(row['detail_json'])}'" if row.get("detail_json") else "NULL"
    return (
        f"({row['book_id']}, '{sql_escape(row['word_key'])}', '{sql_escape(row['en'])}', "
        f"'{sql_escape(row['cn'])}', {pos}, {ph}, {detail}, {row['sort_order']})"
    )


def write_book_migration(
    book_cfg: dict[str, Any],
    rows: list[dict],
    batch_size: int,
) -> Path:
    book_id = book_cfg["id"]
    filename = book_cfg["migration"]
    out_path = MIGRATION_DIR / filename
    lines = [
        "-- ===========================================================================",
        f"-- 内置词书 book_id={book_id} {book_cfg['name']} — 真实词库（KyleBing ETL 生成）",
        f"-- 词条数: {len(rows)}",
        "-- ===========================================================================",
        "",
        f"INSERT INTO book_words (book_id, word_key, en, cn, pos, ph, detail_json, sort_order)",
        "VALUES",
    ]
    value_lines = [row_to_sql_values(r) for r in rows]
    chunks: list[str] = []
    for i in range(0, len(value_lines), batch_size):
        batch = value_lines[i : i + batch_size]
        chunk = "INSERT INTO book_words (book_id, word_key, en, cn, pos, ph, detail_json, sort_order)\nVALUES\n"
        chunk += ",\n".join(batch)
        chunk += ";\n"
        chunks.append(chunk)

    # 单文件多 INSERT 语句更清晰
    out_path.write_text(
        lines[0] + "\n" + "\n".join(lines[1:4]) + "\n\n" + "\n".join(chunks),
        encoding="utf-8",
    )
    print(f"  写入 {out_path} ({len(rows)} 条, {len(chunks)} 批)")
    return out_path


def write_meta_migration(
    config: dict[str, Any],
    all_stats: list[dict],
) -> Path:
    filename = config["meta_migration"]
    out_path = MIGRATION_DIR / filename
    counts = {s["book_id"]: s["kept"] for s in all_stats}
    lines = [
        "-- ===========================================================================",
        "-- 清除内置词书占位词条，更新 word_count",
        "-- ===========================================================================",
        "",
        "DELETE FROM book_words WHERE book_id IN (1, 2, 3);",
        "",
    ]
    for book_id in (1, 2, 3):
        wc = counts.get(book_id, 0)
        lines.append(f"UPDATE books SET word_count = {wc} WHERE id = {book_id};")
    lines.append("")
    out_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"  写入 {out_path}")
    return out_path


def main() -> None:
    parser = argparse.ArgumentParser(description="内置词书 ETL")
    parser.add_argument("--all", action="store_true", help="下载、清洗、生成 SQL")
    parser.add_argument("--force-download", action="store_true", help="强制重新下载")
    args = parser.parse_args()
    if not args.all:
        parser.print_help()
        sys.exit(0)

    config = load_config()
    base_url = config["base_url"]
    batch_size = config.get("batch_size", 500)
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    MIGRATION_DIR.mkdir(parents=True, exist_ok=True)

    all_stats: list[dict] = []
    for book_cfg in config["books"]:
        print(f"\n处理 book_id={book_cfg['id']} {book_cfg['name']}...")
        rows, stats = process_book(book_cfg, base_url, args.force_download)
        all_stats.append(stats)
        write_book_migration(book_cfg, rows, batch_size)
        processed_path = PROCESSED_DIR / f"book_{book_cfg['id']}.json"
        with open(processed_path, "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False, indent=2)
        print(f"  kept={stats['kept']} dup={stats['skipped_dup']} invalid={stats['skipped_invalid']}")

    write_meta_migration(config, all_stats)

    stats_path = PROCESSED_DIR / "stats.json"
    summary = {
        "books": [
            {k: v for k, v in s.items() if k != "samples"}
            | {"sample_words": [r["en"] for r in s.get("samples", [])]}
            for s in all_stats
        ]
    }
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print(f"\n统计写入 {stats_path}")
    total = sum(s["kept"] for s in all_stats)
    print(f"合计 {total} 条词条 → Flyway V3 迁移已生成")


if __name__ == "__main__":
    main()
