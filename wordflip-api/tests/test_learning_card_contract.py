"""词书学习卡体系 OpenAPI 契约测试。"""

from pathlib import Path
import unittest

import yaml


class LearningCardContractTest(unittest.TestCase):
    """锁定学习计划、学习卡与来源资料的新 API 边界。"""

    @classmethod
    def setUpClass(cls) -> None:
        openapi_path = Path(__file__).resolve().parents[1] / "openapi.yaml"
        cls.spec = yaml.safe_load(openapi_path.read_text(encoding="utf-8"))

    def test_global_dictionary_switch_is_removed(self) -> None:
        """词典只作为资料来源，不再提供全局切换入口或字段。"""
        self.assertNotIn("/dicts", self.spec["paths"])
        serialized = yaml.safe_dump(self.spec, allow_unicode=True)
        self.assertNotIn("activeDictId", serialized)
        self.assertNotIn("dictId", serialized)

    def test_all_local_references_resolve(self) -> None:
        """所有本地组件引用都必须指向存在的节点。"""
        unresolved: list[str] = []

        def visit(value: object) -> None:
            if isinstance(value, dict):
                reference = value.get("$ref")
                if isinstance(reference, str) and reference.startswith("#/"):
                    current: object = self.spec
                    for segment in reference[2:].split("/"):
                        if not isinstance(current, dict) or segment not in current:
                            unresolved.append(reference)
                            break
                        current = current[segment]
                for child in value.values():
                    visit(child)
            elif isinstance(value, list):
                for child in value:
                    visit(child)

        visit(self.spec)
        self.assertEqual([], sorted(set(unresolved)))

    def test_learning_plan_endpoints_exist(self) -> None:
        """用户应通过学习计划选择唯一当前主词书。"""
        paths = self.spec["paths"]
        self.assertIn("post", paths["/learning-plans"])
        self.assertIn("get", paths["/learning-plans/current"])
        self.assertIn("patch", paths["/learning-plans/current"])

    def test_book_and_learning_card_endpoints_use_card_id(self) -> None:
        """词书内容、学习详情和测验必须以 cardId 为学习单位。"""
        paths = self.spec["paths"]
        self.assertIn("get", paths["/books/{bookId}/cards"])
        self.assertIn("get", paths["/learning/cards/{cardId}"])

        card = self.spec["components"]["schemas"]["LearningCard"]
        self.assertTrue({"cardId", "lexemeId", "bookId", "senses"}.issubset(card["required"]))

        quiz_question = self.spec["components"]["schemas"]["QuizQuestion"]
        self.assertTrue({"cardId", "lexemeId"}.issubset(quiz_question["required"]))

    def test_legacy_learning_paths_are_replaced_by_card_paths(self) -> None:
        """分组、待分配和媒体入口不得再以 wordKey 作为学习主键。"""
        paths = self.spec["paths"]
        for legacy_path in (
            "/words/unassigned",
            "/groups/{groupId}/words",
            "/words/{wordKey}/image",
            "/words/{wordKey}/stain",
        ):
            self.assertNotIn(legacy_path, paths)

        for card_path in (
            "/learning/cards/unassigned",
            "/groups/{groupId}/cards",
            "/learning/cards/{cardId}/image",
            "/learning/cards/{cardId}/stain",
        ):
            self.assertIn(card_path, paths)

    def test_settings_no_longer_selects_books(self) -> None:
        """设置接口只保留偏好更新，选书统一由学习计划负责。"""
        self.assertNotIn("put", self.spec["paths"]["/settings"])

    def test_answer_submission_requires_idempotency_request_id(self) -> None:
        """每次答案提交必须携带 UUID，且客户端不得传入 FSRS 评分。"""
        request = self.spec["components"]["schemas"]["SubmitAnswerRequest"]
        self.assertIn("requestId", request["required"])
        self.assertEqual("uuid", request["properties"]["requestId"]["format"])
        self.assertNotIn("rating", request["properties"])

    def test_learning_payloads_expose_card_and_lexeme_ids(self) -> None:
        """学习、分组、测验和媒体响应必须同时暴露卡片与词形标识。"""
        schemas = self.spec["components"]["schemas"]
        for schema_name in (
            "LearningCard",
            "QuizQuestion",
            "WordImageResponse",
            "WordStainResponse",
        ):
            self.assertTrue(
                {"cardId", "lexemeId"}.issubset(schemas[schema_name]["required"]),
                schema_name,
            )

    def test_study_and_quiz_results_use_card_vocabulary(self) -> None:
        """学习日志和错题结果不得退回 word 级学习标识。"""
        schemas = self.spec["components"]["schemas"]
        study_properties = schemas["StudySessionReportRequest"]["properties"]
        self.assertIn("cardsViewed", study_properties)
        self.assertNotIn("wordsViewed", study_properties)
        result_properties = schemas["QuizResult"]["properties"]
        self.assertIn("wrongCards", result_properties)
        self.assertNotIn("wrongWords", result_properties)
        wrong_card = result_properties["wrongCards"]["items"]
        self.assertTrue({"cardId", "lexemeId"}.issubset(wrong_card["required"]))

    def test_word_lookup_returns_current_card_and_source_materials(self) -> None:
        """查词返回当前词书学习卡以及可展开的来源资料。"""
        operation = self.spec["paths"]["/words/{wordKey}"]["get"]
        parameters = operation["parameters"]
        self.assertEqual([{"$ref": "#/components/parameters/WordKey"}], parameters)

        response_schema = self.spec["components"]["schemas"]["WordLookupResponse"]
        self.assertTrue(
            {"lexemeId", "wordKey", "en", "currentCard", "sourceMaterials"}.issubset(
                response_schema["required"]
            )
        )


if __name__ == "__main__":
    unittest.main()
