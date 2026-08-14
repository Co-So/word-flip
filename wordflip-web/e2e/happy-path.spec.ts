import { expect, test } from "@playwright/test";
import { collectPageErrors, scenarios } from "./helpers";

test("登录并完成首次设置后进入今日", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.loggedOut);

  await expect(page.getByRole("heading", { name: "登录 WordFlip" })).toBeVisible();
  await page.getByLabel("邮箱或手机号").fill("demo@wordflip.local");
  await page.getByLabel("密码").fill("wordflip-demo");
  await page.getByRole("button", { name: "登录" }).click();

  await expect(page).toHaveURL(/\/onboarding$/);
  await page.getByRole("radio", { name: "雅思核心词汇" }).check();
  await page.getByRole("button", { name: "完成设置" }).click();

  await expect(page).toHaveURL(/\/today$/);
  await expect(page.getByRole("heading", { name: "今天继续前进" })).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("今日任务可进入真实当前计划分组", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.configured);
  await page.goto("/today");
  await page.getByRole("link", { name: /第 12 组/ }).first().click();

  await expect(page).toHaveURL(/\/groups\/group-12$/);
  await expect(page.getByRole("heading", { name: "第 12 组 · 城市与环境" })).toBeVisible();
  expect(pageErrors).toEqual([]);
});

test("自定义分组路由可加载候选卡且不提交", async ({ page }) => {
  const pageErrors = collectPageErrors(page);
  await page.goto(scenarios.configured);
  await page.goto("/groups");
  await page.getByRole("link", { name: "新建自定义分组" }).click();

  await expect(page).toHaveURL(/\/groups\/new$/);
  await expect(page.getByRole("heading", { name: "新建自定义分组" })).toBeVisible();
  await expect(page.getByRole("group", { name: "选择学习卡" })).toBeVisible();
  await expect(page.getByRole("button", { name: "保存分组" })).toBeEnabled();
  expect(pageErrors).toEqual([]);
});
