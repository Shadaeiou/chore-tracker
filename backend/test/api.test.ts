import { SELF } from "cloudflare:test";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

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
  return SELF.fetch(`https://worker${path}`, { ...init, headers });
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
