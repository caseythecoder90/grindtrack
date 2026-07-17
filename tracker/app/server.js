import express from "express";
import pg from "pg";

const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL });
const app = express();
app.use(express.json());
app.use(express.static("public"));

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const ok = (res, data) => res.json(data);
const bad = (res, msg) => res.status(400).json({ error: msg });

function validDate(d) {
  return typeof d === "string" && DATE_RE.test(d) && !Number.isNaN(Date.parse(d));
}

// Monday of the week containing an ISO date string
function mondayOf(dateStr) {
  const d = new Date(dateStr + "T00:00:00Z");
  const dow = (d.getUTCDay() + 6) % 7; // Mon=0
  d.setUTCDate(d.getUTCDate() - dow);
  return d.toISOString().slice(0, 10);
}

app.get("/api/health", async (_req, res) => {
  try {
    await pool.query("SELECT 1");
    ok(res, { status: "up" });
  } catch (e) {
    res.status(500).json({ status: "down", error: e.message });
  }
});

// ---------- daily logs ----------
app.get("/api/days", async (req, res) => {
  const { from, to } = req.query;
  if (!validDate(from) || !validDate(to)) return bad(res, "from and to (YYYY-MM-DD) are required");
  const { rows } = await pool.query(
    `SELECT log_date::text AS log_date, hours::float AS hours, categories, focus, did, wins, blockers, energy
       FROM daily_logs WHERE log_date BETWEEN $1 AND $2 ORDER BY log_date`,
    [from, to]
  );
  ok(res, rows);
});

app.get("/api/days/:date", async (req, res) => {
  if (!validDate(req.params.date)) return bad(res, "invalid date");
  const { rows } = await pool.query(
    `SELECT log_date::text AS log_date, hours::float AS hours, categories, focus, did, wins, blockers, energy
       FROM daily_logs WHERE log_date = $1`,
    [req.params.date]
  );
  ok(res, rows[0] || null);
});

app.put("/api/days/:date", async (req, res) => {
  const date = req.params.date;
  if (!validDate(date)) return bad(res, "invalid date");
  const { hours = 0, categories = [], focus = "", did = "", wins = "", blockers = "", energy = null } = req.body || {};
  const h = Number(hours);
  if (Number.isNaN(h) || h < 0 || h > 24) return bad(res, "hours must be 0-24");
  if (!Array.isArray(categories)) return bad(res, "categories must be an array");
  const en = energy === null || energy === "" ? null : Number(energy);
  if (en !== null && (Number.isNaN(en) || en < 1 || en > 5)) return bad(res, "energy must be 1-5");
  const { rows } = await pool.query(
    `INSERT INTO daily_logs (log_date, hours, categories, focus, did, wins, blockers, energy, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8, now())
     ON CONFLICT (log_date) DO UPDATE SET
       hours=$2, categories=$3, focus=$4, did=$5, wins=$6, blockers=$7, energy=$8, updated_at=now()
     RETURNING log_date::text AS log_date`,
    [date, h, categories, focus, did, wins, blockers, en]
  );
  ok(res, { saved: rows[0].log_date });
});

app.delete("/api/days/:date", async (req, res) => {
  if (!validDate(req.params.date)) return bad(res, "invalid date");
  await pool.query("DELETE FROM daily_logs WHERE log_date = $1", [req.params.date]);
  ok(res, { deleted: req.params.date });
});

// ---------- weekly reviews ----------
app.get("/api/weeks/:weekStart", async (req, res) => {
  if (!validDate(req.params.weekStart)) return bad(res, "invalid date");
  const ws = mondayOf(req.params.weekStart);
  const { rows } = await pool.query(
    `SELECT week_start::text AS week_start, summary, wins, blockers, adjustments, next_focus, on_track
       FROM weekly_reviews WHERE week_start = $1`,
    [ws]
  );
  ok(res, rows[0] || null);
});

app.put("/api/weeks/:weekStart", async (req, res) => {
  if (!validDate(req.params.weekStart)) return bad(res, "invalid date");
  const ws = mondayOf(req.params.weekStart);
  const { summary = "", wins = "", blockers = "", adjustments = "", next_focus = "", on_track = null } = req.body || {};
  await pool.query(
    `INSERT INTO weekly_reviews (week_start, summary, wins, blockers, adjustments, next_focus, on_track, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7, now())
     ON CONFLICT (week_start) DO UPDATE SET
       summary=$2, wins=$3, blockers=$4, adjustments=$5, next_focus=$6, on_track=$7, updated_at=now()`,
    [ws, summary, wins, blockers, adjustments, next_focus, on_track]
  );
  ok(res, { saved: ws });
});

// ---------- stats ----------
app.get("/api/stats", async (_req, res) => {
  const totals = await pool.query(
    `SELECT COALESCE(SUM(hours),0)::float AS total_hours, COUNT(*)::int AS days_logged FROM daily_logs`
  );
  const weeks = await pool.query(
    `SELECT (date_trunc('week', log_date))::date::text AS week_start, SUM(hours)::float AS hours
       FROM daily_logs
      WHERE log_date >= (date_trunc('week', CURRENT_DATE) - interval '11 weeks')::date
      GROUP BY 1 ORDER BY 1`
  );
  const cats = await pool.query(
    `SELECT c AS category, SUM(hours / GREATEST(array_length(categories,1),1))::float AS hours
       FROM daily_logs, unnest(categories) AS c
      GROUP BY 1 ORDER BY 2 DESC`
  );
  // streak: consecutive days with hours > 0 ending today or yesterday
  const days = await pool.query(
    `SELECT log_date::text AS d FROM daily_logs WHERE hours > 0 ORDER BY log_date DESC LIMIT 400`
  );
  const logged = new Set(days.rows.map(r => r.d));
  let streak = 0;
  const cursor = new Date();
  const iso = dt => dt.toISOString().slice(0, 10);
  if (!logged.has(iso(cursor))) cursor.setUTCDate(cursor.getUTCDate() - 1); // today not logged yet is fine
  while (logged.has(iso(cursor))) {
    streak += 1;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  ok(res, {
    total_hours: totals.rows[0].total_hours,
    days_logged: totals.rows[0].days_logged,
    streak,
    weeks: weeks.rows,
    categories: cats.rows,
  });
});

// ---------- export ----------
app.get("/api/export", async (_req, res) => {
  const days = await pool.query(
    `SELECT log_date::text AS log_date, hours::float AS hours, categories, focus, did, wins, blockers, energy
       FROM daily_logs ORDER BY log_date`
  );
  const weeks = await pool.query(
    `SELECT week_start::text AS week_start, summary, wins, blockers, adjustments, next_focus, on_track
       FROM weekly_reviews ORDER BY week_start`
  );
  res.setHeader("Content-Disposition", `attachment; filename="grindtrack-export-${new Date().toISOString().slice(0, 10)}.json"`);
  ok(res, { exported_at: new Date().toISOString(), daily_logs: days.rows, weekly_reviews: weeks.rows });
});

const port = process.env.PORT || 8080;
app.listen(port, () => console.log(`grindtrack listening on :${port}`));
