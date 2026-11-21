import React from "react";
import ReactDOM from "react-dom/client";
import { isConfigured } from "../lib/supabase";
import SiteApp from "./SiteApp";
import "@fontsource/plus-jakarta-sans/400.css";
import "@fontsource/plus-jakarta-sans/600.css";
import "@fontsource/plus-jakarta-sans/700.css";
import "@fontsource/plus-jakarta-sans/800.css";
import "./site.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {isConfigured ? <SiteApp /> : <p style={{ padding: 24 }}>Website not configured yet — see docs/SETUP_GUIDE.md.</p>}
  </React.StrictMode>,
);

if ("serviceWorker" in navigator && location.protocol === "https:") {
  window.addEventListener("load", () => { void navigator.serviceWorker.register("./sw.js", { updateViaCache: "none" }); });
  // When a new version takes over, reload once so the customer sees it immediately.
  let reloaded = false;
  navigator.serviceWorker.addEventListener("controllerchange", () => { if (!reloaded) { reloaded = true; location.reload(); } });
}
