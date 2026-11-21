import { resolve } from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Two pages from one free static site:
//   index.html → customer ordering website (installable PWA)
//   admin.html → restaurant ERP / admin dashboard
// base "./" lets the build run from any host or sub-path (GitHub Pages, Netlify, Cloudflare Pages).
export default defineConfig({
  base: "./",
  plugins: [react()],
  build: {
    outDir: "dist",
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      input: { main: resolve(__dirname, "index.html"), admin: resolve(__dirname, "admin.html") },
    },
  },
  test: { environment: "node" },
});
