import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { fileURLToPath, URL } from "node:url";

type VitestConfig = Extract<Parameters<typeof defineConfig>[0], { plugins?: unknown }>;
type VitestPluginOption = NonNullable<VitestConfig["plugins"]>[number];

export default defineConfig({
  // Vitest 2 内嵌 Vite 5；运行时可加载锁定的 Vite 6 插件，但其类型不可互认。
  plugins: [react() as unknown as VitestPluginOption],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) }
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true,
    globals: true
  }
});
