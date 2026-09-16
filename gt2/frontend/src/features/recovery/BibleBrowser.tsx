import { useEffect, useRef, useState } from "react";
import { errorMessage } from "../../lib/api";
import {
  getBibleBooks,
  getBibleChapter,
  searchBible,
  type BibleBook,
  type BibleChapter,
  type BibleHit,
} from "./recoveryApi";
import Verses from "./Verses";

/** Where the browser is open: a chapter, and a verse in it when one was named. */
export interface Place {
  book: string;
  chapter: number;
  verse: number | null;
}

interface Props {
  place: Place | null;
  onPlace: (place: Place | null) => void;
}

/**
 * The whole Bible, from anywhere: the sixty-six books, a chapter set like a page with the
 * previous and the next a tap away, and one box that takes either a reference — "John 3:16",
 * "psalm 23" — or words to find.
 */
export default function BibleBrowser({ place, onPlace }: Props) {
  const [books, setBooks] = useState<BibleBook[]>([]);
  const [openBook, setOpenBook] = useState<string | null>(null);
  const [chapter, setChapter] = useState<BibleChapter | null>(null);
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<BibleHit[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const top = useRef<HTMLDivElement>(null);

  useEffect(() => {
    getBibleBooks().then(setBooks).catch((e) => setError(errorMessage(e, "could not load the books")));
  }, []);

  useEffect(() => {
    if (!place) {
      setChapter(null);
      return;
    }
    let live = true;
    setError("");
    getBibleChapter(place.book, place.chapter)
      .then((c) => live && setChapter(c))
      .catch((e) => live && setError(errorMessage(e, "could not open that chapter")));
    return () => {
      live = false;
    };
  }, [place]);

  // Once the chapter is on screen, land on the verse named, or the top of the chapter.
  useEffect(() => {
    if (!chapter) return;
    const el = place?.verse ? document.getElementById(`bv-${place.verse}`) : null;
    if (el) el.scrollIntoView({ block: "center" });
    else top.current?.scrollIntoView({ block: "start" });
  }, [chapter, place]);

  async function find() {
    const query = q.trim();
    if (query.length < 2) return;
    setBusy(true);
    setError("");
    try {
      const found = await searchBible(query);
      if (found.place) {
        setHits(null);
        setQ("");
        onPlace({ book: found.place.book, chapter: found.place.chapter, verse: found.place.verse });
      } else {
        setHits(found.hits);
      }
    } catch (e) {
      setError(errorMessage(e, "could not search"));
    } finally {
      setBusy(false);
    }
  }

  const old = books.filter((b) => b.testament === "old");
  const fresh = books.filter((b) => b.testament === "new");

  return (
    <>
      <div className="rec-card rec-search">
        <div className="rec-search-row bible">
          <input
            type="search"
            value={q}
            placeholder="John 3:16, or words to find"
            aria-label="Search the Bible or go to a reference"
            enterKeyHint="search"
            onChange={(e) => {
              setQ(e.target.value);
              if (!e.target.value.trim()) setHits(null);
            }}
            onKeyDown={(e) => { if (e.key === "Enter") find(); }}
          />
          <button type="button" disabled={busy || q.trim().length < 2} onClick={find}>find</button>
        </div>
        {error && <div className="error">{error}</div>}
        {hits !== null && (
          <div className="rec-hits">
            <div className="rec-cardhead">
              <span className="rec-note">{hits.length === 0 ? "nothing found" : hits.length === 40 ? "40 hits — the first forty; add a word" : `${hits.length} hit${hits.length === 1 ? "" : "s"}`}</span>
              <button type="button" className="linkish" onClick={() => { setHits(null); setQ(""); }}>clear</button>
            </div>
            {hits.map((h) => (
              <button
                type="button"
                key={`${h.book}-${h.chapter}-${h.verse}`}
                className="rec-hit"
                onClick={() => onPlace({ book: h.book, chapter: h.chapter, verse: h.verse })}
              >
                <span className="rec-note">{h.reference}</span>
                <span className="rec-hit-text">{h.text}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {place && chapter ? (
        <div className="rec-card rec-pages rec-reader" ref={top}>
          <div className="rec-cardhead">
            <span className="rec-lbl">{chapter.name} {chapter.chapter}</span>
            <span className="rec-note">chapter {chapter.chapter} of {chapter.chapters}</span>
          </div>
          <div className="rec-reader-nav">
            <button type="button" className="linkish" onClick={() => { onPlace(null); setOpenBook(chapter.book); }}>‹ books</button>
            <span>
              {chapter.prev && (
                <button type="button" className="linkish" onClick={() => onPlace({ book: chapter.prev!.book, chapter: chapter.prev!.chapter, verse: null })}>‹ previous</button>
              )}
              {chapter.next && (
                <button type="button" className="linkish" onClick={() => onPlace({ book: chapter.next!.book, chapter: chapter.next!.chapter, verse: null })}>next ›</button>
              )}
            </span>
          </div>
          <div className="rec-reader-text">
            <Verses verses={chapter.verses} focus={place.verse} idPrefix="bv" />
          </div>
          <div className="rec-reader-nav bottom">
            <span>
              {chapter.prev && (
                <button type="button" className="linkish" onClick={() => onPlace({ book: chapter.prev!.book, chapter: chapter.prev!.chapter, verse: null })}>‹ {chapter.prev.name} {chapter.prev.chapter}</button>
              )}
            </span>
            <span>
              {chapter.next && (
                <button type="button" className="linkish" onClick={() => onPlace({ book: chapter.next!.book, chapter: chapter.next!.chapter, verse: null })}>{chapter.next.name} {chapter.next.chapter} ›</button>
              )}
            </span>
          </div>
        </div>
      ) : place ? (
        <div className="rec-card"><span className="rec-note">opening…</span></div>
      ) : (
        <div className="rec-card">
          <div className="rec-cardhead">
            <span className="rec-lbl">the books</span>
            {openBook && (
              <button type="button" className="linkish" onClick={() => setOpenBook(null)}>‹ all books</button>
            )}
          </div>
          {openBook ? (
            <BookChapters book={books.find((b) => b.code === openBook)!} onPick={(n) => onPlace({ book: openBook, chapter: n, verse: null })} />
          ) : (
            <>
              <div className="rec-note">old testament</div>
              <div className="rec-books">
                {old.map((b) => <button type="button" key={b.code} onClick={() => setOpenBook(b.code)}>{b.name}</button>)}
              </div>
              <div className="rec-note" style={{ marginTop: 10 }}>new testament</div>
              <div className="rec-books">
                {fresh.map((b) => <button type="button" key={b.code} onClick={() => setOpenBook(b.code)}>{b.name}</button>)}
              </div>
            </>
          )}
        </div>
      )}
    </>
  );
}

/** A book's chapters as a grid of numbers. */
function BookChapters({ book, onPick }: { book: BibleBook; onPick: (chapter: number) => void }) {
  return (
    <>
      <div className="rec-title">{book.name} <span className="muted">· {book.chapters} chapter{book.chapters === 1 ? "" : "s"}</span></div>
      <div className="rec-chapgrid">
        {Array.from({ length: book.chapters }, (_, i) => i + 1).map((n) => (
          <button type="button" key={n} onClick={() => onPick(n)}>{n}</button>
        ))}
      </div>
    </>
  );
}
