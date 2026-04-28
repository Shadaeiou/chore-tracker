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
    "SELECT id, name, created_at AS createdAt, paused_until AS pausedUntil FROM households WHERE id = ?",
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

app.patch("/api/household", async (c) => {
  const { hh } = c.get("user");
  const body = await c.req.json<{ pausedUntil?: number | null }>();
  if (!("pausedUntil" in body))
    throw new HTTPException(400, { message: "nothing to update" });
  await c.env.DB.prepare("UPDATE households SET paused_until = ? WHERE id = ?")
    .bind(body.pausedUntil ?? null, hh)
    .run();
  return c.json({ ok: true });
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

app.patch("/api/areas/:id", async (c) => {
  const { hh } = c.get("user");
  const id = c.req.param("id");
  const body = await c.req.json<{ name?: string; icon?: string; sortOrder?: number }>();

  const area = await c.env.DB.prepare(
    "SELECT id FROM areas WHERE id = ? AND household_id = ?",
  ).bind(id, hh).first();
  if (!area) throw new HTTPException(404);

  const sets: string[] = [];
  const bindings: unknown[] = [];
  if (body.name !== undefined) { sets.push("name = ?"); bindings.push(body.name); }
  if ("icon" in body) { sets.push("icon = ?"); bindings.push(body.icon ?? null); }
  if (body.sortOrder !== undefined) { sets.push("sort_order = ?"); bindings.push(body.sortOrder); }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });

  bindings.push(id);
  await c.env.DB.prepare(
    `UPDATE areas SET ${sets.join(", ")} WHERE id = ?`,
  ).bind(...bindings).run();
  return c.json({ ok: true });
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
  const household = await c.env.DB.prepare(
    "SELECT paused_until FROM households WHERE id = ?",
  ).bind(hh).first<{ paused_until: number | null }>();
  const now = Date.now();
  const isPaused = household?.paused_until != null && household.paused_until > now;

  const { results } = await c.env.DB.prepare(
    `SELECT t.id, t.area_id AS areaId, t.name,
            t.frequency_days AS frequencyDays,
            t.last_done_at   AS lastDoneAt,
            t.created_at     AS createdAt,
            t.assigned_to    AS assignedTo,
            t.auto_rotate    AS autoRotate,
            t.effort_points  AS effortPoints,
            t.snoozed_until  AS snoozedUntil,
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
    .all<{
      autoRotate: number;
      snoozedUntil: number | null;
      lastDoneAt: number | null;
      frequencyDays: number;
    } & Record<string, unknown>>();

  return c.json(results.map((r) => {
    const isSnoozed = r.snoozedUntil != null && r.snoozedUntil > now;
    let dueness: number;
    if (isPaused || isSnoozed) {
      dueness = 0;
    } else if (r.lastDoneAt == null) {
      dueness = 1.0;
    } else {
      const window = r.frequencyDays * 86_400_000;
      dueness = window > 0 ? (now - r.lastDoneAt) / window : 1.0;
    }
    return {
      ...r,
      autoRotate: r.autoRotate !== 0,
      dueness,
    };
  }));
});

app.post("/api/tasks", async (c) => {
  const { sub, hh } = c.get("user");
  const body = await c.req.json<{
    areaId: string;
    name?: string;
    frequencyDays?: number;
    assignedTo?: string;
    autoRotate?: boolean;
    effortPoints?: number;
    templateId?: string;
    lastDoneAt?: number | null;
  }>();
  if (!body.areaId) throw new HTTPException(400, { message: "areaId required" });

  // If templateId is provided, fill in any unspecified fields from the template.
  if (body.templateId) {
    const tmpl = await c.env.DB.prepare(
      "SELECT name, suggested_frequency_days, suggested_effort FROM task_templates WHERE id = ?",
    ).bind(body.templateId).first<{
      name: string;
      suggested_frequency_days: number;
      suggested_effort: number;
    }>();
    if (!tmpl) throw new HTTPException(400, { message: "unknown templateId" });
    body.name = body.name ?? tmpl.name;
    body.frequencyDays = body.frequencyDays ?? tmpl.suggested_frequency_days;
    body.effortPoints = body.effortPoints ?? tmpl.suggested_effort;
  }

  if (!body.name || !body.frequencyDays || body.frequencyDays <= 0)
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
  const lastDoneAt = body.lastDoneAt ?? null;
  await c.env.DB.prepare(
    `INSERT INTO tasks (id, area_id, name, frequency_days, assigned_to, auto_rotate, effort_points, last_done_at, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(id, body.areaId, body.name, body.frequencyDays, assignedTo, autoRotate, effortPoints, lastDoneAt, now)
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
    lastDoneAt,
    lastDoneBy: null,
    createdAt: now,
  });
});

app.get("/api/task-templates", async (c) => {
  const area = c.req.query("area");
  const query = area
    ? c.env.DB.prepare(
        `SELECT id, name, suggested_area AS suggestedArea,
                suggested_frequency_days AS suggestedFrequencyDays,
                suggested_effort AS suggestedEffort
           FROM task_templates WHERE suggested_area = ? ORDER BY id`,
      ).bind(area)
    : c.env.DB.prepare(
        `SELECT id, name, suggested_area AS suggestedArea,
                suggested_frequency_days AS suggestedFrequencyDays,
                suggested_effort AS suggestedEffort
           FROM task_templates ORDER BY suggested_area, id`,
      );
  const { results } = await query.all();
  return c.json(results);
});

app.post("/api/tasks/:id/complete", async (c) => {
  const { sub, hh } = c.get("user");
  const taskId = c.req.param("id");
  // Optional retroactive timestamp; body may be empty for "right now" completion.
  const body = await c.req.json<{ at?: number }>().catch(() => ({} as { at?: number }));

  const task = await c.env.DB.prepare(
    `SELECT t.id, t.name, t.auto_rotate, t.assigned_to, t.created_at FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  )
    .bind(taskId, hh)
    .first<{ id: string; name: string; auto_rotate: number; assigned_to: string | null; created_at: number }>();
  if (!task) throw new HTTPException(404);

  const realNow = Date.now();
  let completedAt = realNow;
  if (body.at !== undefined && body.at !== null) {
    if (body.at > realNow) throw new HTTPException(400, { message: "completion time cannot be in the future" });
    if (body.at < task.created_at) throw new HTTPException(400, { message: "completion time predates task creation" });
    completedAt = body.at;
  }
  const completionId = newId();
  const stmts: D1PreparedStatement[] = [
    c.env.DB.prepare(
      "INSERT INTO completions (id, task_id, user_id, done_at) VALUES (?, ?, ?, ?)",
    ).bind(completionId, taskId, sub, completedAt),
    // last_done_at uses MAX so an older retroactive completion doesn't overwrite a newer one.
    c.env.DB.prepare(
      `UPDATE tasks SET last_done_at = (
         SELECT MAX(done_at) FROM completions WHERE task_id = ?
       ), snoozed_until = NULL WHERE id = ?`,
    ).bind(taskId, taskId),
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

  return c.json({ ok: true, doneAt: completedAt });
});

app.post("/api/tasks/:id/snooze", async (c) => {
  const { hh } = c.get("user");
  const taskId = c.req.param("id");
  const body = await c.req.json<{ until: number }>();
  if (!body.until || body.until <= Date.now())
    throw new HTTPException(400, { message: "until must be a future timestamp" });

  const task = await c.env.DB.prepare(
    `SELECT t.id FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  ).bind(taskId, hh).first();
  if (!task) throw new HTTPException(404);

  await c.env.DB.prepare("UPDATE tasks SET snoozed_until = ? WHERE id = ?")
    .bind(body.until, taskId).run();
  return c.json({ ok: true });
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

// Delete an arbitrary completion by id (used by Activity tab undo).
app.delete("/api/completions/:id", async (c) => {
  const { hh } = c.get("user");
  const completionId = c.req.param("id");

  const row = await c.env.DB.prepare(
    `SELECT c.id, c.task_id FROM completions c
       JOIN tasks t ON t.id = c.task_id
       JOIN areas a ON a.id = t.area_id
      WHERE c.id = ? AND a.household_id = ?`,
  ).bind(completionId, hh).first<{ id: string; task_id: string }>();
  if (!row) throw new HTTPException(404);

  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM completions WHERE id = ?").bind(completionId),
    c.env.DB.prepare(
      `UPDATE tasks SET last_done_at = (
         SELECT MAX(done_at) FROM completions WHERE task_id = ?
       ) WHERE id = ?`,
    ).bind(row.task_id, row.task_id),
  ]);
  return c.json({ ok: true });
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
