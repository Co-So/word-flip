import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test } from "vitest";
import { createDemoState } from "@/data/mock/createDemoState";
import { createDemoStateStore } from "@/data/mock/DemoStateStore";
import { MockQuizRepository } from "@/data/mock/repositories/MockQuizRepository";
import type { QuizAnswerSubmission } from "@/domain/quiz";
import { renderAuthenticatedApp, renderScenarioApp } from "@/test/renderApp";

afterEach(() => cleanup());

function activePlan(store: ReturnType<typeof createDemoStateStore>) {
  const state = store.read();
  return state.planStates[state.books.activePlanId!];
}

describe("Quiz 工作台", () => {
  test("设置页可分别启动听写和选择会话", async () => {
    const user = userEvent.setup();
    const dictationApp = renderAuthenticatedApp("/quiz");

    expect(await screen.findByText("当前主词书 · 固定演示题")).toBeVisible();
    await user.click(await screen.findByRole("button", { name: "开始听写测验" }));
    expect(await screen.findByLabelText("输入英文单词")).toBeVisible();
    expect(screen.getByRole("heading", { name: "听写测验" })).toBeVisible();

    dictationApp.unmount();
    const choiceApp = renderAuthenticatedApp("/quiz");
    await user.click(await screen.findByRole("button", { name: "开始选择测验" }));
    expect(await screen.findByRole("radiogroup", { name: "选择正确释义" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "选择测验" })).toBeVisible();
    choiceApp.unmount();
  });

  test("听写提交只更新当前 cardId 的 dictation", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("quiz-dictation", "/quiz/quiz-dictation-1");
    const before = app.store.read().planStates["plan-core"].cards.byCardId;
    const choiceBefore = structuredClone(before["card-sustainable"].progress.choice);
    const otherCardsBefore = structuredClone(
      Object.fromEntries(
        Object.entries(before)
          .filter(([cardId]) => cardId !== "card-sustainable")
          .map(([cardId, card]) => [cardId, card.progress])
      )
    );

    await user.type(await screen.findByLabelText("输入英文单词"), "sustainable");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      const cards = app.store.read().planStates["plan-core"].cards.byCardId;
      expect(cards["card-sustainable"].progress.dictation.stability).toBe(30);
      expect(cards["card-sustainable"].progress.choice).toEqual(choiceBefore);
      expect(
        Object.fromEntries(
          Object.entries(cards)
            .filter(([cardId]) => cardId !== "card-sustainable")
            .map(([cardId, card]) => [cardId, card.progress])
        )
      ).toEqual(otherCardsBefore);
    });
  });

  test("选择提交只更新当前 cardId 的 choice", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("quiz-choice", "/quiz/quiz-choice-1");
    const before = app.store.read().planStates["plan-core"].cards.byCardId;
    const dictationBefore = structuredClone(before["card-sustainable"].progress.dictation);
    const otherCardsBefore = structuredClone(
      Object.fromEntries(
        Object.entries(before)
          .filter(([cardId]) => cardId !== "card-sustainable")
          .map(([cardId, card]) => [cardId, card.progress])
      )
    );

    await user.click(await screen.findByRole("radio", { name: "可持续的" }));
    await user.click(screen.getByRole("button", { name: "提交答案" }));

    await waitFor(() => {
      const cards = app.store.read().planStates["plan-core"].cards.byCardId;
      expect(cards["card-sustainable"].progress.choice.stability).toBe(24);
      expect(cards["card-sustainable"].progress.dictation).toEqual(dictationBefore);
      expect(
        Object.fromEntries(
          Object.entries(cards)
            .filter(([cardId]) => cardId !== "card-sustainable")
            .map(([cardId, card]) => [cardId, card.progress])
        )
      ).toEqual(otherCardsBefore);
    });
  });

  test("页面展示仓储 fixture 的判题与反馈，不在组件内自行判题", async () => {
    const user = userEvent.setup();
    renderScenarioApp("quiz-dictation", "/quiz/quiz-dictation-1");

    await user.type(await screen.findByLabelText("输入英文单词"), "fixture-says-correct");
    await user.keyboard("{Enter}");

    expect(await screen.findByRole("status", { name: "答题反馈" })).toHaveTextContent("服务端快照：回答正确");
    expect(screen.getByLabelText("输入英文单词")).toHaveValue("fixture-says-correct");
  });

  test("同 requestId 同载荷重试幂等，不同载荷返回 conflict", async () => {
    const store = createDemoStateStore({ initialState: createDemoState(), storage: null });
    const quiz = new MockQuizRepository(store);
    const submission: QuizAnswerSubmission = {
      sessionId: "quiz-dictation-1",
      questionId: "question-dictation-sustainable",
      requestId: "request-idempotent",
      cardId: "card-sustainable",
      answer: "sustainable"
    };

    const first = await quiz.submitAnswer(submission);
    const once = store.read();
    const second = await quiz.submitAnswer(submission);

    expect(second).toEqual(first);
    expect(store.read()).toEqual(once);
    expect(activePlan(store).quiz.idempotency).toHaveLength(1);
    await expect(
      quiz.submitAnswer({ ...submission, answer: "different-answer" })
    ).rejects.toMatchObject({ kind: "conflict" });
    expect(store.read()).toEqual(once);
  });

  test("同 requestId 的大小写或空格变化属于不同原始载荷并返回 conflict", async () => {
    for (const changedAnswer of ["SUSTAINABLE", " sustainable "]) {
      const store = createDemoStateStore({ initialState: createDemoState(), storage: null });
      const quiz = new MockQuizRepository(store);
      const submission: QuizAnswerSubmission = {
        sessionId: "quiz-dictation-1",
        questionId: "question-dictation-sustainable",
        requestId: `request-raw-${changedAnswer.length}`,
        cardId: "card-sustainable",
        answer: "sustainable"
      };
      await quiz.submitAnswer(submission);
      const once = store.read();

      await expect(
        quiz.submitAnswer({ ...submission, answer: changedAnswer })
      ).rejects.toMatchObject({ kind: "conflict" });
      expect(store.read()).toEqual(once);
      expect(activePlan(store).quiz.idempotency).toHaveLength(1);
    }
  });

  test("requestId、session、question、card 与当前计划绑定错误时不写状态", async () => {
    const cases: QuizAnswerSubmission[] = [
      {
        sessionId: "missing-session",
        questionId: "question-dictation-sustainable",
        requestId: "request-wrong-session",
        cardId: "card-sustainable",
        answer: "sustainable"
      },
      {
        sessionId: "quiz-dictation-1",
        questionId: "question-choice-sustainable",
        requestId: "request-wrong-question",
        cardId: "card-sustainable",
        answer: "sustainable"
      },
      {
        sessionId: "quiz-dictation-1",
        questionId: "question-dictation-sustainable",
        requestId: "request-wrong-card",
        cardId: "card-infrastructure",
        answer: "sustainable"
      }
    ];

    for (const submission of cases) {
      const store = createDemoStateStore({ initialState: createDemoState(), storage: null });
      const quiz = new MockQuizRepository(store);
      const before = store.read();
      await expect(quiz.submitAnswer(submission)).rejects.toMatchObject({
        kind: expect.stringMatching(/not-found|conflict/)
      });
      expect(store.read()).toEqual(before);
    }

    const store = createDemoStateStore({ initialState: createDemoState(), storage: null });
    const quiz = new MockQuizRepository(store);
    const before = store.read();
    await store.switchActivePlan("plan-advanced");
    const switched = store.read();
    await expect(
      quiz.submitAnswer({
        sessionId: "quiz-dictation-1",
        questionId: "question-dictation-sustainable",
        requestId: "request-old-plan",
        cardId: "card-sustainable",
        answer: "sustainable"
      })
    ).rejects.toMatchObject({ kind: expect.stringMatching(/not-found|conflict/) });
    expect(store.read()).toEqual(switched);
    expect(before.planStates["plan-core"].cards).toEqual(store.read().planStates["plan-core"].cards);
  });

  test("输入框 Enter 与 radio 按钮键盘语义完成提交，提交中不会重复写入", async () => {
    const user = userEvent.setup();
    const dictationApp = renderScenarioApp("quiz-dictation", "/quiz/quiz-dictation-1");
    const input = await screen.findByLabelText("输入英文单词");
    await user.type(input, "sustainable{Enter}{Enter}");

    expect(await screen.findByRole("status", { name: "答题反馈" })).toBeVisible();
    expect(activePlan(dictationApp.store).quiz.idempotency).toHaveLength(1);

    dictationApp.unmount();
    const choiceApp = renderScenarioApp("quiz-choice", "/quiz/quiz-choice-1");
    const radio = await screen.findByRole("radio", { name: "可持续的" });
    radio.focus();
    await user.keyboard(" ");
    const submit = screen.getByRole("button", { name: "提交答案" });
    submit.focus();
    await user.keyboard("{Enter}");

    expect(await screen.findByRole("status", { name: "答题反馈" })).toBeVisible();
    expect(activePlan(choiceApp.store).quiz.idempotency).toHaveLength(1);
  });

  test("听写错误答案回放独立错误快照且只更新 dictation", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("quiz-dictation", "/quiz/quiz-dictation-1");
    const before = app.store.read().planStates["plan-core"].cards.byCardId;
    const choiceBefore = structuredClone(before["card-sustainable"].progress.choice);
    const otherCardsBefore = structuredClone(
      Object.fromEntries(
        Object.entries(before)
          .filter(([cardId]) => cardId !== "card-sustainable")
          .map(([cardId, card]) => [cardId, card.progress])
      )
    );

    await user.type(await screen.findByLabelText("输入英文单词"), "not-sustainable");
    await user.keyboard("{Enter}");

    const feedback = await screen.findByRole("status", { name: "答题反馈" });
    expect(feedback).toHaveTextContent("KEEP GOING");
    expect(feedback).toHaveTextContent("服务端快照：再巩固一次");
    expect(feedback).toHaveTextContent("标准答案：sustainable");
    expect(
      app.store.read().planStates["plan-core"].quiz.idempotency.at(-1)?.response.correct
    ).toBe(false);
    const after = app.store.read().planStates["plan-core"].cards.byCardId;
    expect(after["card-sustainable"].progress.dictation).toEqual({
      skill: "dictation",
      state: "unknown",
      stability: 4,
      heatLevel: 0,
      lastQuizSucceeded: false
    });
    expect(after["card-sustainable"].progress.choice).toEqual(choiceBefore);
    expect(
      Object.fromEntries(
        Object.entries(after)
          .filter(([cardId]) => cardId !== "card-sustainable")
          .map(([cardId, card]) => [cardId, card.progress])
      )
    ).toEqual(otherCardsBefore);

    await user.click(screen.getByRole("link", { name: "查看测验结果" }));
    const dictationSummary = await screen.findByRole("heading", { name: "听写摘要" });
    expect(within(dictationSummary.closest("article")!).getByText("0 / 1")).toBeVisible();
    expect(within(dictationSummary.closest("article")!).getByText("稳定性 4 天")).toBeVisible();
  });

  test("听写输入聚焦时 Esc 需要确认退出，取消保留答案并恢复输入焦点", async () => {
    const user = userEvent.setup();
    renderScenarioApp("quiz-dictation", "/quiz/quiz-dictation-1");
    const input = await screen.findByLabelText("输入英文单词");
    await user.type(input, "sustain");

    await user.keyboard("{Escape}");
    expect(screen.getByRole("dialog", { name: "退出本次测验？" })).toBeVisible();
    await user.click(screen.getByRole("button", { name: "继续当前任务" }));

    expect(input).toHaveValue("sustain");
    expect(input).toHaveFocus();
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: "确认退出" }));
    expect(await screen.findByRole("button", { name: "开始听写测验" })).toBeVisible();
  });

  test("选择错误答案回放独立错误快照且只更新 choice", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("quiz-choice", "/quiz/quiz-choice-1");
    const before = app.store.read().planStates["plan-core"].cards.byCardId;
    const dictationBefore = structuredClone(before["card-sustainable"].progress.dictation);
    const otherCardsBefore = structuredClone(
      Object.fromEntries(
        Object.entries(before)
          .filter(([cardId]) => cardId !== "card-sustainable")
          .map(([cardId, card]) => [cardId, card.progress])
      )
    );

    await user.click(await screen.findByRole("radio", { name: "临时的" }));
    await user.click(screen.getByRole("button", { name: "提交答案" }));

    const feedback = await screen.findByRole("status", { name: "答题反馈" });
    expect(feedback).toHaveTextContent("KEEP GOING");
    expect(feedback).toHaveTextContent("服务端快照：选择错误");
    expect(feedback).toHaveTextContent("标准答案：可持续的");
    expect(
      app.store.read().planStates["plan-core"].quiz.idempotency.at(-1)?.response.correct
    ).toBe(false);
    const after = app.store.read().planStates["plan-core"].cards.byCardId;
    expect(after["card-sustainable"].progress.choice).toEqual({
      skill: "choice",
      state: "unknown",
      stability: 4,
      heatLevel: 0,
      lastQuizSucceeded: false
    });
    expect(after["card-sustainable"].progress.dictation).toEqual(dictationBefore);
    expect(
      Object.fromEntries(
        Object.entries(after)
          .filter(([cardId]) => cardId !== "card-sustainable")
          .map(([cardId, card]) => [cardId, card.progress])
      )
    ).toEqual(otherCardsBefore);

    await user.click(screen.getByRole("link", { name: "查看测验结果" }));
    const choiceSummary = await screen.findByRole("heading", { name: "选择摘要" });
    expect(within(choiceSummary.closest("article")!).getByText("0 / 1")).toBeVisible();
    expect(within(choiceSummary.closest("article")!).getByText("稳定性 4 天")).toBeVisible();
  });

  test("active 会话直达结果页会恢复工作台，completed 会话才能展示结果", async () => {
    renderScenarioApp("quiz-dictation", "/quiz/quiz-dictation-1/result");
    expect(await screen.findByRole("heading", { name: "听写测验" })).toBeVisible();
    cleanup();

    renderScenarioApp("quiz-complete", "/quiz/quiz-dictation-1/result");
    expect(await screen.findByRole("heading", { name: "测验完成" })).toBeVisible();
    expect(screen.getByText("听写摘要")).toBeVisible();
  });

  test("从缺失会话导航到有效会话后清除旧错误", async () => {
    const app = renderScenarioApp("quiz-dictation", "/quiz/missing-session");
    expect(await screen.findByText("找不到测验会话")).toBeVisible();

    await app.navigate("/quiz/quiz-dictation-1");

    expect(await screen.findByRole("heading", { name: "听写测验" })).toBeVisible();
    expect(screen.queryByText("找不到测验会话")).not.toBeInTheDocument();
  });

  test("结果页展示双轨摘要与测验唯一写入来源说明", async () => {
    renderScenarioApp("quiz-complete", "/quiz/quiz-dictation-1/result");

    expect(await screen.findByText("听写摘要")).toBeVisible();
    expect(screen.getByText("选择摘要")).toBeVisible();
    expect(screen.getByText("本次变化来自测验结果；翻卡学习不会改变掌握度。")).toBeVisible();
    expect(screen.getByRole("link", { name: "查看统计" })).toHaveAttribute("href", "/stats");
  });
});
