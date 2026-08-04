import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, vi } from "vitest";
import { createDemoState } from "@/data/mock/createDemoState";
import {
  renderAuthenticatedApp,
  renderScenarioApp,
  renderStateApp
} from "@/test/renderApp";

afterEach(() => {
  vi.unstubAllGlobals();
});

test("学习会话以卡片墙同时展示全部词汇", async () => {
  renderAuthenticatedApp("/study/study-demo");

  expect(await screen.findByRole("heading", { name: "sustainable" })).toBeVisible();
  expect(screen.getByRole("heading", { name: "infrastructure" })).toBeVisible();
  expect(screen.getByRole("heading", { name: "urban" })).toBeVisible();
  expect(screen.getByTestId("study-card-wall")).toBeVisible();
  expect(screen.getByText("3 张词汇卡")).toBeVisible();
  expect(screen.queryByRole("button", { name: "上一词" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "下一词" })).not.toBeInTheDocument();
  expect(screen.queryByText("1 / 3")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "18 / 25 WORDS" })).toBeVisible();
  expect(screen.getByRole("heading", { name: "队列摘要" })).toBeVisible();
  expect(screen.queryByRole("navigation", { name: "主导航" })).not.toBeInTheDocument();
});

test("每张卡片独立翻面且不会重置其他卡片", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const sustainable = await screen.findByRole("button", {
    name: /sustainable 学习卡/
  });
  const infrastructure = screen.getByRole("button", {
    name: /infrastructure 学习卡/
  });

  await user.click(sustainable);
  expect(screen.getByText("可持续的")).toBeVisible();
  expect(screen.queryByText("基础设施")).not.toBeInTheDocument();
  expect(sustainable).toHaveAccessibleName("将 sustainable 学习卡翻回正面");

  await user.click(infrastructure);
  expect(screen.getByText("可持续的")).toBeVisible();
  expect(screen.getByText("基础设施")).toBeVisible();
});

test("点击学习卡片时按照发音开关决定是否朗读当前词", async () => {
  const user = userEvent.setup();
  const spoken: Array<{ lang: string; rate: number; text: string }> = [];

  class FakeSpeechSynthesisUtterance {
    lang = "";
    rate = 1;

    constructor(readonly text: string) {}
  }

  vi.stubGlobal("SpeechSynthesisUtterance", FakeSpeechSynthesisUtterance);
  vi.stubGlobal("speechSynthesis", {
    cancel: vi.fn(),
    speak: (utterance: FakeSpeechSynthesisUtterance) => spoken.push(utterance)
  });
  const enabledApp = renderAuthenticatedApp("/study/study-demo");
  const card = await screen.findByRole("button", {
    name: "翻转 sustainable 学习卡查看释义"
  });

  await user.click(card);
  await user.click(card);

  expect(spoken).toEqual([
    { lang: "en-US", rate: 1, text: "sustainable" },
    { lang: "en-US", rate: 1, text: "sustainable" }
  ]);

  enabledApp.unmount();
  const disabledState = createDemoState("configured");
  disabledState.settings.soundEnabled = false;
  renderStateApp(disabledState, "/study/study-demo");

  await user.click(await screen.findByRole("button", {
    name: "翻转 sustainable 学习卡查看释义"
  }));
  expect(spoken).toHaveLength(2);
});

test("左右方向键在卡片墙中移动焦点并停在边界", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const cards = await screen.findAllByRole("button", { name: /学习卡/ });

  cards[0].focus();
  await user.keyboard("{ArrowRight}");
  expect(cards[1]).toHaveFocus();
  await user.keyboard("{ArrowLeft}");
  expect(cards[0]).toHaveFocus();
  await user.keyboard("{ArrowLeft}");
  expect(cards[0]).toHaveFocus();

  const lastCard = cards[cards.length - 1];
  lastCard.focus();
  await user.keyboard("{ArrowRight}");
  expect(lastCard).toHaveFocus();
});

test("完成学习只回放固定会话和今日快照，不改变 cardId 下的双轨记忆", async () => {
  const user = userEvent.setup();
  const app = renderAuthenticatedApp("/study/study-demo");
  const beforeState = app.store.read();
  const beforeProgressByCardId = Object.fromEntries(
    Object.entries(beforeState.planStates["plan-core"].cards.byCardId).map(
      ([cardId, card]) => [cardId, structuredClone(card.progress)]
    )
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
    expect(
      Object.fromEntries(
        Object.entries(afterPlan.cards.byCardId).map(
          ([cardId, card]) => [cardId, card.progress]
        )
      )
    ).toEqual(beforeProgressByCardId);
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
  const card = await screen.findByRole("button", {
    name: "翻转 sustainable 学习卡查看释义"
  });

  await user.click(card);
  expect(screen.getByText("可持续的")).toBeVisible();
  await user.keyboard("{Enter}");

  expect(screen.queryByText("可持续的")).not.toBeInTheDocument();
});

test("完成按钮聚焦时 Enter 使用原生激活并完成会话", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const complete = await screen.findByRole("button", { name: "完成学习" });

  complete.focus();
  await user.keyboard("{Enter}");

  expect(await screen.findByRole("heading", { name: "本次学习已完成" })).toBeVisible();
});

test("退出按钮的标准键盘激活不被学习快捷键拦截", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const exit = await screen.findByRole("button", { name: "退出" });
  exit.focus();
  await user.keyboard("{Enter}");
  expect(await screen.findByRole("dialog", { name: "退出本次学习？" })).toBeVisible();
  await user.click(screen.getByRole("button", { name: "确认退出" }));
  expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
});

test("Esc 退出学习需要确认，取消后留在原卡并恢复焦点", async () => {
  const user = userEvent.setup();
  renderAuthenticatedApp("/study/study-demo");
  const card = await screen.findByRole("button", {
    name: "翻转 sustainable 学习卡查看释义"
  });
  card.focus();

  await user.keyboard("{Escape}");
  expect(screen.getByRole("dialog", { name: "退出本次学习？" })).toBeVisible();
  await user.keyboard("{Escape}");

  expect(screen.queryByRole("dialog", { name: "退出本次学习？" })).not.toBeInTheDocument();
  expect(card).toHaveFocus();
  expect(screen.getByRole("heading", { name: "sustainable" })).toBeVisible();
});

test("直达 active 会话的完成路由会返回学习页", async () => {
  renderAuthenticatedApp("/study/study-demo/complete");

  expect(await screen.findByRole("heading", { name: "sustainable" })).toBeVisible();
  expect(screen.queryByRole("heading", { name: "本次学习已完成" })).not.toBeInTheDocument();
});

test("完成页从不存在会话导航到有效 completed 会话后清除错误", async () => {
  const app = renderScenarioApp("quiz-complete", "/study/missing-session/complete");
  expect(await screen.findByText("找不到学习会话")).toBeVisible();

  await app.navigate("/study/study-demo/complete");

  expect(await screen.findByRole("heading", { name: "本次学习已完成" })).toBeVisible();
  expect(screen.queryByText("找不到学习会话")).not.toBeInTheDocument();
});
