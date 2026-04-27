JAVA_HOME  := C:/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot
ANDROID_HOME := C:/Users/burke/android-sdk
GRADLE     := C:/Users/burke/gradle-8.10.2/bin/gradle.bat

export JAVA_HOME
export ANDROID_HOME

# ── Backend ────────────────────────────────────────────────────────────────
.PHONY: backend-typecheck backend-test-local

backend-typecheck:
	cd backend && npm run typecheck

# Requires: `npm run dev:test` running in another terminal (wrangler dev --local --port 8788)
backend-test-local:
	cd backend && npm run test:local

# ── Android ────────────────────────────────────────────────────────────────
.PHONY: android-test

android-test:
	cd android && "$(GRADLE)" :app:testDebugUnitTest

# ── All local tests ────────────────────────────────────────────────────────
.PHONY: test

# Requires wrangler dev running; see backend/README or CLAUDE.md
test: backend-typecheck android-test backend-test-local
