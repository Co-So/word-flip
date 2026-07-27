import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test } from "vitest";
import { renderScenarioApp } from "@/test/renderApp";

afterEach(() => cleanup());

describe("设置", () => {
  test("保存固定设置快照并持久化，不出现全局词典切换", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("configured", "/settings");

    await user.click(await screen.findByRole("checkbox", { name: "播放发音" }));
    await user.click(screen.getByRole("checkbox", { name: "减少动态效果" }));
    await user.selectOptions(screen.getByLabelText("每组单词数"), "30");
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    expect(await screen.findByRole("status")).toHaveTextContent("设置已保存");
    expect(app.store.read().settings).toEqual({
      soundEnabled: false,
      reducedMotion: true,
      groupSize: 30
    });
    expect(screen.queryByText(/activeDictId|全局词典|切换词典/i)).not.toBeInTheDocument();
  });

  test("重置对话框困住焦点，Esc 关闭并恢复触发按钮焦点", async () => {
    const user = userEvent.setup();
    renderScenarioApp("configured", "/settings");
    const trigger = await screen.findByRole("button", { name: "重置演示数据" });

    await user.click(trigger);
    const dialog = screen.getByRole("dialog", { name: "确认重置演示数据" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(screen.getByRole("button", { name: "取消" })).toHaveFocus();

    await user.tab({ shift: true });
    expect(screen.getByRole("button", { name: "确认重置" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "取消" })).toHaveFocus();
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  test("明确确认后恢复 126、清除当前变更并导航今日", async () => {
    const user = userEvent.setup();
    const app = renderScenarioApp("mutated", "/settings");
    app.store.update((draft) => {
      draft.planStates["plan-core"].today.masteredCount = 999;
      draft.settings.groupSize = 50;
    });

    await user.click(await screen.findByRole("button", { name: "重置演示数据" }));
    await user.click(screen.getByRole("button", { name: "确认重置" }));

    expect(await screen.findByRole("heading", { name: "今天继续前进" })).toBeVisible();
    await waitFor(() => expect(app.store.read().planStates["plan-core"].today.masteredCount).toBe(126));
    expect(app.store.read().settings.groupSize).toBe(20);
    expect(app.store.read().planStates["plan-core"].media.byCardId["card-sustainable"].stainLevel).toBe(0);
  });
});
