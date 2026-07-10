"""CLI：export-mysql / rules / llm / merge / report / emit。"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import emit as emit_mod
from . import export_mysql
from . import llm as llm_mod
from . import report as report_mod
from .io_jsonl import dump_cleaned, load_cleaned, load_raw
from .models import CleanedWord
from .rules import clean_many


def _cmd_export(args: argparse.Namespace) -> int:
    n = export_mysql.export_book_words(Path(args.output))
    print(f"exported {n} rows → {args.output}")
    return 0


def _cmd_rules(args: argparse.Namespace) -> int:
    raw = load_raw(Path(args.input))
    cleaned = clean_many(raw)
    n = dump_cleaned(Path(args.output), cleaned)
    ok = sum(1 for w in cleaned if w.quality == "ok")
    print(f"rules: {n} words, ok={ok} ({ok / n:.1%})" if n else "rules: 0")
    return 0


def _cmd_llm(args: argparse.Namespace) -> int:
    rows = load_cleaned(Path(args.input))
    if not llm_mod.llm_configured():
        print("LLM API Key 未配置，跳过调用（uncertain 原样写出）", file=sys.stderr)
    out = llm_mod.process_file(rows)
    n = dump_cleaned(Path(args.output), out)
    print(f"llm: wrote {n} → {args.output}")
    return 0


def _cmd_merge(args: argparse.Namespace) -> int:
    rules = {w.word_key: w for w in load_cleaned(Path(args.rules))}
    llm_rows = load_cleaned(Path(args.llm)) if args.llm else []
    for w in llm_rows:
        # LLM 结果覆盖同 key 的 uncertain
        prev = rules.get(w.word_key)
        if prev is None or prev.quality == "uncertain" or w.source.startswith("llm"):
            if w.quality != "uncertain" or w.source == "llm":
                w.source = "merge"
                rules[w.word_key] = w
            elif prev is None:
                rules[w.word_key] = w
    merged = list(rules.values())
    n = dump_cleaned(Path(args.output), merged)
    print(f"merge: {n} → {args.output}")
    return 0


def _cmd_report(args: argparse.Namespace) -> int:
    words = load_cleaned(Path(args.input))
    md = Path(args.output)
    js = Path(args.json) if args.json else md.with_suffix(".json")
    stats = report_mod.write_report(words, md, js)
    print(
        f"report: total={stats['total']} ok={stats['ok']} "
        f"uncertain={stats['uncertain']} reject={stats['reject']} "
        f"ok_ratio={stats['ok_ratio']:.1%}"
    )
    return 0


def _cmd_emit(args: argparse.Namespace) -> int:
    words = load_cleaned(Path(args.input))
    n = emit_mod.emit_sql(words, Path(args.output), only_ok=not args.all)
    print(f"emit: {n} ok words → {args.output}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="word-lexicon-cleaner")
    sub = p.add_subparsers(dest="cmd", required=True)

    e = sub.add_parser("export-mysql", help="从 Docker MySQL 导出 book_words")
    e.add_argument("-o", "--output", default="out/raw.jsonl")
    e.set_defaults(func=_cmd_export)

    r = sub.add_parser("rules", help="规则清洗")
    r.add_argument("-i", "--input", required=True)
    r.add_argument("-o", "--output", required=True)
    r.set_defaults(func=_cmd_rules)

    l = sub.add_parser("llm", help="LLM 处理 uncertain（无 Key 跳过）")
    l.add_argument("-i", "--input", required=True)
    l.add_argument("-o", "--output", required=True)
    l.set_defaults(func=_cmd_llm)

    m = sub.add_parser("merge", help="合并 rules + llm")
    m.add_argument("-r", "--rules", required=True)
    m.add_argument("-l", "--llm", default=None)
    m.add_argument("-o", "--output", required=True)
    m.set_defaults(func=_cmd_merge)

    rep = sub.add_parser("report", help="生成报告")
    rep.add_argument("-i", "--input", required=True)
    rep.add_argument("-o", "--output", default="out/report.md")
    rep.add_argument("--json", default=None)
    rep.set_defaults(func=_cmd_report)

    em = sub.add_parser("emit", help="生成 dict_* SQL 草稿")
    em.add_argument("-i", "--input", required=True)
    em.add_argument("-o", "--output", default="out/V13__dict_seed_draft.sql")
    em.add_argument("--all", action="store_true", help="包含非 ok（不推荐）")
    em.set_defaults(func=_cmd_emit)

    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
