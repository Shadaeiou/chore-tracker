import { describe, expect, it } from "vitest";
import {
  hashPassword,
  newId,
  newInviteCode,
  signJwt,
  verifyJwt,
  verifyPassword,
} from "../src/auth";

describe("password hashing", () => {
  it("round-trips a correct password", async () => {
    const { hash, salt } = await hashPassword("hunter2hunter2");
    expect(await verifyPassword("hunter2hunter2", hash, salt)).toBe(true);
  });

  it("rejects a wrong password", async () => {
    const { hash, salt } = await hashPassword("hunter2hunter2");
    expect(await verifyPassword("wrongpw1234", hash, salt)).toBe(false);
  });

  it("produces distinct salts for the same password", async () => {
    const a = await hashPassword("samepassword");
    const b = await hashPassword("samepassword");
    expect(a.salt).not.toBe(b.salt);
    expect(a.hash).not.toBe(b.hash);
  });
});

describe("JWT", () => {
  const secret = "a-test-secret";

  it("signs and verifies a token", async () => {
    const token = await signJwt({ sub: "u1", hh: "h1" }, secret);
    const payload = await verifyJwt(token, secret);
    expect(payload?.sub).toBe("u1");
    expect(payload?.hh).toBe("h1");
    expect(payload?.exp).toBeGreaterThan(Math.floor(Date.now() / 1000));
  });

  it("rejects a token signed with a different secret", async () => {
    const token = await signJwt({ sub: "u1", hh: "h1" }, secret);
    expect(await verifyJwt(token, "other-secret")).toBeNull();
  });

  it("rejects a tampered payload", async () => {
    const token = await signJwt({ sub: "u1", hh: "h1" }, secret);
    const [h, _b, s] = token.split(".");
    const evil = btoa(JSON.stringify({ sub: "admin", hh: "x", exp: 9e9, iat: 0 }))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
    expect(await verifyJwt(`${h}.${evil}.${s}`, secret)).toBeNull();
  });

  it("rejects an expired token", async () => {
    const token = await signJwt({ sub: "u1", hh: "h1" }, secret, -1);
    expect(await verifyJwt(token, secret)).toBeNull();
  });

  it("rejects malformed tokens", async () => {
    expect(await verifyJwt("not-a-jwt", secret)).toBeNull();
    expect(await verifyJwt("", secret)).toBeNull();
    expect(await verifyJwt("a.b", secret)).toBeNull();
  });
});

describe("id helpers", () => {
  it("newId returns a uuid", () => {
    expect(newId()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    );
  });

  it("newInviteCode is short and url-safe", () => {
    const code = newInviteCode();
    expect(code).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(code.length).toBeGreaterThanOrEqual(15);
    expect(code.length).toBeLessThanOrEqual(17);
  });
});
