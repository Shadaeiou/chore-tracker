import {
  defineWorkersConfig,
  readD1Migrations,
} from "@cloudflare/vitest-pool-workers/config";
import path from "node:path";

export default defineWorkersConfig(async () => {
  const migrations = await readD1Migrations(path.resolve("./migrations"));
  return {
    test: {
      setupFiles: ["./test/apply-migrations.ts"],
      poolOptions: {
        workers: {
          wrangler: { configPath: "./wrangler.test.toml" },
          miniflare: {
            bindings: {
              JWT_SECRET: "test-secret-not-for-prod",
              TEST_MIGRATIONS: migrations,
            },
          },
        },
      },
    },
  };
});
