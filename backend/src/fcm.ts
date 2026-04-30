interface ServiceAccount {
  project_id: string;
  private_key: string;
  client_email: string;
}

interface Env {
  FCM_SERVICE_ACCOUNT: string;
  DB: D1Database;
}

// Module-level OAuth2 token memo — valid within a single Worker isolate lifetime.
let _accessToken: string | undefined;
let _tokenExpiry = 0;

function pemToDer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

function b64url(buf: ArrayBuffer | ArrayBufferLike): string {
  return btoa(String.fromCharCode(...new Uint8Array(buf)))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (_accessToken && now < _tokenExpiry - 60) return _accessToken;

  const enc = new TextEncoder();
  const header = b64url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })).buffer as ArrayBuffer);
  const claims = b64url(
    enc.encode(
      JSON.stringify({
        iss: sa.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      }),
    ).buffer as ArrayBuffer,
  );

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = b64url(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(`${header}.${claims}`)),
  );
  const jwt = `${header}.${claims}.${sig}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const data = await res.json<{ access_token: string; expires_in: number }>();
  _accessToken = data.access_token;
  _tokenExpiry = now + data.expires_in;
  return _accessToken;
}

export async function sendToTokens(
  tokens: string[],
  payload: { title: string; body: string },
  env: Env,
): Promise<void> {
  await sendFcm(tokens, env, (token) => ({
    token,
    notification: payload,
    android: { notification: { channel_id: "chore_updates" } },
  }));
}

/**
 * Silent data-only push that wakes the client to refresh its state. No
 * system notification is shown; the app reacts to `data.action === "refresh"`
 * and re-pulls /api/tasks etc. Used to fan out edits (rename, add task,
 * delete task, snooze, …) so other devices in the household stay in sync
 * without the client polling.
 */
export async function sendRefreshToTokens(
  tokens: string[],
  env: Env,
): Promise<void> {
  await sendFcm(tokens, env, (token) => ({
    token,
    data: { action: "refresh" },
    android: { priority: "HIGH" },
  }));
}

/**
 * Comment push that the Android client builds itself so it can attach
 * inline-reply + quick-react action buttons. Sent as data-only — the app's
 * PushService.onMessageReceived handler reads `data.type === "comment"` and
 * constructs a NotificationCompat with a RemoteInput action and a 👍 react
 * action. iOS / web clients (none today) would need their own handler.
 */
export async function sendCommentToTokens(
  tokens: string[],
  payload: {
    completionId: string;
    taskName: string;
    actorName: string;
    text: string;
  },
  env: Env,
): Promise<void> {
  await sendFcm(tokens, env, (token) => ({
    token,
    data: {
      type: "comment",
      completionId: payload.completionId,
      taskName: payload.taskName,
      actorName: payload.actorName,
      text: payload.text,
    },
    android: { priority: "HIGH" },
  }));
}

async function sendFcm(
  tokens: string[],
  env: Env,
  buildMessage: (token: string) => Record<string, unknown>,
): Promise<void> {
  if (tokens.length === 0 || !env.FCM_SERVICE_ACCOUNT) return;

  const sa = JSON.parse(env.FCM_SERVICE_ACCOUNT) as ServiceAccount;
  const accessToken = await getAccessToken(sa);
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

  await Promise.allSettled(
    tokens.map(async (token) => {
      const res = await fetch(url, {
        method: "POST",
        headers: {
          authorization: `Bearer ${accessToken}`,
          "content-type": "application/json",
        },
        body: JSON.stringify({ message: buildMessage(token) }),
      });
      if (!res.ok) {
        const err = await res.json<{
          error?: { details?: Array<{ errorCode?: string }> };
        }>();
        const code = err.error?.details?.[0]?.errorCode;
        if (code === "UNREGISTERED" || code === "INVALID_ARGUMENT") {
          await env.DB.prepare("DELETE FROM device_tokens WHERE token = ?")
            .bind(token)
            .run();
        }
      }
    }),
  );
}
