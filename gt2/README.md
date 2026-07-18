# Grindtrack v2

Self-hosted tracker for a 4-year engineering study plan. Spring Boot 3 / Java 21 (virtual
threads) + React 18 / TypeScript + Postgres 16, with real production-pattern auth:
BCrypt password + TOTP second factor + short-lived JWT access tokens + rotating opaque
refresh tokens, all in httpOnly SameSite=Strict cookies. Public landing page shows the
heatmap and streak; everything else requires login.

## Docs

| Doc | What's in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | System view, VPS topology diagram, request lifecycle, preliquibase → Liquibase → JPA flow, package layout |
| [docs/backend.md](docs/backend.md) | Layers, endpoint tables, auth internals, data model (ER), config, build, request trace |
| [docs/frontend.md](docs/frontend.md) | Structure, view/tab state machine, the single-flight 401→refresh→retry wrapper, each screen |
| [docs/auth.md](docs/auth.md) | The full auth design with Mermaid sequence diagrams and threat notes |
| [docs/api.md](docs/api.md) | Endpoint reference |
| [docs/deployment.md](docs/deployment.md) | VPS runbook: GHCR image, shared nginx container, TLS + renewal, CI/CD, backups, checklist |

**Diagrams:** sequence/state diagrams are **Mermaid** (GitHub renders them inline). Structural
diagrams (topology, ER) are **PlantUML** — source in [`docs/diagrams/`](docs/diagrams), rendered to
committed SVGs (GitHub renders the SVG, not PlantUML source). Regenerate with
`docs/diagrams/render.sh` after editing a `.puml`.

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

See [docs/deployment.md](docs/deployment.md). Short version: CI builds the image and pushes it to
GHCR; the VPS pulls it. Clone to `/opt/grindtrack`, fill `.env`, `docker compose -f
docker-compose.prod.yml up -d`, route `track.<domain>` through the existing nginx **container**
(a `conf.d` block proxying to `grindtrack-app:8080`), issue the cert into the shared certbot
volume, enroll TOTP from the first-boot log, install the backup cron. After the three VPS secrets
are set, every push to `main` auto-deploys.

## Extending (all good agentic-coding reps)

- Liquibase changeset 004: a `milestones` table seeded from the plan workbook
- Import endpoint for the v1 Node tracker's Export JSON
- Prometheus metrics via Spring Actuator → a Grafana panel next to your portfolio
- Kustomize overlays to run this on the kind/kubeadm cluster instead of compose
