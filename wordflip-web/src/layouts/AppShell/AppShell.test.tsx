import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { AppShell } from "@/layouts/AppShell/AppShell";
import { CommandPalette } from "@/components/CommandPalette/CommandPalette";
import appShellCss from "./AppShell.module.css?raw";
import commandPaletteSource from "@/components/CommandPalette/CommandPalette.tsx?raw";
import globalCss from "@/design-system/global.css?raw";
import tokensCss from "@/design-system/tokens.css?raw";
import { vi } from "vitest";

const routerFuture = { v7_relativeSplatPath: true, v7_startTransition: true };

function RouteProbe() {
  const location = useLocation();
  return <output data-testid="route-path">{location.pathname}</output>;
}

test("左侧导航只显示五个稳定入口", () => {
  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  expect(screen.getAllByRole("link")).toHaveLength(5);
  for (const label of ["今日", "词书", "分组", "统计", "设置"]) {
    expect(screen.getByRole("link", { name: label })).toBeVisible();
  }
});

test("Ctrl K 打开全局命令入口", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  await user.keyboard("{Control>}k{/Control}");

  expect(screen.getByRole("dialog", { name: "搜索单词或功能" })).toBeVisible();
});

test("Esc 关闭命令入口后恢复触发按钮焦点", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  const trigger = screen.getByRole("button", { name: /搜索/ });
  await user.click(trigger);
  await user.keyboard("{Escape}");

  expect(screen.queryByRole("dialog", { name: "搜索单词或功能" })).not.toBeInTheDocument();
  expect(trigger).toHaveFocus();
});

test("命令项由单个链接路径导航", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
      <RouteProbe />
    </MemoryRouter>
  );

  await user.keyboard("{Control>}k{/Control}");
  await user.click(within(screen.getByRole("dialog")).getByRole("link", { name: "词书" }));
  expect(screen.getByTestId("route-path")).toHaveTextContent("/books");
  expect(commandPaletteSource).not.toContain("useNavigate");
  expect(commandPaletteSource).not.toMatch(/\bnavigate\s*\(/);
});

test("Cmd K 关闭后恢复打开前的实际焦点", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  const settingsLink = screen.getByRole("link", { name: "设置" });
  settingsLink.focus();
  await user.keyboard("{Meta>}k{/Meta}");
  await user.keyboard("{Escape}");

  expect(settingsLink).toHaveFocus();
});

test("重复 Ctrl K 后关闭仍恢复首次打开前的焦点", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  const settingsLink = screen.getByRole("link", { name: "设置" });
  settingsLink.focus();
  await user.keyboard("{Control>}k{/Control}");
  await user.keyboard("{Control>}k{/Control}");
  await user.keyboard("{Escape}");

  expect(settingsLink).toHaveFocus();
});

test("命令面板的 Tab 与 Shift Tab 循环停留在面板内", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  await user.keyboard("{Control>}k{/Control}");
  const dialog = screen.getByRole("dialog");
  const search = within(dialog).getByRole("textbox");
  const lastCommand = within(dialog).getByRole("link", { name: "设置" });

  expect(search).toHaveFocus();
  await user.keyboard("{Shift>}{Tab}{/Shift}");
  expect(lastCommand).toHaveFocus();
  await user.keyboard("{Tab}");
  expect(search).toHaveFocus();
});

test("点击命令面板背景会关闭面板", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  await user.keyboard("{Control>}k{/Control}");
  await user.click(screen.getByRole("dialog"));

  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

test("卸载外壳后全局快捷键监听器已清理", async () => {
  const user = userEvent.setup();
  const rendered = render(
    <MemoryRouter future={routerFuture}>
      <AppShell />
    </MemoryRouter>
  );

  rendered.unmount();
  await user.keyboard("{Control>}k{/Control}");

  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});

test("关闭后的命令面板不会保留 window Escape 或 Tab 监听器", async () => {
  const user = userEvent.setup();
  const onClose = vi.fn();
  const addEventListener = vi.spyOn(window, "addEventListener");
  const removeEventListener = vi.spyOn(window, "removeEventListener");
  const rendered = render(
    <MemoryRouter future={routerFuture}>
      <CommandPalette destinations={[{ label: "今日", to: "/" }]} onClose={onClose} open />
    </MemoryRouter>
  );
  const keydownHandler = addEventListener.mock.calls.find(([type]) => type === "keydown")?.[1];

  rendered.rerender(
    <MemoryRouter future={routerFuture}>
      <CommandPalette destinations={[{ label: "今日", to: "/" }]} onClose={onClose} open={false} />
    </MemoryRouter>
  );

  await user.keyboard("{Escape}");
  await user.keyboard("{Tab}");

  expect(onClose).not.toHaveBeenCalled();
  expect(keydownHandler).toBeDefined();
  expect(removeEventListener).toHaveBeenCalledWith("keydown", keydownHandler);
  addEventListener.mockRestore();
  removeEventListener.mockRestore();
});

test("卸载命令面板后 window Escape 不会触发旧监听器", async () => {
  const user = userEvent.setup();
  const onClose = vi.fn();
  const addEventListener = vi.spyOn(window, "addEventListener");
  const removeEventListener = vi.spyOn(window, "removeEventListener");
  const rendered = render(
    <MemoryRouter future={routerFuture}>
      <CommandPalette destinations={[{ label: "今日", to: "/" }]} onClose={onClose} open />
    </MemoryRouter>
  );
  const keydownHandler = addEventListener.mock.calls.find(([type]) => type === "keydown")?.[1];

  rendered.unmount();
  await user.keyboard("{Escape}");
  await user.keyboard("{Tab}");

  expect(onClose).not.toHaveBeenCalled();
  expect(keydownHandler).toBeDefined();
  expect(removeEventListener).toHaveBeenCalledWith("keydown", keydownHandler);
  addEventListener.mockRestore();
  removeEventListener.mockRestore();
});

test("窄屏样式不会隐藏命令触发器", () => {
  expect(appShellCss).not.toMatch(/\.commandTrigger\s*\{\s*display:\s*none/);
});

test("陶土状态标签使用设计系统语义 token", () => {
  expect(tokensCss).toContain("--wf-terracotta-container");
  expect(tokensCss).toContain("--wf-terracotta-foreground");
  expect(globalCss).toContain("background: var(--wf-terracotta-container);");
  expect(globalCss).toContain("color: var(--wf-terracotta-foreground);");
});
