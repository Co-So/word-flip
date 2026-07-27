import { screen, within } from "@testing-library/react";
import { renderAuthenticatedApp } from "@/test/renderApp";

test("分组列表只展示当前计划分组并进入详情", async () => {
  renderAuthenticatedApp("/groups");

  const group = await screen.findByRole("article", { name: "第 12 组 · 城市与环境" });
  expect(within(group).getByText("1 张学习卡")).toBeVisible();
  expect(within(group).getByRole("link", { name: "查看分组" })).toHaveAttribute("href", "/groups/group-12");
});

test("组详情按 cardId 展示每张卡的双 skill heatLevel", async () => {
  renderAuthenticatedApp("/groups/group-12");

  const row = await screen.findByRole("row", { name: /sustainable/ });
  expect(within(row).getByText("热力 2")).toBeVisible();
  expect(within(row).getByText("热力 0")).toBeVisible();
  expect(within(row).queryByRole("button", { name: /记得|模糊/ })).not.toBeInTheDocument();
});

test("从无效分组导航到有效分组后清除错误并恢复详情", async () => {
  const app = renderAuthenticatedApp("/groups/group-missing");

  expect(await screen.findByText("找不到指定分组")).toBeVisible();
  await app.navigate("/groups/group-12");

  expect(await screen.findByRole("heading", { name: "第 12 组 · 城市与环境" })).toBeVisible();
  expect(screen.getByRole("row", { name: /sustainable/ })).toBeVisible();
  expect(screen.queryByText("找不到指定分组")).not.toBeInTheDocument();
});
