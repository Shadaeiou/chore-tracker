import { afterEach, beforeEach, describe, expect, it } from "vitest";

// When TEST_BASE_URL is set (local dev against `wrangler dev --local`) we hit
// the real HTTP server; otherwise we use SELF from cloudflare:test (CI Workers pool).
const LOCAL_BASE = process.env.TEST_BASE_URL;

// Lazily-resolved SELF so the module can load in plain Node.js when LOCAL_BASE is set.
let _selfFetch: ((url: string, init?: RequestInit) => Promise<Response>) | undefined;
async function workerFetch(url: string, init?: RequestInit): Promise<Response> {
  if (LOCAL_BASE) return fetch(url.replace("https://worker", LOCAL_BASE), init);
  if (!_selfFetch) {
    const { SELF } = await import("cloudflare:test");
    _selfFetch = SELF.fetch.bind(SELF);
  }
  return _selfFetch(url, init);
}

// Helper: hit the worker's fetch handler.
async function api(
  path: string,
  init: RequestInit & { token?: string } = {},
): Promise<Response> {
  const headers: Record<string, string> = {
    "content-type": "application/json",
    ...((init.headers as Record<string, string>) ?? {}),
  };
  if (init.token) headers["authorization"] = `Bearer ${init.token}`;
  return workerFetch(`https://worker${path}`, { ...init, headers });
}

interface AuthResponse {
  token: string;
  userId: string;
  householdId: string;
}

let counter = 0;
function uniqueEmail(): string {
  counter += 1;
  return `user-${Date.now()}-${counter}-${Math.random().toString(36).slice(2)}@example.com`;
}

async function register(opts: {
  email?: string;
  password?: string;
  displayName?: string;
  householdName?: string;
  inviteCode?: string;
} = {}): Promise<AuthResponse> {
  const res = await api("/auth/register", {
    method: "POST",
    body: JSON.stringify({
      email: opts.email ?? uniqueEmail(),
      password: opts.password ?? "longenoughpass",
      displayName: opts.displayName ?? "Tester",
      householdName: opts.inviteCode ? undefined : (opts.householdName ?? "Home"),
      inviteCode: opts.inviteCode,
    }),
  });
  expect(res.status, await res.clone().text()).toBe(200);
  return (await res.json()) as AuthResponse;
}

describe("auth endpoints", () => {
  it("registers a user and creates their household", async () => {
    const auth = await register();
    expect(auth.token).toMatch(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/);
    expect(auth.userId).toBeTruthy();
    expect(auth.householdId).toBeTruthy();
  });

  it("rejects duplicate email", async () => {
    const email = uniqueEmail();
    await register({ email });
    const res = await api("/auth/register", {
      method: "POST",
      body: JSON.stringify({
        email,
        password: "longenoughpass",
        displayName: "x",
        householdName: "y",
      }),
    });
    expect(res.status).toBe(409);
  });

  it("rejects short passwords", async () => {
    const res = await api("/auth/register", {
      method: "POST",
      body: JSON.stringify({
        email: uniqueEmail(),
        password: "short",
        displayName: "x",
        householdName: "y",
      }),
    });
    expect(res.status).toBe(400);
  });

  it("logs in with correct credentials", async () => {
    const email = uniqueEmail();
    await register({ email, password: "longenoughpass" });
    const res = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password: "longenoughpass" }),
    });
    expect(res.status).toBe(200);
  });

  it("rejects bad credentials", async () => {
    const email = uniqueEmail();
    await register({ email, password: "longenoughpass" });
    const res = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password: "wrong-password-here" }),
    });
    expect(res.status).toBe(401);
  });
});

describe("authorization", () => {
  it("rejects /api/* without a token", async () => {
    const res = await api("/api/areas");
    expect(res.status).toBe(401);
  });

  it("rejects garbage tokens", async () => {
    const res = await api("/api/areas", { token: "totally-fake-token" });
    expect(res.status).toBe(401);
  });
});

describe("areas + tasks lifecycle", () => {
  it("create area → create task → complete task → list reflects state", async () => {
    const auth = await register();

    const areaRes = await api("/api/areas", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    });
    expect(areaRes.status).toBe(200);
    const area = (await areaRes.json()) as { id: string; name: string };
    expect(area.name).toBe("Kitchen");

    const taskRes = await api("/api/tasks", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Mop floor", frequencyDays: 7 }),
    });
    expect(taskRes.status).toBe(200);
    const task = (await taskRes.json()) as { id: string; lastDoneAt: number | null };
    expect(task.lastDoneAt).toBeNull();

    const completeRes = await api(`/api/tasks/${task.id}/complete`, {
      method: "POST",
      token: auth.token,
    });
    expect(completeRes.status).toBe(200);

    const listRes = await api("/api/tasks", { token: auth.token });
    const list = (await listRes.json()) as Array<{
      id: string;
      lastDoneAt: number | null;
      lastDoneBy: string | null;
    }>;
    const found = list.find((t) => t.id === task.id);
    expect(found?.lastDoneAt).toBeGreaterThan(0);
    expect(found?.lastDoneBy).toBe("Tester");
  });

  it("rejects completing a task in another household", async () => {
    const alice = await register({ displayName: "Alice" });
    const bob = await register({ displayName: "Bob" });

    const aliceArea = (await (await api("/api/areas", {
      method: "POST",
      token: alice.token,
      body: JSON.stringify({ name: "Bath" }),
    })).json()) as { id: string };

    const aliceTask = (await (await api("/api/tasks", {
      method: "POST",
      token: alice.token,
      body: JSON.stringify({
        areaId: aliceArea.id,
        name: "Scrub tub",
        frequencyDays: 14,
      }),
    })).json()) as { id: string };

    const res = await api(`/api/tasks/${aliceTask.id}/complete`, {
      method: "POST",
      token: bob.token,
    });
    expect(res.status).toBe(404);
  });

  it("households are isolated from each other in list endpoints", async () => {
    const alice = await register();
    const bob = await register();

    await api("/api/areas", {
      method: "POST",
      token: alice.token,
      body: JSON.stringify({ name: "Alice room" }),
    });

    const bobAreas = (await (await api("/api/areas", { token: bob.token })).json()) as Array<unknown>;
    expect(bobAreas).toHaveLength(0);
  });

  it("validates required fields when creating a task", async () => {
    const auth = await register();
    const res = await api("/api/tasks", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({ name: "no-area" }),
    });
    expect(res.status).toBe(400);
  });

  it("returns 404 for tasks in unknown areas", async () => {
    const auth = await register();
    const res = await api("/api/tasks", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({
        areaId: "00000000-0000-0000-0000-000000000000",
        name: "ghost task",
        frequencyDays: 1,
      }),
    });
    expect(res.status).toBe(404);
  });

  it("creates an on-demand task without requiring a frequency", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token, body: JSON.stringify({ name: "Nursery" }),
    })).json()) as { id: string };
    const created = await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Empty diaper trash", onDemand: true }),
    });
    expect(created.status).toBe(200);
    const tasks = (await (await api("/api/tasks", { token: auth.token })).json()) as Array<{
      name: string; onDemand: boolean; dueness: number;
    }>;
    const t = tasks.find((x) => x.name === "Empty diaper trash")!;
    expect(t.onDemand).toBe(true);
    // On-demand tasks never look "due" — they have no schedule.
    expect(t.dueness).toBe(0);
  });

  it("deleting an area cascades its tasks", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({ name: "Garage" }),
    })).json()) as { id: string };
    await api("/api/tasks", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Sweep", frequencyDays: 30 }),
    });
    const del = await api(`/api/areas/${area.id}`, {
      method: "DELETE",
      token: auth.token,
    });
    expect(del.status).toBe(200);
    const list = (await (await api("/api/tasks", { token: auth.token })).json()) as Array<unknown>;
    expect(list).toHaveLength(0);
  });
});

describe("multi-user household sync via invites", () => {
  it("invite flow: A invites, B joins, both see shared data and B's completions are attributed", async () => {
    const alice = await register({ displayName: "Alice" });

    // Alice creates an area + task.
    const area = (await (await api("/api/areas", {
      method: "POST",
      token: alice.token,
      body: JSON.stringify({ name: "Living room" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST",
      token: alice.token,
      body: JSON.stringify({
        areaId: area.id,
        name: "Vacuum",
        frequencyDays: 7,
      }),
    })).json()) as { id: string };

    // Alice creates an invite.
    const inviteRes = await api("/api/invites", {
      method: "POST",
      token: alice.token,
    });
    expect(inviteRes.status).toBe(200);
    const { code } = (await inviteRes.json()) as { code: string };

    // Bob registers using the invite code.
    const bob = await register({ displayName: "Bob", inviteCode: code });
    expect(bob.householdId).toBe(alice.householdId);

    // Bob immediately sees Alice's data.
    const bobAreas = (await (await api("/api/areas", { token: bob.token })).json()) as Array<{
      id: string;
    }>;
    expect(bobAreas.map((a) => a.id)).toContain(area.id);

    // Bob completes the task; Alice sees Bob's name attributed.
    const complete = await api(`/api/tasks/${task.id}/complete`, {
      method: "POST",
      token: bob.token,
    });
    expect(complete.status).toBe(200);

    const aliceTasks = (await (await api("/api/tasks", { token: alice.token })).json()) as Array<{
      id: string;
      lastDoneBy: string | null;
    }>;
    const t = aliceTasks.find((t) => t.id === task.id)!;
    expect(t.lastDoneBy).toBe("Bob");
  });

  it("rejects an invite code reused twice", async () => {
    const alice = await register();
    const { code } = (await (await api("/api/invites", {
      method: "POST",
      token: alice.token,
    })).json()) as { code: string };

    await register({ inviteCode: code }); // first use OK
    const second = await api("/auth/register", {
      method: "POST",
      body: JSON.stringify({
        email: uniqueEmail(),
        password: "longenoughpass",
        displayName: "Carol",
        inviteCode: code,
      }),
    });
    expect(second.status).toBe(400);
  });

  it("rejects an unknown invite code", async () => {
    const res = await api("/auth/register", {
      method: "POST",
      body: JSON.stringify({
        email: uniqueEmail(),
        password: "longenoughpass",
        displayName: "x",
        inviteCode: "definitely-not-real",
      }),
    });
    expect(res.status).toBe(400);
  });

  it("/api/household lists all members", async () => {
    const alice = await register({ displayName: "Alice" });
    const { code } = (await (await api("/api/invites", {
      method: "POST",
      token: alice.token,
    })).json()) as { code: string };
    await register({ displayName: "Bob", inviteCode: code });

    const hh = (await (await api("/api/household", {
      token: alice.token,
    })).json()) as {
      members: Array<{ displayName: string }>;
    };
    expect(hh.members.map((m) => m.displayName).sort()).toEqual(["Alice", "Bob"]);
  });
});

describe("undo task completion", () => {
  // Helper: create one user, one area, one task. Returns ids + token.
  async function seed(): Promise<{
    token: string;
    taskId: string;
  }> {
    const auth = await register({ displayName: "Tester" });
    const area = (await (await api("/api/areas", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST",
      token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Wipe counter", frequencyDays: 3 }),
    })).json()) as { id: string };
    return { token: auth.token, taskId: task.id };
  }

  async function listTask(token: string, taskId: string): Promise<{
    lastDoneAt: number | null;
    lastDoneBy: string | null;
  } | undefined> {
    const list = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string;
      lastDoneAt: number | null;
      lastDoneBy: string | null;
    }>;
    return list.find((t) => t.id === taskId);
  }

  it("undo reverts a single completion to never-done state", async () => {
    const { token, taskId } = await seed();

    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });
    expect((await listTask(token, taskId))?.lastDoneAt).toBeGreaterThan(0);

    const undo = await api(`/api/tasks/${taskId}/completions/last`, {
      method: "DELETE",
      token,
    });
    expect(undo.status).toBe(200);

    const after = await listTask(token, taskId);
    expect(after?.lastDoneAt).toBeNull();
    expect(after?.lastDoneBy).toBeNull();
  });

  it("undo with multiple completions rolls back to the previous one", async () => {
    const { token, taskId } = await seed();

    // Two completions in a row. Sleep 5ms so done_at differs.
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });
    await new Promise((r) => setTimeout(r, 5));
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });
    const beforeUndo = (await listTask(token, taskId))?.lastDoneAt!;

    const undo = await api(`/api/tasks/${taskId}/completions/last`, {
      method: "DELETE",
      token,
    });
    expect(undo.status).toBe(200);

    const after = await listTask(token, taskId);
    expect(after?.lastDoneAt).toBeGreaterThan(0);
    expect(after?.lastDoneAt).toBeLessThan(beforeUndo);
  });

  it("returns 404 when there is nothing to undo", async () => {
    const { token, taskId } = await seed();
    const undo = await api(`/api/tasks/${taskId}/completions/last`, {
      method: "DELETE",
      token,
    });
    expect(undo.status).toBe(404);
  });

  it("rejects undo from another household", async () => {
    const { token: aliceToken, taskId } = await seed();
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token: aliceToken });

    const bob = await register({ displayName: "Bob" });
    const undo = await api(`/api/tasks/${taskId}/completions/last`, {
      method: "DELETE",
      token: bob.token,
    });
    expect(undo.status).toBe(404);

    // Alice's completion is still there.
    expect((await listTask(aliceToken, taskId))?.lastDoneAt).toBeGreaterThan(0);
  });

  it("returns 404 for an unknown task id", async () => {
    const auth = await register();
    const undo = await api(
      `/api/tasks/00000000-0000-0000-0000-000000000000/completions/last`,
      { method: "DELETE", token: auth.token },
    );
    expect(undo.status).toBe(404);
  });
});

describe("PATCH /api/areas/:id", () => {
  async function seedArea(): Promise<{ token: string; areaId: string }> {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    return { token: auth.token, areaId: area.id };
  }

  it("updates area name", async () => {
    const { token, areaId } = await seedArea();
    const res = await api(`/api/areas/${areaId}`, {
      method: "PATCH", token,
      body: JSON.stringify({ name: "Bathroom" }),
    });
    expect(res.status).toBe(200);
    const areas = (await (await api("/api/areas", { token })).json()) as Array<{ id: string; name: string }>;
    expect(areas.find((a) => a.id === areaId)?.name).toBe("Bathroom");
  });

  it("rejects patching another household's area", async () => {
    const { areaId } = await seedArea();
    const bob = await register();
    const res = await api(`/api/areas/${areaId}`, {
      method: "PATCH", token: bob.token,
      body: JSON.stringify({ name: "Hijack" }),
    });
    expect(res.status).toBe(404);
  });

  it("returns 404 for unknown area", async () => {
    const auth = await register();
    const res = await api("/api/areas/00000000-0000-0000-0000-000000000000", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ name: "Ghost" }),
    });
    expect(res.status).toBe(404);
  });

  it("rejects empty patch body", async () => {
    const { token, areaId } = await seedArea();
    const res = await api(`/api/areas/${areaId}`, {
      method: "PATCH", token,
      body: JSON.stringify({}),
    });
    expect(res.status).toBe(400);
  });

  it("persists sortOrder and lists areas in that order", async () => {
    const auth = await register();
    const a = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const b = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Bathroom" }),
    })).json()) as { id: string };
    const c = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Living Room" }),
    })).json()) as { id: string };
    // Reorder: Bathroom, Living Room, Kitchen.
    // Send each PATCH the way the kotlinx Android client serializes it
    // (encodeDefaults=true → unset fields ride along as nulls). The endpoint
    // must treat null as "leave alone" or this batch will fail on the
    // NOT NULL name / sort_order columns.
    const responses = await Promise.all([
      api(`/api/areas/${b.id}`, { method: "PATCH", token: auth.token,
        body: JSON.stringify({ name: null, icon: null, sortOrder: 0 }) }),
      api(`/api/areas/${c.id}`, { method: "PATCH", token: auth.token,
        body: JSON.stringify({ name: null, icon: null, sortOrder: 1 }) }),
      api(`/api/areas/${a.id}`, { method: "PATCH", token: auth.token,
        body: JSON.stringify({ name: null, icon: null, sortOrder: 2 }) }),
    ]);
    expect(responses.map((r) => r.status)).toEqual([200, 200, 200]);
    const areas = (await (await api("/api/areas", { token: auth.token })).json()) as Array<{ id: string; name: string; sortOrder: number }>;
    expect(areas.map((x) => x.name)).toEqual(["Bathroom", "Living Room", "Kitchen"]);
    expect(areas.map((x) => x.sortOrder)).toEqual([0, 1, 2]);
  });
});

describe("DELETE /api/completions/:id", () => {
  async function seedAndComplete(): Promise<{ token: string; taskId: string }> {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Mop", frequencyDays: 7 }),
    })).json()) as { id: string };
    await api(`/api/tasks/${task.id}/complete`, { method: "POST", token: auth.token });
    return { token: auth.token, taskId: task.id };
  }

  it("deletes a specific completion and recomputes last_done_at", async () => {
    const { token, taskId } = await seedAndComplete();
    const activity = (await (await api("/api/activity", { token })).json()) as Array<{ id: string }>;
    const completionId = activity[0].id;

    const res = await api(`/api/completions/${completionId}`, { method: "DELETE", token });
    expect(res.status).toBe(200);

    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; lastDoneAt: number | null;
    }>;
    expect(tasks.find((t) => t.id === taskId)?.lastDoneAt).toBeNull();
  });

  it("recomputes last_done_at to the prior completion when one remains", async () => {
    const { token, taskId } = await seedAndComplete();
    await new Promise((r) => setTimeout(r, 5));
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });
    const activity = (await (await api("/api/activity", { token })).json()) as Array<{ id: string; doneAt: number }>;
    const newest = activity[0]; // DESC order

    await api(`/api/completions/${newest.id}`, { method: "DELETE", token });

    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; lastDoneAt: number | null;
    }>;
    // Should now reflect the older completion's timestamp
    expect(tasks.find((t) => t.id === taskId)?.lastDoneAt).toBe(activity[1].doneAt);
  });

  it("rejects cross-household completion delete", async () => {
    const { token } = await seedAndComplete();
    const activity = (await (await api("/api/activity", { token })).json()) as Array<{ id: string }>;
    const bob = await register();
    const res = await api(`/api/completions/${activity[0].id}`, { method: "DELETE", token: bob.token });
    expect(res.status).toBe(404);
  });

  it("returns 404 for unknown completion id", async () => {
    const auth = await register();
    const res = await api("/api/completions/00000000-0000-0000-0000-000000000000", {
      method: "DELETE", token: auth.token,
    });
    expect(res.status).toBe(404);
  });
});

describe("cross-household delete guards", () => {
  it("rejects deleting another household's area", async () => {
    const alice = await register();
    const bob = await register();

    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ name: "Alice's Kitchen" }),
    })).json()) as { id: string };

    const res = await api(`/api/areas/${area.id}`, { method: "DELETE", token: bob.token });
    expect(res.status).toBe(404);

    // Alice's area still exists.
    const aliceAreas = (await (await api("/api/areas", { token: alice.token })).json()) as Array<unknown>;
    expect(aliceAreas).toHaveLength(1);
  });

  it("rejects deleting another household's task", async () => {
    const alice = await register();
    const bob = await register();

    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ name: "Garage" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ areaId: area.id, name: "Sweep", frequencyDays: 7 }),
    })).json()) as { id: string };

    const res = await api(`/api/tasks/${task.id}`, { method: "DELETE", token: bob.token });
    expect(res.status).toBe(404);

    const aliceTasks = (await (await api("/api/tasks", { token: alice.token })).json()) as Array<unknown>;
    expect(aliceTasks).toHaveLength(1);
  });
});

describe("PATCH /api/tasks/:id", () => {
  async function seedTask(): Promise<{ token: string; taskId: string }> {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Room" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Clean", frequencyDays: 7 }),
    })).json()) as { id: string };
    return { token: auth.token, taskId: task.id };
  }

  it("updates name and frequencyDays", async () => {
    const { token, taskId } = await seedTask();
    const res = await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token,
      body: JSON.stringify({ name: "Deep clean", frequencyDays: 14 }),
    });
    expect(res.status).toBe(200);

    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; name: string; frequencyDays: number;
    }>;
    const t = tasks.find((t) => t.id === taskId)!;
    expect(t.name).toBe("Deep clean");
    expect(t.frequencyDays).toBe(14);
  });

  it("reassigns when only assignedTo is sent (kotlinx-style null fields)", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });
    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token, body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ areaId: area.id, name: "Trash", frequencyDays: 1, assignedTo: alice.userId }),
    })).json()) as { id: string };
    // Send the exact shape kotlinx with encodeDefaults=true emits.
    const res = await api(`/api/tasks/${task.id}`, {
      method: "PATCH", token: alice.token,
      body: JSON.stringify({
        name: null, frequencyDays: null, assignedTo: bob.userId,
        autoRotate: null, effortPoints: null, notes: null, areaId: null, onDemand: null,
      }),
    });
    expect(res.status).toBe(200);
    const tasks = (await (await api("/api/tasks", { token: alice.token })).json()) as Array<{
      id: string; name: string; assignedTo: string;
    }>;
    const t = tasks.find((x) => x.id === task.id)!;
    expect(t.assignedTo).toBe(bob.userId);
    expect(t.name).toBe("Trash");  // not nulled
  });

  it("updates autoRotate and effortPoints", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token,
      body: JSON.stringify({ autoRotate: true, effortPoints: 3 }),
    });
    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; autoRotate: boolean; effortPoints: number;
    }>;
    const t = tasks.find((t) => t.id === taskId)!;
    expect(t.autoRotate).toBe(true); // must be JSON boolean, not the raw SQLite integer
    expect(t.effortPoints).toBe(3);
  });

  it("rejects invalid frequencyDays", async () => {
    const { token, taskId } = await seedTask();
    const res = await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token,
      body: JSON.stringify({ frequencyDays: -1 }),
    });
    expect(res.status).toBe(400);
  });

  it("rejects empty patch body", async () => {
    const { token, taskId } = await seedTask();
    const res = await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token,
      body: JSON.stringify({}),
    });
    expect(res.status).toBe(400);
  });

  it("rejects patching another household's task", async () => {
    const { taskId } = await seedTask();
    const bob = await register();
    const res = await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token: bob.token,
      body: JSON.stringify({ name: "Hijack" }),
    });
    expect(res.status).toBe(404);
  });
});

describe("rotation", () => {
  it("auto-rotate advances assigned_to on completion", async () => {
    const alice = await register({ displayName: "Alice" });
    const { code } = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ displayName: "Bob", inviteCode: code });

    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({
        areaId: area.id, name: "Mop", frequencyDays: 7,
        assignedTo: alice.userId, autoRotate: true,
      }),
    })).json()) as { id: string; assignedTo: string };
    expect(task.assignedTo).toBe(alice.userId);

    // Alice completes → should rotate to Bob.
    await api(`/api/tasks/${task.id}/complete`, { method: "POST", token: alice.token });

    const tasks = (await (await api("/api/tasks", { token: alice.token })).json()) as Array<{
      id: string; assignedTo: string; autoRotate: boolean;
    }>;
    const updated = tasks.find((t) => t.id === task.id)!;
    expect(updated.assignedTo).toBe(bob.userId);
    expect(updated.autoRotate).toBe(true); // contract: must be JSON boolean not integer
  });

  it("non-rotating task keeps assignee after completion", async () => {
    const alice = await register({ displayName: "Alice" });
    const { code } = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    await register({ displayName: "Bob", inviteCode: code });

    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ name: "Bath" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({
        areaId: area.id, name: "Scrub", frequencyDays: 7,
        assignedTo: alice.userId, autoRotate: false,
      }),
    })).json()) as { id: string };

    await api(`/api/tasks/${task.id}/complete`, { method: "POST", token: alice.token });

    const tasks = (await (await api("/api/tasks", { token: alice.token })).json()) as Array<{
      id: string; assignedTo: string;
    }>;
    expect(tasks.find((t) => t.id === task.id)!.assignedTo).toBe(alice.userId);
  });
});

describe("GET /api/activity", () => {
  it("returns completions in DESC order, scoped to household", async () => {
    const alice = await register({ displayName: "Alice" });
    const bob = await register({ displayName: "Bob" });

    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ name: "Living room" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ areaId: area.id, name: "Vacuum", frequencyDays: 7 }),
    })).json()) as { id: string };

    await api(`/api/tasks/${task.id}/complete`, { method: "POST", token: alice.token });
    await new Promise((r) => setTimeout(r, 5));
    await api(`/api/tasks/${task.id}/complete`, { method: "POST", token: alice.token });

    const activity = (await (await api("/api/activity", { token: alice.token })).json()) as Array<{
      taskName: string; areaName: string; doneBy: string; doneAt: number;
    }>;
    expect(activity).toHaveLength(2);
    expect(activity[0].doneAt).toBeGreaterThan(activity[1].doneAt);
    expect(activity[0].taskName).toBe("Vacuum");
    expect(activity[0].areaName).toBe("Living room");
    expect(activity[0].doneBy).toBe("Alice");

    // Bob's household sees nothing.
    const bobActivity = (await (await api("/api/activity", { token: bob.token })).json()) as Array<unknown>;
    expect(bobActivity).toHaveLength(0);
  });

  it("paginates via before parameter", async () => {
    const alice = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ name: "Room" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ areaId: area.id, name: "Task", frequencyDays: 1 }),
    })).json()) as { id: string };

    await api(`/api/tasks/${task.id}/complete`, { method: "POST", token: alice.token });
    await new Promise((r) => setTimeout(r, 5));
    await api(`/api/tasks/${task.id}/complete`, { method: "POST", token: alice.token });

    const all = (await (await api("/api/activity", { token: alice.token })).json()) as Array<{ doneAt: number }>;
    expect(all).toHaveLength(2);

    const older = (await (await api(`/api/activity?before=${all[0].doneAt}`, {
      token: alice.token,
    })).json()) as Array<{ doneAt: number }>;
    expect(older).toHaveLength(1);
    expect(older[0].doneAt).toBe(all[1].doneAt);
  });
});

describe("GET /api/household/workload", () => {
  it("sums effort points per member for current month completions", async () => {
    const alice = await register({ displayName: "Alice" });
    const { code } = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ displayName: "Bob", inviteCode: code });

    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const bigTask = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ areaId: area.id, name: "Deep clean", frequencyDays: 30, effortPoints: 3 }),
    })).json()) as { id: string };
    const smallTask = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ areaId: area.id, name: "Quick wipe", frequencyDays: 7, effortPoints: 1 }),
    })).json()) as { id: string };

    // Alice does the big task twice (6 points), Bob does the small task once (1 point).
    await api(`/api/tasks/${bigTask.id}/complete`, { method: "POST", token: alice.token });
    await api(`/api/tasks/${bigTask.id}/complete`, { method: "POST", token: alice.token });
    await api(`/api/tasks/${smallTask.id}/complete`, { method: "POST", token: bob.token });

    const workload = (await (await api("/api/household/workload", { token: alice.token })).json()) as Array<{
      userId: string; displayName: string; effortPoints: number;
    }>;

    const aliceEntry = workload.find((w) => w.userId === alice.userId)!;
    const bobEntry = workload.find((w) => w.userId === bob.userId)!;
    expect(aliceEntry.effortPoints).toBe(6);
    expect(bobEntry.effortPoints).toBe(1);
  });

  it("is scoped to the caller's household", async () => {
    const alice = await register({ displayName: "Alice" });
    const bob = await register({ displayName: "Bob" });

    const workload = (await (await api("/api/household/workload", { token: alice.token })).json()) as Array<{
      userId: string;
    }>;
    expect(workload.every((w) => w.userId !== bob.userId)).toBe(true);
  });
});

describe("device tokens", () => {
  it("registers and deregisters a token", async () => {
    const auth = await register();
    const res = await api("/api/device-tokens", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ token: "fcm-token-abc", platform: "android" }),
    });
    expect(res.status).toBe(200);

    const del = await api("/api/device-tokens/fcm-token-abc", {
      method: "DELETE", token: auth.token,
    });
    expect(del.status).toBe(200);
  });

  it("upserts on duplicate token (same token, new user)", async () => {
    const alice = await register();
    const { code } = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: code });

    await api("/api/device-tokens", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ token: "shared-device-token", platform: "android" }),
    });
    // Bob registers same token (same physical device, re-logged-in)
    const res = await api("/api/device-tokens", {
      method: "POST", token: bob.token,
      body: JSON.stringify({ token: "shared-device-token", platform: "android" }),
    });
    expect(res.status).toBe(200);
  });

  it("rejects missing token field", async () => {
    const auth = await register();
    const res = await api("/api/device-tokens", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ platform: "android" }),
    });
    expect(res.status).toBe(400);
  });

  it("delete is a no-op for unknown token", async () => {
    const auth = await register();
    const res = await api("/api/device-tokens/nonexistent-token", {
      method: "DELETE", token: auth.token,
    });
    expect(res.status).toBe(200);
  });
});

describe("notes (persistent task notes + per-completion notes)", () => {
  async function seedTask(): Promise<{ token: string; areaId: string; taskId: string }> {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Mop", frequencyDays: 7 }),
    })).json()) as { id: string };
    return { token: auth.token, areaId: area.id, taskId: task.id };
  }

  it("persistent notes attached to a task survive completion", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token,
      body: JSON.stringify({ notes: "Use Method, not bleach" }),
    });
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });

    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; notes: string | null;
    }>;
    expect(tasks.find((t) => t.id === taskId)?.notes).toBe("Use Method, not bleach");
  });

  it("PATCH with notes='' clears the note", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token,
      body: JSON.stringify({ notes: "Some text" }),
    });
    await api(`/api/tasks/${taskId}`, {
      method: "PATCH", token,
      body: JSON.stringify({ notes: "" }),
    });
    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; notes: string | null;
    }>;
    expect(tasks.find((t) => t.id === taskId)?.notes).toBeNull();
  });

  it("per-completion notes are recorded and surface in activity", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}/complete`, {
      method: "POST", token,
      body: JSON.stringify({ notes: "got milk, eggs, bread" }),
    });

    const activity = (await (await api("/api/activity", { token })).json()) as Array<{
      taskId: string; notes: string | null;
    }>;
    expect(activity[0].taskId).toBe(taskId);
    expect(activity[0].notes).toBe("got milk, eggs, bread");
  });

  it("completions without notes have notes=null", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });
    const activity = (await (await api("/api/activity", { token })).json()) as Array<{
      notes: string | null;
    }>;
    expect(activity[0].notes).toBeNull();
  });

  it("per-completion notes don't bleed onto the task itself", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}/complete`, {
      method: "POST", token,
      body: JSON.stringify({ notes: "weekly run notes" }),
    });
    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; notes: string | null;
    }>;
    expect(tasks.find((t) => t.id === taskId)?.notes).toBeNull();
  });
});

describe("/api/me (self profile)", () => {
  it("returns the current user's profile", async () => {
    const auth = await register({ displayName: "Burke" });
    const res = await api("/api/me", { token: auth.token });
    expect(res.status).toBe(200);
    const me = (await res.json()) as {
      id: string; email: string; displayName: string; avatar: string | null; avatarVersion: number;
    };
    expect(me.id).toBe(auth.userId);
    expect(me.displayName).toBe("Burke");
    expect(me.avatar).toBeNull();
    expect(me.avatarVersion).toBe(0);
  });

  it("updates displayName", async () => {
    const auth = await register({ displayName: "Old Name" });
    const res = await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ displayName: "New Name" }),
    });
    expect(res.status).toBe(200);
    const me = (await res.json()) as { displayName: string };
    expect(me.displayName).toBe("New Name");
  });

  it("rejects blank displayName", async () => {
    const auth = await register();
    const res = await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ displayName: "   " }),
    });
    expect(res.status).toBe(400);
  });

  it("stores and clears avatar data URL and bumps avatarVersion", async () => {
    const auth = await register();
    // 1x1 transparent png as a data URL
    const png =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
    const set = await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ avatar: png }),
    });
    expect(set.status).toBe(200);
    const afterSet = (await set.json()) as { avatar: string; avatarVersion: number };
    expect(afterSet.avatar).toBe(png);
    expect(afterSet.avatarVersion).toBe(1);
    const cleared = await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ avatar: null }),
    });
    expect(cleared.status).toBe(200);
    const afterClear = (await cleared.json()) as { avatar: string | null; avatarVersion: number };
    expect(afterClear.avatar).toBeNull();
    expect(afterClear.avatarVersion).toBe(2);
  });

  it("does not bump avatarVersion when only renaming", async () => {
    const auth = await register();
    const res = await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ displayName: "Renamed" }),
    });
    const me = (await res.json()) as { avatarVersion: number };
    expect(me.avatarVersion).toBe(0);
  });

  it("rejects non-data-URL avatars", async () => {
    const auth = await register();
    const res = await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ avatar: "https://example.com/me.png" }),
    });
    expect(res.status).toBe(400);
  });

  it("rejects empty patch body", async () => {
    const auth = await register();
    const res = await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({}),
    });
    expect(res.status).toBe(400);
  });

  it("members listing returns avatarVersion (no inline avatar payload)", async () => {
    const auth = await register({ displayName: "Burke" });
    const png =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
    await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ avatar: png }),
    });
    const hh = (await (await api("/api/household", { token: auth.token })).json()) as {
      members: Array<{ id: string; avatarVersion: number; avatar?: string }>;
    };
    const me = hh.members.find((m) => m.id === auth.userId)!;
    expect(me.avatarVersion).toBe(1);
    expect(me.avatar).toBeUndefined();
  });

  it("GET /api/users/:id/avatar returns the avatar payload", async () => {
    const auth = await register();
    const png =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
    await api("/api/me", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ avatar: png }),
    });
    const res = await api(`/api/users/${auth.userId}/avatar`, { token: auth.token });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { avatar: string | null; avatarVersion: number };
    expect(body.avatar).toBe(png);
    expect(body.avatarVersion).toBe(1);
  });

  it("GET /api/users/:id/avatar 404s for users in other households", async () => {
    const a = await register();
    const b = await register();
    const res = await api(`/api/users/${b.userId}/avatar`, { token: a.token });
    expect(res.status).toBe(404);
  });
});

describe("/api/todos (à la carte reminders)", () => {
  it("creates and lists my own todos", async () => {
    const auth = await register();
    const create = await api("/api/todos", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ text: "Buy diapers" }),
    });
    expect(create.status).toBe(200);
    const list = (await (await api("/api/todos", { token: auth.token })).json()) as Array<{
      text: string; ownerId: string; isPublic: boolean; doneAt: number | null;
    }>;
    expect(list).toHaveLength(1);
    expect(list[0].text).toBe("Buy diapers");
    expect(list[0].ownerId).toBe(auth.userId);
    expect(list[0].isPublic).toBe(false);
    expect(list[0].doneAt).toBeNull();
  });

  it("rejects empty text", async () => {
    const auth = await register();
    const res = await api("/api/todos", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ text: "   " }),
    });
    expect(res.status).toBe(400);
  });

  it("marks done via PATCH", async () => {
    const auth = await register();
    const created = (await (await api("/api/todos", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ text: "Mop" }),
    })).json()) as { id: string };
    const now = Date.now();
    const patch = await api(`/api/todos/${created.id}`, {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ doneAt: now }),
    });
    expect(patch.status).toBe(200);
    const list = (await (await api("/api/todos", { token: auth.token })).json()) as Array<{ doneAt: number | null }>;
    expect(list[0].doneAt).toBe(now);
  });

  it("housemates see public todos but not private ones", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });

    await api("/api/todos", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ text: "Public reminder", isPublic: true }),
    });
    await api("/api/todos", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ text: "Private note" }),
    });

    const bobView = (await (await api("/api/todos", { token: bob.token })).json()) as Array<{ text: string }>;
    const texts = bobView.map((t) => t.text);
    expect(texts).toContain("Public reminder");
    expect(texts).not.toContain("Private note");
  });

  it("only the owner can patch or delete their todo", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });
    const aliceTodo = (await (await api("/api/todos", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ text: "Mine", isPublic: true }),
    })).json()) as { id: string };
    const bobPatch = await api(`/api/todos/${aliceTodo.id}`, {
      method: "PATCH", token: bob.token,
      body: JSON.stringify({ text: "Hijack" }),
    });
    expect(bobPatch.status).toBe(404);
    const bobDelete = await api(`/api/todos/${aliceTodo.id}`, {
      method: "DELETE", token: bob.token,
    });
    expect(bobDelete.status).toBe(404);
  });

  it("can create a todo on behalf of another household member", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });
    const created = await api("/api/todos", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ text: "Take out trash", isPublic: true, ownerId: bob.userId }),
    });
    expect(created.status).toBe(200);
    const todo = (await created.json()) as { ownerId: string };
    expect(todo.ownerId).toBe(bob.userId);
    // Bob sees it on his list (and would have had a push fire if FCM was on).
    const bobView = (await (await api("/api/todos", { token: bob.token })).json()) as Array<{ text: string }>;
    expect(bobView.map((t) => t.text)).toContain("Take out trash");
  });

  it("rejects ownerId pointing at someone outside the household", async () => {
    const alice = await register();
    const charlie = await register();
    const res = await api("/api/todos", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ text: "Ghost", ownerId: charlie.userId }),
    });
    expect(res.status).toBe(400);
  });
});

describe("DELETE /api/users/:id (remove from household)", () => {
  it("admin can kick another member", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });
    const res = await api(`/api/users/${bob.userId}`, {
      method: "DELETE", token: alice.token,
    });
    expect(res.status).toBe(200);
    const hh = (await (await api("/api/household", { token: alice.token })).json()) as {
      members: Array<{ id: string }>;
    };
    expect(hh.members.map((m) => m.id)).not.toContain(bob.userId);
  });

  it("non-admin cannot kick anyone", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });
    const res = await api(`/api/users/${alice.userId}`, {
      method: "DELETE", token: bob.token,
    });
    expect(res.status).toBe(403);
  });

  it("admin cannot remove themselves", async () => {
    const alice = await register();
    const res = await api(`/api/users/${alice.userId}`, {
      method: "DELETE", token: alice.token,
    });
    expect(res.status).toBe(400);
  });

  it("admin gets 404 trying to kick a user from another household", async () => {
    const alice = await register();
    const charlie = await register();
    const res = await api(`/api/users/${charlie.userId}`, {
      method: "DELETE", token: alice.token,
    });
    expect(res.status).toBe(404);
  });

  it("kick unassigns the kicked user's tasks but keeps the tasks", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });
    const area = (await (await api("/api/areas", {
      method: "POST", token: alice.token, body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: alice.token,
      body: JSON.stringify({ areaId: area.id, name: "Mop", frequencyDays: 7, assignedTo: bob.userId }),
    })).json()) as { id: string };
    await api(`/api/users/${bob.userId}`, { method: "DELETE", token: alice.token });
    const tasks = (await (await api("/api/tasks", { token: alice.token })).json()) as Array<{
      id: string; assignedTo: string | null;
    }>;
    expect(tasks.find((t) => t.id === task.id)?.assignedTo).toBeNull();
  });

  it("members listing includes role", async () => {
    const alice = await register();
    const hh = (await (await api("/api/household", { token: alice.token })).json()) as {
      members: Array<{ id: string; role: string }>;
    };
    expect(hh.members.find((m) => m.id === alice.userId)?.role).toBe("admin");
  });

  it("invite-joined member is a regular member, not admin", async () => {
    const alice = await register({ householdName: "Shared" });
    const invite = (await (await api("/api/invites", {
      method: "POST", token: alice.token,
    })).json()) as { code: string };
    const bob = await register({ inviteCode: invite.code });
    const me = (await (await api("/api/me", { token: bob.token })).json()) as { role: string };
    expect(me.role).toBe("member");
  });
});

describe("PATCH /api/household (rename)", () => {
  it("updates name", async () => {
    const auth = await register({ householdName: "Old name" });
    const res = await api("/api/household", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ name: "New name" }),
    });
    expect(res.status).toBe(200);
    const hh = (await (await api("/api/household", { token: auth.token })).json()) as {
      household: { name: string };
    };
    expect(hh.household.name).toBe("New name");
  });

  it("rejects blank name", async () => {
    const auth = await register();
    const res = await api("/api/household", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ name: "   " }),
    });
    expect(res.status).toBe(400);
  });
});

describe("POST /api/areas/:id/copy", () => {
  async function seedAreaWithTasks(): Promise<{ token: string; areaId: string }> {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Bathroom" }),
    })).json()) as { id: string };
    await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Scrub tub", frequencyDays: 14, effortPoints: 3 }),
    });
    await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Mop floor", frequencyDays: 7 }),
    });
    return { token: auth.token, areaId: area.id };
  }

  it("creates new area with copied tasks (no completions)", async () => {
    const { token, areaId } = await seedAreaWithTasks();
    const res = await api(`/api/areas/${areaId}/copy`, {
      method: "POST", token,
      body: JSON.stringify({ name: "Master Bathroom" }),
    });
    expect(res.status).toBe(200);
    const result = (await res.json()) as { id: string; copiedTasks: number };
    expect(result.copiedTasks).toBe(2);

    // Verify both areas exist
    const areas = (await (await api("/api/areas", { token })).json()) as Array<{ name: string }>;
    expect(areas.map((a) => a.name).sort()).toEqual(["Bathroom", "Master Bathroom"]);

    // Verify tasks were copied
    const allTasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      areaId: string; name: string; lastDoneAt: number | null;
    }>;
    const newAreaTasks = allTasks.filter((t) => t.areaId === result.id);
    expect(newAreaTasks).toHaveLength(2);
    expect(newAreaTasks.map((t) => t.name).sort()).toEqual(["Mop floor", "Scrub tub"]);
    // Copied tasks are seeded with lastDoneAt = now (so they start green, not red).
    // No completion records should be carried over though — the lastDoneAt is just
    // a stamp on the task row, not a real completion.
    expect(newAreaTasks.every((t) => t.lastDoneAt !== null)).toBe(true);
  });

  it("rejects copy of another household's area", async () => {
    const { areaId } = await seedAreaWithTasks();
    const bob = await register();
    const res = await api(`/api/areas/${areaId}/copy`, {
      method: "POST", token: bob.token,
      body: JSON.stringify({ name: "Hijack" }),
    });
    expect(res.status).toBe(404);
  });

  it("rejects empty name", async () => {
    const { token, areaId } = await seedAreaWithTasks();
    const res = await api(`/api/areas/${areaId}/copy`, {
      method: "POST", token,
      body: JSON.stringify({ name: "  " }),
    });
    expect(res.status).toBe(400);
  });
});

describe("PATCH /api/household (vacation/pause)", () => {
  it("sets and clears paused_until", async () => {
    const auth = await register();
    const future = Date.now() + 86_400_000;
    const set = await api("/api/household", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ pausedUntil: future }),
    });
    expect(set.status).toBe(200);

    const hh = (await (await api("/api/household", { token: auth.token })).json()) as {
      household: { pausedUntil: number | null };
    };
    expect(hh.household.pausedUntil).toBe(future);

    const clear = await api("/api/household", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ pausedUntil: null }),
    });
    expect(clear.status).toBe(200);

    const after = (await (await api("/api/household", { token: auth.token })).json()) as {
      household: { pausedUntil: number | null };
    };
    expect(after.household.pausedUntil).toBeNull();
  });

  it("dueness clamps to 0 when paused", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Mop", frequencyDays: 1 }),
    });

    // Before pause: dueness > 0 (never done, freq=1 day)
    const before = (await (await api("/api/tasks", { token: auth.token })).json()) as Array<{ dueness: number }>;
    expect(before[0].dueness).toBeGreaterThan(0);

    // Pause
    await api("/api/household", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({ pausedUntil: Date.now() + 86_400_000 }),
    });

    const after = (await (await api("/api/tasks", { token: auth.token })).json()) as Array<{ dueness: number }>;
    expect(after[0].dueness).toBe(0);
  });

  it("rejects empty body", async () => {
    const auth = await register();
    const res = await api("/api/household", {
      method: "PATCH", token: auth.token,
      body: JSON.stringify({}),
    });
    expect(res.status).toBe(400);
  });
});

describe("POST /api/tasks/:id/snooze", () => {
  async function seedTask(): Promise<{ token: string; taskId: string }> {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Room" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Clean", frequencyDays: 1 }),
    })).json()) as { id: string };
    return { token: auth.token, taskId: task.id };
  }

  it("snoozes a task; dueness becomes 0 until snooze expires", async () => {
    const { token, taskId } = await seedTask();
    const future = Date.now() + 3 * 86_400_000;
    const res = await api(`/api/tasks/${taskId}/snooze`, {
      method: "POST", token,
      body: JSON.stringify({ until: future }),
    });
    expect(res.status).toBe(200);

    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; snoozedUntil: number | null; dueness: number;
    }>;
    const t = tasks.find((t) => t.id === taskId)!;
    expect(t.snoozedUntil).toBe(future);
    expect(t.dueness).toBe(0);
  });

  it("rejects past or zero until", async () => {
    const { token, taskId } = await seedTask();
    const res = await api(`/api/tasks/${taskId}/snooze`, {
      method: "POST", token,
      body: JSON.stringify({ until: Date.now() - 1000 }),
    });
    expect(res.status).toBe(400);
  });

  it("rejects cross-household snooze", async () => {
    const { taskId } = await seedTask();
    const bob = await register();
    const res = await api(`/api/tasks/${taskId}/snooze`, {
      method: "POST", token: bob.token,
      body: JSON.stringify({ until: Date.now() + 86_400_000 }),
    });
    expect(res.status).toBe(404);
  });

  it("completion clears the snooze", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}/snooze`, {
      method: "POST", token,
      body: JSON.stringify({ until: Date.now() + 86_400_000 }),
    });
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });

    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; snoozedUntil: number | null;
    }>;
    expect(tasks.find((t) => t.id === taskId)?.snoozedUntil).toBeNull();
  });

  it("DELETE /api/tasks/:id/snooze unsnoozes a snoozed task", async () => {
    const { token, taskId } = await seedTask();
    await api(`/api/tasks/${taskId}/snooze`, {
      method: "POST", token,
      body: JSON.stringify({ until: Date.now() + 86_400_000 }),
    });
    const res = await api(`/api/tasks/${taskId}/snooze`, { method: "DELETE", token });
    expect(res.status).toBe(200);
    const tasks = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; snoozedUntil: number | null;
    }>;
    expect(tasks.find((t) => t.id === taskId)?.snoozedUntil).toBeNull();
  });

  it("snooze + complete preserves the original cadence (postpone_anchor)", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token, body: JSON.stringify({ name: "Curb" }),
    })).json()) as { id: string };
    // Seed a 7-day task last done a week ago, so its current due is roughly now.
    const weekAgo = Date.now() - 7 * 86_400_000;
    const task = (await (await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({
        areaId: area.id, name: "Trash", frequencyDays: 7, lastDoneAt: weekAgo,
      }),
    })).json()) as { id: string };
    // Snooze a day (holiday pickup).
    await api(`/api/tasks/${task.id}/snooze`, {
      method: "POST", token: auth.token,
      body: JSON.stringify({ until: Date.now() + 86_400_000 }),
    });
    // Server stamps postpone_anchor = weekAgo + 7d (original due).
    // Complete the next day, with the actual completion timestamp.
    const completionTime = Date.now() + 86_400_000 + 3_600_000;
    await api(`/api/tasks/${task.id}/complete`, {
      method: "POST", token: auth.token,
      body: JSON.stringify({ at: completionTime }),
    });
    const tasks = (await (await api("/api/tasks", { token: auth.token })).json()) as Array<{
      id: string; lastDoneAt: number; snoozedUntil: number | null; postponeAnchor: number | null;
    }>;
    const t = tasks.find((x) => x.id === task.id)!;
    // last_done_at anchored to original due, NOT actual completion → next due
    // = weekAgo + 14d (one week from original Thursday), not completionTime + 7d.
    const expectedAnchor = weekAgo + 7 * 86_400_000;
    expect(t.lastDoneAt).toBe(expectedAnchor);
    expect(t.snoozedUntil).toBeNull();
    expect(t.postponeAnchor).toBeNull();
  });

  it("DELETE /api/tasks/:id/snooze 404s for cross-household", async () => {
    const { taskId } = await seedTask();
    const bob = await register();
    const res = await api(`/api/tasks/${taskId}/snooze`, { method: "DELETE", token: bob.token });
    expect(res.status).toBe(404);
  });
});

describe("GET /api/task-templates", () => {
  it("returns all templates when no area filter", async () => {
    const auth = await register();
    const res = await api("/api/task-templates", { token: auth.token });
    expect(res.status).toBe(200);
    const list = (await res.json()) as Array<{ id: string; suggestedArea: string }>;
    expect(list.length).toBeGreaterThan(50);
    const areas = new Set(list.map((t) => t.suggestedArea));
    expect(areas).toContain("kitchen");
    expect(areas).toContain("bathroom");
  });

  it("filters by suggested_area", async () => {
    const auth = await register();
    const list = (await (await api("/api/task-templates?area=kitchen", { token: auth.token })).json()) as Array<{
      suggestedArea: string;
    }>;
    expect(list.length).toBeGreaterThan(0);
    expect(list.every((t) => t.suggestedArea === "kitchen")).toBe(true);
  });
});

describe("POST /api/tasks with templateId", () => {
  it("creates a task using template defaults", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };

    const res = await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, templateId: "tmpl-kitchen-1" }),
    });
    expect(res.status).toBe(200);
    const task = (await res.json()) as {
      name: string; frequencyDays: number; effortPoints: number;
    };
    expect(task.name).toBe("Wipe down counters");
    expect(task.frequencyDays).toBe(1);
    expect(task.effortPoints).toBe(1);
  });

  it("user-provided fields override template", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };

    const res = await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({
        areaId: area.id, templateId: "tmpl-kitchen-1",
        name: "My custom name", frequencyDays: 5,
      }),
    });
    expect(res.status).toBe(200);
    const task = (await res.json()) as { name: string; frequencyDays: number };
    expect(task.name).toBe("My custom name");
    expect(task.frequencyDays).toBe(5);
  });

  it("rejects unknown templateId", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };

    const res = await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, templateId: "tmpl-bogus" }),
    });
    expect(res.status).toBe(400);
  });

  it("accepts optional lastDoneAt to seed initial state", async () => {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };

    const seedTime = Date.now();
    const res = await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({
        areaId: area.id, templateId: "tmpl-kitchen-1",
        lastDoneAt: seedTime,
      }),
    });
    expect(res.status).toBe(200);
    const task = (await res.json()) as { lastDoneAt: number | null };
    expect(task.lastDoneAt).toBe(seedTime);
  });
});

describe("retroactive completion (POST /api/tasks/:id/complete with at)", () => {
  async function seedTask(): Promise<{ token: string; taskId: string; createdAt: number }> {
    const auth = await register();
    const area = (await (await api("/api/areas", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ name: "Kitchen" }),
    })).json()) as { id: string };
    const task = (await (await api("/api/tasks", {
      method: "POST", token: auth.token,
      body: JSON.stringify({ areaId: area.id, name: "Mop", frequencyDays: 7 }),
    })).json()) as { id: string; createdAt: number };
    return { token: auth.token, taskId: task.id, createdAt: task.createdAt };
  }

  it("accepts a past timestamp", async () => {
    const { token, taskId, createdAt } = await seedTask();
    // Wait briefly so we have wiggle room between createdAt and now.
    await new Promise((r) => setTimeout(r, 20));
    const at = createdAt + 5; // 5ms after creation, still in the past
    const res = await api(`/api/tasks/${taskId}/complete`, {
      method: "POST", token,
      body: JSON.stringify({ at }),
    });
    expect(res.status).toBe(200);
    const data = (await res.json()) as { doneAt: number };
    expect(data.doneAt).toBe(at);
  });

  it("rejects future timestamps", async () => {
    const { token, taskId } = await seedTask();
    const res = await api(`/api/tasks/${taskId}/complete`, {
      method: "POST", token,
      body: JSON.stringify({ at: Date.now() + 86_400_000 }),
    });
    expect(res.status).toBe(400);
  });

  it("accepts timestamps before task creation (manual backdating)", async () => {
    // Users may add a task to the tracker after they've already done the
    // chore — letting them backdate the first completion lets the timer
    // start from the real last-done date rather than the date they
    // happened to add the row.
    const { token, taskId } = await seedTask();
    const res = await api(`/api/tasks/${taskId}/complete`, {
      method: "POST", token,
      body: JSON.stringify({ at: Date.now() - 7 * 86_400_000 }),
    });
    expect(res.status).toBe(200);
  });

  it("doesn't overwrite a newer last_done_at with an older retroactive completion", async () => {
    const { token, taskId } = await seedTask();
    // Mark done now
    await api(`/api/tasks/${taskId}/complete`, { method: "POST", token });
    const afterFirst = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; lastDoneAt: number | null;
    }>;
    const firstDoneAt = afterFirst.find((t) => t.id === taskId)!.lastDoneAt!;

    // Mark done yesterday (retroactive)
    await api(`/api/tasks/${taskId}/complete`, {
      method: "POST", token,
      body: JSON.stringify({ at: Date.now() - 86_400_000 }),
    });

    const afterSecond = (await (await api("/api/tasks", { token })).json()) as Array<{
      id: string; lastDoneAt: number | null;
    }>;
    // Should still be the first (newer) timestamp
    expect(afterSecond.find((t) => t.id === taskId)?.lastDoneAt).toBe(firstDoneAt);
  });
});
