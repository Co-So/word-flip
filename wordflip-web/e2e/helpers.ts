import { expect, type Page } from "@playwright/test";

export const scenarios = {
  loggedOut: "/__demo/scenario/logged-out?next=/login",
  configured: "/__demo/scenario/configured?next=/today",
  emptyToday: "/__demo/scenario/empty-today?next=/today",
  emptyBooks: "/__demo/scenario/empty-books?next=/books",
  quizComplete:
    "/__demo/scenario/quiz-complete?next=/quiz/quiz-dictation-1/result",
  study: "/__demo/scenario/configured?next=/study/study-demo",
  quiz:
    "/__demo/scenario/quiz-dictation?next=/quiz/quiz-dictation-1",
  stats: "/__demo/scenario/configured?next=/stats",
  media: "/__demo/scenario/configured?next=/media",
  settings: "/__demo/scenario/configured?next=/settings"
} as const;

export function collectPageErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

export async function expectNoHorizontalOverflow(page: Page) {
  const sizes = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    scroll: document.documentElement.scrollWidth
  }));
  expect(sizes.scroll).toBeLessThanOrEqual(sizes.viewport);
}
