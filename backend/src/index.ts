import { Hono } from "hono";
import { cors } from "hono/cors";
import { HTTPException } from "hono/http-exception";
import {
  hashPassword,
  newId,
  signJwt,
  verifyJwt,
  verifyPassword,
  type JwtPayload,
} from "./auth";

type Bindings = {
  DB: D1Database;
  JWT_SECRET: string;
};

type Variables = {
  user: JwtPayload;
};

const app = new Hono<{ Bindings: Bindings; Variables: Variables }>();

app.use("*", cors());

app.get("/", (c) => c.json({ ok: true, service: "chore-tracker-api" }));

// ---------- Auth ----------

app.post("/auth/register", async (c) => {
  const body = await c.req.json<{
    email: string;
    password: string;
    displayName: string;
    householdName: string;
  }>();
  if (!body.email || !body.password || !body.displayName || !body.householdName)
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
  const householdId = newId();
  const userId = newId();
  const { hash, salt } = await hashPassword(body.password);

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
  return c.json({ id, name: body.name, icon: body.icon, sortOrder: body.sortOrder ?? 0, createdAt: now });
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
  // Returns tasks across the whole household with derived dirtiness ratio.
  // dirtiness = (now - last_done_at) / (frequency_days * 86_400_000)
  // null last_done_at => treat as fully due (1.0)
  const { results } = await c.env.DB.prepare(
    `SELECT t.id, t.area_id AS areaId, t.name, t.frequency_days AS frequencyDays,
            t.last_done_at AS lastDoneAt, t.created_at AS createdAt
       FROM tasks t
       JOIN areas a ON a.id = t.area_id
      WHERE a.household_id = ?
      ORDER BY t.created_at`,
  )
    .bind(hh)
    .all();
  return c.json(results);
});

app.post("/api/tasks", async (c) => {
  const { hh } = c.get("user");
  const body = await c.req.json<{
    areaId: string;
    name: string;
    frequencyDays: number;
  }>();
  if (!body.areaId || !body.name || !body.frequencyDays)
    throw new HTTPException(400, { message: "missing fields" });

  const area = await c.env.DB.prepare(
    "SELECT id FROM areas WHERE id = ? AND household_id = ?",
  )
    .bind(body.areaId, hh)
    .first();
  if (!area) throw new HTTPException(404, { message: "area not found" });

  const id = newId();
  const now = Date.now();
  await c.env.DB.prepare(
    `INSERT INTO tasks (id, area_id, name, frequency_days, last_done_at, created_at)
     VALUES (?, ?, ?, ?, NULL, ?)`,
  )
    .bind(id, body.areaId, body.name, body.frequencyDays, now)
    .run();
  return c.json({
    id,
    areaId: body.areaId,
    name: body.name,
    frequencyDays: body.frequencyDays,
    lastDoneAt: null,
    createdAt: now,
  });
});

app.post("/api/tasks/:id/complete", async (c) => {
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

  const now = Date.now();
  const completionId = newId();
  await c.env.DB.batch([
    c.env.DB.prepare(
      "INSERT INTO completions (id, task_id, user_id, done_at) VALUES (?, ?, ?, ?)",
    ).bind(completionId, taskId, sub, now),
    c.env.DB.prepare("UPDATE tasks SET last_done_at = ? WHERE id = ?").bind(
      now,
      taskId,
    ),
  ]);
  return c.json({ ok: true, doneAt: now });
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
