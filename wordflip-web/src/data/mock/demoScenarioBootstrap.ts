import {
  createDemoState,
  type DemoScenario
} from "@/data/mock/createDemoState";
import { DEMO_STORAGE_KEY } from "@/data/mock/DemoStateStore";

const SCENARIO_PREFIX = "/__demo/scenario/";

const scenarios = new Set<DemoScenario>([
  "logged-out",
  "configured",
  "empty-today",
  "empty-books",
  "quiz-complete",
  "quiz-dictation",
  "quiz-choice",
  "after-quiz",
  "mutated"
]);

const nextPaths = new Set([
  "/login",
  "/register",
  "/onboarding",
  "/today",
  "/books",
  "/groups",
  "/quiz",
  "/media",
  "/stats",
  "/settings",
  "/study/study-demo",
  "/study/not-a-real-session",
  "/study/study-demo/complete",
  "/quiz/quiz-dictation-1",
  "/quiz/quiz-choice-1",
  "/quiz/quiz-dictation-1/result",
  "/quiz/quiz-choice-1/result"
]);

const defaultNext: Partial<Record<DemoScenario, string>> = {
  "logged-out": "/login",
  "quiz-complete": "/quiz/quiz-dictation-1/result",
  "quiz-dictation": "/quiz/quiz-dictation-1",
  "quiz-choice": "/quiz/quiz-choice-1"
};

export interface DemoScenarioBootstrapOptions {
  isDev: boolean;
  location: Pick<Location, "pathname" | "search">;
  history: Pick<History, "replaceState">;
  storage: Pick<Storage, "setItem">;
}

/** 开发模式下以白名单场景覆盖本地快照，并把地址替换为安全的站内页面。 */
export function bootstrapDemoScenario({
  isDev,
  location,
  history,
  storage
}: DemoScenarioBootstrapOptions): boolean {
  if (!isDev || !location.pathname.startsWith(SCENARIO_PREFIX)) {
    return false;
  }

  const scenarioName = location.pathname.slice(SCENARIO_PREFIX.length);
  if (!scenarios.has(scenarioName as DemoScenario)) {
    return false;
  }

  const scenario = scenarioName as DemoScenario;
  const requestedNext = new URLSearchParams(location.search).get("next");
  const next =
    requestedNext && nextPaths.has(requestedNext)
      ? requestedNext
      : defaultNext[scenario] ?? "/today";

  storage.setItem(DEMO_STORAGE_KEY, JSON.stringify(createDemoState(scenario)));
  history.replaceState(null, "", next);
  return true;
}
