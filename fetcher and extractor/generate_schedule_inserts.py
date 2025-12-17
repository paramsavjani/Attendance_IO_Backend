import json
import re

# Defined based on user's time_slots table
VALID_START_TIMES = {
    "08:00:00",
    "09:00:00",
    "10:00:00",
    "11:00:00",
    "12:00:00"
}

def parse_start_time(time_str):
    # Format: "8:00 - 8:50" -> "08:00:00"
    parts = time_str.split('-')
    if not parts:
        return None
    
    start = parts[0].strip()
    
    if ':' not in start:
        return None
    h, m = start.split(':')
    return f"{int(h):02d}:{int(m):02d}:00"

def main():
    json_path = "/home/param/Desktop/new attendance/data.json"
    output_path = "/home/param/Desktop/new attendance/subject_schedule_data.sql"
    
    with open(json_path, 'r') as f:
        data = json.load(f)
        
    with open(output_path, 'w') as f:
        f.write("-- Auto-generated subject schedule data\n")
        f.write("-- Filters: semester_id = 2, Valid Time Slots (08:00-12:00)\n")
        f.write("-- \n")
        
        for subject_code, slots in data.items():
            for slot in slots:
                # Convert Day to Uppercase (MONDAY)
                day_name = slot["Day"].upper()
                
                # Extract Start Time
                start_time = parse_start_time(slot["Time"])
                
                if not start_time:
                    f.write(f"-- WARNING: Invalid time format '{slot['Time']}' for {subject_code}\n")
                    continue
                
                if start_time not in VALID_START_TIMES:
                    f.write(f"-- SKIP: {subject_code} {day_name} at {start_time} (Not in valid slots)\n")
                    continue
                
                # Generate INSERT
                query = f"""
INSERT INTO subject_schedule (subject_id, day_id, slot_id)
SELECT s.id, w.id, t.id
FROM subjects s, week_days w, time_slots t
WHERE s.code = '{subject_code}' AND s.semester_id = 2
  AND w.name = '{day_name}'
  AND t.start_time = '{start_time}'
ON CONFLICT (subject_id, day_id, slot_id) DO NOTHING;
"""
                f.write(query.strip() + "\n")

    print(f"Generated SQL at {output_path}")

if __name__ == "__main__":
    main()
