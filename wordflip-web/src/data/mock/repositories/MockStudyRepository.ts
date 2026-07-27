import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type {
  LearningCard,
  StudyRepository,
  StudySession,
  StudySessionView
} from "@/domain/learning";

function notFound(message: string): AppError {
  return { kind: "not-found", message };
}

function noActivePlan(): AppError {
  return { kind: "conflict", message: "当前没有可用学习计划" };
}

/** 学习仓储只水合当前计划的 cardId 队列，并回放预计算的完成快照。 */
export class MockStudyRepository implements StudyRepository {
  constructor(private readonly store: DemoStateStore) {}

  getCards(): Promise<LearningCard[]> {
    const plan = this.store.readActivePlanState();
    return plan
      ? Promise.resolve(structuredClone(Object.values(plan.cards.byCardId)))
      : Promise.reject(noActivePlan());
  }

  getSession(sessionId: string): Promise<StudySessionView> {
    const plan = this.store.readActivePlanState();
    if (!plan) {
      return Promise.reject(noActivePlan());
    }
    const session = plan.study.sessions[sessionId];
    if (!session) {
      return Promise.reject(notFound("找不到学习会话"));
    }
    const cards = session.cardIds.map((cardId) => plan.cards.byCardId[cardId]);
    if (cards.some((card) => !card)) {
      return Promise.reject(notFound("学习会话包含无效学习卡"));
    }
    return Promise.resolve({
      ...structuredClone(session),
      cards: structuredClone(cards.filter((card): card is LearningCard => card !== undefined))
    });
  }

  completeSession(sessionId: string): Promise<StudySession> {
    const plan = this.store.readActivePlanState();
    if (!plan) {
      return Promise.reject(noActivePlan());
    }
    if (!plan.study.sessions[sessionId]) {
      return Promise.reject(notFound("找不到学习会话"));
    }

    // 完成浏览会话只复制服务端预计算的展示快照，不写任一 skill 记忆或复习事件。
    this.store.updateActivePlan((draft) => {
      draft.study.sessions[sessionId].status = "completed";
      draft.today = structuredClone(draft.study.afterStudySession);
    });
    return Promise.resolve(
      structuredClone(this.store.readActivePlanState()!.study.sessions[sessionId])
    );
  }
}
