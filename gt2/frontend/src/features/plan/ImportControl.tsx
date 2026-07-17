/** File picker for plan.json uploads; clears itself so the same file can be re-picked. */
export default function ImportControl({ onFile }: { onFile: (f: File) => void }) {
  return (
    <input type="file" accept=".json,application/json"
      onChange={(e) => {
        const f = e.target.files?.[0];
        if (f) onFile(f);
        e.target.value = "";
      }} />
  );
}
