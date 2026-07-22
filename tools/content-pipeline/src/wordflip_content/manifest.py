"""受版本控制的 ECDICT 来源清单。"""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path


PIPELINE_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST_PATH = PIPELINE_ROOT / "manifest.json"


@dataclass(frozen=True)
class ArtifactManifest:
    """描述一次可复现的 ECDICT 原始制品。"""

    version: str
    download_url: str
    license: str
    zip_path: Path
    sqlite_path: Path
    zip_sha256: str
    sqlite_sha256: str
    zip_size: int
    sqlite_size: int
    entry_count: int


def load_manifest(path: Path = DEFAULT_MANIFEST_PATH) -> ArtifactManifest:
    """读取 manifest，并把相对制品路径解析为绝对路径。"""
    data = json.loads(path.read_text(encoding="utf-8"))
    root = path.parent
    return ArtifactManifest(
        version=str(data["version"]),
        download_url=str(data["download_url"]),
        license=str(data["license"]),
        zip_path=(root / data["zip_path"]).resolve(),
        sqlite_path=(root / data["sqlite_path"]).resolve(),
        zip_sha256=str(data["zip_sha256"]).upper(),
        sqlite_sha256=str(data["sqlite_sha256"]).upper(),
        zip_size=int(data["zip_size"]),
        sqlite_size=int(data["sqlite_size"]),
        entry_count=int(data["entry_count"]),
    )
