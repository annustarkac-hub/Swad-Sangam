import React from "react";
import ReactDOM from "react-dom/client";
import { isConfigured } from "../lib/supabase";
import SiteApp from "./SiteApp";
import "./site.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {isConfigured ? <SiteApp /> : <p style={{ padding: 24 }}>Website not configured yet — see docs/SETUP_GUIDE.md.</p>}
  </React.StrictMode>,
);

if ("serviceWorker" in navigator && location.protocol === "https:") {
  window.addEventListener("load", () => { void navigator.serviceWorker.register("./sw.js"); });
}
