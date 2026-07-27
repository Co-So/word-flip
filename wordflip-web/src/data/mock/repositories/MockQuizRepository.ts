import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import {
  FIXED_ADVANCED_DICTATION_RESULT,
  FIXED_CHOICE_RESULT,
  FIXED_DICTATION_RESULT,
  QUIZ_FIXTURES
} from "@/data/mock/quizFixtures";
import type {
  QuizAnswerSubmission,
  QuizIdempotencyRecord,
  QuizRepository,
  QuizResult,
  QuizScope,
  QuizSession,
  QuizSessionResult,
  QuizSkill
} from "@/domain/quiz";

export { FIXED_ADVANCED_DICTATION_RESULT, FIXED_CHOICE_RESULT, FIXED_DICTATION_RESULT };

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function conflict(message: string): AppError {
  return { kind: "conflict", message };
}

function unavailable(message: string): AppError {
  return { kind: "unavailable", message, retryable: true };
}

function normalizeForFixtureLookup(answer: string): string {
  return answer.trim().toLowerCase();
}

function sameRawPayload(
  record: QuizIdempotencyRecord,
  submission: QuizAnswerSubmission,
  userId: string,
  planId: string,
  scope: QuizScope | undefined
) {
  return (
    record.userId === userId &&
    record.planId === planId &&
    record.scope === scope &&
    record.sessionId === submission.sessionId &&
    record.questionId === submission.questionId &&
    record.cardId === submission.cardId &&
    record.answer === submission.answer
  );
}

/** 模拟测验仓储只按完整绑定键回放静态服务端结果，不执行判题或任何快照计算。 */
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
    return Promise.resolve(
      structuredClone(this.store.readActivePlanState()!.quiz.sessions[session.sessionId])
    );
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

    const session = plan.quiz.sessions[submission.sessionId];
    const existing = Object.values(state.planStates)
      .flatMap((candidate) => candidate.quiz.idempotency)
      .find((record) => record.requestId === submission.requestId);
    if (existing) {
      return sameRawPayload(existing, submission, userId, planId, session?.scope)
        ? Promise.resolve(structuredClone(existing.response))
        : Promise.reject(conflict("requestId 已绑定到不同的答题载荷"));
    }

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

    const lookupAnswer = normalizeForFixtureLookup(submission.answer);
    const fixture = QUIZ_FIXTURES.find(
      (candidate) =>
        candidate.planId === planId &&
        candidate.sessionId === submission.sessionId &&
        candidate.questionId === submission.questionId &&
        candidate.cardId === submission.cardId &&
        candidate.scope === session.scope &&
        candidate.answerKeys.includes(lookupAnswer)
    );
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
      scope: session.scope,
      questionId: submission.questionId,
      cardId: submission.cardId,
      // 幂等绑定保留原始载荷；规范化值仅用于上方静态 fixture 查询。
      answer: submission.answer,
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
