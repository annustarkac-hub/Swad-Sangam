import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// base "./" lets the static build run from any host or sub-path
// (GitHub Pages, Netlify, Cloudflare Pages, Vercel).
export default defineConfig({
  base: "./",
  plugins: [react()],
  build: { outDir: "dist", sourcemap: false },
  test: { environment: "node" },
});
