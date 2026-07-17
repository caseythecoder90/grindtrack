import type { PlanReferenceSheet } from "../../lib/types";

/**
 * Generic renderer for reference sheets (Overview, Stripe Target, Weekly Schedule, Resources).
 * contentJson is {rows: string[][]}: consecutive multi-cell rows become a table (first row as
 * header), single-cell rows become paragraphs.
 */
export default function Reference({ sheets }: { sheets: PlanReferenceSheet[] }) {
  return (
    <>
      {[...sheets]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((sheet) => (
          <div className="panel refsheet" key={sheet.sheet}>
            <h2>{sheet.title.toLowerCase()}</h2>
            {groupRows(parseRows(sheet.contentJson)).map((group, gi) =>
              group.kind === "table" ? (
                <div className="refscroll" key={gi}>
                  <table>
                    <thead>
                      <tr>
                        {group.rows[0].map((cell, ci) => (
                          <th key={ci}>{cell}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {group.rows.slice(1).map((row, ri) => (
                        <tr key={ri}>
                          {group.rows[0].map((_, ci) => (
                            <td key={ci}>{row[ci] ?? ""}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="refpara" key={gi}>
                  {group.text}
                </p>
              ),
            )}
          </div>
        ))}
    </>
  );
}

function parseRows(contentJson: string): string[][] {
  try {
    const parsed = JSON.parse(contentJson) as { rows?: string[][] };
    return parsed.rows ?? [];
  } catch {
    return [];
  }
}

type Group = { kind: "table"; rows: string[][] } | { kind: "para"; text: string };

function groupRows(rows: string[][]): Group[] {
  const groups: Group[] = [];
  for (const row of rows) {
    if (row.length === 0) continue;
    if (row.length === 1) {
      groups.push({ kind: "para", text: row[0] });
    } else {
      const last = groups[groups.length - 1];
      if (last && last.kind === "table" && last.rows[0].length === row.length) {
        last.rows.push(row);
      } else {
        groups.push({ kind: "table", rows: [row] });
      }
    }
  }
  return groups;
}
