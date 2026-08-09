"""词书进度和偏好设置 OpenAPI 契约测试。"""

from pathlib import Path
import unittest

import yaml


def accepts_book_progress_instance(schema: dict, instance: object) -> bool:
    """按本契约所用的 OAS 3.0 对象/nullable 规则校验进度样例。"""
    if instance is None:
        return schema.get("type") == "object" and schema.get("nullable") is True
    if schema.get("type") != "object" or not isinstance(instance, dict):
        return False

    properties = schema["properties"]
    if any(field_name not in instance for field_name in schema["required"]):
        return False
    for field_name, field_schema in properties.items():
        value = instance.get(field_name)
        if not isinstance(value, int) or isinstance(value, bool):
            return False
        if value < field_schema["minimum"]:
            return False
        if "maximum" in field_schema and value > field_schema["maximum"]:
            return False
    return True


class BooksSettingsContractTest(unittest.TestCase):
    """锁定词书计划状态、进度和分组偏好的接口边界。"""

    @classmethod
    def setUpClass(cls) -> None:
        openapi_path = Path(__file__).resolve().parents[1] / "openapi.yaml"
        cls.spec = yaml.safe_load(openapi_path.read_text(encoding="utf-8"))
        cls.schemas = cls.spec["components"]["schemas"]

    def test_book_item_requires_plan_state_and_nullable_progress(self) -> None:
        """移除词书计划字段或可空进度会破坏词书列表的兼容 DTO。"""
        book_item = self.schemas["BookItem"]
        properties = book_item["properties"]
        self.assertTrue(
            {"planId", "planStatus", "progress"}.issubset(book_item["required"])
        )
        self.assertEqual("integer", properties["planId"]["type"])
        self.assertEqual("int64", properties["planId"]["format"])
        self.assertTrue(properties["planId"]["nullable"])
        self.assertEqual(
            ["active", "paused", "completed"], properties["planStatus"]["enum"]
        )
        self.assertTrue(properties["planStatus"]["nullable"])

    def test_book_list_response_requires_books_envelope(self) -> None:
        """200 响应缺少 books 会让 Web 列表 DTO 无法安全消费。"""
        self.assertEqual(["books"], self.schemas["BookListResponse"]["required"])

    def test_book_item_progress_directly_refs_nullable_book_progress(self) -> None:
        """progress 不可退回 allOf，否则 null 会与对象约束求交。"""
        progress = self.schemas["BookItem"]["properties"]["progress"]
        self.assertEqual(
            {"$ref": "#/components/schemas/BookProgress"}, progress
        )
        book_progress = self.schemas["BookProgress"]
        self.assertEqual("object", book_progress["type"])
        self.assertTrue(book_progress["nullable"])

    def test_book_progress_accepts_null_and_complete_valid_instance(self) -> None:
        """无计划的 null 与有计划的完整进度对象都必须符合契约。"""
        schema = self.schemas["BookProgress"]
        self.assertTrue(accepts_book_progress_instance(schema, None))
        self.assertTrue(
            accepts_book_progress_instance(
                schema,
                {
                    "masteredCount": 3,
                    "assignedCardCount": 10,
                    "completionPercent": 30,
                },
            )
        )

    def test_book_progress_rejects_missing_or_out_of_range_instance(self) -> None:
        """缺少必返进度字段或完成度越界的对象不得被接受。"""
        schema = self.schemas["BookProgress"]
        self.assertFalse(
            accepts_book_progress_instance(
                schema, {"masteredCount": 3, "assignedCardCount": 10}
            )
        )
        self.assertFalse(
            accepts_book_progress_instance(
                schema,
                {
                    "masteredCount": 3,
                    "assignedCardCount": 10,
                    "completionPercent": 101,
                },
            )
        )

    def test_book_item_plan_fields_describe_null_and_progress_presence(self) -> None:
        """缺失计划时的空值或有计划时的进度必返语义必须由契约说明。"""
        properties = self.schemas["BookItem"]["properties"]
        for field_name in ("planId", "planStatus"):
            description = properties[field_name]["description"]
            self.assertIn("没有关联学习计划", description)
            self.assertIn("null", description)
        progress_description = self.schemas["BookProgress"]["description"]
        self.assertIn("没有关联学习计划", progress_description)
        self.assertIn("null", progress_description)
        self.assertIn("有学习计划", progress_description)
        self.assertIn("必返", progress_description)

    def test_book_progress_has_exact_required_nonnegative_counts(self) -> None:
        """变更词书进度必返字段或允许负值会破坏进度展示。"""
        progress = self.schemas["BookProgress"]
        self.assertEqual(
            {"masteredCount", "assignedCardCount", "completionPercent"},
            set(progress["required"]),
        )
        for field_name in ("masteredCount", "assignedCardCount", "completionPercent"):
            self.assertEqual("integer", progress["properties"][field_name]["type"])
            self.assertEqual(0, progress["properties"][field_name]["minimum"])
        self.assertEqual(100, progress["properties"]["completionPercent"]["maximum"])

    def test_mastered_count_descriptions_share_authoritative_rule(self) -> None:
        """词书和统计的掌握数描述必须与全局权威口径一致。"""
        descriptions = (
            self.spec["info"]["description"],
            self.schemas["BookProgress"]["properties"]["masteredCount"][
                "description"
            ],
            self.schemas["StatsSummary"]["properties"]["masteredCount"][
                "description"
            ],
        )
        for description in descriptions:
            normalized = description.replace(" ", "").lower()
            self.assertIn("state='review'", normalized)
            self.assertIn("stability>=80", normalized)
            self.assertIn("最近", normalized)
            self.assertTrue("成功" in normalized or "correct=true" in normalized)
            self.assertIn("scheduled_days>=30", normalized)
            self.assertNotIn("stability>=30", normalized)

    def test_preferences_patch_reuses_group_setting_enums(self) -> None:
        """偏好更新若不复用分组枚举，会与设置响应的取值范围漂移。"""
        properties = self.schemas["PreferencesPatchRequest"]["properties"]
        self.assertEqual(
            {"$ref": "#/components/schemas/GroupSize"}, properties["groupSize"]
        )
        self.assertEqual(
            {"$ref": "#/components/schemas/GroupStrategy"},
            properties["groupStrategy"],
        )

    def test_preferences_patch_describes_transactional_group_append(self) -> None:
        """分组配置变化的追加副作用和事务边界必须写入契约。"""
        operation = self.spec["paths"]["/settings/preferences"]["patch"]
        description = operation["description"]
        self.assertIn("groupSize", description)
        self.assertIn("groupStrategy", description)
        self.assertIn("自动分组", description)
        self.assertIn("同一事务", description)

    def test_create_plan_describes_existing_plan_reuse_and_activation(self) -> None:
        """同书重试必须复用已有计划，避免客户端误判会创建重复历史。"""
        operation = self.spec["paths"]["/learning-plans"]["post"]
        description = operation["description"]
        self.assertIn("已存在", description)
        self.assertIn("复用", description)
        self.assertIn("激活", description)
        self.assertIn("不会创建重复计划", description)

    def test_book_item_keeps_android_compatible_fields_required(self) -> None:
        """移除既有的选中、词数或删除权限字段会破坏 Android 兼容性。"""
        required = set(self.schemas["BookItem"]["required"])
        self.assertTrue({"selected", "wordCount", "canDelete"}.issubset(required))


if __name__ == "__main__":
    unittest.main()
