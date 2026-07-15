"""单词详情查询 OpenAPI 契约测试。"""

from pathlib import Path
import unittest

import yaml


class WordLookupContractTest(unittest.TestCase):
    """确保详情抽屉切换词典所需端点与响应字段不会漂移。"""

    @classmethod
    def setUpClass(cls) -> None:
        openapi_path = Path(__file__).resolve().parents[1] / "openapi.yaml"
        cls.spec = yaml.safe_load(openapi_path.read_text(encoding="utf-8"))

    def test_word_lookup_endpoint_matches_server_response(self) -> None:
        """查询端点应声明词典参数，并返回完整词典元信息。"""
        operation = self.spec["paths"]["/words/{wordKey}"]["get"]
        parameters = operation["parameters"]

        self.assertIn(
            {"$ref": "#/components/parameters/WordKey"},
            parameters,
        )
        dict_parameter = next(item for item in parameters if item.get("name") == "dictId")
        self.assertEqual("query", dict_parameter["in"])
        self.assertFalse(dict_parameter.get("required", False))
        self.assertEqual(
            "#/components/schemas/WordLookupResponse",
            operation["responses"]["200"]["content"]["application/json"]["schema"]["$ref"],
        )

        response_schema = self.spec["components"]["schemas"]["WordLookupResponse"]
        self.assertEqual(
            {"wordKey", "en", "senses", "dictId", "dictName", "dictLocale"},
            set(response_schema["required"]),
        )
        self.assertEqual(
            "#/components/schemas/Sense",
            response_schema["properties"]["senses"]["items"]["$ref"],
        )


if __name__ == "__main__":
    unittest.main()
