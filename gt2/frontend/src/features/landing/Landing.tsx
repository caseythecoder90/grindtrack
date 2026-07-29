
import { useEffect, useState } from "react";
import Heatmap from "../../components/Heatmap";
import { api } from "../../lib/api";
import type { PublicStats } from "../../lib/types";

interface Props {
  onLoginClick: () => void;
}

/** Public read-only view: heatmap + counters. No notes ever appear here. */
export default function Landing({ onLoginClick }: Props) {
  const [stats, setStats] = useState<PublicStats | null>(null);

  useEffect(() => {
    api<PublicStats>("/api/public/stats").then(setStats).catch(() => setStats(null));
  }, []);

  return (
    <>
      <p className="sub">
        A 4-year engineering study plan, tracked in public: Kubernetes → protocols → distributed
        systems → payments. Green squares are hours logged before work and on weekends.
      </p>
      {stats && (
        <>
          {/* Study only, and no scope switcher: day-job hours are not public. */}
          <section className="statbar" aria-label="Study progress">
            <div className="stats public">
              <div className="stat">
                <span className="k">streak</span>
                <span className="v">{stats.streak}d</span>
              </div>
              <div className="stat">
                <span className="k">total hours</span>
                <span className="v">{stats.totalHours.toFixed(0)}</span>
              </div>
              <div className="stat">
                <span className="k">days logged</span>
                <span className="v">{stats.daysLogged}</span>
              </div>
            </div>
          </section>
          <Heatmap study={stats.days} work={[]} scope="study" />
        </>
      )}
      <div className="landing-cta">
        <button className="primary" onClick={onLoginClick}>Owner login</button>
      </div>
    </>
  );
}
