import { expect, test } from "@playwright/test";
import { collectPageErrors, scenarios } from "./helpers";

test("登录失败保留账号并给出安全恢复动作", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.loggedOut);
  await page.getByLabel("邮箱或手机号").fill("demo@wordflip.local");
  await page.getByLabel("密码").fill("wrong-password");
  await page.getByRole("button", { name: "登录" }).click();

  await expect(page.getByRole("alert")).toHaveText("账号或密码错误");
  await expect(page.getByLabel("邮箱或手机号")).toHaveValue("demo@wordflip.local");
  await expect(page.getByRole("button", { name: "登录" })).toBeEnabled();
  expect(pageErrors).toEqual([]);
});

test("无今日任务状态有说明和恢复动作", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.emptyToday);
  await expect(page.getByRole("heading", { name: "今天的任务已完成" })).toBeVisible();
  await expect(page.getByText("今天没有待完成的复习或学习任务。")).toBeVisible();
  await expect(page.getByRole("link", { name: "浏览词书" })).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("空词书状态有说明和恢复动作", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.emptyBooks);
  await expect(page.getByRole("heading", { name: "还没有可用词书" })).toBeVisible();
  await expect(page.getByText("演示数据中暂时没有可创建计划的已发布词书。")).toBeVisible();
  await expect(page.getByRole("link", { name: "返回首次设置" })).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("已完成测验直接显示固定结果和后续动作", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.quizComplete);
  await expect(page.getByRole("heading", { name: "测验完成" })).toBeVisible();
  await expect(page.getByRole("link", { name: "查看统计" })).toBeVisible();
  await expect(page.getByRole("link", { name: "返回 Today" })).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("无效学习会话有解释和恢复动作", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(
    "/__demo/scenario/configured?next=/study/not-a-real-session"
  );
  await expect(page.getByRole("heading", { name: "无法继续本次学习" })).toBeVisible();
  await expect(page.getByText("找不到学习会话")).toBeVisible();
  await expect(page.getByRole("link", { name: "返回 Today" })).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("学习退出确认框使用可读的实体表面和遮罩", async ({ page }) => {
  await page.goto(scenarios.study);
  await page.getByRole("button", { name: "退出" }).click();

  const dialog = page.getByRole("dialog", { name: "退出本次学习？" });
  await expect(dialog).toBeVisible();
  const appearance = await dialog.evaluate((element) => ({
    backdropBackground: getComputedStyle(element.parentElement!).backgroundColor,
    dialogBackground: getComputedStyle(element).backgroundColor
  }));

  expect(appearance.backdropBackground).toBe("rgba(48, 45, 41, 0.42)");
  expect(appearance.dialogBackground).toBe("rgb(253, 251, 247)");
});
