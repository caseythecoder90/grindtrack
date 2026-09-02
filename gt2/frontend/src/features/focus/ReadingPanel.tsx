import { FOCUS_KIND_LABEL, type ReadingProgress } from "../../lib/types";

interface Props {
  progress: ReadingProgress;
}

/**
 * What the lunch habit adds up to.
 *
 * Four numbers and two lists, chosen because they answer the four questions that actually
 * keep a habit running: am I still going (streak), am I on pace this week (target), is this
 * book moving (per-subject hours), and did any of it stick (takeaways).
 *
 * Deliberately no chart and no all-time graph — this is a nudge on the way back from lunch,
 * not an analytics page.
 */
export default function ReadingPanel({ progress }: Props) {
  const { weekdayStreak, sessionsThisWeek, weeklyTarget, hoursThisWeek } = progress;
  const pct = Math.min(100, (sessionsThisWeek / Math.max(1, weeklyTarget)) * 100);
  const onPace = sessionsThisWeek >= weeklyTarget;

  return (
    <div className="panel">
      <h2>lunch habit</h2>

      <div className="stats public">
        <div className="stat">
          <span className="k">weekday streak</span>
          <span className="v">{weekdayStreak}</span>
          <span className="split">weekends don't break it</span>
        </div>
        <div className="stat">
          <span className="k">this week</span>
          <span className="v">
            {sessionsThisWeek}
            <small>/{weeklyTarget}</small>
          </span>
          <span className="split">{hoursThisWeek.toFixed(1)}h</span>
        </div>
        <div className="stat">
          <span className="k">banked</span>
          <span className="v">{progress.totalHours.toFixed(1)}h</span>
          <span className="split">{progress.totalSessions} sessions</span>
        </div>
      </div>

      <div className={"progress" + (onPace ? " over" : "")}>
        <i style={{ width: `${pct}%` }} />
      </div>

      <h2 style={{ marginTop: 24 }}>what it went into</h2>
      {progress.subjects.length === 0 ? (
        <div className="empty">nothing yet — start a reading or review session</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>subject</th>
              <th style={{ width: 70 }}>sessions</th>
              <th style={{ width: 60 }}>hours</th>
              <th style={{ width: 100 }}>last</th>
            </tr>
          </thead>
          <tbody>
            {progress.subjects.map((s) => (
              <tr key={`${s.planItemId ?? "t"}-${s.label}`}>
                <td>
                  <span className={"badge badge-" + (s.kind === "review" ? "project" : "book")}>
                    {FOCUS_KIND_LABEL[s.kind]}
                  </span>{" "}
                  {s.label}
                </td>
                <td className="num">{s.sessions}</td>
                <td className="num">{s.hours.toFixed(1)}</td>
                <td className="muted num">{s.lastOn}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {progress.recentTakeaways.length > 0 && (
        <>
          <h2 style={{ marginTop: 24 }}>takeaways</h2>
          {progress.recentTakeaways.map((t) => (
            <div key={t.sessionId} className="plan-detail">
              <div className="muted small">
                {t.on} · {t.label}
              </div>
              <div className="refpara pre">{t.text}</div>
            </div>
          ))}
        </>
      )}
    </div>
  );
}
