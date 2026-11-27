#!/usr/bin/env python3
"""
Fetch DAIICT institute attendance and update institute_attendance.json.
"""

from __future__ import annotations

import argparse
import json
import re
import warnings
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable, List, Optional, Sequence
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from requests.packages.urllib3.exceptions import InsecureRequestWarning


DEFAULT_SOURCE_URL = (
    "https://laboratory.daiict.ac.in/attendance/AY2026-2027%20Autumn%20Semester%20attendance.html"
)
DEFAULT_OUTPUT_PATH = (
    Path(__file__).resolve().parents[1]
    / "src"
    / "main"
    / "resources"
    / "data"
    / "institute_attendance.json"
)

ROLL_NUMBER_RE = re.compile(r"^\d{6,}$")
SHEET_LINK_RE = re.compile(r'href="([^"]*sheet\d+\.htm)"', re.IGNORECASE)
CUTOFF_RE = re.compile(r"till\s+(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+)\s+(\d{4})", re.IGNORECASE)
MONTHS = {
    "january": 1,
    "february": 2,
    "march": 3,
    "april": 4,
    "may": 5,
    "june": 6,
    "july": 7,
    "august": 8,
    "september": 9,
    "october": 10,
    "november": 11,
    "december": 12,
}


@dataclass
class AttendanceRecord:
    subject_code: str
    roll_number: str
    student_name: str
    total_classes: int
    present_classes: int
    absent_classes: int
    cutoff_date: Optional[str] = None

    def to_json(self) -> dict:
        out = {
            "subjectCode": self.subject_code,
            "rollNumber": self.roll_number,
            "studentName": self.student_name,
            "totalClasses": self.total_classes,
            "presentClasses": self.present_classes,
            "absentClasses": self.absent_classes,
        }
        if self.cutoff_date:
            out["cutoffDate"] = self.cutoff_date
        return out


def normalize_text(value: str) -> str:
    return " ".join(value.replace("\xa0", " ").split())


def cleaned_row_cells(row) -> List[str]:
    cells = [normalize_text(cell.get_text(" ", strip=True)) for cell in row.find_all(["td", "th"])]
    return [cell for cell in cells if cell]


def resolve_month(month_name: str) -> Optional[int]:
    """Accept both full month names ("October") and abbreviations ("Oct", "Sept")."""
    if month_name in MONTHS:
        return MONTHS[month_name]
    matches = {number for name, number in MONTHS.items() if name.startswith(month_name)}
    return matches.pop() if len(matches) == 1 else None


def parse_cutoff_date(sheet_soup: BeautifulSoup) -> Optional[str]:
    text = normalize_text(sheet_soup.get_text(" ", strip=True))
    match = CUTOFF_RE.search(text)
    if not match:
        return None

    day = int(match.group(1))
    month_name = match.group(2).lower()
    year = int(match.group(3))
    month = resolve_month(month_name)
    if not month:
        return None

    return datetime(year=year, month=month, day=day).strftime("%Y-%m-%d")


def extract_sheet_links(index_html: str, index_url: str) -> List[str]:
    seen = set()
    links: List[str] = []
    for raw_link in SHEET_LINK_RE.findall(index_html):
        full_link = urljoin(index_url, raw_link)
        if full_link not in seen:
            seen.add(full_link)
            links.append(full_link)
    return links


def find_attendance_header_row(rows: Sequence) -> Optional[int]:
    for idx, row in enumerate(rows):
        cells = cleaned_row_cells(row)
        if not cells:
            continue
        has_subject = any(cell == "Subject" for cell in cells)
        has_roll = any("Roll" in cell for cell in cells)
        has_name = any(cell == "Name" for cell in cells)
        if has_subject and has_roll and has_name:
            return idx
    return None


def parse_records_from_sheet(sheet_soup: BeautifulSoup) -> Iterable[AttendanceRecord]:
    rows = sheet_soup.find_all("tr")
    header_idx = find_attendance_header_row(rows)
    if header_idx is None:
        return []

    records: List[AttendanceRecord] = []
    current_subject: Optional[str] = None

    for row in rows[header_idx + 1 :]:
        cells = cleaned_row_cells(row)
        if not cells:
            continue

        if len(cells) >= 8 and not ROLL_NUMBER_RE.match(cells[0]):
            current_subject = cells[0]
            roll, name, total, present, absent = cells[1], cells[2], cells[3], cells[4], cells[5]
        elif len(cells) >= 7 and current_subject and ROLL_NUMBER_RE.match(cells[0]):
            roll, name, total, present, absent = cells[0], cells[1], cells[2], cells[3], cells[4]
        else:
            continue

        if not ROLL_NUMBER_RE.match(roll):
            continue

        try:
            total_i = int(float(total))
            present_i = int(float(present))
            absent_i = int(float(absent))
        except ValueError:
            continue

        records.append(
            AttendanceRecord(
                subject_code=current_subject,
                roll_number=roll,
                student_name=name,
                total_classes=total_i,
                present_classes=present_i,
                absent_classes=absent_i,
            )
        )

    return records


# ---- Per-subject layout (from Nov 2026): the index links one workbook per subject ("AYA2627/CT204-A.htm"),
# each with a sheet whose header is "Roll No | Name | <dd-Mon-yyyy n>... | Total Class | Present | Present(%) |
# Absent | Absent(%)". There is no "Till <date>" line and subjects are updated on different days, so every
# record carries its own cutoff: the last lecture date in that subject's sheet.

SUBJECT_LINK_RE = re.compile(r'<a\s+href="([^"]+\.html?)"\s*>\s*([^<]+?)\s*</a>', re.IGNORECASE)
LECTURE_DATE_RE = re.compile(r"^(\d{2})-([A-Za-z]{3})-(\d{4})")

# Link texts that differ from the codes the sync understands ("PC1(X)" resolves to the subject "PC1-X").
SUBJECT_CODE_FIXES = {
    "PC1-EVD": "PC1(EVD)",
    "PC1-ICT": "PC1(ICT)",
    "PC1-MNC": "PC1(MNC)",
    "PC1CS-AI": "PC1(CS-AI)",
    "PCECE-AI": "PC1(ECE-AI)",
    "PCICT-CS": "PC1(ICT&CS)",
}


def lecture_date(cell: str) -> Optional[str]:
    match = LECTURE_DATE_RE.match(cell)
    if not match:
        return None
    month = resolve_month(match.group(2).lower())
    if not month:
        return None
    return datetime(int(match.group(3)), month, int(match.group(1))).strftime("%Y-%m-%d")


def parse_subject_sheet(soup: BeautifulSoup, subject_code: str) -> List[AttendanceRecord]:
    records: List[AttendanceRecord] = []
    last_date: Optional[str] = None
    for row in soup.find_all("tr"):
        cells = [normalize_text(c.get_text(" ", strip=True)) for c in row.find_all(["td", "th"])]
        if cells and cells[0].startswith("Roll"):
            dates = [d for d in (lecture_date(c) for c in cells) if d]
            last_date = max(dates) if dates else last_date
            continue
        cells = [c for c in cells if c]
        if len(cells) < 7 or not ROLL_NUMBER_RE.match(cells[0]):
            continue
        # the last five cells are Total, Present, Present(%), Absent, Absent(%)
        try:
            total, present, absent = int(float(cells[-5])), int(float(cells[-4])), int(float(cells[-2]))
        except ValueError:
            continue
        records.append(AttendanceRecord(subject_code, cells[0], cells[1], total, present, absent, None))
    for record in records:
        record.cutoff_date = last_date
    return [r for r in records if r.cutoff_date]


def fetch_per_subject_payload(source_url: str, index_html: str, session: requests.Session) -> Optional[dict]:
    links: List[tuple] = []
    seen = set()
    for href, text in SUBJECT_LINK_RE.findall(index_html):
        url = urljoin(source_url, href)
        if url in seen:
            continue
        seen.add(url)
        code = normalize_text(text)
        links.append((SUBJECT_CODE_FIXES.get(code, code), url))
    if not links:
        return None

    all_records: List[AttendanceRecord] = []
    for code, url in links:
        workbook = session.get(url, timeout=30, verify=False)
        workbook.raise_for_status()
        sheets = extract_sheet_links(workbook.text, url) or [url]
        for sheet in sheets:
            response = session.get(sheet, timeout=30, verify=False)
            response.raise_for_status()
            soup = BeautifulSoup(response.content.decode("windows-1252", "replace"), "html.parser")
            all_records.extend(parse_subject_sheet(soup, code))

    if not all_records:
        raise RuntimeError("No attendance rows found in the subject sheets.")
    return {
        "cutoffDate": max(r.cutoff_date for r in all_records),
        "scrapedAt": datetime.now().replace(microsecond=0).isoformat(),
        "totalSubjects": len({r.subject_code for r in all_records}),
        "totalRecords": len(all_records),
        "records": [r.to_json() for r in all_records],
    }


def carry_over_missing(payload: dict, previous_path: Path) -> List[str]:
    """Subjects the new page no longer lists keep their previous figures, each with its old cutoff."""
    if not previous_path.exists():
        return []
    previous = json.loads(previous_path.read_text(encoding="utf-8"))
    present = {r["subjectCode"] for r in payload["records"]}
    kept = [dict(r, cutoffDate=r.get("cutoffDate") or previous["cutoffDate"])
            for r in previous.get("records", []) if r["subjectCode"] not in present]
    payload["records"].extend(kept)
    payload["totalRecords"] = len(payload["records"])
    payload["totalSubjects"] = len({r["subjectCode"] for r in payload["records"]})
    return sorted({r["subjectCode"] for r in kept})


def fetch_and_build_payload(source_url: str) -> dict:
    warnings.simplefilter("ignore", InsecureRequestWarning)

    session = requests.Session()
    index_response = session.get(source_url, timeout=30, verify=False)
    index_response.raise_for_status()
    index_html = index_response.text

    per_subject = fetch_per_subject_payload(source_url, index_html, session)
    if per_subject:
        return per_subject

    sheet_links = extract_sheet_links(index_html=index_html, index_url=source_url)
    if not sheet_links:
        raise RuntimeError("No sheet links found in source page.")

    all_records: List[AttendanceRecord] = []
    subject_codes = set()
    cutoff_date: Optional[str] = None

    for sheet_link in sheet_links:
        sheet_response = session.get(sheet_link, timeout=30, verify=False)
        sheet_response.raise_for_status()
        soup = BeautifulSoup(sheet_response.text, "html.parser")

        if cutoff_date is None:
            cutoff_date = parse_cutoff_date(soup)

        for record in parse_records_from_sheet(soup):
            all_records.append(record)
            subject_codes.add(record.subject_code)

    if not cutoff_date:
        raise RuntimeError("Could not parse cutoff date from attendance sheets.")

    payload = {
        "cutoffDate": cutoff_date,
        "scrapedAt": datetime.now().replace(microsecond=0).isoformat(),
        "totalSubjects": len(subject_codes),
        "totalRecords": len(all_records),
        "records": [record.to_json() for record in all_records],
    }
    return payload


def write_json(payload: dict, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8") as output_file:
        json.dump(payload, output_file, indent=2)
        output_file.write("\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch DAIICT attendance page and update institute_attendance.json"
    )
    parser.add_argument(
        "--source-url",
        default=DEFAULT_SOURCE_URL,
        help="Attendance index URL (default: AY2026-27 Semester I page).",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
        help="Output JSON file path.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    payload = fetch_and_build_payload(source_url=args.source_url)
    kept = carry_over_missing(payload, args.output)
    if kept:
        print(f"Not on the new page, kept from the previous file: {', '.join(kept)}")
    write_json(payload=payload, output_path=args.output)
    print(
        f"Updated {args.output} with {payload['totalRecords']} records "
        f"across {payload['totalSubjects']} subjects (cutoff {payload['cutoffDate']})."
    )


if __name__ == "__main__":
    main()
