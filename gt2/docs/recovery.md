# Recovery tab

A morning page: the day count, the day's reflection, a short Bible passage,
a meditation timer, a few minutes of the Big Book, and a journal. The
assistant sees the day count and nothing else from this page.

This document is the plan and the record: what is stored, where the text
comes from, how a book is imported, and what runs when.

## What the page shows

| Card | Source | Advances by |
|---|---|---|
| **The number** | `SOBRIETY_DATE` from the environment (set once in the cluster's secret, never in the repo). Day one is the date itself. | The calendar |
| **Daily reflection** | Your imported copy of *Daily Reflections* (one entry per calendar day) | The calendar (month + day) |
| **Today's passage** | The built-in Bible (see below): one short passage a day, in order | The calendar (days since the plan started) |
| **Meditation** | A timer of any length; a bell at the end; sessions are logged | You |
| **Big Book** | Your imported copy, read in order, cut at paragraph ends to fit your minutes | "Done for today" (missing a day waits, it does not skip) |
| **Journal** | Typed or spoken (the same speech relay as the ask tab) | You |

Three sub-views, like the us and work tabs: **today · read · journal**. A
fourth screen, **your books**, is where the texts are imported.

## Where the text comes from

The app is public on GitHub, so nothing under copyright is checked in and
nothing is fetched from a publisher's website. Three kinds of text, three
answers:

**The Bible: built in.** The World English Bible (WEB) is a modern-English
translation that its makers placed in the public domain, so the app ships
it: 66 books, 31,098 verses, about 1.3 MB compressed, in
`backend/src/main/resources/recovery/web.jsonl.gz`. On first start the app
seeds the `bible_verses` table from it (a few seconds, once). Nothing to
upload.

The file was produced from the USFX edition published by eBible.org and
mirrored in `seven1m/open-bibles`, by `tools/recovery/usfx_to_verses.py`.
The King James Version is also public domain; if you would rather read
that, the same script converts `eng-kjv.osis.xml` with a one-line change
and the app does not care which. Modern translations under copyright (NIV,
ESV, NLT, The Message) cannot be stored in the app.

**The AA books: yours to bring.** *Alcoholics Anonymous* (the Big Book,
4th edition), *Daily Reflections* and Hazelden's *Twenty-Four Hours a Day*
are copyrighted, so the app has none of the text. You import your own copy,
once, as one plain-text file per book. It lives only in your database.

How to get a plain-text file of a book you own:

- **An ebook you bought (EPUB, without DRM):** Calibre's command line does
  it in one step: `ebook-convert book.epub book.txt`. AA World Services
  sells the Big Book and Daily Reflections as ebooks on its online store;
  Hazelden sells *Twenty-Four Hours a Day*.
- **A PDF:** `pdftotext -layout book.pdf book.txt` (poppler), then a quick
  look for running headers and page numbers, which the importer mostly
  ignores anyway (see below).
- **The physical book:** a phone scan with OCR (Apple Notes, Google Lens,
  Microsoft Lens) pasted into one file. Slower, but the importer is
  forgiving about stray line breaks.

Whichever route, the file goes in through **your books → upload the book**.
A dry run reads it and reports what it found before writing anything.

## What the importer does with a file

The importer needs no markup from you. It reads headings.

**A book read in order (the Big Book).** Paragraphs are runs of text
separated by blank lines. A paragraph is a chapter heading when it is short
(under 60 characters), stands alone, and either matches one of the known
chapter titles case-insensitively (*The Doctor's Opinion, Bill's Story,
There Is a Solution, More About Alcoholism, We Agnostics, How It Works,
Into Action, Working with Others, To Wives, The Family Afterward, To
Employers, A Vision for You*) or is in ALL CAPS; a "Chapter 3" line before
the title folds into it. Everything before the first heading is the front
matter: kept as chapter 0 when it is long enough to be text, dropped when it
is a copyright line. Lines that are only a number (page numbers from a PDF)
are dropped; a line break inside a paragraph is joined with a space, and a
file whose lines are not wrapped at all (one long line per paragraph) is
read line by line. The result is one row per
paragraph: chapter number, chapter title, sequence, text, word count.

Each day's reading is then *whole paragraphs from the cursor until the
words add up to your minutes* (at 180 words a minute; 5 minutes is about
900 words). Change the minutes and tomorrow's part changes size. "Done for
today" moves the cursor. After the last paragraph the cursor returns to
the first and the count of read-throughs goes up by one.

**A book read by date (Daily Reflections, Twenty-Four Hours a Day).** A
line that is a month name and a day (`JANUARY 1`, `January 1`, `Jan. 1`,
`1 January`) starts an entry; the next line is the title; everything to the next date
line is the body. 366 entries is the whole book; 365 means February 29 is
missing and the importer says so. The today card shows the entry for the
current month and day.

Both importers run as a **dry run** first and report: chapters found and
their titles (or dates found and any missing), paragraphs, words, and the
first 200 characters of chapter one, so a wrong split is visible before
anything is saved. Committing replaces the previous import of that slot
wholesale; the reading cursor is kept if the paragraph count is close
(within 5%), otherwise it resets and says so.

**The Bible plan** needs no import. It is a list of passages built at
startup from the verse table: within a chapter, verses are grouped from
one paragraph start to the next, joining groups until there are at least
five verses and cutting at sixteen. Poetry keeps its line breaks. Books are
read in the order in `application.yml` (`grindtrack.recovery.bible.books`:
John, Psalms, Matthew, Proverbs, Mark, Romans, Philippians, Luke, James,
Ephesians, Acts, 1 Peter, Colossians, 1 John, Isaiah, Genesis, then the rest
of the New Testament; whatever is not listed follows in canonical order, so
the plan covers the whole Bible: 5,058 passages of roughly 140 words, a
minute read aloud). Day *n* of the plan is passage *n*; the plan starts on
the day the feature is deployed and can be restarted from the your-books
screen.

## Data model

Migration `033-recovery.sql`, package `dev.grindtrack.recovery` (`domain`, `service`, `api`):

| Table | Row is | Notes |
|---|---|---|
| `recovery_texts` | one imported book | `slot` (`big_book`, `reflection`, `meditation`), `title`, `imported_at`, `paragraph_count`, `word_count` |
| `recovery_paragraphs` | one paragraph of a book read in order | `text_id`, `chapter_no`, `chapter_title`, `seq`, `body`, `words` |
| `recovery_daily_entries` | one dated entry of a book read by date | `text_id`, `month`, `day`, `title`, `body` |
| `recovery_settings` | one row | `read_cursor` (paragraph seq), `read_minutes` (default 5), `read_throughs`, `meditation_minutes` (last used), `bible_plan_start` |
| `recovery_journal` | one entry | `created_at`, `body`, `spoken` |
| `recovery_sessions` | one meditation | `started_at`, `minutes`, `completed` |
| `recovery_days` | one day's marks | `day` (pk), `read_done`, `read_from`/`read_to` (the part that was read, so it stays on screen after the cursor moved), `meditated` |
| `bible_verses` | one verse | `book` (USFM code), `book_ord`, `chapter`, `verse`, `para`, `text`; seeded once from the resource |

No personal text is ever in a migration; the tables are created empty and
filled by you (imports, journal) or by the seed (the Bible).

## API

All under `/api/recovery`, same auth as everything else.

| Method and path | Does |
|---|---|
| `GET /today` | Everything the today view needs: day count, reflection, meditation entry, passage, today's Big Book part, settings, today's marks |
| `POST /read/done` | Marks today read and advances the cursor; a second press changes nothing |
| `POST /read/restart` | Cursor back to the first paragraph |
| `PUT /settings` | `readMinutes`, `meditationMinutes` |
| `POST /sessions` | Logs a meditation `{minutes, completed}`; only a completed one marks the day |
| `GET /journal?before=` | Entries newest first, 50 at a time |
| `POST /journal` | `{body, spoken}` |
| `DELETE /journal/{id}` | Removes one |
| `GET /library` | The three slots (imported or not, counts) and the Bible's status |
| `POST /import/{slot}?dryRun=true&title=` | Multipart `file`: the plain-text file; returns the report; `dryRun=false` writes |
| `POST /bible/restart` | Plan starts today |

The exact shapes are in [api.md](api.md#recovery-authenticated).

## What runs when

| Time | Job | Push |
|---|---|---|
| 05:30 | Morning brief (unchanged) | "good morning" line, then the brief |
| **07:55** | `RecoveryReadingScheduler` | One notification, tag `readings`: the reflection's title, the passage reference, and the Big Book chapter, for reading together later. Tapping opens the recovery tab. Sent only if at least one of the three exists. |
| 08:00, 18:00 | Todo reminders (unchanged) | |

`grindtrack.recovery.readings-cron: "0 55 7 * * *"` in the app's zone.

## Privacy

The journal and the imported books stay in your database. The assistant's
context carries the day count only; no tool reads the journal, the
reflection or the Big Book. The push at 07:55 carries titles and a verse
reference, not the text.

## The code, for someone reading it

| Piece | Where | What to read it for |
|---|---|---|
| The parsers | `recovery/service/BookParser.java`, `DailyParser.java`, `Blocks.java` | Pure functions from text to structure; every rule above is a test in `BookParserTest` / `DailyParserTest` |
| The day's part | `ReadingPlanner.java` | Whole paragraphs until the minutes are filled |
| The Bible | `BibleBooks.java`, `BiblePlan.java`, `BibleSeeder.java`, `BibleService.java` | The canon, the cutting rule, the batched seed, the plan held in memory |
| The number | `Milestones.java` | Day one is the date; the next milestone |
| Everything else | `RecoveryService.java` | The today view, the cursor, the imports, the push line |
| The push | `RecoveryReadingScheduler.java` + `PushService.Notification.readings` | Same shape as the todo reminder |
| The page | `frontend/src/features/recovery/` | `RecoveryPage` (views and columns), `MeditationTimer` (+ `bell.ts`), `JournalComposer` (the ask tab's composer with `useSpeech`), `LibraryPanel` |

## Decisions taken

- **Colour:** seafoam (`--rec: #9ad8c8`), used only on this tab.
- **Where it lives:** the last visible button of the bottom bar; "more"
  moves to a small button in the header.
- **Meditation:** any length. The chips are the last lengths used; the
  last chip takes a number.
- **Translation:** WEB, unless you say KJV.
