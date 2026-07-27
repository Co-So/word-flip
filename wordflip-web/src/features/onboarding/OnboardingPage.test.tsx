import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createDemoState } from "@/data/mock/createDemoState";
import { renderStateApp } from "@/test/renderApp";

function renderOnboardingApp() {
  const initialState = createDemoState("configured");
  initialState.books.activePlanId = null;
  initialState.books.plans = [];
  initialState.planStates = {};
  return renderStateApp(initialState, "/onboarding");
}

test("首次设置选择主词书和每组 30 词后进入今日", async () => {
  const user = userEvent.setup();
  const { store } = renderOnboardingApp();

  await user.click(await screen.findByRole("radio", { name: "雅思核心词汇" }));
  const groupSize = screen.getByLabelText("每组单词数");
  expect(groupSize).toHaveValue("20");
  await user.selectOptions(groupSize, "30");
  await user.click(screen.getByRole("button", { name: "完成设置" }));

  expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
  expect(store.read().books.activePlanId).not.toBeNull();
});

test("首次设置只允许需求定义的四种分组大小", async () => {
  renderOnboardingApp();

  expect(await screen.findByLabelText("每组单词数")).toHaveTextContent("10 词每组20 词每组30 词每组50 词每组");
  expect(screen.queryByRole("option", { name: /25/ })).not.toBeInTheDocument();
});
