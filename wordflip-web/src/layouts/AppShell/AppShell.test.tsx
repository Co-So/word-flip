import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { AppShell } from "@/layouts/AppShell/AppShell";

test("左侧导航只显示五个稳定入口", () => {
  render(
    <MemoryRouter>
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
    <MemoryRouter>
      <AppShell />
    </MemoryRouter>
  );

  await user.keyboard("{Control>}k{/Control}");

  expect(screen.getByRole("dialog", { name: "搜索单词或功能" })).toBeVisible();
});

test("Esc 关闭命令入口后恢复触发按钮焦点", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter>
      <AppShell />
    </MemoryRouter>
  );

  const trigger = screen.getByRole("button", { name: /搜索/ });
  await user.click(trigger);
  await user.keyboard("{Escape}");

  expect(screen.queryByRole("dialog", { name: "搜索单词或功能" })).not.toBeInTheDocument();
  expect(trigger).toHaveFocus();
});
