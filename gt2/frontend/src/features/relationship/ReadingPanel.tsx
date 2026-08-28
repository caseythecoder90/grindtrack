import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  addReading,
  deleteReading,
  getReading,
  markRead,
  promoteTakeaway,
} from "./relationshipApi";
import type { ReadingItem, ReadingKind } from "../../lib/types";
import { READING_LABEL } from "./kinds";

const KINDS: ReadingKind[] = ["ARTICLE", "BOOK", "PODCAST"];

/**
 * Reading, and the field that actually matters.
 *
 * <p>The point of reading something about relationships is what you would do differently. A list
 * without a takeaway is a list of things you can say you read — so marking one read asks for the
 * takeaway, and a takeaway can be promoted straight into a gesture idea, which is where it stops
 * being a thought and becomes something that happens on a Tuesday.
 */
export default function ReadingPanel({ onChange }: { onChange: () => void }) {
  const [items, setItems] = useState<ReadingItem[]>([]);
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const [url, setUrl] = useState("");
  const [kind, setKind] = useState<ReadingKind>("ARTICLE");
  const [reading, setReading] = useState<number | null>(null);
  const [takeaway, setTakeaway] = useState("");
  const [error, setError] = useState("");
  const [note, setNote] = useState("");

  const load = useCallback(async () => {
    try {
      setItems(await getReading());
    } catch (e) {
      setError(errorMessage(e, "could not load your reading list"));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function add() {
    if (!title.trim()) return;
    setError("");
    try {
      setItems(await addReading({ title, url: url || null, source: null, kind }));
      setTitle("");
      setUrl("");
      setAdding(false);
    } catch (e) {
      setError(errorMessage(e, "could not save that"));
    }
  }

  async function saveRead(item: ReadingItem) {
    setError("");
    try {
      setItems(await markRead(item.id, takeaway));
      setReading(null);
      setTakeaway("");
    } catch (e) {
      setError(errorMessage(e, "could not save that"));
    }
  }

  async function promote(item: ReadingItem) {
    setError("");
    setNote("");
    try {
      await promoteTakeaway(item.id);
      setNote(`Added "${item.takeaway}" to your ideas.`);
      onChange();
    } catch (e) {
      setError(errorMessage(e, "could not turn that into an idea"));
    }
  }

  async function remove(item: ReadingItem) {
    setError("");
    try {
      await deleteReading(item.id);
      load();
    } catch (e) {
      setError(errorMessage(e, "could not remove that"));
    }
  }

  return (
    <section className="reading">
      <div className="section-head">
        <h3>reading</h3>
        <button type="button" onClick={() => setAdding((v) => !v)}>
          {adding ? "cancel" : "+ item"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}
      {note && <p className="muted small">{note}</p>}

      {adding && (
        <div className="account-form">
          <input placeholder="title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <input placeholder="link (optional)" value={url} onChange={(e) => setUrl(e.target.value)} />
          <select value={kind} onChange={(e) => setKind(e.target.value as ReadingKind)}>
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {READING_LABEL[k]}
              </option>
            ))}
          </select>
          <button type="button" onClick={add}>
            add
          </button>
        </div>
      )}

      {items.length === 0 ? (
        <p className="muted small">
          Nothing on the list. Anything you save here asks what you took from it once you have read
          it — and a takeaway can become an idea in one click.
        </p>
      ) : (
        <table className="txn-table reading-table">
          <tbody>
            {items.map((item) => (
              <tr key={item.id} className={item.status === "READ" ? "read" : ""}>
                <td>
                  {item.url ? (
                    <a href={item.url} target="_blank" rel="noreferrer noopener">
                      {item.title}
                    </a>
                  ) : (
                    item.title
                  )}
                  <div className="muted small">
                    {READING_LABEL[item.kind]}
                    {item.readOn && ` · read ${item.readOn}`}
                  </div>
                  {item.takeaway && <div className="takeaway">{item.takeaway}</div>}
                  {reading === item.id && (
                    <div className="account-form">
                      <input
                        autoFocus
                        placeholder="what would you do differently?"
                        value={takeaway}
                        onChange={(e) => setTakeaway(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") saveRead(item);
                        }}
                      />
                      <button type="button" onClick={() => saveRead(item)}>
                        save
                      </button>
                    </div>
                  )}
                </td>
                <td className="num">
                  {item.status === "TO_READ" ? (
                    <button
                      type="button"
                      className="ghost"
                      onClick={() => {
                        setReading(item.id);
                        setTakeaway("");
                      }}
                    >
                      read it
                    </button>
                  ) : (
                    item.takeaway && (
                      <button type="button" className="ghost" onClick={() => promote(item)}>
                        make it an idea
                      </button>
                    )
                  )}
                  <button type="button" className="ghost" onClick={() => remove(item)}>
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
