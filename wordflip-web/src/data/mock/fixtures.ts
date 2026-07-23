import type { RepositoryBundle } from "@/data/contracts/RepositoryBundle";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { PrecomputedQuizResult } from "@/domain/quiz";

export const FIXED_DICTATION_RESULT: PrecomputedQuizResult = {
  wordKey: "sustainable",
  skill: "dictation",
  next: {
    skill: "dictation",
    state: "fuzzy",
    stability: 30,
    heatLevel: 1,
    lastQuizSucceeded: true
  },
  dashboardSnapshot: { dueCount: 23, masteredCount: 127, reviewedCount: 19 },
  statsSnapshot: { totalReviewed: 843, retentionRate: 0.901, streakDays: 14 }
};

function notFound(message: string) {
  return { kind: "not-found" as const, message };
}

/** 供视觉演示和测试注入的仓储实现；它仅回放固定数据，便于日后替换为 HTTP 实现。 */
export function createMockRepositoryBundle(store: DemoStateStore): RepositoryBundle {
  return {
    auth: {
      getSession: () => Promise.resolve(store.read().auth.session),
      signOut: () => {
        store.update((draft) => {
          draft.auth.session = null;
        });
        return Promise.resolve({ signedOut: true as const });
      }
    },
    settings: {
      getSettings: () => Promise.resolve(store.read().settings),
      updateSettings: (settings) => {
        store.update((draft) => {
          draft.settings = structuredClone(settings);
        });
        return Promise.resolve(store.read().settings);
      }
    },
    books: {
      listBooks: () => Promise.resolve(store.read().books.items),
      getActivePlan: () => Promise.resolve(store.read().books.activePlan),
      switchActivePlan: (planId) => {
        const next = store.read().books.items.find((book) => `plan-${book.bookId}` === planId);
        if (!next) {
          return Promise.reject(notFound("找不到指定学习计划"));
        }
        const plan = { planId, bookId: next.bookId, title: next.title };
        store.update((draft) => {
          draft.books.activePlan = plan;
        });
        return Promise.resolve(plan);
      }
    },
    groups: {
      listGroups: () => Promise.resolve(store.read().groups.items),
      appendMembers: (groupId, cardIds) => {
        let found = false;
        store.update((draft) => {
          const group = draft.groups.items.find((item) => item.groupId === groupId);
          if (!group) {
            return;
          }
          found = true;
          // 分组成员遵循追加语义，不以新数组覆盖现有成员。
          group.cardIds.push(...cardIds.filter((cardId) => !group.cardIds.includes(cardId)));
        });
        const group = store.read().groups.items.find((item) => item.groupId === groupId);
        return found && group ? Promise.resolve(group) : Promise.reject(notFound("找不到指定分组"));
      }
    },
    today: {
      getSummary: () => Promise.resolve(store.read().today)
    },
    study: {
      getCards: () => Promise.resolve(Object.values(store.read().cards.byCardId)),
      getSession: (sessionId) => {
        const session = store.read().study.sessions[sessionId];
        return session ? Promise.resolve(session) : Promise.reject(notFound("找不到学习会话"));
      },
      completeSession: (sessionId) => {
        const session = store.read().study.sessions[sessionId];
        if (!session) {
          return Promise.reject(notFound("找不到学习会话"));
        }
        // 完成学习仅更新会话展示状态，不写任何 skill 记忆。
        store.update((draft) => {
          draft.study.sessions[sessionId].status = "completed";
        });
        return Promise.resolve(store.read().study.sessions[sessionId]);
      }
    },
    quiz: {
      submitAnswer: () =>
        Promise.reject({
          kind: "unavailable" as const,
          message: "演示仓储不判题；请使用服务端返回的预计算结果。",
          retryable: true as const
        })
    },
    media: {
      getMedia: (cardId) => {
        const media = store.read().media.byCardId[cardId];
        return media ? Promise.resolve(media) : Promise.reject(notFound("找不到卡片媒体"));
      },
      saveMedia: (media) => {
        store.update((draft) => {
          draft.media.byCardId[media.cardId] = structuredClone(media);
        });
        return Promise.resolve(store.read().media.byCardId[media.cardId]);
      }
    },
    stats: {
      getSummary: () => Promise.resolve(store.read().stats)
    }
  };
}
