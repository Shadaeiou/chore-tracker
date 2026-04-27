#!/usr/bin/env node
// Tests the FCM JWT auth flow locally using the service account JSON file.
// Usage: node scripts/test-fcm-auth.mjs path/to/service-account.json [fcm-device-token]
import { readFileSync } from "fs";
import { createSign } from "crypto";

const [,, saPath, deviceToken] = process.argv;
if (!saPath) { console.error("Usage: node scripts/test-fcm-auth.mjs <service-account.json> [device-token]"); process.exit(1); }

const sa = JSON.parse(readFileSync(saPath, "utf8"));
console.log("Service account:", sa.client_email);
console.log("Project:", sa.project_id);

// Build JWT using Node crypto (same logic as fcm.ts but with Node's API for comparison)
function b64url(buf) {
  return Buffer.from(buf).toString("base64url");
}

const now = Math.floor(Date.now() / 1000);
const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
const payload = b64url(JSON.stringify({
  iss: sa.client_email,
  scope: "https://www.googleapis.com/auth/firebase.messaging",
  aud: "https://oauth2.googleapis.com/token",
  iat: now,
  exp: now + 3600,
}));

const sign = createSign("RSA-SHA256");
sign.update(`${header}.${payload}`);
const sig = sign.sign(sa.private_key, "base64url");
const jwt = `${header}.${payload}.${sig}`;

console.log("\n--- JWT header ---");
console.log(JSON.stringify(JSON.parse(Buffer.from(header, "base64url").toString())));
console.log("\n--- JWT payload ---");
console.log(JSON.stringify(JSON.parse(Buffer.from(payload, "base64url").toString()), null, 2));
console.log("\nJWT prefix:", jwt.slice(0, 60) + "...");

// Exchange JWT for access token
console.log("\n--- Token exchange ---");
const body = `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`;
const tr = await fetch("https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "content-type": "application/x-www-form-urlencoded" },
  body,
});
const td = await tr.json();
if (!td.access_token) {
  console.error("Token exchange FAILED:", td);
  process.exit(1);
}
console.log("Access token obtained:", td.access_token.slice(0, 20) + "...");
console.log("Expires in:", td.expires_in, "seconds");

if (!deviceToken) { console.log("\nNo device token provided, skipping FCM send."); process.exit(0); }

// Send test FCM notification
console.log("\n--- FCM send ---");
const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
const fr = await fetch(url, {
  method: "POST",
  headers: { authorization: `Bearer ${td.access_token}`, "content-type": "application/json" },
  body: JSON.stringify({
    message: {
      token: deviceToken,
      notification: { title: "FCM local test", body: "It works!" },
      android: { notification: { channel_id: "chore_updates" } },
    },
  }),
});
const fd = await fr.json();
console.log("FCM response:", fr.status, JSON.stringify(fd));
