import { cleanup, fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { createDemoState } from "@/data/mock/createDemoState";
import { createDemoStateStore, DEMO_STORAGE_KEY } from "@/data/mock/DemoStateStore";
import { MockMediaRepository } from "@/data/mock/repositories/MockMediaRepository";
import { renderStateApp } from "@/test/renderApp";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("卡片媒体", () => {
  beforeEach(() => {
    window.localStorage.clear();
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:test-preview")
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn()
    });
  });

  test("只展示当前计划的 cardId 媒体，不会按相同 wordKey 误取旧计划资源", async () => {
    const state = createDemoState();
    const oldCard = structuredClone(state.planStates["plan-core"].cards.byCardId["card-sustainable"]);
    const advancedCard = { ...oldCard, cardId: "card-advanced-sustainable" };
    state.books.activePlanId = "plan-advanced";
    state.planStates["plan-advanced"].cards = {
      byCardId: { [advancedCard.cardId]: advancedCard },
      byWordKey: { sustainable: advancedCard }
    };
    state.planStates["plan-advanced"].media.byCardId = {
      [advancedCard.cardId]: {
        cardId: advancedCard.cardId,
        imageUrl: null,
        stainLevel: 0,
        transform: { rotation: 0, scale: 1, positionX: 0, positionY: 0 }
      }
    };

    renderStateApp(state, "/media");

    expect(await screen.findByText("card-advanced-sustainable")).toBeVisible();
    expect(screen.queryByText("card-sustainable")).not.toBeInTheDocument();
    expect(screen.queryByAltText("sustainable 的记忆图片")).not.toBeInTheDocument();
  });

  test("卡片选择保留原生 button 语义并可切换 aria-pressed", async () => {
    const user = userEvent.setup();
    renderStateApp(createDemoState(), "/media");

    const sustainable = await screen.findByRole("button", { name: /sustainable.*card-sustainable/ });
    const infrastructure = screen.getByRole("button", { name: /infrastructure.*card-infrastructure/ });
    expect(sustainable).toHaveAttribute("aria-pressed", "true");

    await user.click(infrastructure);

    expect(infrastructure).toHaveAttribute("aria-pressed", "true");
    expect(sustainable).toHaveAttribute("aria-pressed", "false");
  });

  test("旋转保存后，以同一持久化快照重新挂载仍显示 90°", async () => {
    const user = userEvent.setup();
    const first = renderStateApp(createDemoState(), "/media");

    await user.click(await screen.findByRole("button", { name: "向右旋转" }));
    await user.click(screen.getByRole("button", { name: "保存图片位置" }));
    expect(await screen.findByText("旋转 90°")).toBeVisible();

    const persisted = first.store.read();
    first.unmount();
    renderStateApp(persisted, "/media");
    expect(await screen.findByText("旋转 90°")).toBeVisible();
  });

  test("合法上传只用 object URL 预览，保存映射固定 WebP 并撤销临时 URL", async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:preview-only");
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined);
    const app = renderStateApp(createDemoState(), "/media");
    const file = new File(["image"], "memory.png", { type: "image/png" });

    await user.upload(await screen.findByLabelText("选择图片"), file);
    expect(screen.getByRole("img", { name: "memory.png 临时预览" })).toHaveAttribute("src", "blob:preview-only");
    await user.click(screen.getByRole("button", { name: "保存图片位置" }));

    await waitFor(() => {
      expect(
        app.store.read().planStates["plan-core"].media.byCardId["card-sustainable"].imageUrl
      ).toBe("/card-images/custom-placeholder.webp");
    });
    expect(JSON.stringify(app.store.read())).not.toContain("blob:preview-only");
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-only");
    expect(createObjectURL).toHaveBeenCalledWith(file);
  });

  test("替换、取消和卸载临时预览都会撤销各自 object URL", async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.spyOn(URL, "createObjectURL")
      .mockReturnValueOnce("blob:first")
      .mockReturnValueOnce("blob:second")
      .mockReturnValueOnce("blob:third");
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL");
    const app = renderStateApp(createDemoState(), "/media");
    const input = await screen.findByLabelText("选择图片");

    await user.upload(input, new File(["a"], "first.png", { type: "image/png" }));
    await user.upload(input, new File(["b"], "second.png", { type: "image/png" }));
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:first");
    await user.click(screen.getByRole("button", { name: "取消临时预览" }));
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:second");
    await user.upload(input, new File(["c"], "third.png", { type: "image/png" }));
    app.unmount();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:third");
    expect(createObjectURL).toHaveBeenCalledTimes(3);
  });

  test("取消预览会清空文件控件并允许再次选择同一文件", async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.spyOn(URL, "createObjectURL")
      .mockReturnValueOnce("blob:same-first")
      .mockReturnValueOnce("blob:same-second");
    renderStateApp(createDemoState(), "/media");
    const input = await screen.findByLabelText("选择图片");
    const file = new File(["same"], "same.png", { type: "image/png" });

    await user.upload(input, file);
    await user.click(screen.getByRole("button", { name: "取消临时预览" }));
    expect(input).toHaveValue("");
    await user.upload(input, file);

    expect(screen.getByRole("img", { name: "same.png 临时预览" })).toHaveAttribute("src", "blob:same-second");
    expect(createObjectURL).toHaveBeenCalledTimes(2);
  });

  test.each([
    { file: new File(["text"], "memory.txt", { type: "text/plain" }), errorMessage: "仅支持 JPEG、PNG 或 WebP" },
    {
      file: Object.assign(new File(["x"], "huge.webp", { type: "image/webp" }), {}),
      errorMessage: "图片不能超过 5MB",
      oversized: true
    }
  ])("非法文件显示“$errorMessage”且保留编辑页", async ({ file, errorMessage, oversized }) => {
    if (oversized) {
      Object.defineProperty(file, "size", { configurable: true, value: 5 * 1024 * 1024 + 1 });
    }
    renderStateApp(createDemoState(), "/media");

    fireEvent.change(await screen.findByLabelText("选择图片"), { target: { files: [file] } });

    expect(await screen.findByRole("alert")).toHaveTextContent(errorMessage);
    expect(screen.getByRole("heading", { name: "卡片图片" })).toBeVisible();
  });

  test("清除只作用于当前计划和当前 cardId，不修改历史计划媒体", async () => {
    const state = createDemoState();
    state.planStates["plan-advanced"].media.byCardId["card-resilient"] = {
      cardId: "card-resilient",
      imageUrl: "/card-images/custom-placeholder.webp",
      stainLevel: 2,
      transform: { rotation: 180, scale: 1.2, positionX: 4, positionY: -3 }
    };
    const user = userEvent.setup();
    const app = renderStateApp(state, "/media");

    await user.click(await screen.findByRole("button", { name: "清除图片" }));

    expect(app.store.read().planStates["plan-core"].media.byCardId["card-sustainable"].imageUrl).toBeNull();
    expect(app.store.read().planStates["plan-advanced"].media.byCardId["card-resilient"]).toEqual(
      state.planStates["plan-advanced"].media.byCardId["card-resilient"]
    );
  });

  test("媒体变换持久化损坏时恢复完整固定种子", () => {
    const state = createDemoState();
    state.planStates["plan-core"].media.byCardId["card-sustainable"].transform.rotation = 45 as 90;
    state.planStates["plan-core"].today.masteredCount = 999;
    window.localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(state));

    const restored = createDemoStateStore({ storage: window.localStorage });

    expect(restored.read().planStates["plan-core"].today.masteredCount).toBe(126);
    expect(
      restored.read().planStates["plan-core"].media.byCardId["card-sustainable"].transform.rotation
    ).toBe(0);
  });

  test("仓储按 activePlanId 与 cardId 约束保存", async () => {
    const store = createDemoStateStore({ initialState: createDemoState(), storage: null });
    const media = new MockMediaRepository(store);
    await store.switchActivePlan("plan-advanced");

    await expect(
      media.saveTransform("card-sustainable", { rotation: 90, scale: 1, positionX: 0, positionY: 0 })
    ).rejects.toMatchObject({ kind: "not-found" });
  });
});
