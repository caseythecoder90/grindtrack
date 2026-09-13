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
| **Big Book** | Your imported copy, two pages a day from a page cursor; a missed day carries over | "Done for today", or "mark read to here" from anywhere in the book |
| **Journal** | Typed or spoken (the same speech relay as the ask tab) | You |
| **People** | A sponsor, a sponsor still to be asked, friends in the program, each with a cadence | Logging a call |

Four sub-views, like the us and work tabs: **today · read · journal · people**.
A fifth screen, **your books**, is where the texts are imported.

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
once. It lives only in your database — the files too, so a better parser
later re-reads them without another upload ("read the stored files again"
on the your-books screen).

The Big Book goes in as **the publisher's PDFs, all at once**: AA World
Services publishes the 4th edition as one PDF per chapter, foreword, story
part and appendix, and those are what the importer expects. Pick them all
in the file dialog (thirty-one files, under three megabytes together) and
press import. They land in the book's order by their names, the title page,
copyright page and contents are skipped, and every paragraph carries the
page number printed on it, so "two pages a day" means the same as in the
printed book. A single plain-text file also works (Calibre's
`ebook-convert book.epub book.txt`, or `pdftotext`), with a page invented
every three hundred words.

*Daily Reflections* and a meditation book go in the same way, as their PDF
or as one text file; the day headings are found either way.

Whichever route, the files go in through **your books**. A dry run reads
them and reports what it found before writing anything.

## What the importer does with a file

The importer needs no markup from you. It reads headings.

**The PDFs (the Big Book).** PDFBox reads each file page by page, sorting
the text by position and marking the lines that start a paragraph by their
indentation — how a typeset book marks them. `PdfBookParser` then reads
the shape of each page: the print-shop footer line goes; the running head
("18 ALCOHOLICS ANONYMOUS", "DOCTOR BOB'S NIGHTMARE 173", "xvi FOREWORD",
or a number alone at the foot) gives the page its printed label and is
dropped; a line of capitals opens a chapter, with "Chapter 3" or a bare
numeral before it folded away and a second title line joined on;
"A.A." at the end of a title is not the end of a sentence; a line ending
in a hyphen is joined to the next without it, a soft hyphen likewise; a
marked line that starts in lower case mid-sentence is the rest of the
paragraph, not a new one; a drop cap that came out as "W e," is closed
up. Pages without a printed number still turn the page. Afterwards a
"chapter" of under forty words (the note before the stories, the line
that opens the appendices) folds into its neighbour: into the next
chapter when it opens a file, otherwise into the one before, its title
kept as a line of text; a note that opens a section of short pieces gives
the section its name. Files go in the book's order by name (preface,
forewords, the doctor's opinion, chapters 1–11, the stories, the
appendices); title page, copyright and contents are skipped. The result
is one row per paragraph: chapter, sequence, text, word count, the page
label as printed and the page's running number across the book.

On the 4th-edition PDFs from aa.org this gives 69 chapters — every
foreword, chapter, story and appendix under its own title with its
printed page range — 588 pages and about 168,000 words.

**Plain text (the Big Book from a text file).** Paragraphs are runs of
text separated by blank lines. A paragraph is a chapter heading when it is
short (under 60 characters), stands alone, and either matches one of the
known chapter titles case-insensitively or is in ALL CAPS; a "Chapter 3"
line before the title folds into it. Front matter before the first heading
is kept when it is text and dropped when it is a copyright line; page
numbers standing alone are dropped; wrapped lines are joined; a file with
one long line per paragraph is read line by line. A page is invented every
three hundred words.

**Two pages a day, and the carry-over.** The cursor is a paragraph; the
page it starts on is where today's part begins. Today owes `pages_per_day`
pages for every day since the reading was last done (never done: since
yesterday), so a missed day carries over: two pages, then four, then six,
shown as "6 pages · 4 carried over". The part is *every paragraph that
starts on a page owed*, so the paragraph that begins on the last page is
read to its end rather than cut. "Done for today" marks the day, moves
the cursor past the part and sets today as the last day done; "catch up
from here" forgives the backlog (last done becomes yesterday, so tomorrow
owes the usual). After the last paragraph the cursor returns to the first
and the count of read-throughs goes up by one. Replacing the book keeps
the cursor when the paragraph count is close (within 5%), otherwise it
resets, clears today's mark, and says so.

**Reading anywhere.** The read view carries the table of contents: every
chapter with its printed page range and whether the cursor has passed it.
Tapping one opens the chapter in the reader — serif, the book's own
paragraphs, a thin rule where a page turns — and saves it as your place,
so the other device offers "continue from …". Paragraphs before the
cursor are dimmed. Tapping a paragraph and pressing **mark read to here**
counts everything up to it as read today and moves the cursor, which is
how reading ahead, or a long sitting to catch up, is recorded.

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

Migrations `033-recovery.sql` and `034-recovery-pages.sql`, package
`dev.grindtrack.recovery` (`domain`, `service`, `api`):

| Table | Row is | Notes |
|---|---|---|
| `recovery_texts` | one imported book | `slot` (`big_book`, `reflection`, `meditation`), `title`, `imported_at`, `paragraph_count`, `word_count` |
| `recovery_paragraphs` | one paragraph of a book read in order | `text_id`, `chapter_no`, `chapter_title`, `seq`, `body`, `words`, `page_label` (as printed: "xvi", "58"), `page_seq` (running, from zero) |
| `recovery_files` | one uploaded file | `slot`, `ordinal`, `filename`, `bytes`, `size`, `uploaded_at` — kept so the parser can run again |
| `recovery_daily_entries` | one dated entry of a book read by date | `text_id`, `month`, `day`, `title`, `body` |
| `recovery_settings` | one row | `read_cursor` (paragraph seq), `pages_per_day` (default 2), `read_last_done` (the carry-over counts from here), `reading_place` (where the reader was opened), `read_throughs`, `meditation_minutes` (last used), `bible_plan_start` |
| `recovery_journal` | one entry | `created_at`, `body`, `spoken` |
| `recovery_sessions` | one meditation | `started_at`, `minutes`, `completed` |
| `recovery_days` | one day's marks | `day` (pk), `read_done`, `read_from`/`read_to` (the part that was read, so it stays on screen after the cursor moved), `meditated` |
| `bible_verses` | one verse | `book` (USFM code), `book_ord`, `chapter`, `verse`, `para`, `text`; seeded once from the resource |
| `recovery_people` | someone to keep in touch with | `name`, `role` (`sponsor`, `prospect`, `friend`), `cadence_days`, `note`, `archived` |
| `recovery_contacts` | one call | `person_id`, `at`, `note` |

No personal text is ever in a migration; the tables are created empty and
filled by you (imports, journal) or by the seed (the Bible).

## API

All under `/api/recovery`, same auth as everything else.

| Method and path | Does |
|---|---|
| `GET /today` | Everything the today view needs: day count, reflection, meditation entry, passage, today's Big Book part, settings, today's marks |
| `POST /read/done` | Marks today read and advances the cursor; a second press changes nothing |
| `POST /read/mark?seq=` | Everything up to that paragraph counts as read, today |
| `POST /read/catch-up` | Forgives the backlog |
| `POST /read/restart` | Cursor back to the first paragraph |
| `GET /book` | The table of contents and where the cursor is |
| `GET /book/chapters/{no}` | A chapter's paragraphs, with the page labels and its neighbours |
| `PUT /book/place` | Where the reader is |
| `PUT /settings` | `pagesPerDay`, `meditationMinutes` |
| `POST /sessions` | Logs a meditation `{minutes, completed}`; only a completed one marks the day |
| `GET /journal?before=` | Entries newest first, 50 at a time |
| `POST /journal` | `{body, spoken}` |
| `DELETE /journal/{id}` | Removes one |
| `GET /people` … `POST /people/{id}/contacts` | The people and their calls; see api.md |
| `GET /library` | The three slots (imported or not, counts, stored files) and the Bible's status |
| `POST /import/{slot}?dryRun=true&title=` | Multipart `files`: the PDFs together, or one text file; returns the report; `dryRun=false` writes and keeps the files |
| `POST /import/{slot}/reparse` | The stored files through the parser again |
| `POST /bible/restart` | Plan starts today |

The exact shapes are in [api.md](api.md#recovery-authenticated).

## What runs when

| Time | Job | Push |
|---|---|---|
| 05:30 | Morning brief (unchanged) | "good morning" line, then the brief |
| **07:55** | `RecoveryReadingScheduler` | One notification, tag `readings`: the reflection's title, the passage reference, and the Big Book pages ("Big Book · pp. 58–59"), for reading together later. Tapping opens the recovery tab. Sent only if at least one of the three exists. |
| 08:00, 18:00 | Todo reminders (unchanged) | |
| **18:00** | `PeopleReminderScheduler` | One notification, tag `people`: who is due a call — the sponsor still to be asked first, then whoever is furthest past their cadence, three names and a count. Sent only while someone is. |

`grindtrack.recovery.readings-cron: "0 55 7 * * *"` and `people-cron: "0 0 18 * * *"` in the app's zone.

## The people

One list: a name, a role (sponsor, a sponsor still to be asked, friend), a
cadence in days, a note. Sorted with the one still to be asked first, then
by who is next due, so the rotation is the top of the list; the today view
shows the top of it when someone is due. Logging a call is a tap and an
optional line about how they are doing; for the one still to be asked,
the first logged call is the asking, and they become a sponsor. People are
archived rather than deleted, because the calls are a record.

## Privacy

The journal and the imported books stay in your database. The assistant's
context carries the day count only; no tool reads the journal, the
reflection or the Big Book. The push at 07:55 carries titles and a verse
reference, not the text.

## The code, for someone reading it

| Piece | Where | What to read it for |
|---|---|---|
| The parsers | `recovery/service/BookParser.java`, `DailyParser.java`, `Blocks.java` | Pure functions from text to structure; every rule above is a test in `BookParserTest` / `DailyParserTest` |
| The PDFs | `PdfText.java`, `PdfBookParser.java` | PDFBox to lines with paragraph marks; the shape of a page to chapters, pages and paragraphs — every rule above is a test in `PdfBookParserTest` |
| The day's part | `ReadingPlanner.java` | Every paragraph that starts on a page owed |
| The Bible | `BibleBooks.java`, `BiblePlan.java`, `BibleSeeder.java`, `BibleService.java` | The canon, the cutting rule, the batched seed, the plan held in memory |
| The number | `Milestones.java` | Day one is the date; the next milestone |
| Everything else | `RecoveryService.java` | The today view, the cursor and the carry-over, the reader, the imports and the stored files, the people, the push lines |
| The pushes | `RecoveryReadingScheduler.java`, `PeopleReminderScheduler.java` + `PushService.Notification.readings` / `peopleToCall` | Same shape as the todo reminder |
| The page | `frontend/src/features/recovery/` | `RecoveryPage` (views and columns), `BookReader` (any chapter, mark read to here), `PeoplePanel`, `MeditationTimer` (+ `bell.ts`), `JournalComposer` (the ask tab's composer with `useSpeech`), `LibraryPanel` |

## Decisions taken

- **Colour:** seafoam (`--rec: #9ad8c8`), used only on this tab.
- **Where it lives:** the last visible button of the bottom bar; "more"
  moves to a small button in the header.
- **Meditation:** any length. The chips are the last lengths used; the
  last chip takes a number.
- **Translation:** WEB, unless you say KJV.
- **Pages, not minutes:** two a day, as the sponsor said; a day's part ends
  at the end of the paragraph that starts on the last page owed.
- **Carry-over uncapped** in the reading; "catch up from here" is the
  escape hatch. (The count is capped at a year in code so a returning
  reader is not asked for a thousand pages.)
