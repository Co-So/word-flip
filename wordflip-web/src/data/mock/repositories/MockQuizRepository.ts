import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { TodaySummary } from "@/domain/today";
import type {
  PrecomputedQuizResult,
  QuizAnswerSubmission,
  QuizIdempotencyRecord,
  QuizRepository,
  QuizResult,
  QuizScope,
  QuizSession,
  QuizSessionResult,
  QuizSkill
} from "@/domain/quiz";

interface QuizFixture {
  planId: string;
  sessionId: string;
  questionId: string;
  cardId: string;
  answerKey: string;
  scope: QuizScope;
  precomputed: PrecomputedQuizResult;
}

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function conflict(message: string): AppError {
  return { kind: "conflict", message };
}

function unavailable(message: string): AppError {
  return { kind: "unavailable", message, retryable: true };
}

function normalizeAnswer(answer: string): string {
  return answer.trim().toLowerCase();
}

function sessionFixture(
  skill: QuizSkill,
  cardId: string,
  wordKey: string,
  definition: string,
  phonetic: string
): QuizSession {
  const isDictation = skill === "dictation";
  return {
    sessionId: isDictation ? "quiz-dictation-1" : "quiz-choice-1",
    status: "completed",
    skill,
    scope: "current-plan",
    totalQuestions: 1,
    currentIndex: 1,
    score: 1,
    progressLabel: "QUESTION 1 / 1",
    question: {
      questionId: isDictation
        ? `question-dictation-${wordKey}`
        : `question-choice-${wordKey}`,
      questionIndex: 0,
      cardId,
      skill,
      prompt: isDictation ? definition : wordKey,
      hint: isDictation ? `${phonetic} · adjective` : phonetic,
      options: isDictation
        ? undefined
        : [
            { key: `meaning-${wordKey}`, label: definition },
            { key: "meaning-temporary", label: "临时的" },
            { key: "meaning-fragile", label: "脆弱的" }
          ]
    }
  };
}

function resultFixture(session: QuizSession, stabilityLabel: string): QuizSessionResult {
  return {
    sessionId: session.sessionId,
    status: "completed",
    score: session.score,
    total: 1,
    accuracy: session.score === 1 ? 100 : 0,
    rating: session.score === 1 ? "excellent" : "keep_going",
    dictation: {
      label: "听写摘要",
      attempted: session.skill === "dictation" ? 1 : 0,
      correct: session.skill === "dictation" ? session.score : 0,
      progressLabel: session.skill === "dictation" ? stabilityLabel : "本次未作答"
    },
    choice: {
      label: "选择摘要",
      attempted: session.skill === "choice" ? 1 : 0,
      correct: session.skill === "choice" ? session.score : 0,
      progressLabel: session.skill === "choice" ? stabilityLabel : "本次未作答"
    }
  };
}

function dashboardFixture(
  currentBookTitle: string,
  cardId: string,
  headword: string,
  definition: string,
  dueCount: number,
  masteredCount: number
): TodaySummary {
  return {
    dueCount,
    masteredCount,
    reviewedCount: masteredCount > 100 ? 19 : 7,
    completionRate: masteredCount > 100 ? 76 : 44,
    currentBookTitle,
    recentStudy: [{ cardId, headword, definition, reviewedAtLabel: "刚刚" }],
    tasks: [{ taskId: "task-review", title: "到期复习", description: `${dueCount} 张卡片等待巩固` }]
  };
}

function precomputedFixture({
  cardId,
  wordKey,
  definition,
  phonetic,
  skill,
  stability,
  correct,
  feedback,
  currentBookTitle,
  dueCount,
  masteredCount
}: {
  cardId: string;
  wordKey: string;
  definition: string;
  phonetic: string;
  skill: QuizSkill;
  stability: number;
  correct: boolean;
  feedback: string;
  currentBookTitle: string;
  dueCount: number;
  masteredCount: number;
}): PrecomputedQuizResult {
  const session = sessionFixture(skill, cardId, wordKey, definition, phonetic);
  if (!correct) {
    session.score = 0;
  }
  return {
    cardId,
    skill,
    next: {
      skill,
      state: correct ? "fuzzy" : "unknown",
      stability,
      heatLevel: correct ? 1 : 0,
      lastQuizSucceeded: correct
    },
    correct,
    feedback,
    expectedAnswer: correct ? null : wordKey,
    sessionSnapshot: session,
    resultSnapshot: resultFixture(session, `稳定性 ${stability} 天`),
    dashboardSnapshot: dashboardFixture(
      currentBookTitle,
      cardId,
      wordKey,
      definition,
      dueCount,
      masteredCount
    ),
    statsSnapshot: {
      totalReviewed: currentBookTitle === "雅思核心词汇" ? 1 : 843,
      retentionRate: correct ? 0.901 : 0.889,
      streakDays: 14
    }
  };
}

/** 错题快照也在 fixture 初始化时固定，提交阶段只执行精确键查找。 */
function wrongFixture(
  source: PrecomputedQuizResult,
  expectedAnswer: string,
  feedback: string
): PrecomputedQuizResult {
  const session = structuredClone(source.sessionSnapshot);
  session.score = 0;
  return {
    ...structuredClone(source),
    next: {
      skill: source.skill,
      state: "unknown",
      stability: 4,
      heatLevel: 0,
      lastQuizSucceeded: false
    },
    correct: false,
    feedback,
    expectedAnswer,
    sessionSnapshot: session,
    resultSnapshot: resultFixture(session, "稳定性 4 天"),
    statsSnapshot: { ...source.statsSnapshot, retentionRate: 0.889 }
  };
}

export const FIXED_DICTATION_RESULT = precomputedFixture({
  cardId: "card-sustainable",
  wordKey: "sustainable",
  definition: "可持续的",
  phonetic: "/səˈsteɪnəbl/",
  skill: "dictation",
  stability: 30,
  correct: true,
  feedback: "服务端快照：回答正确",
  currentBookTitle: "核心词汇",
  dueCount: 23,
  masteredCount: 127
});

export const FIXED_CHOICE_RESULT = precomputedFixture({
  cardId: "card-sustainable",
  wordKey: "sustainable",
  definition: "可持续的",
  phonetic: "/səˈsteɪnəbl/",
  skill: "choice",
  stability: 24,
  correct: true,
  feedback: "服务端快照：选择正确",
  currentBookTitle: "核心词汇",
  dueCount: 23,
  masteredCount: 127
});

export const FIXED_ADVANCED_DICTATION_RESULT = precomputedFixture({
  cardId: "card-resilient",
  wordKey: "resilient",
  definition: "有韧性的",
  phonetic: "/rɪˈzɪliənt/",
  skill: "dictation",
  stability: 4,
  correct: true,
  feedback: "服务端快照：回答正确",
  currentBookTitle: "进阶词汇",
  dueCount: 8,
  masteredCount: 43
});

const FIXED_ADVANCED_CHOICE_RESULT = precomputedFixture({
  cardId: "card-resilient",
  wordKey: "resilient",
  definition: "有韧性的",
  phonetic: "/rɪˈzɪliənt/",
  skill: "choice",
  stability: 18,
  correct: true,
  feedback: "服务端快照：选择正确",
  currentBookTitle: "进阶词汇",
  dueCount: 8,
  masteredCount: 43
});

const FIXED_IELTS_DICTATION_RESULT = precomputedFixture({
  cardId: "card-ielts-sustainable",
  wordKey: "sustainable",
  definition: "可持续的",
  phonetic: "/səˈsteɪnəbl/",
  skill: "dictation",
  stability: 30,
  correct: true,
  feedback: "服务端快照：回答正确",
  currentBookTitle: "雅思核心词汇",
  dueCount: 19,
  masteredCount: 1
});

const FIXED_IELTS_CHOICE_RESULT = precomputedFixture({
  cardId: "card-ielts-sustainable",
  wordKey: "sustainable",
  definition: "可持续的",
  phonetic: "/səˈsteɪnəbl/",
  skill: "choice",
  stability: 24,
  correct: true,
  feedback: "服务端快照：选择正确",
  currentBookTitle: "雅思核心词汇",
  dueCount: 19,
  masteredCount: 1
});

const FIXED_WRONG_DICTATION_RESULT = wrongFixture(
  FIXED_DICTATION_RESULT,
  "sustainable",
  "服务端快照：再巩固一次"
);
const FIXED_WRONG_CHOICE_RESULT = wrongFixture(
  FIXED_CHOICE_RESULT,
  "可持续的",
  "服务端快照：选择错误"
);
const FIXED_ADVANCED_WRONG_DICTATION_RESULT = wrongFixture(
  FIXED_ADVANCED_DICTATION_RESULT,
  "resilient",
  "服务端快照：再巩固一次"
);
const FIXED_ADVANCED_WRONG_CHOICE_RESULT = wrongFixture(
  FIXED_ADVANCED_CHOICE_RESULT,
  "有韧性的",
  "服务端快照：选择错误"
);
const FIXED_IELTS_WRONG_DICTATION_RESULT = wrongFixture(
  FIXED_IELTS_DICTATION_RESULT,
  "sustainable",
  "服务端快照：再巩固一次"
);
const FIXED_IELTS_WRONG_CHOICE_RESULT = wrongFixture(
  FIXED_IELTS_CHOICE_RESULT,
  "可持续的",
  "服务端快照：选择错误"
);

const BASE_QUIZ_FIXTURES: Omit<QuizFixture, "scope">[] = [
  {
    planId: "plan-core",
    sessionId: "quiz-dictation-1",
    questionId: "question-dictation-sustainable",
    cardId: "card-sustainable",
    answerKey: "sustainable",
    precomputed: FIXED_DICTATION_RESULT
  },
  {
    planId: "plan-core",
    sessionId: "quiz-dictation-1",
    questionId: "question-dictation-sustainable",
    cardId: "card-sustainable",
    answerKey: "fixture-says-correct",
    precomputed: FIXED_DICTATION_RESULT
  },
  {
    planId: "plan-core",
    sessionId: "quiz-dictation-1",
    questionId: "question-dictation-sustainable",
    cardId: "card-sustainable",
    answerKey: "__wrong__",
    precomputed: FIXED_WRONG_DICTATION_RESULT
  },
  {
    planId: "plan-core",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-sustainable",
    cardId: "card-sustainable",
    answerKey: "meaning-sustainable",
    precomputed: FIXED_CHOICE_RESULT
  },
  {
    planId: "plan-core",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-sustainable",
    cardId: "card-sustainable",
    answerKey: "meaning-temporary",
    precomputed: FIXED_WRONG_CHOICE_RESULT
  },
  {
    planId: "plan-core",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-sustainable",
    cardId: "card-sustainable",
    answerKey: "meaning-fragile",
    precomputed: FIXED_WRONG_CHOICE_RESULT
  },
  {
    planId: "plan-advanced",
    sessionId: "quiz-dictation-1",
    questionId: "question-dictation-resilient",
    cardId: "card-resilient",
    answerKey: "resilient",
    precomputed: FIXED_ADVANCED_DICTATION_RESULT
  },
  {
    planId: "plan-advanced",
    sessionId: "quiz-dictation-1",
    questionId: "question-dictation-resilient",
    cardId: "card-resilient",
    answerKey: "__wrong__",
    precomputed: FIXED_ADVANCED_WRONG_DICTATION_RESULT
  },
  {
    planId: "plan-advanced",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-resilient",
    cardId: "card-resilient",
    answerKey: "meaning-resilient",
    precomputed: FIXED_ADVANCED_CHOICE_RESULT
  },
  {
    planId: "plan-advanced",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-resilient",
    cardId: "card-resilient",
    answerKey: "meaning-temporary",
    precomputed: FIXED_ADVANCED_WRONG_CHOICE_RESULT
  },
  {
    planId: "plan-advanced",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-resilient",
    cardId: "card-resilient",
    answerKey: "meaning-fragile",
    precomputed: FIXED_ADVANCED_WRONG_CHOICE_RESULT
  },
  {
    planId: "plan-ielts",
    sessionId: "quiz-dictation-1",
    questionId: "question-dictation-sustainable",
    cardId: "card-ielts-sustainable",
    answerKey: "sustainable",
    precomputed: FIXED_IELTS_DICTATION_RESULT
  },
  {
    planId: "plan-ielts",
    sessionId: "quiz-dictation-1",
    questionId: "question-dictation-sustainable",
    cardId: "card-ielts-sustainable",
    answerKey: "__wrong__",
    precomputed: FIXED_IELTS_WRONG_DICTATION_RESULT
  },
  {
    planId: "plan-ielts",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-sustainable",
    cardId: "card-ielts-sustainable",
    answerKey: "meaning-sustainable",
    precomputed: FIXED_IELTS_CHOICE_RESULT
  },
  {
    planId: "plan-ielts",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-sustainable",
    cardId: "card-ielts-sustainable",
    answerKey: "meaning-temporary",
    precomputed: FIXED_IELTS_WRONG_CHOICE_RESULT
  },
  {
    planId: "plan-ielts",
    sessionId: "quiz-choice-1",
    questionId: "question-choice-sustainable",
    cardId: "card-ielts-sustainable",
    answerKey: "meaning-fragile",
    precomputed: FIXED_IELTS_WRONG_CHOICE_RESULT
  }
];

const QUIZ_FIXTURES: QuizFixture[] = BASE_QUIZ_FIXTURES.flatMap((fixture) =>
  (["current-plan", "due-today"] as const).map((scope) => ({
    ...fixture,
    scope,
    precomputed: {
      ...structuredClone(fixture.precomputed),
      sessionSnapshot: {
        ...structuredClone(fixture.precomputed.sessionSnapshot),
        scope
      }
    }
  }))
);

function samePayload(record: QuizIdempotencyRecord, submission: QuizAnswerSubmission, userId: string, planId: string) {
  return (
    record.userId === userId &&
    record.planId === planId &&
    record.sessionId === submission.sessionId &&
    record.questionId === submission.questionId &&
    record.cardId === submission.cardId &&
    record.answer === normalizeAnswer(submission.answer)
  );
}

/** 模拟测验仓储只按完整绑定键回放固定服务端结果，不执行判题或 FSRS 计算。 */
export class MockQuizRepository implements QuizRepository {
  constructor(private readonly store: DemoStateStore) {}

  createSession(skill: QuizSkill, scope: QuizScope): Promise<QuizSession> {
    const state = this.store.read();
    const planId = state.books.activePlanId;
    const plan = planId ? state.planStates[planId] : undefined;
    const session = plan
      ? Object.values(plan.quiz.sessions).find((candidate) => candidate.skill === skill)
      : undefined;
    if (!plan || !session) {
      return Promise.reject(notFound("当前计划没有可用的测验会话"));
    }
    this.store.updateActivePlan((draft) => {
      const next = structuredClone(session);
      next.status = "active";
      next.scope = scope;
      next.currentIndex = 0;
      next.score = 0;
      draft.quiz.sessions[next.sessionId] = next;
      delete draft.quiz.results[next.sessionId];
    });
    return Promise.resolve(structuredClone(this.store.readActivePlanState()!.quiz.sessions[session.sessionId]));
  }

  getSession(sessionId: string): Promise<QuizSession> {
    const session = this.store.readActivePlanState()?.quiz.sessions[sessionId];
    return session
      ? Promise.resolve(structuredClone(session))
      : Promise.reject(notFound("找不到测验会话"));
  }

  submitAnswer(submission: QuizAnswerSubmission): Promise<QuizResult> {
    const state = this.store.read();
    const userId = state.auth.session?.userId;
    const planId = state.books.activePlanId;
    const plan = planId ? state.planStates[planId] : undefined;
    if (!userId || !planId || !plan) {
      return Promise.reject(conflict("当前用户或学习计划不可用"));
    }

    const existing = Object.values(state.planStates)
      .flatMap((candidate) => candidate.quiz.idempotency)
      .find((record) => record.requestId === submission.requestId);
    if (existing) {
      return samePayload(existing, submission, userId, planId)
        ? Promise.resolve(structuredClone(existing.response))
        : Promise.reject(conflict("requestId 已绑定到不同的答题载荷"));
    }

    const session = plan.quiz.sessions[submission.sessionId];
    if (!session) {
      return Promise.reject(notFound("找不到测验会话"));
    }
    if (session.status !== "active") {
      return Promise.reject(conflict("测验会话已完成"));
    }
    if (
      session.question.questionId !== submission.questionId ||
      session.question.cardId !== submission.cardId ||
      !plan.cards.byCardId[submission.cardId]
    ) {
      return Promise.reject(conflict("答题载荷与当前题目不一致"));
    }

    const normalizedAnswer = normalizeAnswer(submission.answer);
    const exactFixture = QUIZ_FIXTURES.find(
      (fixture) =>
        fixture.planId === planId &&
        fixture.sessionId === submission.sessionId &&
        fixture.questionId === submission.questionId &&
        fixture.cardId === submission.cardId &&
        fixture.scope === session.scope &&
        fixture.answerKey === normalizedAnswer
    );
    const fallbackFixture =
      session.skill === "dictation"
        ? QUIZ_FIXTURES.find(
            (fixture) =>
              fixture.planId === planId &&
              fixture.sessionId === submission.sessionId &&
              fixture.questionId === submission.questionId &&
              fixture.cardId === submission.cardId &&
              fixture.scope === session.scope &&
              fixture.answerKey === "__wrong__"
          )
        : undefined;
    const fixture = exactFixture ?? fallbackFixture;
    if (!fixture) {
      return Promise.reject(unavailable("当前答题载荷没有可回放的服务端预计算结果"));
    }

    const precomputed = structuredClone(fixture.precomputed);
    const response: QuizResult = {
      requestId: submission.requestId,
      accepted: true,
      correct: precomputed.correct,
      feedback: precomputed.feedback,
      expectedAnswer: precomputed.expectedAnswer,
      precomputed
    };
    const idempotency: QuizIdempotencyRecord = {
      requestId: submission.requestId,
      userId,
      planId,
      sessionId: submission.sessionId,
      questionId: submission.questionId,
      cardId: submission.cardId,
      answer: normalizedAnswer,
      response
    };
    try {
      this.store.applyQuizResult(precomputed, idempotency);
      return Promise.resolve(structuredClone(response));
    } catch (error) {
      return Promise.reject(error);
    }
  }

  getResult(sessionId: string): Promise<QuizSessionResult> {
    const plan = this.store.readActivePlanState();
    const session = plan?.quiz.sessions[sessionId];
    if (!session) {
      return Promise.reject(notFound("找不到测验会话"));
    }
    if (session.status !== "completed") {
      return Promise.reject(conflict("测验尚未完成"));
    }
    const result = plan.quiz.results[sessionId];
    return result
      ? Promise.resolve(structuredClone(result))
      : Promise.reject(notFound("找不到测验结果"));
  }
}
