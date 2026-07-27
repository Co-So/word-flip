import type { AppError } from "@/data/contracts/AppError";
import { createDemoState, type DemoState, type PlanDemoState } from "@/data/mock/createDemoState";
import type { LearningCard } from "@/domain/learning";
import type { Book, LearningPlan } from "@/domain/books";
import type {
  PrecomputedQuizResult,
  QuizIdempotencyRecord,
  QuizSession,
  QuizSessionResult,
  QuizSkill
} from "@/domain/quiz";

export const DEMO_STORAGE_KEY = "wordflip.web.demo.v2";

export interface DemoStateStoreOptions {
  initialState?: DemoState;
  storage?: Storage | null;
}

type UnknownRecord = Record<string, unknown>;

function clone<T>(value: T): T {
  return structuredClone(value);
}

function browserStorage(): Storage | null {
  return typeof window === "undefined" ? null : window.localStorage;
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function isSkillProgress(value: unknown, skill: QuizSkill): boolean {
  return (
    isRecord(value) &&
    value.skill === skill &&
    (value.state === "unlearned" || value.state === "fuzzy" || value.state === "unknown") &&
    isNumber(value.stability) &&
    (value.heatLevel === 0 || value.heatLevel === 1 || value.heatLevel === 2 || value.heatLevel === 3) &&
    typeof value.lastQuizSucceeded === "boolean"
  );
}

function isLearningCard(value: unknown): value is LearningCard {
  return (
    isRecord(value) &&
    typeof value.cardId === "string" &&
    typeof value.wordKey === "string" &&
    typeof value.headword === "string" &&
    typeof value.phonetic === "string" &&
    typeof value.definition === "string" &&
    typeof value.example === "string" &&
    typeof value.imageDescription === "string" &&
    isRecord(value.progress) &&
    isSkillProgress(value.progress.dictation, "dictation") &&
    isSkillProgress(value.progress.choice, "choice")
  );
}

function isLearningPlan(value: unknown): value is LearningPlan {
  return isRecord(value) && typeof value.planId === "string" && typeof value.bookId === "string" && typeof value.title === "string";
}

function isBook(value: unknown): value is Book {
  return isRecord(value) && typeof value.bookId === "string" && typeof value.title === "string" && isNumber(value.cardCount);
}

function isTodaySnapshot(value: unknown): boolean {
  return (
    isRecord(value) &&
    isNumber(value.dueCount) &&
    isNumber(value.masteredCount) &&
    isNumber(value.reviewedCount) &&
    isNumber(value.completionRate) &&
    typeof value.currentBookTitle === "string" &&
    Array.isArray(value.recentStudy) &&
    value.recentStudy.every(
      (item) =>
        isRecord(item) &&
        typeof item.cardId === "string" &&
        typeof item.headword === "string" &&
        typeof item.definition === "string" &&
        typeof item.reviewedAtLabel === "string"
    ) &&
    Array.isArray(value.tasks) &&
    value.tasks.every(
      (task) =>
        isRecord(task) &&
        typeof task.taskId === "string" &&
        typeof task.title === "string" &&
        typeof task.description === "string"
    )
  );
}

function isStatsSnapshot(value: unknown): boolean {
  return (
    isRecord(value) &&
    isNumber(value.totalReviewed) &&
    isNumber(value.retentionRate) &&
    isNumber(value.streakDays)
  );
}

function isQuizSession(value: unknown): value is QuizSession {
  if (
    !isRecord(value) ||
    typeof value.sessionId !== "string" ||
    (value.status !== "active" && value.status !== "completed") ||
    (value.skill !== "dictation" && value.skill !== "choice") ||
    (value.scope !== "current-plan" && value.scope !== "due-today") ||
    !isNumber(value.totalQuestions) ||
    !isNumber(value.currentIndex) ||
    !isNumber(value.score) ||
    typeof value.progressLabel !== "string" ||
    !isRecord(value.question)
  ) {
    return false;
  }
  const question = value.question;
  return (
    typeof question.questionId === "string" &&
    isNumber(question.questionIndex) &&
    typeof question.cardId === "string" &&
    question.skill === value.skill &&
    typeof question.prompt === "string" &&
    typeof question.hint === "string" &&
    (question.options === undefined ||
      (Array.isArray(question.options) &&
        question.options.every(
          (option) =>
            isRecord(option) &&
            typeof option.key === "string" &&
            typeof option.label === "string"
        )))
  );
}

function isQuizSkillSummary(value: unknown): boolean {
  return (
    isRecord(value) &&
    typeof value.label === "string" &&
    isNumber(value.attempted) &&
    isNumber(value.correct) &&
    typeof value.progressLabel === "string"
  );
}

function isQuizSessionResult(value: unknown): value is QuizSessionResult {
  return (
    isRecord(value) &&
    typeof value.sessionId === "string" &&
    value.status === "completed" &&
    isNumber(value.score) &&
    isNumber(value.total) &&
    isNumber(value.accuracy) &&
    (value.rating === "excellent" || value.rating === "good" || value.rating === "keep_going") &&
    isQuizSkillSummary(value.dictation) &&
    isQuizSkillSummary(value.choice)
  );
}

function isPrecomputedQuizResult(value: unknown): value is PrecomputedQuizResult {
  return (
    isRecord(value) &&
    typeof value.cardId === "string" &&
    (value.skill === "dictation" || value.skill === "choice") &&
    isSkillProgress(value.next, value.skill) &&
    typeof value.correct === "boolean" &&
    typeof value.feedback === "string" &&
    (value.expectedAnswer === null || typeof value.expectedAnswer === "string") &&
    isQuizSession(value.sessionSnapshot) &&
    isQuizSessionResult(value.resultSnapshot) &&
    isTodaySnapshot(value.dashboardSnapshot) &&
    isStatsSnapshot(value.statsSnapshot)
  );
}

function isQuizIdempotencyRecord(value: unknown): value is QuizIdempotencyRecord {
  if (
    !isRecord(value) ||
    typeof value.requestId !== "string" ||
    typeof value.userId !== "string" ||
    typeof value.planId !== "string" ||
    typeof value.sessionId !== "string" ||
    typeof value.questionId !== "string" ||
    typeof value.cardId !== "string" ||
    typeof value.answer !== "string" ||
    !isRecord(value.response)
  ) {
    return false;
  }
  return (
    value.response.requestId === value.requestId &&
    value.response.accepted === true &&
    typeof value.response.correct === "boolean" &&
    typeof value.response.feedback === "string" &&
    (value.response.expectedAnswer === null || typeof value.response.expectedAnswer === "string") &&
    isPrecomputedQuizResult(value.response.precomputed) &&
    value.response.correct === value.response.precomputed.correct &&
    value.response.feedback === value.response.precomputed.feedback &&
    value.response.expectedAnswer === value.response.precomputed.expectedAnswer &&
    value.sessionId === value.response.precomputed.sessionSnapshot.sessionId &&
    value.questionId === value.response.precomputed.sessionSnapshot.question.questionId &&
    value.cardId === value.response.precomputed.cardId
  );
}

function isPlanState(value: unknown): boolean {
  if (!isRecord(value) || !isRecord(value.groups) || !Array.isArray(value.groups.items)) {
    return false;
  }
  if (!value.groups.items.every((group) => isRecord(group) && typeof group.groupId === "string" && typeof group.name === "string" && Array.isArray(group.cardIds) && group.cardIds.every((id) => typeof id === "string"))) {
    return false;
  }
  const cards = value.cards;
  if (!isRecord(cards) || !isRecord(cards.byCardId) || !isRecord(cards.byWordKey)) {
    return false;
  }
  const byCardId = cards.byCardId;
  const byWordKey = cards.byWordKey;
  if (!Object.entries(byCardId).every(([cardId, card]) => isLearningCard(card) && card.cardId === cardId)) {
    return false;
  }
  const cardIdsByWordKey = new Set<string>();
  if (!Object.entries(byWordKey).every(([wordKey, card]) => {
    if (!isLearningCard(card) || card.wordKey !== wordKey || byCardId[card.cardId] === undefined) {
      return false;
    }
    cardIdsByWordKey.add(card.cardId);
    return true;
  })) {
    return false;
  }
  // 两个索引必须覆盖完全相同的 cardId 集合，避免持久化数据缺失查询入口后被静默修补。
  if (cardIdsByWordKey.size !== Object.keys(byCardId).length || !Object.keys(byCardId).every((cardId) => cardIdsByWordKey.has(cardId))) {
    return false;
  }
  if (!isTodaySnapshot(value.today)) {
    return false;
  }
  if (
    !isRecord(value.bookProgress) ||
    !isNumber(value.bookProgress.learnedCount) ||
    !isNumber(value.bookProgress.publishedCardCount) ||
    !isNumber(value.bookProgress.completionRate)
  ) {
    return false;
  }
  if (
    !isRecord(value.study) ||
    !isRecord(value.study.sessions) ||
    !Object.values(value.study.sessions).every(
      (session) =>
        isRecord(session) &&
        typeof session.sessionId === "string" &&
        (session.status === "active" || session.status === "completed") &&
        Array.isArray(session.cardIds) &&
        session.cardIds.every((cardId) => typeof cardId === "string") &&
        typeof session.progressLabel === "string" &&
        Array.isArray(session.queueSummary) &&
        session.queueSummary.every(
          (item) => isRecord(item) && typeof item.label === "string" && typeof item.value === "string"
        )
    ) ||
    !isTodaySnapshot(value.study.afterStudySession)
  ) {
    return false;
  }
  if (
    !isRecord(value.quiz) ||
    !isRecord(value.quiz.sessions) ||
    !Object.entries(value.quiz.sessions).every(
      ([sessionId, session]) =>
        isQuizSession(session) &&
        session.sessionId === sessionId &&
        byCardId[session.question.cardId] !== undefined
    ) ||
    !isRecord(value.quiz.results) ||
    !Object.entries(value.quiz.results).every(
      ([sessionId, result]) =>
        isQuizSessionResult(result) &&
        result.sessionId === sessionId &&
        value.quiz !== null &&
        isRecord(value.quiz) &&
        isRecord(value.quiz.sessions) &&
        isQuizSession(value.quiz.sessions[sessionId]) &&
        value.quiz.sessions[sessionId].status === "completed"
    ) ||
    !Array.isArray(value.quiz.idempotency) ||
    !value.quiz.idempotency.every(isQuizIdempotencyRecord)
  ) {
    return false;
  }
  if (!isRecord(value.media) || !isRecord(value.media.byCardId) || !Object.values(value.media.byCardId).every((media) => isRecord(media) && typeof media.cardId === "string" && (media.imageUrl === null || typeof media.imageUrl === "string") && (media.stainLevel === 0 || media.stainLevel === 1 || media.stainLevel === 2 || media.stainLevel === 3))) {
    return false;
  }
  return isStatsSnapshot(value.stats);
}

/** 校验同版本数据的完整形状，避免截断 payload 通过版本检查后污染运行态。 */
function isCompatibleState(value: unknown): value is DemoState {
  if (!isRecord(value) || value.schemaVersion !== 2 || !isRecord(value.clock) || typeof value.clock.today !== "string") {
    return false;
  }
  if (!isRecord(value.auth) || (value.auth.session !== null && (!isRecord(value.auth.session) || typeof value.auth.session.userId !== "string" || typeof value.auth.session.displayName !== "string" || typeof value.auth.session.authenticated !== "boolean"))) {
    return false;
  }
  if (!isRecord(value.settings) || typeof value.settings.soundEnabled !== "boolean" || typeof value.settings.reducedMotion !== "boolean") {
    return false;
  }
  const books = value.books;
  const planStates = value.planStates;
  if (!isRecord(books) || (books.activePlanId !== null && typeof books.activePlanId !== "string") || !Array.isArray(books.plans) || !Array.isArray(books.items) || !isRecord(planStates)) {
    return false;
  }
  const plans = books.plans;
  if (!plans.every(isLearningPlan) || !books.items.every(isBook)) {
    return false;
  }
  const planIds = plans.map((plan) => plan.planId);
  if (
    (books.activePlanId !== null && !planIds.includes(books.activePlanId)) ||
    !planIds.every((planId) => isPlanState(planStates[planId])) ||
    !Object.keys(planStates).every((planId) => planIds.includes(planId))
  ) {
    return false;
  }
  const requestIds = new Set<string>();
  for (const planId of planIds) {
    const planState = planStates[planId] as PlanDemoState;
    for (const record of planState.quiz.idempotency) {
      if (record.planId !== planId || requestIds.has(record.requestId)) {
        return false;
      }
      requestIds.add(record.requestId);
    }
  }
  return true;
}

/** wordKey 只保留为查询索引；卡片与记忆的权威记录始终位于 cardId 索引。 */
function normalizeCardIndexes(state: DemoState): DemoState {
  for (const planState of Object.values(state.planStates)) {
    const byWordKey: Record<string, LearningCard> = {};
    for (const card of Object.values(planState.cards.byCardId)) {
      byWordKey[card.wordKey] = card;
    }
    planState.cards.byWordKey = byWordKey;
  }
  return state;
}

function conflict(message: string): AppError {
  return { kind: "conflict", message };
}

/** 演示状态只持久化或回放服务端快照，不在 Web 端执行任何学习算法。 */
export class DemoStateStore {
  private state: DemoState;
  private readonly storage: Storage | null;

  constructor(options: DemoStateStoreOptions = {}) {
    this.storage = options.storage === undefined ? browserStorage() : options.storage;
    this.state = normalizeCardIndexes(clone(options.initialState ?? createDemoState()));
    this.restore();
  }

  read(): DemoState {
    return clone(this.state);
  }

  readActivePlanState(): PlanDemoState | null {
    const state = this.read();
    return state.books.activePlanId ? state.planStates[state.books.activePlanId] ?? null : null;
  }

  write(state: DemoState): void {
    this.state = normalizeCardIndexes(clone(state));
    this.storage?.setItem(DEMO_STORAGE_KEY, JSON.stringify(this.state));
  }

  reset(): void {
    this.write(createDemoState());
  }

  update(mutator: (draft: DemoState) => void): void {
    const draft = this.read();
    mutator(draft);
    this.write(draft);
  }

  updateActivePlan(mutator: (draft: PlanDemoState) => void): void {
    this.update((draft) => {
      mutator(this.requireActivePlanState(draft));
    });
  }

  switchActivePlan(planId: string) {
    const state = this.read();
    const plan = state.books.plans.find((candidate) => candidate.planId === planId);
    if (!plan || !state.planStates[planId]) {
      throw conflict("找不到指定学习计划");
    }
    // 只切换指针，所有计划分区原样保留，因此切回时能够恢复各自历史。
    state.books.activePlanId = planId;
    this.write(state);
    return clone(plan);
  }

  applyQuizResult(result: PrecomputedQuizResult, idempotency?: QuizIdempotencyRecord): void {
    if (result.next.skill !== result.skill) {
      throw {
        kind: "validation",
        message: "服务端预计算结果的 skill 不一致",
        fieldErrors: { skill: "next.skill 必须与 result.skill 一致" }
      } satisfies AppError;
    }
    this.update((state) => {
      const activePlanId = state.books.activePlanId;
      const draft = this.requireActivePlanState(state);
      const card = draft.cards.byCardId[result.cardId];
      const currentSession = draft.quiz.sessions[result.sessionSnapshot.sessionId];
      if (!card) {
        throw conflict(`当前计划不存在学习卡：${result.cardId}`);
      }
      if (
        !currentSession ||
        currentSession.question.questionId !== result.sessionSnapshot.question.questionId ||
        currentSession.question.cardId !== result.cardId ||
        result.sessionSnapshot.question.cardId !== result.cardId ||
        result.sessionSnapshot.skill !== result.skill ||
        result.resultSnapshot.sessionId !== result.sessionSnapshot.sessionId
      ) {
        throw conflict("服务端预计算结果与测验会话绑定不一致");
      }
      if (idempotency) {
        if (
          !activePlanId ||
          idempotency.planId !== activePlanId ||
          idempotency.userId !== state.auth.session?.userId ||
          idempotency.sessionId !== result.sessionSnapshot.sessionId ||
          idempotency.questionId !== result.sessionSnapshot.question.questionId ||
          idempotency.cardId !== result.cardId ||
          idempotency.response.requestId !== idempotency.requestId ||
          idempotency.response.precomputed.cardId !== result.cardId
        ) {
          throw conflict("测验幂等记录与当前用户、计划或会话不一致");
        }
      }

      // 仅复制服务端给出的指定轨道和汇总快照，绝不在演示层推导 FSRS 或统计。
      card.progress[result.skill] = clone(result.next);
      draft.today = clone(result.dashboardSnapshot);
      draft.stats = clone(result.statsSnapshot);
      draft.quiz.sessions[result.sessionSnapshot.sessionId] = clone(result.sessionSnapshot);
      draft.quiz.results[result.resultSnapshot.sessionId] = clone(result.resultSnapshot);
      if (idempotency) {
        draft.quiz.idempotency.push(clone(idempotency));
      }
    });
  }

  private requireActivePlanState(state: DemoState): PlanDemoState {
    const planId = state.books.activePlanId;
    const planState = planId ? state.planStates[planId] : undefined;
    if (!planState) {
      throw conflict("当前没有可用学习计划");
    }
    return planState;
  }

  private restore(): void {
    const serialized = this.storage?.getItem(DEMO_STORAGE_KEY);
    if (!serialized) {
      return;
    }
    try {
      const parsed: unknown = JSON.parse(serialized);
      if (isCompatibleState(parsed)) {
        this.state = normalizeCardIndexes(parsed);
        return;
      }
    } catch {
      // 损坏 JSON 与同版本截断 payload 都恢复为可预测的固定种子。
    }
    this.reset();
  }
}

export function createDemoStateStore(options?: DemoStateStoreOptions): DemoStateStore {
  return new DemoStateStore(options);
}
