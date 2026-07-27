import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderAuthenticatedApp } from "@/test/renderApp";

test("空格翻面，方向键切词并在切词后恢复正面", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");

  expect(await screen.findByRole("heading", { name: "sustainable" })).toBeVisible();
  expect(screen.getByText("/səˈsteɪnəbl/")).toBeVisible();
  expect(screen.getByRole("img", { name: "树木与城市建筑交叠的可持续发展图像占位" })).toBeVisible();
  expect(screen.getByText("听写热力 2 · 只读")).toBeVisible();
  expect(screen.getByRole("heading", { name: "18 / 25 WORDS" })).toBeVisible();
  expect(screen.getByRole("heading", { name: "队列摘要" })).toBeVisible();
  expect(screen.queryByRole("navigation", { name: "主导航" })).not.toBeInTheDocument();
  await user.keyboard(" ");
  expect(screen.getByText("可持续的")).toBeVisible();
  expect(screen.getByText("The city needs a sustainable transport plan.")).toBeVisible();

  await user.keyboard("{ArrowRight}");
  expect(await screen.findByRole("heading", { name: "infrastructure" })).toBeVisible();
  expect(screen.queryByText("基础设施")).not.toBeInTheDocument();

  await user.keyboard("{ArrowLeft}");
  expect(await screen.findByRole("heading", { name: "sustainable" })).toBeVisible();
});

test("完成学习只回放固定会话和今日快照，不改变 cardId 下的双轨记忆", async () => {
  const user = userEvent.setup();
  const app = renderAuthenticatedApp("/study/study-demo");
  const beforeState = app.store.read();
  const beforeProgress = structuredClone(
    beforeState.planStates["plan-core"].cards.byCardId["card-sustainable"].progress
  );
  const beforeToday = structuredClone(beforeState.planStates["plan-core"].today);

  await user.click(await screen.findByRole("button", { name: "完成学习" }));

  expect(await screen.findByRole("heading", { name: "本次学习已完成" })).toBeVisible();
  await waitFor(() => {
    const afterPlan = app.store.read().planStates["plan-core"];
    expect(afterPlan.study.sessions["study-demo"].status).toBe("completed");
    expect(afterPlan.today).toEqual({
      ...beforeToday,
      reviewedCount: 25,
      completionRate: 100,
      recentStudy: [
        {
          cardId: "card-infrastructure",
          headword: "infrastructure",
          definition: "基础设施",
          reviewedAtLabel: "刚刚"
        },
        ...beforeToday.recentStudy
      ]
    });
    expect(afterPlan.cards.byCardId["card-sustainable"].progress).toEqual(beforeProgress);
  });
  expect(screen.getByRole("link", { name: "返回 Today" })).toHaveAttribute("href", "/today");
  expect(screen.getByRole("link", { name: "进入 Quiz" })).toHaveAttribute("href", "/quiz");
});

test("不存在的学习会话显示返回 Today 的恢复动作", async () => {
  renderAuthenticatedApp("/study/missing-session");

  expect(await screen.findByText("找不到学习会话")).toBeVisible();
  expect(screen.getByRole("link", { name: "返回 Today" })).toHaveAttribute("href", "/today");
});

test("输入控件聚焦时不触发学习快捷键", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  expect(await screen.findByRole("heading", { name: "sustainable" })).toBeVisible();

  const input = document.createElement("input");
  input.setAttribute("aria-label", "测试输入");
  document.body.append(input);
  input.focus();
  await user.keyboard(" ");

  expect(screen.queryByText("可持续的")).not.toBeInTheDocument();
  input.remove();
});

test("点击与聚焦卡片后的 Enter 各翻转一次", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const card = await screen.findByRole("button", { name: "翻转 sustainable 学习卡" });

  await user.click(card);
  expect(screen.getByText("可持续的")).toBeVisible();
  await user.keyboard("{Enter}");

  expect(screen.queryByText("可持续的")).not.toBeInTheDocument();
});
