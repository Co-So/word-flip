"""今日与分组 OpenAPI 契约测试。"""

from pathlib import Path
import unittest

import yaml


class TodayGroupsContractTest(unittest.TestCase):
    """锁定 Today 仪表盘和分组 API 的稳定 DTO 边界。"""

    @classmethod
    def setUpClass(cls) -> None:
        path = Path(__file__).resolve().parents[1] / "openapi.yaml"
        cls.spec = yaml.safe_load(path.read_text(encoding="utf-8"))
        cls.schemas = cls.spec["components"]["schemas"]

    def test_today_requires_complete_dashboard(self) -> None:
        """缺少仪表盘必返字段或待办分类会破坏 Today 页面。"""
        dashboard = self.schemas["TodayDashboard"]
        self.assertTrue(
            {
                "date",
                "streakDays",
                "stats",
                "tasks",
                "recommendedStudy",
                "recentGroups",
            }.issubset(dashboard["required"])
        )
        tasks = dashboard["properties"]["tasks"]
        self.assertEqual(["newWords", "dueReview", "quiz"], tasks["required"])
        self.assertIs(
            dashboard["properties"]["recommendedStudy"]["nullable"], True
        )

        sources = self.schemas["TodayTask"]["properties"]["sources"]["items"]
        self.assertEqual(
            ["groupId", "groupName", "count"], sources["required"]
        )

    def test_group_envelopes_and_page_cards_are_required(self) -> None:
        """列表包装和分页卡片数组必须始终存在，避免客户端空值分支。"""
        self.assertEqual(["groups"], self.schemas["GroupListResponse"]["required"])
        self.assertIn(
            "cards", self.schemas["GroupCardsResponse"]["allOf"][1]["required"]
        )
        self.assertIn(
            "cards", self.schemas["UnassignedCardsResponse"]["allOf"][1]["required"]
        )

    def test_recommended_study_object_requires_complete_payload(self) -> None:
        """推荐存在时必须完整返回 Web 展示与跳转依赖的四个字段。"""
        recommended = self.schemas["TodayDashboard"]["properties"][
            "recommendedStudy"
        ]
        self.assertEqual(
            ["groupId", "groupName", "wordCount", "reason"],
            recommended["required"],
        )

    def test_mastery_descriptions_lock_authoritative_rule(self) -> None:
        """今日和分组都必须分别说明完整且权威的掌握条件。"""
        descriptions = (
            self.schemas["TodayDashboard"]["properties"]["stats"]["properties"][
                "masteredCount"
            ]["description"],
            self.schemas["GroupDetail"]["properties"]["progress"]["description"],
        )
        for description in descriptions:
            for fragment in (
                "dictation",
                "state='review'",
                "stability >= 80",
                "scheduled_days >= 30",
                "最近一次有效测验 correct=true",
            ):
                self.assertIn(fragment, description)

    def test_protected_operations_declare_expected_errors(self) -> None:
        """当前计划受保护端点必须声明认证、计划和参数错误语义。"""
        paths = self.spec["paths"]
        expected = {
            ("/today", "get"): {"200", "401", "404"},
            ("/groups", "get"): {"200", "400", "401", "404"},
            ("/groups/{groupId}", "get"): {"200", "401", "404"},
            ("/groups/{groupId}/cards", "get"): {"200", "400", "401", "404"},
            ("/learning/cards/unassigned", "get"): {"200", "400", "401", "404"},
            ("/groups/custom", "post"): {"201", "400", "401", "404", "409"},
        }
        for (path, method), statuses in expected.items():
            self.assertEqual(statuses, set(paths[path][method]["responses"]))


if __name__ == "__main__":
    unittest.main()
