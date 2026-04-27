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
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth2:grant-type:jwt-bearer",
      assertion: jwt,
    }),
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
        body: JSON.stringify({
          message: {
            token,
            notification: payload,
            android: { notification: { channel_id: "chore_updates" } },
          },
        }),
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
