"""ECDICT ZIP 与 SQLite 完整性校验。"""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path
import sqlite3
import zipfile

from .manifest import ArtifactManifest


EXPECTED_COLUMNS = {
    "id", "word", "sw", "phonetic", "definition", "translation", "pos",
    "collins", "oxford", "tag", "bnc", "frq", "exchange", "detail", "audio",
}


@dataclass(frozen=True)
class VerificationReport:
    """记录完整性与内容覆盖校验结果。"""

    ok: bool
    sqlite_quick_check: str
    entry_count: int
    translated_count: int
    zip_sha256: str
    sqlite_sha256: str


def sha256_file(path: Path) -> str:
    """流式计算大文件 SHA-256，避免一次性读入内存。"""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def verify_artifacts(manifest: ArtifactManifest) -> VerificationReport:
    """验证文件大小、摘要、ZIP、SQLite、表结构和词条数量。"""
    if not manifest.zip_path.is_file() or not manifest.sqlite_path.is_file():
        raise FileNotFoundError("ECDICT ZIP 或 SQLite 不存在，请先执行 download")
    if manifest.zip_path.stat().st_size != manifest.zip_size:
        raise ValueError("ECDICT ZIP 文件大小不匹配")
    if manifest.sqlite_path.stat().st_size != manifest.sqlite_size:
        raise ValueError("ECDICT SQLite 文件大小不匹配")

    zip_digest = sha256_file(manifest.zip_path)
    sqlite_digest = sha256_file(manifest.sqlite_path)
    if zip_digest != manifest.zip_sha256:
        raise ValueError("ECDICT ZIP SHA-256 不匹配")
    if sqlite_digest != manifest.sqlite_sha256:
        raise ValueError("ECDICT SQLite SHA-256 不匹配")

    with zipfile.ZipFile(manifest.zip_path) as archive:
        damaged = archive.testzip()
        if damaged is not None:
            raise ValueError(f"ECDICT ZIP 内文件损坏：{damaged}")

    connection = sqlite3.connect(f"file:{manifest.sqlite_path}?mode=ro", uri=True)
    try:
        quick_check = str(connection.execute("PRAGMA quick_check").fetchone()[0])
        if quick_check.lower() != "ok":
            raise ValueError(f"SQLite quick_check 失败：{quick_check}")
        columns = {row[1] for row in connection.execute("PRAGMA table_info(stardict)")}
        missing = EXPECTED_COLUMNS - columns
        if missing:
            raise ValueError(f"stardict 缺少字段：{sorted(missing)}")
        entry_count = int(connection.execute("SELECT COUNT(*) FROM stardict").fetchone()[0])
        unique_count = int(connection.execute("SELECT COUNT(DISTINCT word) FROM stardict").fetchone()[0])
        translated_count = int(
            connection.execute(
                "SELECT COUNT(*) FROM stardict WHERE TRIM(COALESCE(translation, '')) <> ''"
            ).fetchone()[0]
        )
    finally:
        connection.close()

    if entry_count != manifest.entry_count or unique_count != manifest.entry_count:
        raise ValueError(
            f"ECDICT 词条数不匹配：rows={entry_count}, unique={unique_count}, expected={manifest.entry_count}"
        )
    return VerificationReport(
        ok=True,
        sqlite_quick_check=quick_check,
        entry_count=entry_count,
        translated_count=translated_count,
        zip_sha256=zip_digest,
        sqlite_sha256=sqlite_digest,
    )
