import type { PrecomputedQuizResult, QuizScope } from "@/domain/quiz";
import { createStatsSnapshot } from "@/data/mock/statsFixtures";
import type { TodaySummary } from "@/domain/today";

export interface MockQuizFixture {
  planId: string;
  sessionId: string;
  questionId: string;
  cardId: string;
  scope: QuizScope;
  answerKeys: string[];
  precomputed: PrecomputedQuizResult;
}

/** 为预计算测验结果补齐固定 Today v5 快照，不根据答题现场计算业务结果。 */
export function fixedTodaySummary(input: {
  dueReviewCount: number;
  masteredCount: number;
  completionPercent: number;
  groupId: string;
  groupName: string;
}): TodaySummary {
  return {
    date: "2026-07-23",
    streakDays: 14,
    stats: {
      masteredCount: input.masteredCount,
      dueReviewCount: input.dueReviewCount,
      completionPercent: input.completionPercent
    },
    tasks: {
      newWords: { count: 0, label: "新词", sources: [] },
      dueReview: {
        count: input.dueReviewCount,
        label: "到期复习",
        sources: [{ groupId: input.groupId, groupName: input.groupName, count: input.dueReviewCount }]
      },
      quiz: { count: 1, label: "测验", sources: [] }
    },
    recommendedStudy: {
      groupId: input.groupId,
      groupName: input.groupName,
      wordCount: input.dueReviewCount,
      reason: "due_review"
    },
    recentGroups: [
      { groupId: input.groupId, name: input.groupName, lastStudiedAt: "2026-07-23T08:30:00Z" }
    ]
  };
}

/**
 * 每个结果都是服务端响应的独立静态快照。
 * createStatsSnapshot 只补齐固定展示字段，不从答题或记忆数据计算汇总。
 */
export const QUIZ_FIXTURES: readonly MockQuizFixture[] = [
  {
    planId: "plan-core", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-sustainable", scope: "current-plan", answerKeys: ["sustainable", "fixture-says-correct"],
    precomputed: {
      cardId: "card-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "fuzzy", stability: 30, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：回答正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 1, correct: 1, progressLabel: "稳定性 30 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 23, masteredCount: 127, completionPercent: 76, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.901, 14, 127)
    }
  },
  {
    planId: "plan-core", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-sustainable", scope: "due-today", answerKeys: ["sustainable", "fixture-says-correct"],
    precomputed: {
      cardId: "card-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "fuzzy", stability: 30, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：回答正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 1, correct: 1, progressLabel: "稳定性 30 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 23, masteredCount: 127, completionPercent: 76, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.901, 14, 127)
    }
  },
  {
    planId: "plan-core", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-sustainable", scope: "current-plan", answerKeys: ["not-sustainable"],
    precomputed: {
      cardId: "card-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "unknown", stability: 4, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：再巩固一次", expectedAnswer: "sustainable",
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 1, correct: 0, progressLabel: "稳定性 4 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 24, masteredCount: 126, completionPercent: 72, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.889, 14, 126)
    }
  },
  {
    planId: "plan-core", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-sustainable", scope: "due-today", answerKeys: ["not-sustainable"],
    precomputed: {
      cardId: "card-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "unknown", stability: 4, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：再巩固一次", expectedAnswer: "sustainable",
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 1, correct: 0, progressLabel: "稳定性 4 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 24, masteredCount: 126, completionPercent: 72, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.889, 14, 126)
    }
  },
  {
    planId: "plan-core", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-sustainable", scope: "current-plan", answerKeys: ["meaning-sustainable"],
    precomputed: {
      cardId: "card-sustainable", skill: "choice",
      next: { skill: "choice", state: "fuzzy", stability: 24, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：选择正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 1, progressLabel: "稳定性 24 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 23, masteredCount: 127, completionPercent: 76, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.901, 14, 127)
    }
  },
  {
    planId: "plan-core", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-sustainable", scope: "due-today", answerKeys: ["meaning-sustainable"],
    precomputed: {
      cardId: "card-sustainable", skill: "choice",
      next: { skill: "choice", state: "fuzzy", stability: 24, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：选择正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 1, progressLabel: "稳定性 24 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 23, masteredCount: 127, completionPercent: 76, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.901, 14, 127)
    }
  },
  {
    planId: "plan-core", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-sustainable", scope: "current-plan", answerKeys: ["meaning-temporary", "meaning-fragile"],
    precomputed: {
      cardId: "card-sustainable", skill: "choice",
      next: { skill: "choice", state: "unknown", stability: 4, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：选择错误", expectedAnswer: "可持续的",
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 0, progressLabel: "稳定性 4 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 24, masteredCount: 126, completionPercent: 72, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.889, 14, 126)
    }
  },
  {
    planId: "plan-core", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-sustainable", scope: "due-today", answerKeys: ["meaning-temporary", "meaning-fragile"],
    precomputed: {
      cardId: "card-sustainable", skill: "choice",
      next: { skill: "choice", state: "unknown", stability: 4, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：选择错误", expectedAnswer: "可持续的",
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 0, progressLabel: "稳定性 4 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 24, masteredCount: 126, completionPercent: 72, groupId: "group-12", groupName: "第 12 组 · 城市与环境" }),
      statsSnapshot: createStatsSnapshot(843, 0.889, 14, 126)
    }
  },

  {
    planId: "plan-advanced", sessionId: "quiz-dictation-1", questionId: "question-dictation-resilient",
    cardId: "card-resilient", scope: "current-plan", answerKeys: ["resilient"],
    precomputed: {
      cardId: "card-resilient", skill: "dictation",
      next: { skill: "dictation", state: "fuzzy", stability: 4, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：回答正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-resilient", questionIndex: 0, cardId: "card-resilient", skill: "dictation", prompt: "有韧性的", hint: "/rɪˈzɪliənt/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 1, correct: 1, progressLabel: "稳定性 4 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 8, masteredCount: 43, completionPercent: 44, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.903, 14, 43, "advanced")
    }
  },
  {
    planId: "plan-advanced", sessionId: "quiz-dictation-1", questionId: "question-dictation-resilient",
    cardId: "card-resilient", scope: "due-today", answerKeys: ["resilient"],
    precomputed: {
      cardId: "card-resilient", skill: "dictation",
      next: { skill: "dictation", state: "fuzzy", stability: 4, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：回答正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-resilient", questionIndex: 0, cardId: "card-resilient", skill: "dictation", prompt: "有韧性的", hint: "/rɪˈzɪliənt/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 1, correct: 1, progressLabel: "稳定性 4 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 8, masteredCount: 43, completionPercent: 44, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.903, 14, 43, "advanced")
    }
  },
  {
    planId: "plan-advanced", sessionId: "quiz-dictation-1", questionId: "question-dictation-resilient",
    cardId: "card-resilient", scope: "current-plan", answerKeys: ["not-resilient"],
    precomputed: {
      cardId: "card-resilient", skill: "dictation",
      next: { skill: "dictation", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：再巩固一次", expectedAnswer: "resilient",
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-resilient", questionIndex: 0, cardId: "card-resilient", skill: "dictation", prompt: "有韧性的", hint: "/rɪˈzɪliənt/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 9, masteredCount: 42, completionPercent: 38, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.887, 14, 42, "advanced")
    }
  },
  {
    planId: "plan-advanced", sessionId: "quiz-dictation-1", questionId: "question-dictation-resilient",
    cardId: "card-resilient", scope: "due-today", answerKeys: ["not-resilient"],
    precomputed: {
      cardId: "card-resilient", skill: "dictation",
      next: { skill: "dictation", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：再巩固一次", expectedAnswer: "resilient",
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-resilient", questionIndex: 0, cardId: "card-resilient", skill: "dictation", prompt: "有韧性的", hint: "/rɪˈzɪliənt/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 9, masteredCount: 42, completionPercent: 38, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.887, 14, 42, "advanced")
    }
  },
  {
    planId: "plan-advanced", sessionId: "quiz-choice-1", questionId: "question-choice-resilient",
    cardId: "card-resilient", scope: "current-plan", answerKeys: ["meaning-resilient"],
    precomputed: {
      cardId: "card-resilient", skill: "choice",
      next: { skill: "choice", state: "fuzzy", stability: 18, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：选择正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-resilient", questionIndex: 0, cardId: "card-resilient", skill: "choice", prompt: "resilient", hint: "/rɪˈzɪliənt/",
          options: [{ key: "meaning-resilient", label: "有韧性的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 1, progressLabel: "稳定性 18 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 8, masteredCount: 43, completionPercent: 44, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.903, 14, 43, "advanced")
    }
  },
  {
    planId: "plan-advanced", sessionId: "quiz-choice-1", questionId: "question-choice-resilient",
    cardId: "card-resilient", scope: "due-today", answerKeys: ["meaning-resilient"],
    precomputed: {
      cardId: "card-resilient", skill: "choice",
      next: { skill: "choice", state: "fuzzy", stability: 18, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：选择正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-resilient", questionIndex: 0, cardId: "card-resilient", skill: "choice", prompt: "resilient", hint: "/rɪˈzɪliənt/",
          options: [{ key: "meaning-resilient", label: "有韧性的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 1, progressLabel: "稳定性 18 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 8, masteredCount: 43, completionPercent: 44, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.903, 14, 43, "advanced")
    }
  },
  {
    planId: "plan-advanced", sessionId: "quiz-choice-1", questionId: "question-choice-resilient",
    cardId: "card-resilient", scope: "current-plan", answerKeys: ["meaning-temporary", "meaning-fragile"],
    precomputed: {
      cardId: "card-resilient", skill: "choice",
      next: { skill: "choice", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：选择错误", expectedAnswer: "有韧性的",
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-resilient", questionIndex: 0, cardId: "card-resilient", skill: "choice", prompt: "resilient", hint: "/rɪˈzɪliənt/",
          options: [{ key: "meaning-resilient", label: "有韧性的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 9, masteredCount: 42, completionPercent: 38, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.887, 14, 42, "advanced")
    }
  },
  {
    planId: "plan-advanced", sessionId: "quiz-choice-1", questionId: "question-choice-resilient",
    cardId: "card-resilient", scope: "due-today", answerKeys: ["meaning-temporary", "meaning-fragile"],
    precomputed: {
      cardId: "card-resilient", skill: "choice",
      next: { skill: "choice", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：选择错误", expectedAnswer: "有韧性的",
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-resilient", questionIndex: 0, cardId: "card-resilient", skill: "choice", prompt: "resilient", hint: "/rɪˈzɪliənt/",
          options: [{ key: "meaning-resilient", label: "有韧性的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 9, masteredCount: 42, completionPercent: 38, groupId: "group-advanced", groupName: "进阶复习" }),
      statsSnapshot: createStatsSnapshot(843, 0.887, 14, 42, "advanced")
    }
  },

  {
    planId: "plan-ielts", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-ielts-sustainable", scope: "current-plan", answerKeys: ["sustainable"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "fuzzy", stability: 30, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：回答正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 1, correct: 1, progressLabel: "稳定性 30 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 19, masteredCount: 1, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.901, 1, 1, "new-dictation")
    }
  },
  {
    planId: "plan-ielts", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-ielts-sustainable", scope: "due-today", answerKeys: ["sustainable"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "fuzzy", stability: 30, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：回答正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 1, correct: 1, progressLabel: "稳定性 30 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 19, masteredCount: 1, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.901, 1, 1, "new-dictation")
    }
  },
  {
    planId: "plan-ielts", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-ielts-sustainable", scope: "current-plan", answerKeys: ["not-sustainable"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：再巩固一次", expectedAnswer: "sustainable",
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 20, masteredCount: 0, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.5, 1, 0, "new-dictation")
    }
  },
  {
    planId: "plan-ielts", sessionId: "quiz-dictation-1", questionId: "question-dictation-sustainable",
    cardId: "card-ielts-sustainable", scope: "due-today", answerKeys: ["not-sustainable"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "dictation",
      next: { skill: "dictation", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：再巩固一次", expectedAnswer: "sustainable",
      sessionSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", skill: "dictation", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: { questionId: "question-dictation-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "dictation", prompt: "可持续的", hint: "/səˈsteɪnəbl/ · adjective" }
      },
      resultSnapshot: {
        sessionId: "quiz-dictation-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" },
        choice: { label: "选择摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 20, masteredCount: 0, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.5, 1, 0, "new-dictation")
    }
  },
  {
    planId: "plan-ielts", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-ielts-sustainable", scope: "current-plan", answerKeys: ["meaning-sustainable"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "choice",
      next: { skill: "choice", state: "fuzzy", stability: 24, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：选择正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 1, progressLabel: "稳定性 24 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 19, masteredCount: 1, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.901, 1, 1, "new-choice")
    }
  },
  {
    planId: "plan-ielts", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-ielts-sustainable", scope: "due-today", answerKeys: ["meaning-sustainable"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "choice",
      next: { skill: "choice", state: "fuzzy", stability: 24, heatLevel: 1, lastQuizSucceeded: true },
      correct: true, feedback: "服务端快照：选择正确", expectedAnswer: null,
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 1, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 1, total: 1, accuracy: 100, rating: "excellent",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 1, progressLabel: "稳定性 24 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 19, masteredCount: 1, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.901, 1, 1, "new-choice")
    }
  },
  {
    planId: "plan-ielts", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-ielts-sustainable", scope: "current-plan", answerKeys: ["meaning-temporary", "meaning-fragile"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "choice",
      next: { skill: "choice", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：选择错误", expectedAnswer: "可持续的",
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "current-plan",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 20, masteredCount: 0, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.5, 1, 0, "new-choice")
    }
  },
  {
    planId: "plan-ielts", sessionId: "quiz-choice-1", questionId: "question-choice-sustainable",
    cardId: "card-ielts-sustainable", scope: "due-today", answerKeys: ["meaning-temporary", "meaning-fragile"],
    precomputed: {
      cardId: "card-ielts-sustainable", skill: "choice",
      next: { skill: "choice", state: "unknown", stability: 2, heatLevel: 0, lastQuizSucceeded: false },
      correct: false, feedback: "服务端快照：选择错误", expectedAnswer: "可持续的",
      sessionSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", skill: "choice", scope: "due-today",
        totalQuestions: 1, currentIndex: 1, score: 0, progressLabel: "QUESTION 1 / 1",
        question: {
          questionId: "question-choice-sustainable", questionIndex: 0, cardId: "card-ielts-sustainable", skill: "choice", prompt: "sustainable", hint: "/səˈsteɪnəbl/",
          options: [{ key: "meaning-sustainable", label: "可持续的" }, { key: "meaning-temporary", label: "临时的" }, { key: "meaning-fragile", label: "脆弱的" }]
        }
      },
      resultSnapshot: {
        sessionId: "quiz-choice-1", status: "completed", score: 0, total: 1, accuracy: 0, rating: "keep_going",
        dictation: { label: "听写摘要", attempted: 0, correct: 0, progressLabel: "本次未作答" },
        choice: { label: "选择摘要", attempted: 1, correct: 0, progressLabel: "稳定性 2 天" }
      },
      dashboardSnapshot: fixedTodaySummary({ dueReviewCount: 20, masteredCount: 0, completionPercent: 5, groupId: "group-ielts-01", groupName: "第 1 组" }),
      statsSnapshot: createStatsSnapshot(1, 0.5, 1, 0, "new-choice")
    }
  }
];

export const FIXED_DICTATION_RESULT = QUIZ_FIXTURES[0].precomputed;
export const FIXED_CHOICE_RESULT = QUIZ_FIXTURES[4].precomputed;
export const FIXED_ADVANCED_DICTATION_RESULT = QUIZ_FIXTURES[8].precomputed;
