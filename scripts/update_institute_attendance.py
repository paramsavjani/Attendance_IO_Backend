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
    "https://laboratory.daiict.ac.in/attendance/AY2025-2026%20Winter%20Semester.htm"
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

    def to_json(self) -> dict:
        return {
            "subjectCode": self.subject_code,
            "rollNumber": self.roll_number,
            "studentName": self.student_name,
            "totalClasses": self.total_classes,
            "presentClasses": self.present_classes,
            "absentClasses": self.absent_classes,
        }


def normalize_text(value: str) -> str:
    return " ".join(value.replace("\xa0", " ").split())


def cleaned_row_cells(row) -> List[str]:
    cells = [normalize_text(cell.get_text(" ", strip=True)) for cell in row.find_all(["td", "th"])]
    return [cell for cell in cells if cell]


def parse_cutoff_date(sheet_soup: BeautifulSoup) -> Optional[str]:
    text = normalize_text(sheet_soup.get_text(" ", strip=True))
    match = CUTOFF_RE.search(text)
    if not match:
        return None

    day = int(match.group(1))
    month_name = match.group(2).lower()
    year = int(match.group(3))
    month = MONTHS.get(month_name)
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


def fetch_and_build_payload(source_url: str) -> dict:
    warnings.simplefilter("ignore", InsecureRequestWarning)

    session = requests.Session()
    index_response = session.get(source_url, timeout=30, verify=False)
    index_response.raise_for_status()
    index_html = index_response.text

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
        help="Attendance index URL (default: AY2025-2026 Winter Semester page).",
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
    write_json(payload=payload, output_path=args.output)
    print(
        f"Updated {args.output} with {payload['totalRecords']} records "
        f"across {payload['totalSubjects']} subjects (cutoff {payload['cutoffDate']})."
    )


if __name__ == "__main__":
    main()
