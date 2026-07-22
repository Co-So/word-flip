"""WordFlip 内容管线命令入口。"""

from __future__ import annotations

import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path

from .build import build_content
from .download import download_artifacts
from .manifest import load_manifest
from .publish import publish_content
from .verify import verify_artifacts


def main(argv: list[str] | None = None) -> int:
    """执行 download、verify、build 或 publish。"""
    parser = argparse.ArgumentParser(description="WordFlip 可复现内容管线")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("download", help="缺失或校验失败时下载 ECDICT")
    subparsers.add_parser("verify", help="验证 ECDICT 完整性与覆盖统计")

    build_parser = subparsers.add_parser("build", help="按词书构建学习卡候选")
    build_parser.add_argument("--book", action="append", required=True, metavar="CODE=PATH")
    build_parser.add_argument("--output", type=Path, required=True)
    build_parser.add_argument("--overrides", type=Path)

    publish_parser = subparsers.add_parser("publish", help="幂等发布已审核内容")
    publish_parser.add_argument(
        "--dsn",
        default=os.environ.get("WORDFLIP_CONTENT_DSN"),
        help="MySQL DSN；建议通过 WORDFLIP_CONTENT_DSN 环境变量传入，避免命令历史泄密",
    )
    publish_parser.add_argument("--build-dir", type=Path, required=True)

    args = parser.parse_args(argv)
    manifest = load_manifest()
    if args.command == "download":
        print(json.dumps({"downloaded": download_artifacts(manifest)}, ensure_ascii=False))
    elif args.command == "verify":
        print(json.dumps(asdict(verify_artifacts(manifest)), ensure_ascii=False, indent=2))
    elif args.command == "build":
        books = _parse_books(args.book)
        print(json.dumps(asdict(build_content(manifest.sqlite_path, books, args.output, args.overrides)), ensure_ascii=False, indent=2))
    elif args.command == "publish":
        if not args.dsn:
            parser.error("publish 需要 --dsn 或 WORDFLIP_CONTENT_DSN")
        print(json.dumps(publish_content(args.dsn, args.build_dir, manifest.version), ensure_ascii=False, indent=2))
    return 0


def _parse_books(values: list[str]) -> dict[str, Path]:
    books: dict[str, Path] = {}
    for value in values:
        if "=" not in value:
            raise ValueError("--book 格式必须为 CODE=PATH")
        code, path = value.split("=", 1)
        books[code.strip()] = Path(path).resolve()
    return books


if __name__ == "__main__":
    raise SystemExit(main())
