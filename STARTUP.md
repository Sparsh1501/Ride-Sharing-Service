# Startup guide — Dispatch Engine

This guide helps you **build and run** the Spring Boot app on your machine. For architecture, APIs, and demos, see [README.md](README.md).

The application is a **single JVM** (`DispatchApplication`) on **port 8080**. It needs **PostgreSQL**, **Redis**, and **Kafka** at startup (Flyway, JPA, and consumers). Those services are provided via Docker Compose in local dev.

---

## 1. Prerequisites

| Requirement | Notes |
|-------------|--------|
| **JDK 17** | Project toolchain is Java 17. JDK 21+ can run Gradle if the wrapper is current, but compile/run target is **17**. |
| **Docker Desktop** (or Docker Engine + Compose) | For Postgres, Redis, Kafka, Zookeeper. |
| **Git** | To clone the repo. |
| **Gradle** | Not required; use `./gradlew`. Wrapper is **Gradle 8.14.6**. |
| **IntelliJ IDEA** (optional) | Community or Ultimate; import as **Gradle** project. |
| **Node 18+** (optional) | Only for the `frontend/` simulator. |

Check Java:

```bash
java -version   # should report 17.x when running the app
```

---

## 2. Get the code

```bash
git clone <repository-url>
cd Ride-Sharing-Service   # folder that contains build.gradle
```

Open **this directory** in your IDE (the repo root), not only `src/`.

Optional env file (used by Compose):

```bash
cp .env.example .env
```

---

## 3. Build (no Docker required)

**macOS / Linux**

```bash
chmod +x gradlew    # once, if needed on macOS/Linux
./gradlew build
```

**Windows (PowerShell or Git Bash)**

```bat
gradlew.bat build
```

Unit tests run by default; integration tests need Docker:

```bash
./gradlew integrationTest          # macOS/Linux
gradlew.bat integrationTest        # Windows
```

---

## 4. Run backing services

Start infrastructure **before** the Spring Boot app.

**Helper script (recommended)**

```bash
./scripts/dev-infra.sh                  # Postgres on host 5432
./scripts/dev-infra.sh --alt-postgres   # Postgres on host 5433 (avoids local PG conflict)
```

**Or Compose directly**

```bash
docker compose up -d zookeeper kafka postgres redis
```

If local PostgreSQL already uses **5432**, use the dev overlay instead:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d zookeeper kafka postgres redis
export POSTGRES_PORT=5433   # Windows: set POSTGRES_PORT=5433
```

Wait until Postgres is healthy:

```bash
docker compose ps
```

Default connection settings (from `application.yml`):

| Service | Host | Port | Credentials |
|---------|------|------|-------------|
| PostgreSQL | `localhost` | `5432` | db/user/password: `dispatch` |
| Redis | `localhost` | `6379` | none |
| Kafka | `localhost` | `9092` | PLAINTEXT |

### Port 5432 conflict (common on macOS)

If startup fails with:

```text
FATAL: role "dispatch" does not exist
```

you are often hitting **local PostgreSQL**, not the Compose database.

1. Check what owns 5432: `lsof -i :5432` (macOS/Linux)
2. **Option A:** Stop local Postgres while developing (e.g. `brew services stop postgresql@18`)
3. **Option B (recommended):** `./scripts/dev-infra.sh --alt-postgres` then run with `POSTGRES_PORT=5433` (see `docker-compose.dev.yml`)

---

## 5. Run the application

### Option A — Command line (Gradle)

```bash
./gradlew bootRun                    # macOS/Linux
gradlew.bat bootRun                  # Windows

# If infra uses docker-compose.dev.yml:
POSTGRES_PORT=5433 ./gradlew bootRun
```

### Option B — IntelliJ IDEA

1. **Import:** Open repo root → Gradle sync (elephant icon: *Reload All Gradle Projects*).
2. **Settings → Build Tools → Gradle:**
   - **Gradle JVM:** 17
   - **Build and run using:** Gradle (recommended)
3. **Run configuration** (repo includes shared configs under `.run/`):
   - **`bootRun`** — Gradle task; most reliable.
   - **`DispatchApplication`** — Application main class `com.dispatch.DispatchApplication`.
4. For **Application** config:
   - **Module / classpath:** `dispatch-engine.main` (not plain `dispatch-engine`)
   - **Working directory:** repo root (where `build.gradle` lives)
   - **JRE:** 17

If the green **Run** gutter is missing on `main`, use **`bootRun`** or fix Gradle sync and `dispatch-engine.main` module.

### Option C — Full stack in Docker

Runs the app **inside** Compose (plus Prometheus/Grafana):

```bash
docker compose up --build
```

Use this when you do not want a local JVM on 8080. For **local debugging**, prefer Option A/B + infra from section 4.

---

## 6. Verify it works

Look for in logs:

```text
Started DispatchApplication
Tomcat started on port 8080
```

| URL | Expected |
|-----|----------|
| http://localhost:8080/actuator/health | `"status":"UP"`, components `db`, `redis` UP |
| http://localhost:8080/swagger-ui.html | Swagger UI |

Quick health check:

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## 7. Optional: frontend simulator

With the backend on 8080:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 (see [README.md](README.md) for ride simulation `curl` examples).

---

## 8. Troubleshooting

| Symptom | Likely cause | What to do |
|---------|----------------|------------|
| Gradle: `Unsupported class file major version` | Gradle JVM too new/old | Set **Gradle JVM** to 17; use wrapper **8.14.6** |
| `role "dispatch" does not exist` | Wrong Postgres on 5432 | Stop local Postgres or change `POSTGRES_PORT` |
| Connection refused on 6379 / 9092 | Infra not running | `docker compose up -d zookeeper kafka postgres redis` |
| IntelliJ: main class red / no gutter | Module not linked | Reload Gradle; module `dispatch-engine.main` |
| Port 8080 in use | Another process or Compose app | Stop other service or set `SERVER_PORT` |
| Kafka `LEADER_NOT_AVAILABLE` right after start | Kafka still starting | Wait a few seconds; usually self-resolves |
| `BUILD FAILED` after `bootRun` with exit 1 | App exited (often DB) | Scroll up in logs for `Application run failed` |

---

## 9. Stop services

```bash
# Stop infra
docker compose down

# If you started full stack including the app container
docker compose down

# Stop local run: Ctrl+C in terminal or Stop in IntelliJ
```

---

## 10. Checklist (PR / fresh clone smoke test)

Use this to confirm onboarding docs still work:

- [ ] `./gradlew build` passes on **JDK 17**
- [ ] `./scripts/dev-infra.sh` (or `--alt-postgres` if port 5432 is taken)
- [ ] `./gradlew bootRun` (set `POSTGRES_PORT=5433` when using `--alt-postgres`)
- [ ] `curl -s http://localhost:8080/actuator/health` → `"status":"UP"`
- [ ] Optional: IntelliJ **`bootRun`** from `.run/`

CI runs the same Gradle build on every push/PR via `.github/workflows/ci.yml`.

---

## Quick reference (local dev)

**Standard Postgres port**

```bash
cp .env.example .env
./scripts/dev-infra.sh
./gradlew bootRun
# → http://localhost:8080/actuator/health
```

**Local Postgres already on 5432**

```bash
./scripts/dev-infra.sh --alt-postgres
POSTGRES_PORT=5433 ./gradlew bootRun
```
