# Grindtrack v2

Self-hosted tracker for a 5-year engineering study plan. Spring Boot 3 / Java 21 (virtual
threads) + React 18 / TypeScript + Postgres 16, with real production-pattern auth:
BCrypt password + TOTP second factor + short-lived JWT access tokens + rotating opaque
refresh tokens, all in httpOnly SameSite=Strict cookies. Public landing page shows the
heatmap and streak; everything else requires login.

Nine tabs over seven backend features:

| Tab | What it tracks |
|---|---|
| **Today** / **Week** / **Stats** | The daily study log, the weekly review, and the rollups — hours, streak, categories, a 26-week heatmap, all per scope (study / work / combined) |
| **Focus** | A pomodoro timer whose minutes post straight into the day's hours. Four kinds: `study` (the 6–8am block), `work` (the day job), and the two lunch kinds `reading` and `review`, which record what the session went into and what stuck |
| **Todos** | Short-lived actionable items, tagged work or personal — separate from the fixed roadmap |
| **Plan** | The 5-year / 20-quarter roadmap: milestones, certs, modules, books, papers, projects. Content is imported at runtime from a local `plan.json`, never committed |
| **Work** | Day-job hours against 40/week, a journal, and a deliberate competency checklist |
| **Money** | Accounts and net worth, statement import for six bank formats + OFX/QFX, automatic categorization by rule, a review inbox, spending rollups, recurring-charge detection, and budgets |
| **Us** | Moments, ideas, occasions and a reading list. Built to reassure, not to score — there is deliberately no streak, target or rating anywhere in it |

## Docs

| Doc | What's in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | System view, cluster topology diagram, request lifecycle, preliquibase → Liquibase → JPA flow, package layout |
| [docs/architecture-conventions.md](docs/architecture-conventions.md) | How to *add* to the codebase without it drifting — layer rules, where validation goes, and the failure behind each |
| [docs/backend.md](docs/backend.md) | Layers, endpoint tables, auth internals, data model (ER), migrations, config, build, request trace |
| [docs/frontend.md](docs/frontend.md) | Structure, view/tab state machine, the single-flight 401→refresh→retry wrapper, each screen |
| [docs/auth.md](docs/auth.md) | The full auth design with Mermaid sequence diagrams and threat notes |
| [docs/api.md](docs/api.md) | Endpoint reference |
| [docs/deployment.md](docs/deployment.md) | Kubernetes runbook: GHCR image, namespace, ingress + cert-manager TLS, CI/CD, day-2 ops, backups, checklist |

**Diagrams:** sequence/state diagrams are **Mermaid** (GitHub renders them inline). Structural
diagrams (topology, ER) are **PlantUML** — source in [`docs/diagrams/`](docs/diagrams), rendered to
committed SVGs (GitHub renders the SVG, not PlantUML source). Regenerate with
`docs/diagrams/render.sh` after editing a `.puml`; it uses a local `plantuml.jar` (downloaded and
cached on first run, no Graphviz needed) or Docker, and deletes SVGs whose source is gone.

**Keeping this current:** `/docs-sweep` re-reads the code and refreshes every doc and diagram
here. See [`.claude/skills/docs-sweep/`](../.claude/skills/docs-sweep/SKILL.md).

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

See [docs/deployment.md](docs/deployment.md). Short version: every push to `main` builds the image,
pushes it to GHCR as `:latest` and `:<short-sha>`, and rolls that SHA out with
`kubectl -n grindtrack set image deploy/grindtrack`. The app runs on a two-node kubeadm cluster on
Hetzner Cloud behind ingress-nginx, with TLS issued and renewed by cert-manager.

The cluster itself — Terraform, Ansible, ingress-nginx, cert-manager and grindtrack's own
manifests — lives in the separate **[`k8s-cluster-hetzner`](https://github.com/caseythecoder90/k8s-cluster-hetzner)**
repo, under `kubernetes/apps/grindtrack/`. Nothing in this repo describes the cluster; this repo
only has to keep producing an image that listens on `:8080`, is configured entirely by env vars,
and answers `/api/public/stats` for the probes.

## Extending (all good agentic-coding reps)

- **A `pg_dump` CronJob to object storage** — the nightly backup died with the old VPS and has no
  replacement. Highest-value item on this list by a distance
- Import endpoint for the v1 Node tracker's Export JSON
- Prometheus metrics via Spring Actuator → a Grafana panel next to your portfolio
- Normalize `daily_logs.categories` out of its comma-separated column
