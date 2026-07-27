import { expect, test } from "@playwright/test";
import { collectPageErrors, scenarios } from "./helpers";

test("登录到统计变化的完整演示流程", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.loggedOut);

  await expect(page.getByRole("heading", { name: "登录 WordFlip" })).toBeVisible();
  await page.getByLabel("邮箱").fill("demo@wordflip.local");
  await page.getByLabel("密码").fill("wordflip-demo");
  await page.getByRole("button", { name: "登录" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByRole("radio", { name: "雅思核心词汇" }).check();
  await page.getByRole("button", { name: "完成设置" }).click();

  await expect(page).toHaveURL(/\/today$/);
  await expect(page.getByRole("heading", { name: "今天继续前进" })).toBeVisible();
  const beforeStudy = await page.getByLabel("今日摘要").innerText();
  await page.getByRole("link", { name: "开始今日学习" }).click();

  await expect(page).toHaveURL(/\/study\/study-demo$/);
  await expect(page.getByRole("heading", { name: "专注学习" })).toBeVisible();
  await page.keyboard.press("Space");
  await expect(page.getByText("可持续的", { exact: true })).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(page.getByRole("button", { name: /翻转 .+ 学习卡/ })).toBeVisible();
  await page.getByRole("button", { name: "完成学习" }).click();

  await expect(page).toHaveURL(/\/study\/study-demo\/complete$/);
  await expect(page.getByRole("heading", { name: "本次学习已完成" })).toBeVisible();
  await page.getByRole("link", { name: "进入 Quiz" }).click();

  await expect(page).toHaveURL(/\/quiz$/);
  await page.getByRole("button", { name: "开始听写测验" }).click();
  await expect(page).toHaveURL(/\/quiz\/quiz-dictation-1$/);
  await page.getByLabel("输入英文单词").fill("sustainable");
  await page.getByRole("button", { name: "提交答案" }).click();
  await page.getByRole("link", { name: "查看测验结果" }).click();

  await expect(page).toHaveURL(/\/quiz\/quiz-dictation-1\/result$/);
  await expect(page.getByRole("heading", { name: "测验完成" })).toBeVisible();
  await page.getByRole("link", { name: "查看统计" }).click();

  await expect(page).toHaveURL(/\/stats$/);
  await expect(page.getByText("听写进度")).toBeVisible();
  const totalReviewed = page
    .getByLabel("学习摘要")
    .getByRole("listitem")
    .filter({ hasText: "累计复习" });
  await expect(totalReviewed).toContainText("1");
  await page.reload();
  await expect(page.getByText("听写进度")).toBeVisible();
  await expect(totalReviewed).toContainText("1");

  await page.getByRole("link", { name: "今日" }).click();
  await expect(page.getByLabel("今日摘要")).not.toHaveText(beforeStudy);
  expect(pageErrors).toEqual([]);
});
