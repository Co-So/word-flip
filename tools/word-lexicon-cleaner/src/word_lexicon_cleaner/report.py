"""统计报告。"""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path

from .models import CleanedWord


def build_stats(words: list[CleanedWord]) -> dict:
    q = Counter(w.quality for w in words)
    total = len(words) or 1
    reasons = Counter(w.reason.split("|")[0] for w in words if w.quality != "ok")
    multi = sum(1 for w in words if len(w.senses) > 1)
    with_primary = sum(
        1
        for w in words
        if any(s.is_primary and s.quality == "ok" for s in w.senses)
    )
    return {
        "total": len(words),
        "ok": q.get("ok", 0),
        "uncertain": q.get("uncertain", 0),
        "reject": q.get("reject", 0),
        "ok_ratio": round(q.get("ok", 0) / total, 4),
        "multi_sense": multi,
        "with_ok_primary": with_primary,
        "top_reject_uncertain_reasons": reasons.most_common(20),
    }


def write_report(words: list[CleanedWord], md_path: Path, json_path: Path | None = None) -> dict:
    stats = build_stats(words)
    md_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# 词库清洗报告",
        "",
        f"- 总词数：{stats['total']}",
        f"- ok：{stats['ok']}（{stats['ok_ratio']:.1%}）",
        f"- uncertain：{stats['uncertain']}",
        f"- reject：{stats['reject']}",
        f"- 多义（≥2 sense）：{stats['multi_sense']}",
        f"- 有合格 primary：{stats['with_ok_primary']}",
        "",
        "## Top reasons（非 ok）",
        "",
    ]
    for reason, n in stats["top_reject_uncertain_reasons"]:
        lines.append(f"- `{reason}`：{n}")
    lines.append("")
    lines.append("> 目标：规则阶段 ok≥70%；reject 禁止入测验池（REQ-LEX-4）。")
    lines.append("")
    md_path.write_text("\n".join(lines), encoding="utf-8")
    if json_path:
        json_path.write_text(
            json.dumps(stats, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    return stats
