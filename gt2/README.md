# Grindtrack v2

Self-hosted tracker for a 3-year engineering study plan. Spring Boot 3 / Java 21 (virtual
threads) + React 18 / TypeScript + Postgres 16, with real production-pattern auth:
BCrypt password + TOTP second factor + short-lived JWT access tokens + rotating opaque
refresh tokens, all in httpOnly SameSite=Strict cookies. Public landing page shows the
heatmap and streak; everything else requires login.

## Docs

| Doc | What's in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Components, preliquibase → Liquibase → JPA flow, package layout |
| [docs/auth.md](docs/auth.md) | The full auth design with PlantUML sequence diagrams and threat notes |
| [docs/api.md](docs/api.md) | Endpoint reference |
| [docs/deployment.md](docs/deployment.md) | VPS runbook: compose, nginx, TLS, backups, checklist |
| [docs/frontend.md](docs/frontend.md) | View state machine, the 401→refresh→retry wrapper |

PlantUML blocks render natively on GitHub/GitLab, in IntelliJ's PlantUML plugin, or at plantuml.com.

## Local dev

```bash
cp .env.example .env            # defaults are fine locally; COOKIE_SECURE=false
docker compose up -d db         # just Postgres
cd backend && mvn spring-boot:run    # API on :8080 (bootstrap user logs TOTP secret once)
cd frontend && npm install && npm run dev   # UI on :5173, /api proxied to :8080
```

Or the full container exactly as production runs it: `docker compose up -d --build` → :8080.

## Code style

Google Java Style, enforced by Spotless + google-java-format:

```bash
mvn spotless:apply   # format everything
mvn verify           # spotless:check runs here — CI-ready
```

## Deploying

See [docs/deployment.md](docs/deployment.md). Short version: clone on the VPS, fill `.env`,
`docker compose up -d --build`, point nginx at 127.0.0.1:8080, certbot the subdomain,
install the backup cron, enroll TOTP from the first-boot log.

## Extending (all good agentic-coding reps)

- Liquibase changeset 003: a `milestones` table seeded from the plan workbook
- Import endpoint for the v1 Node tracker's Export JSON
- Prometheus metrics via Spring Actuator → a Grafana panel next to your portfolio
- Kustomize overlays to run this on the kind/kubeadm cluster instead of compose
