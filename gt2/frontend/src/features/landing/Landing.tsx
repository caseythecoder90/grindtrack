
import { useEffect, useState } from "react";
import Heatmap from "../../components/Heatmap";
import StatBar from "../../components/StatBar";
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
          <StatBar streak={stats.streak} totalHours={stats.totalHours} daysLogged={stats.daysLogged} />
          <Heatmap days={stats.days} />
        </>
      )}
      <div className="landing-cta">
        <button className="primary" onClick={onLoginClick}>Owner login</button>
      </div>
    </>
  );
}
