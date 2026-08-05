import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderScenarioApp } from "@/test/renderApp";

test("错误密码保留账号并显示不泄露账号状态的提示", async () => {
  const user = userEvent.setup();

  renderScenarioApp("logged-out", "/login");
  await user.type(screen.getByLabelText("邮箱或手机号"), "demo@wordflip.local");
  await user.type(screen.getByLabelText("密码"), "wrong-password");
  await user.click(screen.getByRole("button", { name: "登录" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("账号或密码错误");
  expect(screen.getByLabelText("邮箱或手机号")).toHaveValue("demo@wordflip.local");
});

test("演示账号登录后由计划守卫进入首次设置", async () => {
  const user = userEvent.setup();

  renderScenarioApp("logged-out", "/login");
  await user.type(screen.getByLabelText("邮箱或手机号"), "demo@wordflip.local");
  await user.type(screen.getByLabelText("密码"), "wordflip-demo");
  await user.click(screen.getByRole("button", { name: "登录" }));

  expect(await screen.findByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
});

test("注册按真实契约只提交账号与密码并进入首次设置", async () => {
  const user = userEvent.setup();

  renderScenarioApp("logged-out", "/register");
  expect(screen.queryByLabelText("昵称")).not.toBeInTheDocument();
  await user.type(screen.getByLabelText("邮箱或手机号"), "linmo@example.test");
  await user.type(screen.getByLabelText("密码"), "wordflip-demo");
  await user.click(screen.getByRole("button", { name: "创建账户" }));

  expect(await screen.findByRole("heading", { name: "设置你的学习计划" })).toBeVisible();
  expect(screen.queryByText("仅在此浏览器创建演示账户，不会发送或保存到服务器。"))
    .not.toBeInTheDocument();
});

test("未登录访问今日会被守卫重定向到登录页", async () => {
  renderScenarioApp("logged-out", "/today");

  expect(await screen.findByRole("heading", { name: "登录 WordFlip" })).toBeVisible();
});
