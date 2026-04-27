import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import {
  hashPassword,
  newId,
  newInviteCode,
  signJwt,
  verifyJwt,
  verifyPassword,
  type JwtPayload,
} from "./auth";
import { sendToTokens } from "./fcm";

type Bindings = {
  DB: D1Database;
  JWT_SECRET: string;
  FCM_SERVICE_ACCOUNT: string;
};

type Variables = {
  user: JwtPayload;
};

const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

const app = new Hono<{ Bindings: Bindings; Variables: Variables }>();

app.use("*", cors());

app.get("/", (c) => c.json({ ok: true, service: "chore-tracker-api" }));

// ---------- Auth ----------

app.post("/auth/register", async (c) => {
  const body = await c.req.json<{
    email: string;
    password: string;
    displayName: string;
    householdName?: string;
    inviteCode?: string;
  }>();
  if (!body.email || !body.password || !body.displayName)
    throw new HTTPException(400, { message: "missing fields" });
  if (body.password.length < 8)
    throw new HTTPException(400, { message: "password too short" });

  const existing = await c.env.DB.prepare(
    "SELECT id FROM users WHERE email = ?",
  )
    .bind(body.email.toLowerCase())
    .first();
  if (existing) throw new HTTPException(409, { message: "email taken" });

  const now = Date.now();
  const userId = newId();
  const { hash, salt } = await hashPassword(body.password);

  let householdId: string;

  if (body.inviteCode) {
    // Joining an existing household via invite.
    const invite = await c.env.DB.prepare(
      `SELECT household_id, expires_at, used_at FROM invites WHERE code = ?`,
    )
      .bind(body.inviteCode)
      .first<{ household_id: string; expires_at: number; used_at: number | null }>();
    if (!invite) throw new HTTPException(400, { message: "invalid invite" });
    if (invite.used_at) throw new HTTPException(400, { message: "invite already used" });
    if (invite.expires_at < now)
      throw new HTTPException(400, { message: "invite expired" });
    householdId = invite.household_id;

    await c.env.DB.batch([
      c.env.DB.prepare(
        `INSERT INTO users (id, email, display_name, password_hash, password_salt, household_id, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        userId,
        body.email.toLowerCase(),
        body.displayName,
        hash,
        salt,
        householdId,
        now,
      ),
      c.env.DB.prepare(
        `UPDATE invites SET used_at = ?, used_by = ? WHERE code = ?`,
      ).bind(now, userId, body.inviteCode),
    ]);
  } else {
    // Creating a brand-new household.
    if (!body.householdName)
      throw new HTTPException(400, { message: "householdName required" });
    householdId = newId();
    await c.env.DB.batch([
      c.env.DB.prepare(
        "INSERT INTO households (id, name, created_at) VALUES (?, ?, ?)",
      ).bind(householdId, body.householdName, now),
      c.env.DB.prepare(
        `INSERT INTO users (id, email, display_name, password_hash, password_salt, household_id, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        userId,
        body.email.toLowerCase(),
        body.displayName,
        hash,
        salt,
        householdId,
        now,
      ),
    ]);
  }

  const token = await signJwt(
    { sub: userId, hh: householdId },
    c.env.JWT_SECRET,
  );
  return c.json({ token, userId, householdId });
});

app.post("/auth/login", async (c) => {
  const body = await c.req.json<{ email: string; password: string }>();
  if (!body.email || !body.password)
    throw new HTTPException(400, { message: "missing fields" });

  const row = await c.env.DB.prepare(
    `SELECT id, household_id, password_hash, password_salt
       FROM users WHERE email = ?`,
  )
    .bind(body.email.toLowerCase())
    .first<{
      id: string;
      household_id: string;
      password_hash: string;
      password_salt: string;
    }>();
  if (!row) throw new HTTPException(401, { message: "invalid credentials" });

  const ok = await verifyPassword(
    body.password,
    row.password_hash,
    row.password_salt,
  );
  if (!ok) throw new HTTPException(401, { message: "invalid credentials" });

  const token = await signJwt(
    { sub: row.id, hh: row.household_id },
    c.env.JWT_SECRET,
  );
  return c.json({ token, userId: row.id, householdId: row.household_id });
});

// ---------- Auth middleware ----------

app.use("/api/*", async (c, next) => {
  const auth = c.req.header("authorization");
  if (!auth?.startsWith("Bearer "))
    throw new HTTPException(401, { message: "missing token" });
  const payload = await verifyJwt(auth.slice(7), c.env.JWT_SECRET);
  if (!payload) throw new HTTPException(401, { message: "invalid token" });
  c.set("user", payload);
  await next();
});

// ---------- Household / members ----------

app.get("/api/household", async (c) => {
  const { hh } = c.get("user");
  const household = await c.env.DB.prepare(
    "SELECT id, name, created_at AS createdAt FROM households WHERE id = ?",
  )
    .bind(hh)
    .first();
  const { results: members } = await c.env.DB.prepare(
    `SELECT id, display_name AS displayName, email
       FROM users WHERE household_id = ? ORDER BY created_at`,
  )
    .bind(hh)
    .all();
  return c.json({ household, members });
});

// ---------- Invites ----------

app.post("/api/invites", async (c) => {
  const { sub, hh } = c.get("user");
  const code = newInviteCode();
  const now = Date.now();
  const expiresAt = now + INVITE_TTL_MS;
  await c.env.DB.prepare(
    `INSERT INTO invites (code, household_id, created_by, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?)`,
  )
    .bind(code, hh, sub, now, expiresAt)
    .run();
  return c.json({ code, expiresAt });
});

// ---------- Areas ----------

app.get("/api/areas", async (c) => {
  const { hh } = c.get("user");
  const { results } = await c.env.DB.prepare(
    `SELECT id, name, icon, sort_order AS sortOrder, created_at AS createdAt
       FROM areas WHERE household_id = ? ORDER BY sort_order, created_at`,
  )
    .bind(hh)
    .all();
  return c.json(results);
});

app.post("/api/areas", async (c) => {
  const { hh } = c.get("user");
  const body = await c.req.json<{
    name: string;
    icon?: string;
    sortOrder?: number;
  }>();
  if (!body.name) throw new HTTPException(400, { message: "name required" });
  const id = newId();
  const now = Date.now();
  await c.env.DB.prepare(
    `INSERT INTO areas (id, household_id, name, icon, sort_order, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  )
    .bind(id, hh, body.name, body.icon ?? null, body.sortOrder ?? 0, now)
    .run();
  return c.json({
    id,
    name: body.name,
    icon: body.icon,
    sortOrder: body.sortOrder ?? 0,
    createdAt: now,
  });
});

app.delete("/api/areas/:id", async (c) => {
  const { hh } = c.get("user");
  const id = c.req.param("id");
  const res = await c.env.DB.prepare(
    "DELETE FROM areas WHERE id = ? AND household_id = ?",
  )
    .bind(id, hh)
    .run();
  if (!res.meta.changes) throw new HTTPException(404);
  return c.json({ ok: true });
});

// ---------- Tasks ----------

app.get("/api/tasks", async (c) => {
  const { hh } = c.get("user");
  const { results } = await c.env.DB.prepare(
    `SELECT t.id, t.area_id AS areaId, t.name,
            t.frequency_days AS frequencyDays,
            t.last_done_at   AS lastDoneAt,
            t.created_at     AS createdAt,
            t.assigned_to    AS assignedTo,
            t.auto_rotate    AS autoRotate,
            t.effort_points  AS effortPoints,
            u.display_name   AS lastDoneBy,
            ua.display_name  AS assignedToName
       FROM tasks t
       JOIN areas a ON a.id = t.area_id
       LEFT JOIN completions c ON c.id = (
         SELECT id FROM completions
          WHERE task_id = t.id
          ORDER BY done_at DESC LIMIT 1
       )
       LEFT JOIN users u ON u.id = c.user_id
       LEFT JOIN users ua ON ua.id = t.assigned_to
      WHERE a.household_id = ?
      ORDER BY t.created_at`,
  )
    .bind(hh)
    .all<{ autoRotate: number } & Record<string, unknown>>();
  // SQLite stores booleans as INTEGER; coerce to proper JSON boolean for the client.
  return c.json(results.map((r) => ({ ...r, autoRotate: r.autoRotate !== 0 })));
});

app.post("/api/tasks", async (c) => {
  const { sub, hh } = c.get("user");
  const body = await c.req.json<{
    areaId: string;
    name: string;
    frequencyDays: number;
    assignedTo?: string;
    autoRotate?: boolean;
    effortPoints?: number;
  }>();
  if (!body.areaId || !body.name || !body.frequencyDays || body.frequencyDays <= 0)
    throw new HTTPException(400, { message: "missing fields" });

  const area = await c.env.DB.prepare(
    "SELECT id FROM areas WHERE id = ? AND household_id = ?",
  )
    .bind(body.areaId, hh)
    .first();
  if (!area) throw new HTTPException(404, { message: "area not found" });

  const assignedTo = body.assignedTo ?? sub;
  const autoRotate = body.autoRotate ? 1 : 0;
  const effortPoints = body.effortPoints ?? 1;
  const id = newId();
  const now = Date.now();
  await c.env.DB.prepare(
    `INSERT INTO tasks (id, area_id, name, frequency_days, assigned_to, auto_rotate, effort_points, last_done_at, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)`,
  )
    .bind(id, body.areaId, body.name, body.frequencyDays, assignedTo, autoRotate, effortPoints, now)
    .run();
  return c.json({
    id,
    areaId: body.areaId,
    name: body.name,
    frequencyDays: body.frequencyDays,
    assignedTo,
    assignedToName: null,
    autoRotate: body.autoRotate ?? false,
    effortPoints,
    lastDoneAt: null,
    lastDoneBy: null,
    createdAt: now,
  });
});

app.post("/api/tasks/:id/complete", async (c) => {
  const { sub, hh } = c.get("user");
  const taskId = c.req.param("id");

  const task = await c.env.DB.prepare(
    `SELECT t.id, t.name, t.auto_rotate, t.assigned_to FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  )
    .bind(taskId, hh)
    .first<{ id: string; name: string; auto_rotate: number; assigned_to: string | null }>();
  if (!task) throw new HTTPException(404);

  const now = Date.now();
  const completionId = newId();
  const stmts: D1PreparedStatement[] = [
    c.env.DB.prepare(
      "INSERT INTO completions (id, task_id, user_id, done_at) VALUES (?, ?, ?, ?)",
    ).bind(completionId, taskId, sub, now),
    c.env.DB.prepare("UPDATE tasks SET last_done_at = ? WHERE id = ?").bind(now, taskId),
  ];

  if (task.auto_rotate) {
    const { results: members } = await c.env.DB.prepare(
      "SELECT id FROM users WHERE household_id = ? ORDER BY created_at",
    ).bind(hh).all<{ id: string }>();
    if (members.length > 1) {
      const idx = members.findIndex((m) => m.id === task.assigned_to);
      const nextIdx = idx === -1 ? 0 : (idx + 1) % members.length;
      stmts.push(
        c.env.DB.prepare("UPDATE tasks SET assigned_to = ? WHERE id = ?")
          .bind(members[nextIdx].id, taskId),
      );
    }
  }

  await c.env.DB.batch(stmts);

  // Fan-out push notifications to all household members except the actor.
  if (c.env.FCM_SERVICE_ACCOUNT) {
    const { results: tokenRows } = await c.env.DB.prepare(
      `SELECT dt.token FROM device_tokens dt
         JOIN users u ON u.id = dt.user_id
        WHERE u.household_id = ? AND dt.user_id != ?`,
    ).bind(hh, sub).all<{ token: string }>();
    const tokens = tokenRows.map((r) => r.token);
    if (tokens.length > 0) {
      const actor = await c.env.DB.prepare(
        "SELECT display_name FROM users WHERE id = ?",
      ).bind(sub).first<{ display_name: string }>();
      c.executionCtx.waitUntil(
        sendToTokens(
          tokens,
          { title: "Chore completed", body: `${actor?.display_name ?? "Someone"} completed "${task.name}"` },
          c.env,
        ),
      );
    }
  }

  return c.json({ ok: true, doneAt: now });
});

app.patch("/api/tasks/:id", async (c) => {
  const { hh } = c.get("user");
  const id = c.req.param("id");
  const body = await c.req.json<{
    name?: string;
    frequencyDays?: number;
    assignedTo?: string | null;
    autoRotate?: boolean;
    effortPoints?: number;
  }>();

  const task = await c.env.DB.prepare(
    `SELECT t.id FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  ).bind(id, hh).first();
  if (!task) throw new HTTPException(404);

  const sets: string[] = [];
  const bindings: unknown[] = [];
  if (body.name !== undefined) { sets.push("name = ?"); bindings.push(body.name); }
  if (body.frequencyDays !== undefined) {
    if (body.frequencyDays <= 0) throw new HTTPException(400, { message: "frequencyDays must be positive" });
    sets.push("frequency_days = ?"); bindings.push(body.frequencyDays);
  }
  if ("assignedTo" in body) { sets.push("assigned_to = ?"); bindings.push(body.assignedTo ?? null); }
  if (body.autoRotate !== undefined) { sets.push("auto_rotate = ?"); bindings.push(body.autoRotate ? 1 : 0); }
  if (body.effortPoints !== undefined) { sets.push("effort_points = ?"); bindings.push(body.effortPoints); }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });

  bindings.push(id);
  await c.env.DB.prepare(
    `UPDATE tasks SET ${sets.join(", ")} WHERE id = ?`,
  ).bind(...bindings).run();
  return c.json({ ok: true });
});

// Undo: delete the most-recent completion for this task and recompute
// last_done_at from whatever's left (or NULL if there are no completions).
app.delete("/api/tasks/:id/completions/last", async (c) => {
  const { hh } = c.get("user");
  const taskId = c.req.param("id");

  const task = await c.env.DB.prepare(
    `SELECT t.id FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  )
    .bind(taskId, hh)
    .first();
  if (!task) throw new HTTPException(404);

  const latest = await c.env.DB.prepare(
    `SELECT id FROM completions
      WHERE task_id = ?
      ORDER BY done_at DESC
      LIMIT 1`,
  )
    .bind(taskId)
    .first<{ id: string }>();
  if (!latest) throw new HTTPException(404, { message: "no completion to undo" });

  // SQLite has no MAX-ignoring-NULL trick we need; subquery yields NULL when the
  // table is empty, which is exactly what we want stamped on tasks.last_done_at.
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM completions WHERE id = ?").bind(latest.id),
    c.env.DB.prepare(
      `UPDATE tasks SET last_done_at = (
         SELECT MAX(done_at) FROM completions WHERE task_id = ?
       ) WHERE id = ?`,
    ).bind(taskId, taskId),
  ]);
  return c.json({ ok: true });
});

// ---------- Device tokens ----------

app.post("/api/device-tokens", async (c) => {
  const { sub } = c.get("user");
  const body = await c.req.json<{ token: string; platform: string }>();
  if (!body.token || !body.platform)
    throw new HTTPException(400, { message: "token and platform required" });
  const now = Date.now();
  await c.env.DB.prepare(
    `INSERT INTO device_tokens (token, user_id, platform, updated_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(token) DO UPDATE SET user_id = excluded.user_id, updated_at = excluded.updated_at`,
  ).bind(body.token, sub, body.platform, now).run();
  return c.json({ ok: true });
});

app.delete("/api/device-tokens/:token", async (c) => {
  const { sub } = c.get("user");
  const token = c.req.param("token");
  await c.env.DB.prepare(
    "DELETE FROM device_tokens WHERE token = ? AND user_id = ?",
  ).bind(token, sub).run();
  return c.json({ ok: true });
});

// ---------- Debug (remove after FCM is confirmed working) ----------

app.get("/api/debug/fcm", async (c) => {
  const { sub, hh } = c.get("user");
  if (!c.env.FCM_SERVICE_ACCOUNT) return c.json({ error: "FCM_SERVICE_ACCOUNT not set" }, 500);

  const sa = JSON.parse(c.env.FCM_SERVICE_ACCOUNT) as { project_id: string; private_key: string; client_email: string };

  // Step 1: get access token
  let accessToken: string;
  let tokenError: string | null = null;
  try {
    const { sendToTokens: _, ...rest } = await import("./fcm");
    // Re-implement token fetch inline so we can surface errors
    const enc = new TextEncoder();
    const b64 = (buf: ArrayBuffer) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/=/g,"").replace(/\+/g,"-").replace(/\//g,"_");
    const hdr = b64(enc.encode(JSON.stringify({ alg:"RS256", typ:"JWT" })).buffer as ArrayBuffer);
    const now = Math.floor(Date.now()/1000);
    const pay = b64(enc.encode(JSON.stringify({ iss: sa.client_email, scope:"https://www.googleapis.com/auth/firebase.messaging", aud:"https://oauth2.googleapis.com/token", iat:now, exp:now+3600 })).buffer as ArrayBuffer);
    const pem = sa.private_key.replace(/-----BEGIN PRIVATE KEY-----/,"").replace(/-----END PRIVATE KEY-----/,"").replace(/\s/g,"");
    const der = (() => { const bin=atob(pem); const bytes=new Uint8Array(bin.length); for(let i=0;i<bin.length;i++) bytes[i]=bin.charCodeAt(i); return bytes.buffer; })();
    const key = await crypto.subtle.importKey("pkcs8", der, { name:"RSASSA-PKCS1-v1_5", hash:"SHA-256" }, false, ["sign"]);
    const sig = b64(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(`${hdr}.${pay}`)));
    const jwt = `${hdr}.${pay}.${sig}`;
    // Use a raw string body to avoid any URLSearchParams encoding quirks in Workers.
    const reqBody = `grant_type=urn%3Aietf%3Aparams%3Aoauth2%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`;
    const tr = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: reqBody,
    });
    const td = await tr.json<{ access_token?: string; error?: string; error_description?: string }>();
    if (!td.access_token) return c.json({ step:"token_exchange", status: tr.status, error: td.error, description: td.error_description, jwt_prefix: jwt.slice(0,40), body_prefix: reqBody.slice(0,80) });
    accessToken = td.access_token;
  } catch(e: unknown) {
    return c.json({ step:"jwt_build", error: String(e) });
  }

  // Step 2: fetch my device tokens
  const { results: tokens } = await c.env.DB.prepare(
    "SELECT token FROM device_tokens WHERE user_id = ?"
  ).bind(sub).all<{ token: string }>();

  if (tokens.length === 0) return c.json({ step:"tokens", error:"no device tokens for this user" });

  // Step 3: send to first token
  const token = tokens[0].token;
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const fr = await fetch(url, {
    method:"POST",
    headers:{ authorization:`Bearer ${accessToken}`, "content-type":"application/json" },
    body: JSON.stringify({ message:{ token, notification:{ title:"FCM debug test", body:"If you see this, FCM works!" }, android:{ notification:{ channel_id:"chore_updates" } } } }),
  });
  const fd = await fr.json();
  return c.json({ step:"fcm_send", status: fr.status, token_prefix: token.slice(0,20), response: fd });
});

// ---------- Activity feed ----------

app.get("/api/activity", async (c) => {
  const { hh } = c.get("user");
  const beforeParam = c.req.query("before");
  const before = beforeParam ? parseInt(beforeParam) : Date.now() + 1;
  const limit = Math.min(parseInt(c.req.query("limit") ?? "50"), 100);

  const { results } = await c.env.DB.prepare(
    `SELECT c.id, c.task_id AS taskId, t.name AS taskName,
            a.name AS areaName, u.display_name AS doneBy, c.done_at AS doneAt
       FROM completions c
       JOIN tasks t ON t.id = c.task_id
       JOIN areas a ON a.id = t.area_id
       JOIN users u ON u.id = c.user_id
      WHERE a.household_id = ?
        AND c.done_at < ?
      ORDER BY c.done_at DESC
      LIMIT ?`,
  ).bind(hh, before, limit).all();
  return c.json(results);
});

// ---------- Workload ----------

app.get("/api/household/workload", async (c) => {
  const { hh } = c.get("user");
  const now = new Date();
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime();

  const { results } = await c.env.DB.prepare(
    `SELECT u.id AS userId, u.display_name AS displayName,
            COALESCE(SUM(sq.effort_points), 0) AS effortPoints
       FROM users u
       LEFT JOIN (
         SELECT c.user_id, t.effort_points
           FROM completions c
           JOIN tasks t ON t.id = c.task_id
           JOIN areas a ON a.id = t.area_id
          WHERE a.household_id = ? AND c.done_at >= ?
       ) sq ON sq.user_id = u.id
      WHERE u.household_id = ?
      GROUP BY u.id
      ORDER BY effortPoints DESC`,
  ).bind(hh, monthStart, hh).all();
  return c.json(results);
});

app.delete("/api/tasks/:id", async (c) => {
  const { hh } = c.get("user");
  const id = c.req.param("id");
  const res = await c.env.DB.prepare(
    `DELETE FROM tasks
      WHERE id = ?
        AND area_id IN (SELECT id FROM areas WHERE household_id = ?)`,
  )
    .bind(id, hh)
    .run();
  if (!res.meta.changes) throw new HTTPException(404);
  return c.json({ ok: true });
});

// ---------- Error handler ----------

app.onError((err, c) => {
  if (err instanceof HTTPException) return err.getResponse();
  console.error(err);
  return c.json({ error: "internal error" }, 500);
});

export default app;
