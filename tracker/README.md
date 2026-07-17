# Grindtrack

A self-hosted daily/weekly tracker for the 3-year engineering career plan. Node + Express API,
Postgres for storage (persistent Docker volume), single-page UI. No accounts, no cloud — your
data stays on your machine.

## Run it

```bash
cd grindtrack
docker compose up -d --build
```

Open http://localhost:8080

Data lives in the `pgdata` Docker volume and survives restarts, rebuilds, and reboots.
`docker compose down` keeps your data; only `docker compose down -v` deletes it.

## Daily flow (2 minutes, end of each study block)

1. **Today tab** — hours, energy (1-5), category chips, planned focus vs. what actually
   happened, wins, blockers. Hit **Save day**.
   - Anything you type in **Wins** that's work-related is Impact Log material — copy the
     good ones into the workbook's Impact Log sheet every couple of weeks.

## Weekly flow (Friday review slot)

1. **Week tab** — the 7-day table and the 16h progress bar tell you how the week went.
2. Fill the weekly review: summary, wins, blockers, adjustments, next week's focus,
   on-track toggle. Hit **Save review**.
3. Update the workbook's Milestones sheet at the same time.

## What the header tells you

- **Streak** — consecutive days with hours > 0 (today doesn't break it until tomorrow).
- **This week** — hours against the 16h target.
- **Heatmap** — last 26 weeks, one cell per day. The goal is a wall of green.

## Backups / getting your data out

- **Export JSON** button (top right) downloads everything (`daily_logs` + `weekly_reviews`).
- Or straight from Postgres:
  ```bash
  docker compose exec db pg_dump -U grind grindtrack > backup.sql
  ```

## API (if you want to script it)

| Method | Path | Notes |
|---|---|---|
| GET/PUT/DELETE | `/api/days/:date` | upsert by `YYYY-MM-DD` |
| GET | `/api/days?from=&to=` | range query |
| GET/PUT | `/api/weeks/:weekStart` | any date in the week works; normalized to Monday |
| GET | `/api/stats` | streak, weekly hours (12 wks), category totals |
| GET | `/api/export` | full JSON dump |

## Ideas when you want to extend it (good agentic-coding practice)

- Seed the Milestones list from the workbook and track them here too.
- A `plan.json` with the 12 quarters so the header shows "Q1 · CKAD final prep".
- Deploy it to your kind/kubeadm cluster with Kustomize overlays — a perfect small
  exercise: Deployment + StatefulSet for Postgres + PVC + Service + NetworkPolicy.
