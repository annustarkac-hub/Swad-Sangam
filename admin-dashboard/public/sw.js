// Swad Sangam customer website service worker.
// App shell: cache-first. Menu/API reads: network-first with cached fallback so the
// menu still opens on weak or no internet. Orders (POST) always go to the network.
const SHELL = "ss-shell-v1";
const DATA = "ss-data-v1";

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(["./", "./index.html", "./site.webmanifest", "./icon-192.png", "./favicon.svg"])));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => ![SHELL, DATA].includes(k)).map((k) => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // Only the public menu is cached — never staff/admin data.
  const isApi = /\/rest\/v1\/(restaurant_settings|categories|menu_items|offers)/.test(url.pathname)
    || url.pathname.includes("/storage/v1/object/public/");
  if (isApi) {
    e.respondWith(fetch(req).then((res) => {
      if (res.ok) { const copy = res.clone(); caches.open(DATA).then((c) => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req)));
    return;
  }
  if (url.origin === self.location.origin) {
    e.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => {
      if (res.ok && (url.pathname.includes("/assets/") || url.pathname.endsWith(".png"))) {
        const copy = res.clone(); caches.open(SHELL).then((c) => c.put(req, copy));
      }
      return res;
    }).catch(() => caches.match("./index.html"))));
  }
});
