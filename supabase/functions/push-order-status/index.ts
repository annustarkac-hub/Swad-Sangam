// Supabase Edge Function: push-order-status
// Called by the database (private.notify_order_push trigger) whenever an order
// status changes. Sends a Firebase Cloud Messaging (FCM HTTP v1) notification
// to the customer's device. Push is supplementary — status is always visible
// inside the app too (PRD FR-10).
//
// Required function secrets (Supabase Dashboard → Edge Functions → Secrets):
//   PUSH_WEBHOOK_SECRET          random string, same as private.app_config 'push_function_secret'
//   FIREBASE_SERVICE_ACCOUNT     the full JSON of a Firebase service account key
// Provided automatically by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY

import { createClient } from "jsr:@supabase/supabase-js@2.49.4";

const MESSAGES: Record<string, { title: string; body: string }> = {
  ACCEPTED: { title: "Order accepted", body: "Swad Sangam has accepted your order {n}." },
  PREPARING: { title: "Being prepared", body: "Your order {n} is being prepared." },
  READY: { title: "Ready for pickup", body: "Your order {n} is ready. Please collect it." },
  READY_DELIVERY: { title: "Order packed", body: "Your order {n} is packed and waiting for the rider." },
  READY_DINE_IN: { title: "Food is ready", body: "Your order {n} is ready and will be served shortly." },
  OUT_FOR_DELIVERY: { title: "Out for delivery", body: "Your order {n} is on its way." },
  COMPLETED: { title: "Order completed", body: "Thank you! Order {n} is complete." },
  REJECTED: { title: "Order not accepted", body: "Sorry, order {n} could not be accepted." },
  CANCELLED: { title: "Order cancelled", body: "Order {n} has been cancelled." },
};

function b64url(input: ArrayBuffer | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

let cachedToken: { token: string; exp: number } | null = null;

async function googleAccessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp - 60 > now) return cachedToken.token;
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claim}`));
  const jwt = `${header}.${claim}.${b64url(sig)}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`token exchange failed: ${res.status}`);
  const json = await res.json();
  cachedToken = { token: json.access_token, exp: now + (json.expires_in ?? 3600) };
  return cachedToken.token;
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const secret = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
  if (!secret || !timingSafeEqual(req.headers.get("x-webhook-secret") ?? "", secret)) {
    return new Response("Unauthorized", { status: 401 });
  }

  let orderId: string;
  try {
    ({ orderId } = await req.json());
  } catch {
    return new Response("Bad request", { status: 400 });
  }
  if (!orderId) return new Response("Bad request", { status: 400 });

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
  // Read the committed state (notify only after the change is committed — PRD §11 rule 10)
  const { data: order, error } = await supabase
    .from("orders")
    .select("order_number, order_status, order_type, access_token, fcm_token, rejection_reason, cancel_reason")
    .eq("id", orderId)
    .single();
  if (error || !order?.fcm_token) return new Response("No token", { status: 200 });

  const key = order.order_status === "READY" && order.order_type !== "PICKUP"
    ? `READY_${order.order_type}` : order.order_status;
  const template = MESSAGES[key] ?? MESSAGES[order.order_status];
  if (!template) return new Response("No message for status", { status: 200 });
  let body = template.body.replace("{n}", order.order_number);
  if (order.order_status === "REJECTED" && order.rejection_reason) body += ` Reason: ${order.rejection_reason}`;
  if (order.order_status === "CANCELLED" && order.cancel_reason) body += ` Reason: ${order.cancel_reason}`;

  const saJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
  if (!saJson) return new Response("Push not configured", { status: 200 });
  const sa = JSON.parse(saJson);
  const accessToken = await googleAccessToken(sa);

  const fcm = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        token: order.fcm_token,
        // Data-only: the app's PushService always runs (foreground, background or killed), refreshes the
        // order and shows a localized notification that opens the order. The token is the device's own.
        data: {
          orderId, status: order.order_status, orderNumber: order.order_number, orderType: order.order_type,
          token: order.access_token, title: template.title, body,
          reason: (order.order_status === "REJECTED" ? order.rejection_reason : order.cancel_reason) ?? "",
        },
        android: { priority: "high" },
      },
    }),
  });

  if (fcm.status === 404 || fcm.status === 400) {
    // token expired / app uninstalled — stop trying
    await supabase.from("orders").update({ fcm_token: null }).eq("id", orderId);
  }
  return new Response(JSON.stringify({ sent: fcm.ok }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
});
