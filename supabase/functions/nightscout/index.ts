// Sukoon's readings in Nightscout's format, for widgets (Rainmeter, watch faces) that speak Nightscout:
//   https://<project>.supabase.co/functions/v1/nightscout/api/v1/entries/sgv.json?count=240&token=<feed token>
// Also /api/v1/entries.json, /api/v1/status.json and /pebble. The token (You › Apps & data › Widget link,
// table feed_tokens) picks whose readings; deploy with JWT verification off, since widgets can't sign in.
import { createClient } from "jsr:@supabase/supabase-js@2";

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

// Sukoon's trend codes (-2..2) as Nightscout directions.
const DIRECTION: Record<string, string> = { "-2": "DoubleDown", "-1": "FortyFiveDown", "0": "Flat", "1": "FortyFiveUp", "2": "DoubleUp" };

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "access-control-allow-origin": "*", "cache-control": "no-store" },
  });
}

Deno.serve(async (req) => {
  const url = new URL(req.url);
  const token = url.searchParams.get("token") ?? req.headers.get("api-secret") ?? "";
  if (token.length < 32) return json({ error: "Add your Sukoon widget token: ?token=…" }, 401);
  const { data: owner } = await db.from("feed_tokens").select("user_id").eq("token", token).maybeSingle();
  if (!owner) return json({ error: "Unknown token. Make a new link in Sukoon: You › Apps & data › Widget link." }, 401);

  const path = url.pathname.replace(/^.*?\/nightscout/, "") || "/";
  if (path.startsWith("/api/v1/status")) {
    return json({ status: "ok", name: "Sukoon", version: "sukoon", serverTime: new Date().toISOString(), settings: { units: "mg/dl" } });
  }
  if (!path.startsWith("/api/v1/entries") && path !== "/pebble") return json({ error: "not found" }, 404);

  const count = Math.min(Math.max(parseInt(url.searchParams.get("count") ?? "10") || 10, 1), 1500);
  const { data: rows, error } = await db
    .from("readings").select("ts,mg_dl,trend").eq("user_id", owner.user_id)
    .order("ts", { ascending: false }).limit(path === "/pebble" ? 1 : count);
  if (error) return json({ error: error.message }, 500);

  const entries = (rows ?? []).map((r) => {
    const ms = Date.parse(r.ts);
    return { _id: `sukoon-${ms}`, type: "sgv", sgv: r.mg_dl, date: ms, dateString: new Date(ms).toISOString(), direction: DIRECTION[String(r.trend)] ?? "NONE", device: "sukoon" };
  });
  if (path === "/pebble") {
    return json({ status: [{ now: Date.now() }], bgs: entries.map((e) => ({ sgv: String(e.sgv), datetime: e.date, direction: e.direction })) });
  }
  return json(entries);
});
