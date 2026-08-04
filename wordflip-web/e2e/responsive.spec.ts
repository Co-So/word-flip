import { expect, test } from "@playwright/test";
import {
  expectNoHorizontalOverflow,
  scenarios
} from "./helpers";

const widths = [1440, 1280, 1024, 768] as const;
const appPages = [
  ["Today", scenarios.configured],
  ["Media", scenarios.media],
  ["Settings", scenarios.settings]
] as const;
const focusPages = [
  ["Study", scenarios.study],
  ["Quiz", scenarios.quiz]
] as const;

for (const width of widths) {
  for (const [name, path] of appPages) {
    test(`${name} 在 ${width}px 无横向滚动且 AppShell 正确重排`, async ({
      page
    }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(path);
      await expect(page.getByTestId("app-shell")).toBeVisible();
      await expect(page.getByTestId("page-content")).toBeVisible();
      await expectNoHorizontalOverflow(page);

      const sidebar = page.getByTestId("app-shell").locator("aside").first();
      const content = page.getByTestId("page-content");
      const sidebarBox = await sidebar.boundingBox();
      const contentBox = await content.boundingBox();
      expect(sidebarBox).not.toBeNull();
      expect(contentBox).not.toBeNull();
      if (width < 1024) {
        expect(sidebarBox!.y).toBeLessThan(contentBox!.y);
      } else {
        expect(sidebarBox!.x).toBeLessThan(contentBox!.x);
      }
    });
  }

  for (const [name, path] of focusPages) {
    test(`${name} 在 ${width}px 无横向滚动且 FocusShell 正确堆叠`, async ({
      page
    }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(path);
      const content = page.getByTestId("page-content");
      const aside = page.getByRole("complementary");
      await expect(content).toBeVisible();
      await expect(aside).toBeVisible();
      await expectNoHorizontalOverflow(page);

      const contentBox = await content.boundingBox();
      const asideBox = await aside.boundingBox();
      expect(contentBox).not.toBeNull();
      expect(asideBox).not.toBeNull();
      if (width < 900) {
        expect(asideBox!.y).toBeGreaterThanOrEqual(
          contentBox!.y + contentBox!.height
        );
      } else {
        expect(asideBox!.x).toBeGreaterThan(contentBox!.x);
      }
    });
  }
}

test("桌面滚动主内容时侧栏保持在视口中", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 600 });
  await page.goto(scenarios.settings);
  await page.getByTestId("page-content").evaluate((content) => {
    const spacer = document.createElement("div");
    spacer.style.height = "1200px";
    content.append(spacer);
  });

  const sidebar = page.getByTestId("app-shell").locator("aside").first();
  const initialBox = await sidebar.boundingBox();
  expect(initialBox).not.toBeNull();

  await page.evaluate(() => window.scrollTo(0, 400));
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);

  const scrolledBox = await sidebar.boundingBox();
  expect(scrolledBox).not.toBeNull();
  expect(scrolledBox!.y).toBeCloseTo(initialBox!.y, 0);
});

test("减少动态效果时学习卡不保留 transform 动画", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(scenarios.study);
  const card = page.getByRole("button", {
    name: "翻转 sustainable 学习卡查看释义"
  });
  await expect(card).toBeVisible();
  const motion = await card.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      transitionProperty: style.transitionProperty,
      transitionDuration: style.transitionDuration
    };
  });
  expect(motion.transitionProperty).not.toContain("transform");
  expect(motion.transitionDuration).toBe("0s");
});

test("学习卡片墙在桌面和手机使用响应式列数", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(scenarios.study);
  const wall = page.getByTestId("study-card-wall");
  await expect(wall).toBeVisible();
  const desktopColumns = await wall.evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(" ").length
  );
  expect(desktopColumns).toBeGreaterThanOrEqual(3);
  await expectNoHorizontalOverflow(page);

  await page.setViewportSize({ width: 390, height: 844 });
  const mobileColumns = await wall.evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(" ").length
  );
  expect(mobileColumns).toBe(2);
  await expectNoHorizontalOverflow(page);
});
