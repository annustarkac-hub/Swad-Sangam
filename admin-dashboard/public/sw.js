// Swad Sangam customer website service worker (v3).
// Pages: NETWORK-FIRST (always get the latest version when online; cached copy only when offline).
// Hashed assets/images: cache-first. Public menu API reads: network-first with offline fallback.
// Orders (POST) always go to the network. Bump VERSION to force every phone to refresh.
const VERSION = "v4";
const SHELL = "ss-shell-" + VERSION;
const DATA = "ss-data-" + VERSION;

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(["./site.webmanifest", "./icon-192.png", "./favicon.svg"])));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => ![SHELL, DATA].includes(k)).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

const networkFirst = (req, cacheName) => fetch(req).then((res) => {
  if (res.ok) { const copy = res.clone(); caches.open(cacheName).then((c) => c.put(req, copy)); }
  return res;
}).catch(() => caches.match(req).then((hit) => {
  if (hit) return hit;
  // Only page navigations fall back to the app shell; API calls get JSON so the app shows "offline", not a parse error
  if (req.mode === "navigate") return caches.match("./index.html");
  return new Response('{"error":"offline"}', { status: 503, headers: { "Content-Type": "application/json" } });
}));

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // Only the public menu is cached — never staff/admin data.
  const isMenuApi = /\/rest\/v1\/(restaurant_settings|categories|menu_items|offers)/.test(url.pathname)
    || url.pathname.includes("/storage/v1/object/public/");
  if (isMenuApi) { e.respondWith(networkFirst(req, DATA)); return; }
  if (url.origin !== self.location.origin) return;
  // The staff/admin app is never cached by the customer PWA
  if (url.pathname.includes("admin.html") || url.hash.startsWith("#/pos")) return;
  if (req.mode === "navigate" || url.pathname.endsWith(".html") || url.pathname.endsWith("/")) {
    e.respondWith(networkFirst(req, SHELL));
    return;
  }
  e.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => {
    if (res.ok && (url.pathname.includes("/assets/") || url.pathname.endsWith(".png"))) {
      const copy = res.clone(); caches.open(SHELL).then((c) => c.put(req, copy));
    }
    return res;
  })));
});
