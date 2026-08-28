import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../../lib/api";
import type { FinanceAccount, ImportBatch, ImportResult } from "../../lib/types";

/**
 * Statement upload.
 *
 * <p>The format is detected from the file's header rather than picked from a dropdown — the file
 * already knows which bank it came from, and with three Capital One cards, asking is a good way to
 * get a wrong answer. What you do have to pick is the account, and the importer refuses outright
 * when a Capital One card statement names a different card than the account you chose.
 */
export default function ImportPanel({
  accounts,
  onImported,
}: {
  accounts: FinanceAccount[];
  onImported: () => void;
}) {
  const [accountId, setAccountId] = useState<number | "">(accounts[0]?.id ?? "");
  const [result, setResult] = useState<ImportResult | null>(null);
  const [history, setHistory] = useState<ImportBatch[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await api<ImportBatch[]>("/api/finance/imports"));
    } catch {
      /* history is a nicety; a failure here shouldn't block importing */
    }
  }, []);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  async function send(dryRun: boolean) {
    const file = fileRef.current?.files?.[0];
    if (!file || typeof accountId !== "number") return;

    setBusy(true);
    setError("");
    setResult(null);
    try {
      const form = new FormData();
      form.append("file", file);
      // No Content-Type header: the browser has to set the multipart boundary itself.
      const res = await api<ImportResult>(
        `/api/finance/imports?accountId=${accountId}&dryRun=${dryRun}`,
        { method: "POST", body: form },
      );
      setResult(res);
      if (!dryRun) {
        if (fileRef.current) fileRef.current.value = "";
        loadHistory();
        onImported();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not read that file");
    } finally {
      setBusy(false);
    }
  }

  async function undo(batch: ImportBatch) {
    setError("");
    try {
      await api(`/api/finance/imports/${batch.id}`, { method: "DELETE" });
      loadHistory();
      onImported();
    } catch (e) {
      setError(e instanceof Error ? e.message : "could not undo that import");
    }
  }

  const accountName = (id: number): string =>
    accounts.find((a) => a.id === id)?.name ?? `account ${id}`;

  return (
    <section className="import-panel">
      <div className="section-head">
        <h3>import a statement</h3>
      </div>

      <div className="account-form">
        <select value={accountId} onChange={(e) => setAccountId(Number(e.target.value))}>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
              {a.last4 ? ` ·${a.last4}` : ""}
            </option>
          ))}
        </select>
        <input type="file" accept=".csv,text/csv" ref={fileRef} />
        <button type="button" disabled={busy} onClick={() => send(true)}>
          preview
        </button>
        <button type="button" className="primary" disabled={busy} onClick={() => send(false)}>
          {busy ? "reading…" : "import"}
        </button>
      </div>

      <p className="muted small">
        Capital One (checking, savings, cards), Chase, Wells Fargo, Bank of America and Aidvantage
        are all recognized automatically. Re-importing an overlapping date range is safe — rows
        already present are skipped, not duplicated.
      </p>

      {error && <p className="error">{error}</p>}

      {result && (
        <div className={`import-result${result.dryRun ? " dry" : ""}`}>
          <div className="import-result-head">
            <strong>{result.dryRun ? "Preview" : "Imported"}</strong>
            <span className="muted"> · {result.format}</span>
            {result.periodStart && (
              <span className="muted">
                {" "}
                · {result.periodStart} → {result.periodEnd}
              </span>
            )}
          </div>
          <ul className="import-counts">
            <li>
              <b>{result.imported}</b> {result.dryRun ? "would be added" : "added"}
            </li>
            {result.duplicates > 0 && (
              <li className="muted">
                <b>{result.duplicates}</b> already present
              </li>
            )}
            {result.pending > 0 && (
              <li className="muted">
                <b>{result.pending}</b> still pending at the bank — they'll come in once they post
              </li>
            )}
            <li className="muted">{result.rowsInFile} rows in the file</li>
          </ul>
          {result.balanceUpdate && (
            <p className="muted small">
              Balance from the statement: ${result.balanceUpdate}
              {result.dryRun ? " (not applied yet)" : " — account updated"}
            </p>
          )}
          {result.warnings.map((w) => (
            <p key={w} className="warn">
              {w}
            </p>
          ))}
          {result.dryRun && result.imported > 0 && (
            <p className="muted small">Nothing was written. Press import to commit.</p>
          )}
        </div>
      )}

      {history.length > 0 && (
        <>
          <div className="section-head">
            <h3>import history</h3>
          </div>
          <table className="txn-table">
            <tbody>
              {history.slice(0, 10).map((b) => (
                <tr key={b.id}>
                  <td className="muted small">{b.importedAt.slice(0, 10)}</td>
                  <td>
                    {b.filename}
                    <div className="muted small">
                      {accountName(b.accountId)} · {b.rowsImported} added
                      {b.rowsDuplicate > 0 && `, ${b.rowsDuplicate} skipped`}
                      {b.periodStart && ` · ${b.periodStart} → ${b.periodEnd}`}
                    </div>
                  </td>
                  <td className="num">
                    <button type="button" className="ghost" title="undo this import"
                      onClick={() => undo(b)}>
                      undo
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
