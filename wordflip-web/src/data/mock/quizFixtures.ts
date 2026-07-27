import type { PrecomputedQuizResult, QuizScope } from "@/domain/quiz";
import { createStatsSnapshot } from "@/data/mock/statsFixtures";

export interface MockQuizFixture {
  planId: string;
  sessionId: string;
  questionId: string;
  cardId: string;
  scope: QuizScope;
  answerKeys: string[];
  precomputed: PrecomputedQuizResult;
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
      dashboardSnapshot: {
        dueCount: 23, masteredCount: 127, reviewedCount: 19, completionRate: 76, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "23 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 23, masteredCount: 127, reviewedCount: 19, completionRate: 76, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "23 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 24, masteredCount: 126, reviewedCount: 19, completionRate: 72, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "24 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 24, masteredCount: 126, reviewedCount: 19, completionRate: 72, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "24 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 23, masteredCount: 127, reviewedCount: 19, completionRate: 76, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "23 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 23, masteredCount: 127, reviewedCount: 19, completionRate: 76, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "23 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 24, masteredCount: 126, reviewedCount: 19, completionRate: 72, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "24 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 24, masteredCount: 126, reviewedCount: 19, completionRate: 72, currentBookTitle: "核心词汇",
        recentStudy: [{ cardId: "card-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-review", title: "到期复习", description: "24 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 8, masteredCount: 43, reviewedCount: 7, completionRate: 44, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "8 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 8, masteredCount: 43, reviewedCount: 7, completionRate: 44, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "8 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 9, masteredCount: 42, reviewedCount: 7, completionRate: 38, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "9 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 9, masteredCount: 42, reviewedCount: 7, completionRate: 38, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "9 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 8, masteredCount: 43, reviewedCount: 7, completionRate: 44, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "8 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 8, masteredCount: 43, reviewedCount: 7, completionRate: 44, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "8 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 9, masteredCount: 42, reviewedCount: 7, completionRate: 38, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "9 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 9, masteredCount: 42, reviewedCount: 7, completionRate: 38, currentBookTitle: "进阶词汇",
        recentStudy: [{ cardId: "card-resilient", headword: "resilient", definition: "有韧性的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-advanced", title: "进阶复习", description: "9 张卡片等待巩固" }]
      },
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
      dashboardSnapshot: {
        dueCount: 19, masteredCount: 1, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "19 张新卡等待学习" }]
      },
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
      dashboardSnapshot: {
        dueCount: 19, masteredCount: 1, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "19 张新卡等待学习" }]
      },
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
      dashboardSnapshot: {
        dueCount: 20, masteredCount: 0, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "20 张新卡等待学习" }]
      },
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
      dashboardSnapshot: {
        dueCount: 20, masteredCount: 0, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "20 张新卡等待学习" }]
      },
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
      dashboardSnapshot: {
        dueCount: 19, masteredCount: 1, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "19 张新卡等待学习" }]
      },
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
      dashboardSnapshot: {
        dueCount: 19, masteredCount: 1, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "19 张新卡等待学习" }]
      },
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
      dashboardSnapshot: {
        dueCount: 20, masteredCount: 0, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "20 张新卡等待学习" }]
      },
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
      dashboardSnapshot: {
        dueCount: 20, masteredCount: 0, reviewedCount: 1, completionRate: 5, currentBookTitle: "雅思核心词汇",
        recentStudy: [{ cardId: "card-ielts-sustainable", headword: "sustainable", definition: "可持续的", reviewedAtLabel: "刚刚" }],
        tasks: [{ taskId: "task-first-group", title: "开始第 1 组", description: "20 张新卡等待学习" }]
      },
      statsSnapshot: createStatsSnapshot(1, 0.5, 1, 0, "new-choice")
    }
  }
];

export const FIXED_DICTATION_RESULT = QUIZ_FIXTURES[0].precomputed;
export const FIXED_CHOICE_RESULT = QUIZ_FIXTURES[4].precomputed;
export const FIXED_ADVANCED_DICTATION_RESULT = QUIZ_FIXTURES[8].precomputed;
