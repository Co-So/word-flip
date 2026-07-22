"""WordFlip 内容管线测试。"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import zipfile

from wordflip_content.build import _load_book, build_content
from wordflip_content.manifest import ArtifactManifest, load_manifest
from wordflip_content.verify import verify_artifacts


class ContentPipelineTest(unittest.TestCase):
    """验证固定来源、完整性校验与按词书抽取行为。"""

    def test_official_manifest_is_pinned(self) -> None:
        manifest = load_manifest()
        self.assertEqual("1.0.28", manifest.version)
        self.assertEqual(
            "EA01F76A3B3351021CE47077E89234465CC9441C8793054495320D06C0C3F3F6",
            manifest.zip_sha256,
        )
        self.assertEqual(
            "2B5B40C2BDBA04DA0A51C8672E090F166987D5D895F32EB3FBFC5A516455FC75",
            manifest.sqlite_sha256,
        )
        self.assertEqual(3_402_564, manifest.entry_count)

    def test_verify_checks_zip_sqlite_schema_and_rows(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "stardict.db"
            self._create_database(database)
            archive = root / "ecdict.zip"
            with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
                bundle.write(database, "stardict.db")

            manifest = ArtifactManifest(
                version="fixture",
                download_url="https://example.invalid/ecdict.zip",
                license="MIT",
                zip_path=archive,
                sqlite_path=database,
                zip_sha256=self._sha256(archive),
                sqlite_sha256=self._sha256(database),
                zip_size=archive.stat().st_size,
                sqlite_size=database.stat().st_size,
                entry_count=2,
            )

            report = verify_artifacts(manifest)

            self.assertTrue(report.ok)
            self.assertEqual("ok", report.sqlite_quick_check)
            self.assertEqual(2, report.entry_count)
            self.assertEqual(2, report.translated_count)

    def test_build_keeps_raw_entry_and_uses_book_meaning_first(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "stardict.db"
            self._create_database(database)
            book = root / "ielts.json"
            book.write_text(
                json.dumps(
                    {
                        "words": [
                            {"en": "Apple", "cn": "苹果", "pos": "n."},
                            {"en": "run"},
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            output = root / "out"
            report = build_content(database, {"ielts": book}, output)

            cards = self._read_jsonl(output / "learning_cards.jsonl")
            raw_entries = self._read_jsonl(output / "source_entries.jsonl")
            self.assertEqual(2, report.total_items)
            self.assertEqual("苹果", cards[0]["senses"][0]["cn"])
            self.assertEqual("词书原始考义", cards[0]["provenance"])
            self.assertEqual("跑；运行", cards[1]["senses"][0]["cn"])
            self.assertEqual("ECDICT 候选", cards[1]["provenance"])
            self.assertIn("raw_translation", raw_entries[0])
            self.assertIn("raw_payload", raw_entries[0])
            self.assertEqual("苹果", raw_entries[0]["senses"][0]["cn"])
            self.assertEqual("fruit", raw_entries[0]["senses"][0]["en_gloss"])
            self.assertEqual("n", raw_entries[0]["senses"][0]["pos"])
            self.assertEqual("ecdict_raw_fields", raw_entries[0]["senses"][0]["derivation"]["rule"])

    def test_build_extracts_ecdict_word_forms(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "stardict.db"
            self._create_database(database)
            database_connection = sqlite3.connect(database)
            database_connection.execute("UPDATE stardict SET exchange='s:apples/3:apples' WHERE word='apple'")
            database_connection.commit()
            database_connection.close()
            book = root / "ielts.json"
            book.write_text('[{"en":"apple","cn":"苹果"}]', encoding="utf-8")

            output = root / "out"
            build_content(database, {"ielts": book}, output)

            entry = self._read_jsonl(output / "source_entries.jsonl")[0]
            self.assertEqual(
                [
                    {"form": "apples", "form_key": "apples", "form_type": "plural"},
                    {"form": "apples", "form_key": "apples", "form_type": "third_person"},
                ],
                entry["word_forms"],
            )

    def test_loads_real_book_rows_from_legacy_flyway_sql_without_executing_it(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "book.sql"
            path.write_text(
                """
                INSERT INTO book_words (...) VALUES
                (1, 'can''t', 'can''t', '不能', 'v.', '/kænt/', '{"meaning":"不能"}', 2),
                (1, 'apple', 'apple', '苹果', 'n.', NULL, NULL, 1);
                """,
                encoding="utf-8",
            )

            rows = _load_book(path)

            self.assertEqual(["apple", "can't"], [row["word_key"] for row in rows])
            self.assertEqual("不能", rows[1]["cn"])

    @staticmethod
    def _create_database(path: Path) -> None:
        connection = sqlite3.connect(path)
        connection.execute(
            """
            CREATE TABLE stardict (
              id INTEGER PRIMARY KEY, word TEXT, sw TEXT, phonetic TEXT,
              definition TEXT, translation TEXT, pos TEXT, collins INTEGER,
              oxford INTEGER, tag TEXT, bnc INTEGER, frq INTEGER,
              exchange TEXT, detail TEXT, audio TEXT
            )
            """
        )
        connection.executemany(
            "INSERT INTO stardict VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (1, "apple", "apple", "ˈæpl", "fruit", "苹果", "n", 3, 1, "", 10, 20, "", "{}", ""),
                (2, "run", "run", "rʌn", "move fast", "跑；运行", "v", 5, 1, "", 30, 40, "", "{}", ""),
            ],
        )
        connection.commit()
        connection.close()

    @staticmethod
    def _sha256(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest().upper()

    @staticmethod
    def _read_jsonl(path: Path) -> list[dict[str, object]]:
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]


if __name__ == "__main__":
    unittest.main()
