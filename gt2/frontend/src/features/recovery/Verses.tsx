import type { Verse } from "./recoveryApi";

interface Props {
  verses: Verse[];
  /** A verse to light up: the one a search found, or the one a reference named. */
  focus?: number | null;
  /** Prefix for the verse element ids, so a chapter can scroll to one. */
  idPrefix?: string;
}

/**
 * Verses set like a page: grouped into the paragraphs the edition marks, a small number on each,
 * and a group with a line of poetry in it set line by line.
 */
export default function Verses({ verses, focus = null, idPrefix = "v" }: Props) {
  const groups: Verse[][] = [];
  for (const v of verses) {
    if (v.para || groups.length === 0) groups.push([v]);
    else groups[groups.length - 1].push(v);
  }
  return (
    <div className="rec-serif">
      {groups.map((g) => (
        <p key={g[0].verse} className={"rec-verse-para" + (g.some((v) => v.text.includes("\n")) ? " poetry" : "")}>
          {g.map((v) => (
            <span key={v.verse} id={`${idPrefix}-${v.verse}`} className={"rec-verse" + (v.verse === focus ? " hit" : "")}>
              <sup className="rec-vn">{v.verse}</sup>
              {v.text}{" "}
            </span>
          ))}
        </p>
      ))}
    </div>
  );
}
