import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import type { Moment, Upcoming } from "../../lib/types";
import { deleteMoment, getMoments } from "./relationshipApi";
import { daysAgo, inDays, MOMENT_LABEL } from "./kinds";
import { daysSince } from "../../lib/dates";

/** A page you can read in one sitting. The server caps anything larger. */
const PAGE = 12;

/**
 * The timeline, as one rail.
 *
 * <p>What is coming sits above today on the same line as what happened, because an anniversary in
 * 34 days and a date night 6 days ago are the same kind of thing — this is a record in time, and
 * splitting it into a "coming up" table and a "lately" table was the finance tab's habit, not this
 * feature's.
 *
 * <p>Deleting confirms <em>in the row</em>. A dialog would cover the thing you are deciding about,
 * and on a timeline of near-identical entries the one you can still see is the only reliable way
 * to know you picked the right one.
 */
export default function LatelyPanel({
  upcoming,
  discreet,
  onChange,
}: {
  upcoming: Upcoming[];
  discreet: boolean;
  onChange: () => void;
}) {
  const [items, setItems] = useState<Moment[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [confirming, setConfirming] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (from: number) => {
    setLoading(true);
    setError("");
    try {
      const page = await getMoments(PAGE, from);
      setItems(page.items);
      setTotal(page.total);
      // The server snaps the offset to a page boundary; take its word for where we are.
      setOffset(page.offset);
    } catch (e) {
      setError(errorMessage(e, "could not load the timeline"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(0);
  }, [load]);

  async function remove(id: number) {
    setError("");
    try {
      await deleteMoment(id);
      setConfirming(null);
      // Re-read this page rather than splicing it out: everything below shifts up by one, and
      // the footer's total is now wrong. Deleting the last row on a page also has to fall back.
      await load(offset >= total - 1 ? Math.max(0, offset - PAGE) : offset);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not delete that"));
    }
  }

  const visible = discreet ? items.filter((m) => !m.isPrivate) : items;
  const showing = total === 0 ? "nothing logged yet" : `${offset + 1}–${offset + items.length} of ${total}`;

  return (
    <section className="thread">
      {error && <p className="error">{error}</p>}

      <div className="rail">
        {/* Only on the first page: what is ahead belongs at the top of the record, not repeated
            every time you page backwards through it. */}
        {offset === 0 &&
          upcoming.map((u) => (
            <div className="node ahead" key={`up-${u.id}`}>
              <div className="when">
                {u.on} · {inDays(u.daysAway)}
              </div>
              <div className="what">
                {u.label}
                <span className="soft">
                  {u.ideaCount > 0
                    ? `${u.ideaCount} idea${u.ideaCount === 1 ? "" : "s"} ready`
                    : "nothing saved yet"}
                </span>
              </div>
            </div>
          ))}

        {offset === 0 && (
          <div className="today-rule">
            <span />
            <em>today</em>
            <span />
          </div>
        )}

        {loading && <p className="empty">reading…</p>}

        {!loading && visible.length === 0 && (
          <p className="empty">
            {discreet && items.length > 0
              ? "everything on this page is hidden"
              : "nothing logged yet"}
          </p>
        )}

        {visible.map((m) =>
          confirming === m.id ? (
            <div className="node confirming" key={m.id}>
              <div className="when">
                {daysAgo(daysSince(m.occurredOn))} · {m.occurredOn.slice(5)}
              </div>
              <div className="what">{m.note || MOMENT_LABEL[m.kind]}</div>
              <div className="confirm">
                <span>delete this?</span>
                <button type="button" onClick={() => setConfirming(null)}>
                  keep
                </button>
                <button type="button" className="danger" onClick={() => remove(m.id)}>
                  delete
                </button>
              </div>
            </div>
          ) : (
            <div className={"node" + (m.isPrivate ? " us" : "")} key={m.id}>
              <div className="when">
                {daysAgo(daysSince(m.occurredOn))} · {m.occurredOn.slice(5)}
              </div>
              <div className="what">
                {m.note || MOMENT_LABEL[m.kind]}
                {m.note && <span className="kindtag">{MOMENT_LABEL[m.kind]}</span>}
              </div>
              <button
                type="button"
                className="rowdelete"
                aria-label={`delete ${MOMENT_LABEL[m.kind]} on ${m.occurredOn}`}
                onClick={() => setConfirming(m.id)}
              >
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                  strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
                  <path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" />
                </svg>
              </button>
            </div>
          ),
        )}
      </div>

      <div className="pager">
        <span className="count">{showing}</span>
        <div>
          <button type="button" disabled={offset === 0 || loading} onClick={() => load(offset - PAGE)}>
            newer
          </button>
          <button
            type="button"
            disabled={offset + items.length >= total || loading}
            onClick={() => load(offset + PAGE)}
          >
            older
          </button>
        </div>
      </div>
    </section>
  );
}
