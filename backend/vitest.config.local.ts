// Local test config: runs against `wrangler dev --local --port 8788`.
// No Workers pool needed — tests use plain Node.js fetch against the HTTP server.
// Usage:
//   Terminal 1: npm run dev:test
//   Terminal 2: npm run test:local
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    environment: "node",
    testTimeout: 15_000,
    include: ["test/**/*.test.ts"],
    env: {
      TEST_BASE_URL: "http://localhost:8788",
    },
  },
});
