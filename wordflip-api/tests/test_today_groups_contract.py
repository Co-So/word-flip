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

    def test_group_envelopes_and_page_cards_are_required(self) -> None:
        """列表包装和分页卡片数组必须始终存在，避免客户端空值分支。"""
        self.assertEqual(["groups"], self.schemas["GroupListResponse"]["required"])
        self.assertIn(
            "cards", self.schemas["GroupCardsResponse"]["allOf"][1]["required"]
        )
        self.assertIn(
            "cards", self.schemas["UnassignedCardsResponse"]["allOf"][1]["required"]
        )

    def test_mastery_descriptions_lock_authoritative_rule(self) -> None:
        """掌握口径必须同时锁定双轨默写和 FSRS 权威条件。"""
        serialized = yaml.safe_dump(
            {
                "today": self.schemas["TodayDashboard"],
                "group": self.schemas["GroupDetail"],
            },
            allow_unicode=True,
        )
        for fragment in (
            "dictation",
            "state='review'",
            "stability >= 80",
            "scheduled_days >= 30",
            "correct=true",
        ):
            self.assertIn(fragment, serialized)

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
            self.assertTrue(statuses.issubset(paths[path][method]["responses"]))


if __name__ == "__main__":
    unittest.main()
