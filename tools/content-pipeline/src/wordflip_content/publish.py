"""将已审核内容幂等发布到 WordFlip MySQL。"""

from __future__ import annotations

import json
from collections import Counter
from pathlib import Path
from urllib.parse import urlparse


def publish_content(dsn: str, build_dir: Path, source_version: str) -> dict[str, int]:
    """发布相关来源词条与 published 学习卡；重复执行只更新同一内容版本。"""
    try:
        import pymysql
    except ImportError as error:
        raise RuntimeError("publish 需要安装可选依赖 pymysql") from error

    parsed = urlparse(dsn)
    if parsed.scheme not in {"mysql", "mysql+pymysql"}:
        raise ValueError("DSN 必须使用 mysql://user:password@host:port/database")
    connection = pymysql.connect(
        host=parsed.hostname or "localhost",
        port=parsed.port or 3306,
        user=parsed.username,
        password=parsed.password,
        database=parsed.path.lstrip("/"),
        charset="utf8mb4",
        autocommit=False,
    )
    sources = _read_jsonl(build_dir / "source_entries.jsonl")
    cards = [row for row in _read_jsonl(build_dir / "learning_cards.jsonl") if row["status"] == "published"]
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO content_sources(code, name, license_name, homepage_url)
                VALUES ('ecdict', 'ECDICT', 'MIT', 'https://github.com/skywind3000/ECDICT')
                ON DUPLICATE KEY UPDATE name=VALUES(name), license_name=VALUES(license_name)
                """
            )
            cursor.execute("SELECT id FROM content_sources WHERE code='ecdict'")
            source_id = cursor.fetchone()[0]
            cursor.execute(
                """
                INSERT INTO source_revisions(source_id, version, manifest_json)
                VALUES (%s, %s, JSON_OBJECT('pipeline', 'wordflip-content'))
                ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)
                """,
                (source_id, source_version),
            )
            revision_id = cursor.lastrowid
            for row in sources:
                cursor.execute(
                    """
                    INSERT INTO lexemes(word_key, headword, language, status)
                    VALUES (%s, %s, 'en', 'active')
                    ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id), headword=VALUES(headword)
                    """,
                    (row["word_key"], row["raw_payload"]["word"]),
                )
                lexeme_id = cursor.lastrowid
                cursor.execute(
                    """
                    INSERT INTO source_entries(
                      revision_id, lexeme_id, source_key, raw_payload,
                      raw_definition, raw_translation, match_status
                    ) VALUES (%s, %s, %s, %s, %s, %s, 'matched')
                    ON DUPLICATE KEY UPDATE
                      id=LAST_INSERT_ID(id),
                      lexeme_id=VALUES(lexeme_id), raw_payload=VALUES(raw_payload),
                      raw_definition=VALUES(raw_definition), raw_translation=VALUES(raw_translation)
                    """,
                    (
                        revision_id, lexeme_id, row["source_key"],
                        json.dumps(row["raw_payload"], ensure_ascii=False),
                        row["raw_definition"], row["raw_translation"],
                    ),
                )
                source_entry_id = cursor.lastrowid
                cursor.execute("DELETE FROM dictionary_senses WHERE source_entry_id=%s", (source_entry_id,))
                for order, sense in enumerate(row.get("senses", [])):
                    cursor.execute(
                        """
                        INSERT INTO dictionary_senses(
                          source_entry_id, pos, cn, en_gloss, quality, sort_order, derivation_json
                        ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                        """,
                        (
                            source_entry_id, sense.get("pos"), sense.get("cn"), sense.get("en_gloss"),
                            sense.get("quality", "uncertain"), order,
                            json.dumps(sense.get("derivation"), ensure_ascii=False),
                        ),
                    )
                for form in row.get("word_forms", []):
                    cursor.execute(
                        """
                        INSERT INTO word_forms(lexeme_id, form, form_key, form_type)
                        VALUES (%s, %s, %s, %s)
                        ON DUPLICATE KEY UPDATE form=VALUES(form)
                        """,
                        (lexeme_id, form["form"], form["form_key"], form["form_type"]),
                    )

            counts = Counter(str(card["book_code"]) for card in cards)
            for book_code in counts:
                cursor.execute(
                    """
                    UPDATE learning_cards lc
                    JOIN book_items bi ON bi.id=lc.book_item_id
                    JOIN books b ON b.id=bi.book_id
                    SET lc.status='retired'
                    WHERE b.code=%s AND b.owner_user_id IS NULL AND lc.status='published'
                    """,
                    (book_code,),
                )
            for card in cards:
                _publish_card(cursor, card)
            for book_code, expected_count in counts.items():
                cursor.execute(
                    """
                    UPDATE books b
                    SET b.declared_count=%s,
                        b.published_card_count=(
                          SELECT COUNT(*)
                          FROM book_items bi
                          JOIN learning_cards lc ON lc.book_item_id=bi.id
                          WHERE bi.book_id=b.id AND lc.status='published'
                        ),
                        b.content_version=%s,
                        b.status='published'
                    WHERE b.code=%s AND b.owner_user_id IS NULL
                    """,
                    (expected_count, source_version, book_code),
                )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()
    return {"source_entries": len(sources), "published_cards": len(cards)}


def _publish_card(cursor: object, card: dict[str, object]) -> None:
    cursor.execute("SELECT id FROM books WHERE code=%s", (card["book_code"],))
    book = cursor.fetchone()
    if book is None:
        raise ValueError(f"词书尚未建档：{card['book_code']}")
    cursor.execute("SELECT id FROM lexemes WHERE word_key=%s AND language='en'", (card["word_key"],))
    lexeme = cursor.fetchone()
    if lexeme is None:
        raise ValueError(f"词形尚未发布：{card['word_key']}")
    cursor.execute(
        """
        INSERT INTO book_items(book_id, lexeme_id, sort_order, raw_headword, raw_meaning, status, metadata_json)
        VALUES (%s, %s, %s, %s, %s, 'ready', %s)
        ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id), sort_order=VALUES(sort_order), raw_meaning=VALUES(raw_meaning)
        """,
        (
            book[0], lexeme[0], card["sort_order"], card["headword"],
            card["senses"][0]["cn"], json.dumps(card["raw_book_item"], ensure_ascii=False),
        ),
    )
    item_id = cursor.lastrowid
    cursor.execute(
        "UPDATE learning_cards SET status='retired' WHERE book_item_id=%s AND status='published' AND version<>%s",
        (item_id, card["version"]),
    )
    cursor.execute(
        """
        INSERT INTO learning_cards(book_item_id, version, status, published_at, created_by)
        VALUES (%s, %s, 'published', CURRENT_TIMESTAMP(3), 'content-pipeline')
        ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id), status='published', published_at=CURRENT_TIMESTAMP(3)
        """,
        (item_id, card["version"]),
    )
    card_id = cursor.lastrowid
    cursor.execute("DELETE FROM learning_card_senses WHERE card_id=%s", (card_id,))
    for order, sense in enumerate(card["senses"]):
        cursor.execute(
            """
            INSERT INTO learning_card_senses(card_id, pos, cn, en_gloss, is_primary, quality, sort_order, provenance_json)
            VALUES (%s, %s, %s, NULL, %s, %s, %s, %s)
            """,
            (
                card_id, sense["pos"], sense["cn"], sense["is_primary"], sense["quality"], order,
                json.dumps({"kind": card["provenance"], "sourceEntryKey": card["source_entry_key"]}, ensure_ascii=False),
            ),
        )


def _read_jsonl(path: Path) -> list[dict[str, object]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
