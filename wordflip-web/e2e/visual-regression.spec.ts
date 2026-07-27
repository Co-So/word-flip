import { expect, test } from "@playwright/test";
import { scenarios } from "./helpers";

const entries = [
  { name: "today", path: scenarios.configured },
  { name: "study", path: scenarios.study },
  { name: "quiz", path: scenarios.quiz },
  { name: "stats", path: scenarios.stats },
  { name: "empty-today", path: scenarios.emptyToday }
] as const;

for (const entry of entries) {
  test(`${entry.name} 视觉基线`, async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(entry.path);
    const content = page.getByTestId("page-content");
    await expect(content).toBeVisible();
    await expect(content).toHaveScreenshot(`${entry.name}-1440.png`, {
      animations: "disabled",
      caret: "hide"
    });
  });
}
