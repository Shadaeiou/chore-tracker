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
