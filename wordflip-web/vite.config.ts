import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    // Windows 可能保留 Vite 默认的 5173；固定到未保留端口并避免优先绑定 IPv6 ::1。
    host: "127.0.0.1",
    port: 5273,
    strictPort: true
  },
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) }
  }
});
