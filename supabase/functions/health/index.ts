// Supabase Edge Function: health
// Public, read-only check for an uptime monitor (UptimeRobot keyword monitor on "SWAD-OK").
// Proves the whole chain works: the function runtime, the API gateway and the database
// (it calls the same public restaurant_status() the website calls). Returns no data.
// Uses only the public anon key that Supabase provides to every function.

Deno.serve(async () => {
  const url = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const headers = { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" };
  if (!url || !anon) return new Response("SWAD-DOWN config", { status: 503, headers });
  try {
    const r = await fetch(`${url}/rest/v1/rpc/restaurant_status`, {
      method: "POST",
      headers: { apikey: anon, Authorization: `Bearer ${anon}`, "Content-Type": "application/json" },
      body: "{}",
      signal: AbortSignal.timeout(8000),
    });
    if (!r.ok) return new Response(`SWAD-DOWN database ${r.status}`, { status: 503, headers });
    await r.body?.cancel();
    return new Response("SWAD-OK", { status: 200, headers });
  } catch (_) {
    return new Response("SWAD-DOWN database unreachable", { status: 503, headers });
  }
});
