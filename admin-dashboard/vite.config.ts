import { resolve } from "node:path";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import type { Plugin } from "vite";

// Content-Security-Policy as a <meta> tag (GitHub Pages cannot send headers). Production build only,
// because the dev server injects inline scripts for hot reload.
const csp = (api: string) => [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  // the project's own Supabase host only (falls back to any Supabase project if the URL is not set at build time)
  `connect-src 'self' ${api ? `${api} ${api.replace(/^http/, "ws")}` : "https://*.supabase.co wss://*.supabase.co"}`,
  "worker-src 'self'",
  "manifest-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");
const cspMeta = (api: string): Plugin => ({
  name: "csp-meta",
  apply: "build",
  transformIndexHtml: (html) => html.replace("<head>", `<head>\n    <meta http-equiv="Content-Security-Policy" content="${csp(api)}" />`),
});

// Two pages from one free static site:
//   index.html → customer ordering website (installable PWA)
//   admin.html → restaurant ERP / admin dashboard
// base "./" lets the build run from any host or sub-path (GitHub Pages, Netlify, Cloudflare Pages).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  let api = "";
  try { api = env.VITE_SUPABASE_URL ? new URL(env.VITE_SUPABASE_URL).origin : ""; } catch { api = ""; }
  return {
  base: "./",
  plugins: [react(), cspMeta(api)],
  build: {
    outDir: "dist",
    sourcemap: false,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      input: { main: resolve(__dirname, "index.html"), admin: resolve(__dirname, "admin.html") },
    },
  },
  test: { environment: "node" },
};
});
