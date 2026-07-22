"""按 manifest 下载并解压 ECDICT。"""

from __future__ import annotations

from pathlib import Path
import shutil
import tempfile
from urllib.request import urlopen
import zipfile

from .manifest import ArtifactManifest
from .verify import verify_artifacts


def download_artifacts(manifest: ArtifactManifest) -> bool:
    """仅在现有制品无法通过校验时重新下载，成功下载返回 true。"""
    try:
        verify_artifacts(manifest)
        return False
    except (FileNotFoundError, ValueError, zipfile.BadZipFile):
        pass

    manifest.zip_path.parent.mkdir(parents=True, exist_ok=True)
    manifest.sqlite_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=manifest.zip_path.parent) as directory:
        temporary_zip = Path(directory) / "ecdict.zip"
        with urlopen(manifest.download_url) as response, temporary_zip.open("wb") as target:
            shutil.copyfileobj(response, target)
        with zipfile.ZipFile(temporary_zip) as archive:
            member = next(
                (name for name in archive.namelist() if name.replace("\\", "/").endswith("stardict.db")),
                None,
            )
            if member is None:
                raise ValueError("下载包中未找到 stardict.db")
            temporary_database = Path(directory) / "stardict.db"
            with archive.open(member) as source, temporary_database.open("wb") as target:
                shutil.copyfileobj(source, target)
        shutil.move(str(temporary_zip), manifest.zip_path)
        shutil.move(str(temporary_database), manifest.sqlite_path)
    verify_artifacts(manifest)
    return True
