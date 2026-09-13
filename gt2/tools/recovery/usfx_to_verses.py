#!/usr/bin/env python3
"""Turn a USFX Bible (as published by eBible.org and mirrored in
seven1m/open-bibles) into the compact verse file the app seeds from.

    python3 usfx_to_verses.py eng-web.usfx.xml > web.jsonl
    gzip -9 web.jsonl

Output: one JSON array per line, [book, chapter, verse, paragraph, text]
where `book` is the USFM code (GEN … REV), `paragraph` is 1 when the
verse opens a new paragraph or poetry stanza, and `text` is the verse
with footnotes and cross-references removed; lines of poetry are kept
apart by a newline inside the verse. Only the 66 books of the
Protestant canon are kept; the deuterocanon in the source is skipped.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET

CANON = ("GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB "
         "PSA PRO ECC SNG ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP "
         "HAG ZEC MAL MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI "
         "2TI TIT PHM HEB JAS 1PE 2PE 1JN 2JN 3JN JUD REV").split()
SKIP = {"f", "x", "fe", "vp", "toc", "h", "id", "ide", "rem", "cl", "cp"}
BLOCK = {"p", "b", "d", "s", "mt", "ms", "li", "pi", "m", "nb"}


def main(path):
    tree = ET.parse(path)
    out = []
    names = {}
    for book in tree.getroot().iter("book"):
        code = book.get("id")
        if code not in CANON:
            continue
        for toc in book.iter("toc"):
            if toc.get("level") == "2":
                names[code] = (toc.text or "").strip()
        state = {"chapter": 0, "verse": None, "buf": [], "para": 1}

        def flush():
            if state["verse"] is None:
                return
            text = "".join(state["buf"])
            text = re.sub(r"[ \t\r]+", " ", text)
            text = re.sub(r" ?\n[ \n]*", "\n", text).strip()
            if text:
                out.append([code, state["chapter"], state["verse"], state["para"], text])
            state["verse"] = None
            state["buf"] = []
            state["para"] = 0

        def walk(el):
            tag = el.tag
            if tag in SKIP:
                return
            if tag == "c":
                flush()
                state["chapter"] = int(el.get("id"))
                state["para"] = 1
            elif tag == "v":
                flush()
                state["verse"] = int(re.match(r"\d+", el.get("id")).group())
            elif tag == "ve":
                flush()
            elif tag in BLOCK and state["verse"] is None:
                state["para"] = 1
            elif tag in ("q", "b") and state["verse"] is not None and state["buf"]:
                state["buf"].append("\n")
            if tag not in ("v", "ve", "c") and el.text and state["verse"] is not None:
                state["buf"].append(el.text)
            for child in el:
                walk(child)
                if child.tail and state["verse"] is not None:
                    state["buf"].append(child.tail)

        walk(book)
        flush()
    for row in out:
        print(json.dumps(row, ensure_ascii=False))
    print(json.dumps({"books": {c: names[c] for c in CANON}}), file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1])
