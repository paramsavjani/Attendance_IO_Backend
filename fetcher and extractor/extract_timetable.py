import csv
import json
import re

def extract_schedule(csv_path):
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        rows = list(reader)

    # Configuration
    # Day Columns (0-indexed based on my analysis: start + stride*day)
    # Mon: 3, Tue: 10, Wed: 17, Thu: 24, Fri: 31
    # Each block is 7 columns wide
    days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
    day_stride = 7
    day_start_col = 3
    
    # Internal state
    current_time = ""
    current_batch = ""
    
    schedule = [] # List of dicts
    
    # Iterate from Row 2 (index 1) which describes slots? 
    # Actually Row 2 (index 1) is "8:00 - 8:50..."
    # The data starts from there.
    
    for i in range(1, len(rows)):
        row = rows[i]
        
        # Determine Time
        # Time is in Col 0. Merged cells mean it might be empty for subsequent rows
        # But wait, looking at the CSV:
        # Row 2: "8:00 - 8:50"
        # Row 3: "" (Empty)
        # So we forward fill Time.
        
        # However, we must detect if this is a separator row or something else.
        # "8:00 - 8:50" is a Time.
        # "Time" (Row 1) is header.
        
        # Warning: Sometimes Col 0 contains other text? 
        # Check specific formats if needed, but for now assume any non-empty col 0 is a time update
        if len(row) > 0 and row[0].strip():
            current_time = row[0].strip()
            # If a new time starts, we might reset batch?
            # Usually Batch is stated explicitly in the new time block if relevant.
            # But let's check Row 3 calls Batch "BTech Sem-II...".
            # Row 79 (9:00...) Time row.
            # Row 80 Batch "BTech Sem-II...".
            # So yes, Batch is usually reset or restated. 
            # Safest is to keep current_batch until overwritten, but verify if "Time" row clears it.
            # In excel, Time row is usually one row spanning all cols?
            # Row 2 col 1 is empty. Batch is empty.
            # So if Time row has empty Batch, we effectively have no batch *for that row*.
            # But subsequent rows will set Batch.
            # So valid strategy: Set current_batch to row[1] if row[1] not empty.
            pass
            
        if len(row) > 1 and row[1].strip():
            current_batch = row[1].strip()
            
        # Logic to skip rows that are just headers or empty?
        # If both current_batch is invalid and this is not a Time Definition row...
        # Row 2 has Time "8:00..." but Batch empty. We shouldn't process lectures here because there are none.
        # Lecture blocks are usually populated.
        
        # We can just try to parse the day blocks. If they are empty, nothing happens.
        
        for day_idx, day_name in enumerate(days):
            start_idx = day_start_col + (day_idx * day_stride)
            end_idx = start_idx + day_stride
            
            # Ensure row has enough columns
            # Friday only has 6 columns (31-36) instead of 7, so we need to check
            # if we have at least the start column and enough for Room (index 5)
            # We need at least start_idx + 6 columns (0-5 in block = Room)
            if len(row) < start_idx + 6:
                continue
                
            # Extract block, but only up to available columns (Friday has 6, others have 7)
            block = row[start_idx:min(end_idx, len(row))]
            
            # Analyze block
            # 0: Code
            # 1: Name
            # 2: Structure
            # 3: Type
            # 4: Faculty
            # 5: Room
            # 6: Extra
            
            # Check if valid subject
            # Subject code usually alphanumeric, e.g., HM106, IT205.
            # Filter out "Slot-1", "Free-Slot" which appear in the Time Row (Row 2).
            # Row 2 contains "Slot-1" in column 3 (Mon).
            # We want to ignore these.
            # Subject codes usually have numbers. "Slot-1" has number too.
            # But "Slot-1" is usually in the "Header" row for the time.
            
            # Heuristic: If row[0] was the Time row (e.g. "8:00 - 8:50"), then the content in blocks are Slot Names, not Subjects.
            # So if row[0] is not empty (and looks like time), we treat it as Time Header and DO NOT parse subjects from it.
            # Exception: unless the Time and Subject are on the same line?
            # Row 2: "8:00 - 8:50,,,Slot-1..." -> These are slot names.
            # Row 3: ",BTech..." -> Time is empty (inherited). This has subjects.
            
            # So: If row[0] was just updated (is non-empty), skip parsing slots for this row.
            # This relies on the file structure where Time is on a separate header row.
            is_time_row = (len(row) > 0 and row[0].strip() != "")
            if is_time_row:
                continue
                
            if not current_batch:
                continue
                
            subject_code = block[0].strip()
            subject_name = block[1].strip() if len(block) > 1 else ""
            
            if not subject_code:
                continue
                
            # Valid subject?
            # Ignore if it looks like garbage or empty
            
            # Structure Extraction
            # We want: Subject Code, Name, Time, Day, Room, Batch, Faculty
            
            lecture_info = {
                "Subject Code": subject_code,
                "Subject Name": subject_name,
                "Day": day_name,
                "Time": current_time,
                "Batch": current_batch,
                "Room": block[5].strip() if len(block) > 5 else "",
                "Faculty": block[4].strip() if len(block) > 4 else "",
                "Type": block[3].strip() if len(block) > 3 else "" # Core/Elective
            }
            
            schedule.append(lecture_info)

    return schedule

def main():
    import os
    script_dir = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(script_dir, "Lecture_Time_Table_Win'26_v5.xlsx - Time-Table.csv")
    data = extract_schedule(path)
    
    # Organize by Subject
    # User wants: Keys based on Subject Code.
    # If unique: "CODE"
    # If duplicates (Sections): "CODE-A", "CODE-B"
    # Refinement: Unique entries for Day, Time, Room only. Remove Batch, Faculty.
    
    # Pass 1: Group by Code -> Name -> Set of Slots
    grouped_data = {}
    
    for item in data:
        code = item["Subject Code"]
        name = item["Subject Name"]
        if code not in grouped_data:
            grouped_data[code] = {}
        if name not in grouped_data[code]:
            grouped_data[code][name] = set()
            
        grouped_data[code][name].add((
            item["Day"],
            item["Time"],
            item["Room"]
        ))
        
    # Helper to sort slots
    day_order = {d: i for i, d in enumerate(["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"])}
    def sort_key(slot):
        # slot is dict
        return (day_order.get(slot["Day"], 99), slot["Time"])

    # Pass 2: Generate Final Dict with Correct Keys
    final_subjects = {}
    
    for code, name_groups in grouped_data.items():
        if len(name_groups) == 1:
            # Unique Subject Code
            name = list(name_groups.keys())[0]
            val_set = name_groups[name]
            
            # Convert set to list of dicts
            sorted_list = [{"Day": d, "Time": t, "Room": r} for (d,t,r) in val_set]
            sorted_list.sort(key=sort_key)
            
            final_subjects[code] = sorted_list
        else:
            # Duplicate Subject Code (likely Sections)
            for name, val_set in name_groups.items():
                suffix = ""
                lower_name = name.lower()
                if "(sec a)" in lower_name or "(section a)" in lower_name:
                    suffix = "-A"
                elif "(sec b)" in lower_name or "(section b)" in lower_name:
                    suffix = "-B"
                else:
                    # Fallback if multiple variants but not explicitly Sec A/B
                    # Just append full name to differentiate?
                    # Or keep it as Code if ignoring collisions (bad idea)
                    # Let's try to keep it unique.
                    suffix = f"-{name}"
                
                key = f"{code}{suffix}"
                
                # Convert set to list of dicts
                sorted_list = [{"Day": d, "Time": t, "Room": r} for (d,t,r) in val_set]
                sorted_list.sort(key=sort_key)
                
                final_subjects[key] = sorted_list

    print(json.dumps(final_subjects, indent=2))

if __name__ == "__main__":
    main()
