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
import { sendToTokens, sendRefreshToTokens, sendCommentToTokens, sendUpdateToTokens } from "./fcm";

type Bindings = {
  DB: D1Database;
  JWT_SECRET: string;
  FCM_SERVICE_ACCOUNT: string;
  /** Shared secret between the GitHub Actions release workflow and this
   *  worker. When set, POST /api/releases/announce verifies an
   *  Authorization: Bearer header against it. Set via
   *  `wrangler secret put RELEASE_WEBHOOK_TOKEN`. */
  RELEASE_WEBHOOK_TOKEN?: string;
};

type Variables = {
  user: JwtPayload;
};

const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

/**
 * Fan out a silent "refresh" push to every device in the household except the
 * actor's. Lets other people's apps re-pull state without polling. Errors are
 * swallowed (best-effort delivery; the next manual pull-to-refresh covers
 * anyone we miss).
 */
async function fanOutRefresh(
  c: { env: Bindings; executionCtx: { waitUntil: (p: Promise<unknown>) => void } },
  hh: string,
  exceptUserId: string,
): Promise<void> {
  if (!c.env.FCM_SERVICE_ACCOUNT) return;
  const { results } = await c.env.DB.prepare(
    `SELECT dt.token FROM device_tokens dt
       JOIN users u ON u.id = dt.user_id
      WHERE u.household_id = ? AND dt.user_id != ?`,
  ).bind(hh, exceptUserId).all<{ token: string }>();
  const tokens = results.map((r) => r.token);
  if (tokens.length === 0) return;
  c.executionCtx.waitUntil(sendRefreshToTokens(tokens, c.env));
}

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

    // Invite-based registration → joins as a regular member.
    await c.env.DB.batch([
      c.env.DB.prepare(
        `INSERT INTO users (id, email, display_name, password_hash, password_salt, household_id, role, created_at)
         VALUES (?, ?, ?, ?, ?, ?, 'member', ?)`,
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
    // Creating a brand-new household → registering user is admin.
    if (!body.householdName)
      throw new HTTPException(400, { message: "householdName required" });
    householdId = newId();
    await c.env.DB.batch([
      c.env.DB.prepare(
        "INSERT INTO households (id, name, created_at) VALUES (?, ?, ?)",
      ).bind(householdId, body.householdName, now),
      c.env.DB.prepare(
        `INSERT INTO users (id, email, display_name, password_hash, password_salt, household_id, role, created_at)
         VALUES (?, ?, ?, ?, ?, ?, 'admin', ?)`,
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

// ---------- Webhook: notify all clients of a new release ----------
//
// GitHub Actions calls this after publishing the release tag. The bearer
// token comes from the RELEASE_WEBHOOK_TOKEN secret on both sides
// (`wrangler secret put RELEASE_WEBHOOK_TOKEN` here, and a matching repo
// secret on GitHub). We deliberately mount this on /webhooks to dodge the
// /api/* JWT middleware above.
app.post("/webhooks/release-announce", async (c) => {
  const expected = c.env.RELEASE_WEBHOOK_TOKEN;
  if (!expected) throw new HTTPException(503, { message: "webhook token not configured" });
  const auth = c.req.header("authorization");
  if (auth !== `Bearer ${expected}`) throw new HTTPException(401);
  const body = await c.req.json<{ versionName?: string; versionCode?: number }>();
  if (!body.versionName || typeof body.versionCode !== "number") {
    throw new HTTPException(400, { message: "versionName + versionCode required" });
  }
  const { results } = await c.env.DB.prepare(
    "SELECT token FROM device_tokens",
  ).all<{ token: string }>();
  const tokens = results.map((r) => r.token);
  if (tokens.length === 0) return c.json({ pushed: 0 });
  if (c.env.FCM_SERVICE_ACCOUNT) {
    c.executionCtx.waitUntil(
      sendUpdateToTokens(
        tokens,
        { versionName: body.versionName, versionCode: body.versionCode },
        c.env,
      ),
    );
  }
  return c.json({ pushed: tokens.length });
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
    `SELECT id, display_name AS displayName, email,
            avatar_version AS avatarVersion, role, profile_color AS profileColor
       FROM users WHERE household_id = ? ORDER BY created_at`,
  )
    .bind(hh)
    .all();
  return c.json({ household, members });
});

// ---------- Self profile ----------

const MAX_AVATAR_BYTES = 250_000;

function validateAvatar(avatar: string | null): void {
  if (avatar === null) return;
  if (!avatar.startsWith("data:image/")) {
    throw new HTTPException(400, { message: "avatar must be a data:image/* URL" });
  }
  if (avatar.length > MAX_AVATAR_BYTES) {
    throw new HTTPException(413, { message: "avatar too large" });
  }
}

app.get("/api/me", async (c) => {
  const { sub } = c.get("user");
  const me = await c.env.DB.prepare(
    `SELECT id, email, display_name AS displayName, avatar,
            avatar_version AS avatarVersion, role, profile_color AS profileColor FROM users WHERE id = ?`,
  ).bind(sub).first();
  if (!me) throw new HTTPException(404);
  return c.json(me);
});

app.patch("/api/me", async (c) => {
  const { sub, hh } = c.get("user");
  const body = await c.req.json<{ displayName?: string; avatar?: string | null; profileColor?: string | null }>();
  const sets: string[] = [];
  const bindings: unknown[] = [];
  if (body.displayName !== undefined) {
    if (!body.displayName.trim()) {
      throw new HTTPException(400, { message: "displayName cannot be blank" });
    }
    sets.push("display_name = ?");
    bindings.push(body.displayName.trim());
  }
  if ("avatar" in body) {
    validateAvatar(body.avatar ?? null);
    sets.push("avatar = ?");
    bindings.push(body.avatar ?? null);
    sets.push("avatar_version = avatar_version + 1");
  }
  if ("profileColor" in body) {
    const color = body.profileColor?.trim() ?? null;
    if (color !== null && !/^#[0-9A-Fa-f]{6}$/.test(color)) {
      throw new HTTPException(400, { message: "profileColor must be #RRGGBB or null" });
    }
    sets.push("profile_color = ?");
    bindings.push(color);
  }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });
  bindings.push(sub);
  await c.env.DB.prepare(`UPDATE users SET ${sets.join(", ")} WHERE id = ?`).bind(...bindings).run();
  await fanOutRefresh(c, hh, sub);
  const me = await c.env.DB.prepare(
    `SELECT id, email, display_name AS displayName, avatar,
            avatar_version AS avatarVersion, role, profile_color AS profileColor FROM users WHERE id = ?`,
  ).bind(sub).first();
  return c.json(me);
});

// Admins can remove anyone but themselves from their household. The kicked
// user's row is hard-deleted; their assigned tasks fall back to unassigned
// and their completions cascade away. (We don't try to preserve activity
// history for removed users yet — keep it simple.)
app.delete("/api/users/:id", async (c) => {
  const { sub, hh } = c.get("user");
  const targetId = c.req.param("id");
  if (targetId === sub) {
    throw new HTTPException(400, { message: "cannot remove yourself" });
  }
  const me = await c.env.DB.prepare(
    "SELECT role FROM users WHERE id = ? AND household_id = ?",
  ).bind(sub, hh).first<{ role: string }>();
  if (me?.role !== "admin") throw new HTTPException(403, { message: "admin only" });
  const target = await c.env.DB.prepare(
    "SELECT id FROM users WHERE id = ? AND household_id = ?",
  ).bind(targetId, hh).first();
  if (!target) throw new HTTPException(404);
  await c.env.DB.batch([
    // Unassign tasks the kicked user owned and break references that the
    // foreign keys won't cascade for us (completions.user_id and invites.*
    // have no ON DELETE clause). device_tokens cascades automatically.
    c.env.DB.prepare(
      "UPDATE tasks SET assigned_to = NULL WHERE assigned_to = ?",
    ).bind(targetId),
    c.env.DB.prepare("DELETE FROM completions WHERE user_id = ?").bind(targetId),
    c.env.DB.prepare("DELETE FROM invites WHERE created_by = ? OR used_by = ?").bind(targetId, targetId),
    c.env.DB.prepare("DELETE FROM users WHERE id = ?").bind(targetId),
  ]);
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

app.get("/api/users/:id/avatar", async (c) => {
  const { hh } = c.get("user");
  const id = c.req.param("id");
  const row = await c.env.DB.prepare(
    `SELECT avatar, avatar_version AS avatarVersion, profile_color AS profileColor FROM users
       WHERE id = ? AND household_id = ?`,
  ).bind(id, hh).first<{ avatar: string | null; avatarVersion: number; profileColor: string | null }>();
  if (!row) throw new HTTPException(404);
  return c.json(row);
});

app.patch("/api/household", async (c) => {
  const { sub, hh } = c.get("user");
  const body = await c.req.json<{ pausedUntil?: number | null; name?: string }>();
  const sets: string[] = [];
  const bindings: unknown[] = [];
  if ("pausedUntil" in body) { sets.push("paused_until = ?"); bindings.push(body.pausedUntil ?? null); }
  if (body.name !== undefined) {
    if (!body.name.trim()) throw new HTTPException(400, { message: "name cannot be blank" });
    sets.push("name = ?"); bindings.push(body.name.trim());
  }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });
  bindings.push(hh);
  await c.env.DB.prepare(`UPDATE households SET ${sets.join(", ")} WHERE id = ?`)
    .bind(...bindings).run();
  await fanOutRefresh(c, hh, sub);
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
  const { sub, hh } = c.get("user");
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
  await fanOutRefresh(c, hh, sub);
  return c.json({
    id,
    name: body.name,
    icon: body.icon,
    sortOrder: body.sortOrder ?? 0,
    createdAt: now,
  });
});

app.patch("/api/areas/:id", async (c) => {
  const { sub, hh } = c.get("user");
  const id = c.req.param("id");
  const body = await c.req.json<{ name?: string; icon?: string; sortOrder?: number }>();

  const area = await c.env.DB.prepare(
    "SELECT id FROM areas WHERE id = ? AND household_id = ?",
  ).bind(id, hh).first();
  if (!area) throw new HTTPException(404);

  const sets: string[] = [];
  const bindings: unknown[] = [];
  // Use `!= null` (catches both undefined and null) so the kotlinx Android
  // client's `encodeDefaults = true` default doesn't accidentally clear or
  // overwrite columns. Area icon is nullable but we don't expose a "clear
  // icon" path through this endpoint — pass non-null to set, omit/null to
  // leave alone.
  if (body.name != null) { sets.push("name = ?"); bindings.push(body.name); }
  if (body.icon != null) { sets.push("icon = ?"); bindings.push(body.icon); }
  if (body.sortOrder != null) { sets.push("sort_order = ?"); bindings.push(body.sortOrder); }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });

  bindings.push(id);
  await c.env.DB.prepare(
    `UPDATE areas SET ${sets.join(", ")} WHERE id = ?`,
  ).bind(...bindings).run();
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

app.post("/api/areas/:id/copy", async (c) => {
  const { sub, hh } = c.get("user");
  const sourceId = c.req.param("id");
  const body = await c.req.json<{ name: string }>();
  if (!body.name?.trim()) throw new HTTPException(400, { message: "name required" });

  const source = await c.env.DB.prepare(
    "SELECT id, icon, sort_order FROM areas WHERE id = ? AND household_id = ?",
  ).bind(sourceId, hh).first<{ id: string; icon: string | null; sort_order: number }>();
  if (!source) throw new HTTPException(404);

  const { results: tasks } = await c.env.DB.prepare(
    `SELECT name, frequency_days, auto_rotate, effort_points, notes
       FROM tasks WHERE area_id = ?`,
  ).bind(sourceId).all<{
    name: string; frequency_days: number; auto_rotate: number; effort_points: number;
    notes: string | null;
  }>();

  const newAreaId = newId();
  const now = Date.now();
  const stmts = [
    c.env.DB.prepare(
      `INSERT INTO areas (id, household_id, name, icon, sort_order, created_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
    ).bind(newAreaId, hh, body.name.trim(), source.icon, source.sort_order, now),
  ];
  for (const t of tasks) {
    // Seed last_done_at = now so copied tasks start green, matching the UX
    // for manually-created tasks. Avoids the "wall of red" right after copy.
    stmts.push(
      c.env.DB.prepare(
        `INSERT INTO tasks (id, area_id, name, frequency_days, assigned_to, auto_rotate, effort_points, last_done_at, notes, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(newId(), newAreaId, t.name, t.frequency_days, sub, t.auto_rotate, t.effort_points, now, t.notes, now),
    );
  }
  await c.env.DB.batch(stmts);
  await fanOutRefresh(c, hh, sub);
  // Return a full Area-shaped response so kotlinx.serialization on Android can
  // deserialize it without complaining about missing fields.
  return c.json({
    id: newAreaId,
    name: body.name.trim(),
    icon: source.icon,
    sortOrder: source.sort_order,
    createdAt: now,
    copiedTasks: tasks.length,
  });
});

app.delete("/api/areas/:id", async (c) => {
  const { sub, hh } = c.get("user");
  const id = c.req.param("id");
  const res = await c.env.DB.prepare(
    "DELETE FROM areas WHERE id = ? AND household_id = ?",
  )
    .bind(id, hh)
    .run();
  if (!res.meta.changes) throw new HTTPException(404);
  await fanOutRefresh(c, hh, sub);
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
            t.on_demand      AS onDemand,
            t.postpone_anchor AS postponeAnchor,
            t.notes          AS notes,
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
      onDemand: number;
      snoozedUntil: number | null;
      lastDoneAt: number | null;
      frequencyDays: number;
    } & Record<string, unknown>>();

  return c.json(results.map((r) => {
    const isSnoozed = r.snoozedUntil != null && r.snoozedUntil > now;
    const onDemand = r.onDemand !== 0;
    let dueness: number;
    if (onDemand || isPaused || isSnoozed) {
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
      onDemand,
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
    notes?: string | null;
    onDemand?: boolean;
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

  const onDemand = body.onDemand === true;
  // On-demand tasks have no schedule, so frequency is irrelevant; we still
  // store a non-null value to keep the column NOT NULL.
  const frequencyDays = onDemand ? (body.frequencyDays ?? 1) : body.frequencyDays;
  if (!body.name || !frequencyDays || frequencyDays <= 0)
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
  const notes = body.notes ?? null;
  await c.env.DB.prepare(
    `INSERT INTO tasks (id, area_id, name, frequency_days, assigned_to, auto_rotate, effort_points, last_done_at, notes, on_demand, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(id, body.areaId, body.name, frequencyDays, assignedTo, autoRotate, effortPoints, lastDoneAt, notes, onDemand ? 1 : 0, now)
    .run();
  await fanOutRefresh(c, hh, sub);
  return c.json({
    id,
    areaId: body.areaId,
    name: body.name,
    frequencyDays,
    assignedTo,
    assignedToName: null,
    autoRotate: body.autoRotate ?? false,
    onDemand,
    effortPoints,
    lastDoneAt,
    lastDoneBy: null,
    notes,
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
  // Optional retroactive timestamp, per-completion notes, and an override for
  // who completed the task (defaults to the JWT user). `completedBy` lets a
  // member log a completion on behalf of someone else in the same household.
  const body = await c.req.json<{ at?: number; notes?: string; completedBy?: string }>()
    .catch(() => ({} as { at?: number; notes?: string; completedBy?: string }));

  const task = await c.env.DB.prepare(
    `SELECT t.id, t.name, t.auto_rotate, t.assigned_to, t.created_at, t.postpone_anchor
       FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  )
    .bind(taskId, hh)
    .first<{
      id: string; name: string; auto_rotate: number;
      assigned_to: string | null; created_at: number;
      postpone_anchor: number | null;
    }>();
  if (!task) throw new HTTPException(404);

  // If completedBy is provided, verify it's a member of the same household.
  let completedBy = sub;
  if (body.completedBy && body.completedBy !== sub) {
    const member = await c.env.DB.prepare(
      "SELECT id FROM users WHERE id = ? AND household_id = ?",
    ).bind(body.completedBy, hh).first<{ id: string }>();
    if (!member) throw new HTTPException(400, { message: "completedBy is not a member of this household" });
    completedBy = body.completedBy;
  }

  const realNow = Date.now();
  let completedAt = realNow;
  if (body.at !== undefined && body.at !== null) {
    if (body.at > realNow) throw new HTTPException(400, { message: "completion time cannot be in the future" });
    completedAt = body.at;
  }
  const completionId = newId();
  const trimmedNotes = body.notes?.trim();
  const completionNotes = trimmedNotes ? trimmedNotes : null;
  const stmts: D1PreparedStatement[] = [
    c.env.DB.prepare(
      "INSERT INTO completions (id, task_id, user_id, done_at, notes) VALUES (?, ?, ?, ?, ?)",
    ).bind(completionId, taskId, completedBy, completedAt, completionNotes),
  ];
  if (task.postpone_anchor != null) {
    // The user snoozed this chore (e.g. trash a day late); honor the original
    // schedule by stamping last_done_at to the captured anchor instead of the
    // actual completion time. Activity feed still shows the truthful done_at.
    stmts.push(
      c.env.DB.prepare(
        `UPDATE tasks SET last_done_at = ?, snoozed_until = NULL,
                          postpone_anchor = NULL WHERE id = ?`,
      ).bind(task.postpone_anchor, taskId),
    );
  } else {
    // Normal path: last_done_at uses MAX so an older retroactive completion
    // doesn't overwrite a newer one.
    stmts.push(
      c.env.DB.prepare(
        `UPDATE tasks SET last_done_at = (
           SELECT MAX(done_at) FROM completions WHERE task_id = ?
         ), snoozed_until = NULL WHERE id = ?`,
      ).bind(taskId, taskId),
    );
  }

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
  const { sub, hh } = c.get("user");
  const taskId = c.req.param("id");
  const body = await c.req.json<{ until: number }>();
  if (!body.until || body.until <= Date.now())
    throw new HTTPException(400, { message: "until must be a future timestamp" });

  // Pull the fields needed to compute the original due (so completing a
  // snoozed chore later doesn't permanently shift its cadence).
  const task = await c.env.DB.prepare(
    `SELECT t.id, t.last_done_at, t.frequency_days, t.on_demand, t.postpone_anchor
       FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  ).bind(taskId, hh).first<{
    id: string; last_done_at: number | null; frequency_days: number;
    on_demand: number; postpone_anchor: number | null;
  }>();
  if (!task) throw new HTTPException(404);

  // Capture the schedule anchor on the first snooze of an in-cadence chore.
  // On-demand chores have no cadence to preserve; tasks with a stale anchor
  // from a previous snooze keep that anchor (the original due hasn't moved).
  let postponeAnchor: number | null = task.postpone_anchor;
  if (postponeAnchor == null && task.on_demand === 0 && task.last_done_at != null) {
    postponeAnchor = task.last_done_at + task.frequency_days * 86_400_000;
  }

  await c.env.DB.prepare(
    "UPDATE tasks SET snoozed_until = ?, postpone_anchor = ? WHERE id = ?",
  ).bind(body.until, postponeAnchor, taskId).run();
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

app.delete("/api/tasks/:id/snooze", async (c) => {
  const { sub, hh } = c.get("user");
  const taskId = c.req.param("id");
  const task = await c.env.DB.prepare(
    `SELECT t.id FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  ).bind(taskId, hh).first();
  if (!task) throw new HTTPException(404);
  // Unsnooze cancels the postponement entirely — schedule reverts to
  // last_done_at + frequency.
  await c.env.DB.prepare(
    "UPDATE tasks SET snoozed_until = NULL, postpone_anchor = NULL WHERE id = ?",
  ).bind(taskId).run();
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

app.patch("/api/tasks/:id", async (c) => {
  const { sub, hh } = c.get("user");
  const id = c.req.param("id");
  const body = await c.req.json<{
    name?: string;
    frequencyDays?: number;
    assignedTo?: string | null;
    autoRotate?: boolean;
    effortPoints?: number;
    notes?: string | null;
    areaId?: string;
    onDemand?: boolean;
  }>();

  const task = await c.env.DB.prepare(
    `SELECT t.id, t.name, t.assigned_to, t.frequency_days, t.last_done_at,
            t.snoozed_until, t.on_demand
       FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE t.id = ? AND a.household_id = ?`,
  ).bind(id, hh).first<{
    id: string; name: string; assigned_to: string | null;
    frequency_days: number; last_done_at: number | null;
    snoozed_until: number | null; on_demand: number;
  }>();
  if (!task) throw new HTTPException(404);

  const sets: string[] = [];
  const bindings: unknown[] = [];
  if (body.areaId) {
    // Moving a task to a different area is allowed only within the same household.
    // (Truthy check skips both `undefined` and `null` — clients that include
    // areaId: null to mean "no change" shouldn't trigger validation.)
    const newArea = await c.env.DB.prepare(
      "SELECT id FROM areas WHERE id = ? AND household_id = ?",
    ).bind(body.areaId, hh).first();
    if (!newArea) throw new HTTPException(400, { message: "areaId is not in this household" });
    sets.push("area_id = ?"); bindings.push(body.areaId);
  }
  // The kotlinx Android client serializes every default-null field as
  // explicit null, so use `!= null` (skips both undefined and null) to mean
  // "leave alone" for required columns. assignedTo and notes are the two
  // fields where null legitimately means "clear", so they intentionally
  // round-trip empty-string sentinels: notes uses "" via the editor, and
  // assignment clearing is handled via a separate flow if ever needed.
  if (body.name != null) { sets.push("name = ?"); bindings.push(body.name); }
  if (body.frequencyDays != null) {
    if (body.frequencyDays <= 0) throw new HTTPException(400, { message: "frequencyDays must be positive" });
    sets.push("frequency_days = ?"); bindings.push(body.frequencyDays);
  }
  if (body.assignedTo != null) { sets.push("assigned_to = ?"); bindings.push(body.assignedTo); }
  if (body.autoRotate != null) { sets.push("auto_rotate = ?"); bindings.push(body.autoRotate ? 1 : 0); }
  if (body.effortPoints != null) { sets.push("effort_points = ?"); bindings.push(body.effortPoints); }
  if (body.notes != null) {
    // Empty string is the explicit "clear notes" sentinel sent by the
    // editor dialog. null means "leave alone".
    const trimmed = body.notes.trim();
    sets.push("notes = ?");
    bindings.push(trimmed ? trimmed : null);
  }
  if (body.onDemand != null) { sets.push("on_demand = ?"); bindings.push(body.onDemand ? 1 : 0); }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });

  bindings.push(id);
  await c.env.DB.prepare(
    `UPDATE tasks SET ${sets.join(", ")} WHERE id = ?`,
  ).bind(...bindings).run();
  await fanOutRefresh(c, hh, sub);

  // If the actor reassigned this chore to someone else and that chore is
  // due today (or it's an on-demand item now sitting on their plate),
  // notify the new assignee. Don't notify on assignments due in the
  // future — those'll surface naturally when they roll into Today.
  if (c.env.FCM_SERVICE_ACCOUNT && "assignedTo" in body) {
    const newAssignee = body.assignedTo ?? null;
    const previousAssignee = task.assigned_to;
    if (newAssignee && newAssignee !== sub && newAssignee !== previousAssignee) {
      const isSnoozed = task.snoozed_until != null && task.snoozed_until > Date.now();
      const onDemand = task.on_demand !== 0;
      const dueWindowMs = task.frequency_days * 86_400_000;
      const dueAt = (task.last_done_at ?? 0) + dueWindowMs;
      const startOfTomorrow = new Date();
      startOfTomorrow.setHours(24, 0, 0, 0);
      const dueToday = onDemand || task.last_done_at == null || dueAt < startOfTomorrow.getTime();
      if (!isSnoozed && dueToday) {
        const { results: tokenRows } = await c.env.DB.prepare(
          `SELECT token FROM device_tokens WHERE user_id = ?`,
        ).bind(newAssignee).all<{ token: string }>();
        const tokens = tokenRows.map((r) => r.token);
        if (tokens.length > 0) {
          const actor = await c.env.DB.prepare(
            "SELECT display_name FROM users WHERE id = ?",
          ).bind(sub).first<{ display_name: string }>();
          c.executionCtx.waitUntil(
            sendToTokens(
              tokens,
              {
                title: "Chore assigned to you",
                body: `${actor?.display_name ?? "Someone"} assigned you "${task.name}"`,
              },
              c.env,
            ),
          );
        }
      }
    }
  }
  return c.json({ ok: true });
});

// Undo: delete the most-recent completion for this task and recompute
// last_done_at from whatever's left (or NULL if there are no completions).
app.delete("/api/tasks/:id/completions/last", async (c) => {
  const { sub, hh } = c.get("user");
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
  await fanOutRefresh(c, hh, sub);
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
            a.name AS areaName, u.id AS doneById, u.display_name AS doneBy,
            u.avatar_version AS doneByAvatarVersion,
            c.done_at AS doneAt, c.notes AS notes
       FROM completions c
       JOIN tasks t ON t.id = c.task_id
       JOIN areas a ON a.id = t.area_id
       JOIN users u ON u.id = c.user_id
      WHERE a.household_id = ?
        AND c.done_at < ?
      ORDER BY c.done_at DESC
      LIMIT ?`,
  ).bind(hh, before, limit).all<{
    id: string; taskId: string; taskName: string; areaName: string;
    doneById: string; doneBy: string; doneByAvatarVersion: number;
    doneAt: number; notes: string | null;
  }>();
  if (results.length === 0) return c.json([]);

  // Pull reactions and comments in two extra queries and stitch onto each
  // entry. Faster than N+1 round-trips and easier than embedding JSON in
  // the main query. The IN-clause is built dynamically; D1 doesn't support
  // array binding so we expand to N placeholders.
  const ids = results.map((r) => r.id);
  const placeholders = ids.map(() => "?").join(", ");
  const [reactionsResult, commentsResult] = await c.env.DB.batch([
    c.env.DB.prepare(
      `SELECT completion_id AS completionId, user_id AS userId, emoji
         FROM completion_reactions WHERE completion_id IN (${placeholders})`,
    ).bind(...ids),
    c.env.DB.prepare(
      `SELECT cc.id, cc.completion_id AS completionId, cc.user_id AS userId,
              u.display_name AS displayName, u.avatar_version AS avatarVersion,
              cc.text, cc.created_at AS createdAt
         FROM completion_comments cc
         JOIN users u ON u.id = cc.user_id
        WHERE cc.completion_id IN (${placeholders})
        ORDER BY cc.created_at`,
    ).bind(...ids),
  ]);
  const reactionsByCompletion = new Map<string, Array<{ userId: string; emoji: string }>>();
  for (const r of (reactionsResult.results as Array<{ completionId: string; userId: string; emoji: string }>)) {
    const list = reactionsByCompletion.get(r.completionId) ?? [];
    list.push({ userId: r.userId, emoji: r.emoji });
    reactionsByCompletion.set(r.completionId, list);
  }
  const commentsByCompletion = new Map<string, Array<{
    id: string; userId: string; displayName: string; avatarVersion: number;
    text: string; createdAt: number;
  }>>();
  for (const cm of (commentsResult.results as Array<{
    id: string; completionId: string; userId: string; displayName: string;
    avatarVersion: number; text: string; createdAt: number;
  }>)) {
    const list = commentsByCompletion.get(cm.completionId) ?? [];
    list.push({
      id: cm.id, userId: cm.userId, displayName: cm.displayName,
      avatarVersion: cm.avatarVersion, text: cm.text, createdAt: cm.createdAt,
    });
    commentsByCompletion.set(cm.completionId, list);
  }
  return c.json(results.map((r) => ({
    ...r,
    reactions: reactionsByCompletion.get(r.id) ?? [],
    comments: commentsByCompletion.get(r.id) ?? [],
  })));
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
  const { sub, hh } = c.get("user");
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
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

// ---------- Completion reactions and comments ----------

const COMPLETION_LOOKUP_SQL = `SELECT c.id, c.user_id, t.name AS task_name
  FROM completions c
  JOIN tasks t ON t.id = c.task_id
  JOIN areas a ON a.id = t.area_id
  WHERE c.id = ? AND a.household_id = ?`;

app.post("/api/completions/:id/reactions", async (c) => {
  const { sub, hh } = c.get("user");
  const completionId = c.req.param("id");
  const body = await c.req.json<{ emoji: string }>();
  const emoji = body.emoji?.trim() ?? "";
  const completion = await c.env.DB.prepare(COMPLETION_LOOKUP_SQL)
    .bind(completionId, hh)
    .first<{ id: string; user_id: string; task_name: string }>();
  if (!completion) throw new HTTPException(404);
  // Empty emoji clears the reaction; otherwise upsert (one per user per
  // completion). Tapping the same emoji twice clears it client-side.
  if (!emoji) {
    await c.env.DB.prepare(
      `DELETE FROM completion_reactions WHERE completion_id = ? AND user_id = ?`,
    ).bind(completionId, sub).run();
  } else {
    await c.env.DB.prepare(
      `INSERT INTO completion_reactions (completion_id, user_id, emoji, created_at)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(completion_id, user_id) DO UPDATE SET emoji = excluded.emoji,
                                                          created_at = excluded.created_at`,
    ).bind(completionId, sub, emoji, Date.now()).run();
  }
  await fanOutRefresh(c, hh, sub);

  // Push to the original completer when someone else adds a reaction. Skip
  // self-reactions and clears (the unreact case) to keep noise down.
  if (emoji && c.env.FCM_SERVICE_ACCOUNT && completion.user_id !== sub) {
    const { results: tokenRows } = await c.env.DB.prepare(
      "SELECT token FROM device_tokens WHERE user_id = ?",
    ).bind(completion.user_id).all<{ token: string }>();
    const tokens = tokenRows.map((r) => r.token);
    if (tokens.length > 0) {
      const actor = await c.env.DB.prepare(
        "SELECT display_name FROM users WHERE id = ?",
      ).bind(sub).first<{ display_name: string }>();
      c.executionCtx.waitUntil(
        sendToTokens(
          tokens,
          {
            title: `${actor?.display_name ?? "Someone"} reacted ${emoji}`,
            body: `to "${completion.task_name}"`,
          },
          c.env,
        ),
      );
    }
  }
  return c.json({ ok: true });
});

app.post("/api/completions/:id/comments", async (c) => {
  const { sub, hh } = c.get("user");
  const completionId = c.req.param("id");
  const body = await c.req.json<{ text: string }>();
  const text = body.text?.trim() ?? "";
  if (!text) throw new HTTPException(400, { message: "text required" });
  const completion = await c.env.DB.prepare(COMPLETION_LOOKUP_SQL)
    .bind(completionId, hh)
    .first<{ id: string; user_id: string; task_name: string }>();
  if (!completion) throw new HTTPException(404);
  const id = newId();
  const now = Date.now();
  await c.env.DB.prepare(
    `INSERT INTO completion_comments (id, completion_id, user_id, text, created_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).bind(id, completionId, sub, text, now).run();
  await fanOutRefresh(c, hh, sub);

  // Push to the original completer when someone else comments on their work.
  // Sent data-only so the Android client can build the notification with an
  // inline reply box + quick-react action button. Reactions stay silent to
  // keep the channel from getting noisy.
  if (c.env.FCM_SERVICE_ACCOUNT && completion.user_id !== sub) {
    const { results: tokenRows } = await c.env.DB.prepare(
      "SELECT token FROM device_tokens WHERE user_id = ?",
    ).bind(completion.user_id).all<{ token: string }>();
    const tokens = tokenRows.map((r) => r.token);
    if (tokens.length > 0) {
      const actor = await c.env.DB.prepare(
        "SELECT display_name FROM users WHERE id = ?",
      ).bind(sub).first<{ display_name: string }>();
      c.executionCtx.waitUntil(
        sendCommentToTokens(
          tokens,
          {
            completionId,
            taskName: completion.task_name,
            actorName: actor?.display_name ?? "Someone",
            text: text.length > 140 ? text.substring(0, 137) + "…" : text,
          },
          c.env,
        ),
      );
    }
  }
  return c.json({ id, userId: sub, text, createdAt: now });
});

app.patch("/api/completions/:id/comments/:commentId", async (c) => {
  const { sub, hh } = c.get("user");
  const completionId = c.req.param("id");
  const commentId = c.req.param("commentId");
  const body = await c.req.json<{ text: string }>();
  const text = body.text?.trim() ?? "";
  if (!text) throw new HTTPException(400, { message: "text required" });
  const owned = await c.env.DB.prepare(
    `SELECT cc.id FROM completion_comments cc
       JOIN completions c ON c.id = cc.completion_id
       JOIN tasks t ON t.id = c.task_id
       JOIN areas a ON a.id = t.area_id
      WHERE cc.id = ? AND cc.completion_id = ? AND cc.user_id = ?
        AND a.household_id = ?`,
  ).bind(commentId, completionId, sub, hh).first();
  if (!owned) throw new HTTPException(404);
  await c.env.DB.prepare(
    `UPDATE completion_comments SET text = ? WHERE id = ?`,
  ).bind(text, commentId).run();
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

app.delete("/api/completions/:id/comments/:commentId", async (c) => {
  const { sub, hh } = c.get("user");
  const completionId = c.req.param("id");
  const commentId = c.req.param("commentId");
  const owned = await c.env.DB.prepare(
    `SELECT cc.id FROM completion_comments cc
       JOIN completions c ON c.id = cc.completion_id
       JOIN tasks t ON t.id = c.task_id
       JOIN areas a ON a.id = t.area_id
      WHERE cc.id = ? AND cc.completion_id = ? AND cc.user_id = ?
        AND a.household_id = ?`,
  ).bind(commentId, completionId, sub, hh).first();
  if (!owned) throw new HTTPException(404);
  await c.env.DB.prepare(
    `DELETE FROM completion_comments WHERE id = ?`,
  ).bind(commentId).run();
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

app.delete("/api/tasks/:id", async (c) => {
  const { sub, hh } = c.get("user");
  const id = c.req.param("id");
  const res = await c.env.DB.prepare(
    `DELETE FROM tasks
      WHERE id = ?
        AND area_id IN (SELECT id FROM areas WHERE household_id = ?)`,
  )
    .bind(id, hh)
    .run();
  if (!res.meta.changes) throw new HTTPException(404);
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

// ---------- Todos (à la carte reminders) ----------

app.get("/api/todos", async (c) => {
  const { sub, hh } = c.get("user");
  const { results } = await c.env.DB.prepare(
    `SELECT id, owner_id AS ownerId, text, done_at AS doneAt,
            is_public AS isPublic, created_at AS createdAt
       FROM todos
      WHERE household_id = ?
        AND (owner_id = ? OR is_public = 1)
      ORDER BY done_at IS NOT NULL, created_at DESC`,
  ).bind(hh, sub).all<{
    id: string; ownerId: string; text: string;
    doneAt: number | null; isPublic: number; createdAt: number;
  }>();
  return c.json(results.map((r) => ({ ...r, isPublic: r.isPublic !== 0 })));
});

app.post("/api/todos", async (c) => {
  const { sub, hh } = c.get("user");
  const body = await c.req.json<{ text?: string; isPublic?: boolean; ownerId?: string }>();
  const text = body.text?.trim();
  if (!text) throw new HTTPException(400, { message: "text required" });
  // Default the owner to the creator. If the caller specified another user,
  // make sure that user is in the same household before letting it through.
  let ownerId = sub;
  if (body.ownerId && body.ownerId !== sub) {
    const target = await c.env.DB.prepare(
      "SELECT id FROM users WHERE id = ? AND household_id = ?",
    ).bind(body.ownerId, hh).first();
    if (!target) throw new HTTPException(400, { message: "ownerId is not in this household" });
    ownerId = body.ownerId;
  }
  const id = newId();
  const now = Date.now();
  const isPublic = body.isPublic ? 1 : 0;
  await c.env.DB.prepare(
    `INSERT INTO todos (id, household_id, owner_id, text, is_public, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).bind(id, hh, ownerId, text, isPublic, now).run();
  await fanOutRefresh(c, hh, sub);

  // Notify the assignee whenever someone else creates a todo on their list.
  if (c.env.FCM_SERVICE_ACCOUNT && ownerId !== sub) {
    const { results: tokenRows } = await c.env.DB.prepare(
      "SELECT token FROM device_tokens WHERE user_id = ?",
    ).bind(ownerId).all<{ token: string }>();
    const tokens = tokenRows.map((r) => r.token);
    if (tokens.length > 0) {
      const actor = await c.env.DB.prepare(
        "SELECT display_name FROM users WHERE id = ?",
      ).bind(sub).first<{ display_name: string }>();
      c.executionCtx.waitUntil(
        sendToTokens(
          tokens,
          { title: "New reminder for you", body: `${actor?.display_name ?? "Someone"}: ${text}` },
          c.env,
        ),
      );
    }
  }

  return c.json({
    id, ownerId, text, doneAt: null,
    isPublic: isPublic !== 0, createdAt: now,
  });
});

app.patch("/api/todos/:id", async (c) => {
  const { sub, hh } = c.get("user");
  const id = c.req.param("id");
  const body = await c.req.json<{
    text?: string; isPublic?: boolean; doneAt?: number | null;
  }>();
  const todo = await c.env.DB.prepare(
    `SELECT id FROM todos WHERE id = ? AND household_id = ? AND owner_id = ?`,
  ).bind(id, hh, sub).first();
  if (!todo) throw new HTTPException(404);
  const sets: string[] = [];
  const bindings: unknown[] = [];
  if (body.text !== undefined) {
    if (!body.text.trim()) throw new HTTPException(400, { message: "text cannot be blank" });
    sets.push("text = ?"); bindings.push(body.text.trim());
  }
  if (body.isPublic !== undefined) { sets.push("is_public = ?"); bindings.push(body.isPublic ? 1 : 0); }
  if ("doneAt" in body) { sets.push("done_at = ?"); bindings.push(body.doneAt ?? null); }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });
  bindings.push(id);
  await c.env.DB.prepare(`UPDATE todos SET ${sets.join(", ")} WHERE id = ?`).bind(...bindings).run();
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

app.delete("/api/todos/:id", async (c) => {
  const { sub, hh } = c.get("user");
  const id = c.req.param("id");
  const res = await c.env.DB.prepare(
    `DELETE FROM todos WHERE id = ? AND household_id = ? AND owner_id = ?`,
  ).bind(id, hh, sub).run();
  if (!res.meta.changes) throw new HTTPException(404);
  await fanOutRefresh(c, hh, sub);
  return c.json({ ok: true });
});

// ---------- Rewards ----------

app.get("/api/rewards", async (c) => {
  const { hh } = c.get("user");
  const { results } = await c.env.DB.prepare(
    `SELECT id, name, emoji, effort_cost AS effortCost, created_by AS createdBy,
            created_at AS createdAt, is_active AS isActive
       FROM rewards WHERE household_id = ? ORDER BY effort_cost ASC`,
  ).bind(hh).all();
  return c.json(results.map((r: any) => ({ ...r, isActive: r.isActive !== 0 })));
});

app.post("/api/rewards", async (c) => {
  const { sub, hh } = c.get("user");
  const body = await c.req.json<{ name?: string; emoji?: string; effortCost?: number }>();
  const name = body.name?.trim();
  if (!name) throw new HTTPException(400, { message: "name required" });
  const emoji = body.emoji?.trim() || "🏆";
  const effortCost = body.effortCost ?? 100;
  if (effortCost < 1) throw new HTTPException(400, { message: "effortCost must be >= 1" });
  const id = newId();
  const now = Date.now();
  await c.env.DB.prepare(
    `INSERT INTO rewards (id, household_id, name, emoji, effort_cost, created_by, created_at, is_active)
     VALUES (?, ?, ?, ?, ?, ?, ?, 1)`,
  ).bind(id, hh, name, emoji, effortCost, sub, now).run();
  return c.json({ id, name, emoji, effortCost, createdBy: sub, createdAt: now, isActive: true });
});

app.patch("/api/rewards/:id", async (c) => {
  const { hh } = c.get("user");
  const rewardId = c.req.param("id");
  const body = await c.req.json<{ name?: string; emoji?: string; effortCost?: number; isActive?: boolean }>();
  const reward = await c.env.DB.prepare(
    `SELECT id FROM rewards WHERE id = ? AND household_id = ?`,
  ).bind(rewardId, hh).first();
  if (!reward) throw new HTTPException(404);
  const sets: string[] = [];
  const bindings: unknown[] = [];
  if (body.name !== undefined) {
    if (!body.name.trim()) throw new HTTPException(400, { message: "name cannot be blank" });
    sets.push("name = ?"); bindings.push(body.name.trim());
  }
  if (body.emoji !== undefined) { sets.push("emoji = ?"); bindings.push(body.emoji.trim() || "🏆"); }
  if (body.effortCost !== undefined) { sets.push("effort_cost = ?"); bindings.push(body.effortCost); }
  if (body.isActive !== undefined) { sets.push("is_active = ?"); bindings.push(body.isActive ? 1 : 0); }
  if (sets.length === 0) throw new HTTPException(400, { message: "nothing to update" });
  bindings.push(rewardId);
  await c.env.DB.prepare(`UPDATE rewards SET ${sets.join(", ")} WHERE id = ?`).bind(...bindings).run();
  return c.json({ ok: true });
});

app.delete("/api/rewards/:id", async (c) => {
  const { hh } = c.get("user");
  const rewardId = c.req.param("id");
  const res = await c.env.DB.prepare(
    `DELETE FROM rewards WHERE id = ? AND household_id = ?`,
  ).bind(rewardId, hh).run();
  if (!res.meta.changes) throw new HTTPException(404);
  return c.json({ ok: true });
});

// ---------- Reward settings (per-user point ratio) ----------

app.get("/api/me/reward-settings", async (c) => {
  const { sub } = c.get("user");
  const row = await c.env.DB.prepare(
    `SELECT point_ratio AS pointRatio FROM user_reward_settings WHERE user_id = ?`,
  ).bind(sub).first<{ pointRatio: number }>();
  return c.json({ pointRatio: row?.pointRatio ?? 1.0 });
});

app.patch("/api/me/reward-settings", async (c) => {
  const { sub } = c.get("user");
  const body = await c.req.json<{ pointRatio?: number }>();
  if (body.pointRatio === undefined) throw new HTTPException(400, { message: "pointRatio required" });
  if (body.pointRatio <= 0) throw new HTTPException(400, { message: "pointRatio must be > 0" });
  await c.env.DB.prepare(
    `INSERT INTO user_reward_settings (user_id, point_ratio) VALUES (?, ?)
     ON CONFLICT(user_id) DO UPDATE SET point_ratio = excluded.point_ratio`,
  ).bind(sub, body.pointRatio).run();
  return c.json({ pointRatio: body.pointRatio });
});

// ---------- All-time effort totals (for rewards progress) ----------

app.get("/api/household/effort-totals", async (c) => {
  const { hh } = c.get("user");
  const { results } = await c.env.DB.prepare(
    `SELECT u.id AS userId, u.display_name AS displayName,
            COALESCE(SUM(t.effort_points), 0) AS effortPoints
       FROM users u
       LEFT JOIN completions c ON c.user_id = u.id
       LEFT JOIN tasks t ON t.id = c.task_id
       LEFT JOIN areas a ON a.id = t.area_id AND a.household_id = ?
      WHERE u.household_id = ?
      GROUP BY u.id
      ORDER BY effortPoints DESC`,
  ).bind(hh, hh).all();
  return c.json(results);
});

// ---------- Error handler ----------

app.onError((err, c) => {
  if (err instanceof HTTPException) return err.getResponse();
  console.error(err);
  return c.json({ error: "internal error" }, 500);
});

export default app;
