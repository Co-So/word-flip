import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createDemoState } from "@/data/mock/createDemoState";
import { renderStateApp } from "@/test/renderApp";

test("无历史计划的词书回放固定快照并原子激活", async () => {
  const user = userEvent.setup();
  const initialState = createDemoState("configured");
  initialState.planStates["plan-core"].groups.items.push({
    groupId: "group-history-sentinel",
    name: "历史 sentinel",
    cardIds: []
  });
  const app = renderStateApp(initialState, "/books");

  const ieltsBook = await screen.findByRole("article", { name: "雅思核心词汇" });
  await user.click(within(ieltsBook).getByRole("button", { name: "开始学习" }));

  const state = app.store.read();
  expect(state.books.activePlanId).toBe("plan-ielts");
  expect(state.books.plans.filter((plan) => plan.planId === "plan-ielts")).toHaveLength(1);
  expect(state.planStates["plan-ielts"].today.dueCount).toBe(20);
  expect(state.planStates["plan-core"].groups.items).toContainEqual({
    groupId: "group-history-sentinel",
    name: "历史 sentinel",
    cardIds: []
  });
});

test("切换计划只改变当前资源视图且切回后历史分组与进度仍在", async () => {
  const user = userEvent.setup();
  const initialState = createDemoState("configured");
  initialState.planStates["plan-core"].groups.items.push({
    groupId: "group-history-sentinel",
    name: "历史 sentinel",
    cardIds: ["card-sustainable"]
  });
  initialState.planStates["plan-core"].cards.byCardId["card-sustainable"].progress.choice.heatLevel = 3;
  const app = renderStateApp(initialState, "/books");

  const advancedBook = await screen.findByRole("article", { name: "进阶词汇" });
  await user.click(within(advancedBook).getByRole("button", { name: "切换到此计划" }));
  await app.navigate("/groups");
  expect(await screen.findByText("进阶复习")).toBeVisible();
  expect(screen.queryByText("历史 sentinel")).not.toBeInTheDocument();

  await app.navigate("/books");
  const coreBook = await screen.findByRole("article", { name: "核心词汇" });
  await user.click(within(coreBook).getByRole("button", { name: "切换到此计划" }));
  await app.navigate("/groups/group-history-sentinel");

  expect(await screen.findByRole("heading", { name: "历史 sentinel" })).toBeVisible();
  const sustainable = screen.getByRole("row", { name: /sustainable/ });
  expect(within(sustainable).getByText("热力 3")).toBeVisible();
});

test("词书详情只为当前计划展示预计算进度", async () => {
  renderStateApp(createDemoState("configured"), "/books/book-core");

  expect(await screen.findByRole("heading", { name: "核心词汇" })).toBeVisible();
  expect(screen.getByText("42%")).toBeVisible();
  expect(screen.getByText("126 / 300 张已掌握")).toBeVisible();
});
