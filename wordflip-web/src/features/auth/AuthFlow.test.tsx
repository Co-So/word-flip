import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderScenarioApp } from "@/test/renderApp";

test("错误密码保留邮箱并显示不泄露账号状态的提示", async () => {
  const user = userEvent.setup();

  renderScenarioApp("logged-out", "/login");
  await user.type(screen.getByLabelText("邮箱"), "demo@wordflip.local");
  await user.type(screen.getByLabelText("密码"), "wrong-password");
  await user.click(screen.getByRole("button", { name: "登录" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("邮箱或密码不正确");
  expect(screen.getByLabelText("邮箱")).toHaveValue("demo@wordflip.local");
});

test("演示账号登录后由计划守卫进入首次设置", async () => {
  const user = userEvent.setup();

  renderScenarioApp("logged-out", "/login");
  await user.type(screen.getByLabelText("邮箱"), "demo@wordflip.local");
  await user.type(screen.getByLabelText("密码"), "wordflip-demo");
  await user.click(screen.getByRole("button", { name: "登录" }));

  expect(await screen.findByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
});

test("注册只创建本地演示账户并进入首次设置", async () => {
  const user = userEvent.setup();

  renderScenarioApp("logged-out", "/register");
  await user.type(screen.getByLabelText("昵称"), "林默");
  await user.type(screen.getByLabelText("邮箱"), "linmo@example.test");
  await user.type(screen.getByLabelText("密码"), "wordflip-demo");
  await user.click(screen.getByRole("button", { name: "创建演示账户" }));

  expect(await screen.findByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
  expect(screen.getByText("仅在此浏览器创建演示账户，不会发送或保存到服务器。"))
    .toBeVisible();
});

test("未登录访问今日会被守卫重定向到登录页", async () => {
  renderScenarioApp("logged-out", "/today");

  expect(await screen.findByRole("heading", { name: "登录 WordFlip" })).toBeVisible();
});
