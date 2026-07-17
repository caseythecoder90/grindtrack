"""Converts the 3-Year Engineering Career Plan workbook into plan.json for POST /api/plan/import.

Usage:
    pip install openpyxl
    python xlsx_to_plan_json.py <path-to-plan.xlsx> [output.json]

The output contains personal plan content — plan.json is gitignored; never commit it.
Sheet layout expectations match the workbook: Roadmap, Cert Tracker, Protocols & Security,
Books, Projects, Milestones (trackables) + Overview, Stripe Target, Weekly Schedule,
Resources (reference, stored as row JSON and rendered generically).
"""

import json
import re
import sys
from datetime import date, datetime

import openpyxl

MONTHS = {m: i + 1 for i, m in enumerate(
    ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"])}
PLAN_START = (2026, 7)  # Q1 = Jul-Sep 2026

STATUS_MAP = {
    "not started": "not_started",
    "in progress": "in_progress",
    "done": "done",
}


def cell(v):
    if v is None:
        return ""
    if isinstance(v, (datetime, date)):
        return f"{v:%b %Y}"
    return str(v).strip()


def status_of(v):
    return STATUS_MAP.get(cell(v).lower(), "not_started")


def month_label_to_date(label):
    """'Aug 2026' -> '2026-08-01'."""
    m = re.match(r"([A-Z][a-z]{2})\w*\s+(\d{4})", label)
    if not m or m.group(1) not in MONTHS:
        return None
    return f"{int(m.group(2)):04d}-{MONTHS[m.group(1)]:02d}-01"


def qtr_to_date(qtr):
    """End month of quarter N (Q1 = Jul-Sep 2026) -> ISO date, for sorting."""
    y, m = PLAN_START
    total = (m - 1) + (qtr - 1) * 3 + 2
    return f"{y + total // 12:04d}-{total % 12 + 1:02d}-01"


def parse_when(label):
    """'Q2-Q4 (Y1)', 'Now - Q3 (Y1)', 'Q5 (Y2)', 'Anytime (Y2+)' -> (year, qtr, date)."""
    year = None
    m = re.search(r"\(Y(\d)", label)
    if m:
        year = int(m.group(1))
    quarters = [int(q) for q in re.findall(r"Q(\d+)", label)]
    qtr = quarters[0] if quarters else None
    target = qtr_to_date(max(quarters)) if quarters else None
    return year, qtr, target


def year_of(phase):
    m = re.search(r"Year (\d)", phase)
    return int(m.group(1)) if m else None


def rows_of(ws):
    out = []
    for row in ws.iter_rows():
        values = [cell(c.value) for c in row]
        while values and values[-1] == "":
            values.pop()
        out.append(values)
    return out


def join_details(pairs):
    return "\n\n".join(f"{label}: {text}" for label, text in pairs if text)


def main():
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else "plan.json"
    wb = openpyxl.load_workbook(src, data_only=True)
    items, quarters, reference = [], [], []
    order = 0

    def add(type_, title, details, target_label, target_date, year, qtr, tier, status, notes=""):
        nonlocal order
        if not title:
            return
        order += 1
        items.append({
            "type": type_, "title": title, "details": details, "targetLabel": target_label,
            "targetDate": target_date, "yearNum": year, "qtr": qtr, "tier": tier,
            "status": status, "notes": notes, "sortOrder": order,
        })

    # --- Milestones: # | Milestone | Target | Phase | Status
    for r in rows_of(wb["Milestones"]):
        if len(r) >= 5 and r[0].isdigit():
            add("milestone", r[1], "", r[2], month_label_to_date(r[2]), year_of(r[3]), None,
                None, status_of(r[4]))

    # --- Cert Tracker: Cert | Target | Prep Window | Hours | Cost | Resource | Status | Notes
    for r in rows_of(wb["Cert Tracker"]):
        if len(r) >= 7 and r[0] and r[0] not in ("Cert", "Totals", "Certs done") \
                and not r[0].startswith("Certification") and not r[0].startswith("Update"):
            details = join_details([
                ("Prep window", r[2]), ("Est. prep hours", r[3]), ("Cost (USD)", r[4]),
                ("Primary resource", r[5]), ("Notes", r[7] if len(r) > 7 else ""),
            ])
            target_date = month_label_to_date(r[1])
            year = None
            if target_date:
                y, m = int(target_date[:4]), int(target_date[5:7])
                year = min(3, max(1, (y - PLAN_START[0]) + (1 if m >= 7 else 0)))
            add("cert", r[0], details, r[1], target_date, year, None, None, status_of(r[6]))

    # --- Protocols & Security: Module | When | Concepts | RFCs | Lab | Ties Into | Status
    for r in rows_of(wb["Protocols & Security"]):
        if len(r) >= 7 and r[0].startswith("M") and " - " in r[0]:
            year, qtr, target = parse_when(r[1])
            details = join_details([
                ("Core concepts", r[2]), ("Key RFCs / specs", r[3]), ("Hands-on lab", r[4]),
                ("Ties into", r[5]),
            ])
            add("module", r[0], details, r[1].replace("\n", " "), target, year, qtr, None,
                status_of(r[6]))

    # --- Books: # | Title | Tier | When | Why | Status
    for r in rows_of(wb["Books"]):
        if len(r) >= 6 and r[0].isdigit():
            year, qtr, target = parse_when(r[3])
            add("book", r[1], join_details([("Why it's on the list", r[4])]), r[3], target,
                year, qtr, r[2], status_of(r[5]))

    # --- Projects: # | Project | When | Type | What | Skills | Status
    for r in rows_of(wb["Projects"]):
        if len(r) >= 7 and r[0].isdigit():
            year, qtr, target = parse_when(r[2])
            details = join_details([("What you'll build / do", r[4]), ("Skills it proves", r[5])])
            add("project", r[1], details, r[2], target, year, qtr, r[3], status_of(r[6]))

    # --- Roadmap: Qtr | Window | Primary | Secondary | Career | Deliverables
    for r in rows_of(wb["Roadmap"]):
        if len(r) >= 6 and re.fullmatch(r"Q\d+", r[0]):
            q = int(r[0][1:])
            quarters.append({
                "qtr": q, "windowLabel": r[1], "yearNum": (q - 1) // 4 + 1,
                "primaryFocus": r[2], "secondaryFocus": r[3], "careerTrack": r[4],
                "deliverables": r[5],
            })

    # --- Reference sheets, stored as raw rows and rendered generically
    for i, (sheet_name, key) in enumerate([
        ("Overview", "overview"), ("Stripe Target", "stripe-target"),
        ("Weekly Schedule", "weekly-schedule"), ("Resources", "resources"),
    ]):
        rows = [r for r in rows_of(wb[sheet_name]) if r]
        title = rows[0][0] if rows and rows[0] else sheet_name
        reference.append({
            "sheet": key, "title": title, "sortOrder": i,
            "contentJson": json.dumps({"rows": rows[1:]}, ensure_ascii=False),
        })

    payload = {"items": items, "quarters": quarters, "reference": reference}
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
    counts = {}
    for it in items:
        counts[it["type"]] = counts.get(it["type"], 0) + 1
    print(f"wrote {dst}: {counts}, {len(quarters)} quarters, {len(reference)} reference sheets")


if __name__ == "__main__":
    main()
