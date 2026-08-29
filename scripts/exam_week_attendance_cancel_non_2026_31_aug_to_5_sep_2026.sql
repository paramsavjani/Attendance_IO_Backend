-- Exam week: mark all lectures, labs, and tutorials as CANCELLED for 31 Aug – 5 Sep 2026
-- for every student whose sid does NOT start with '2026' (i.e. not the 2026-batch,
-- who are the ones sitting exams this week).
-- Run with: psql -U <user> -d <database> -f scripts/exam_week_attendance_cancel_non_2026_31_aug_to_5_sep_2026.sql
-- Or execute in your PostgreSQL client.

-- Dates: 2026-08-31 (Mon), 2026-09-01 (Tue), 2026-09-02 (Wed), 2026-09-03 (Thu),
--        2026-09-04 (Fri), 2026-09-05 (Sat)
-- For each date we insert one attendance row per (student, subject, slot/custom times) from
-- student_timetable (lectures), student_lab_timetable (labs), and student_tutorial_timetable (tutorials)
-- for that weekday, with status CANCELLED, restricted to students whose sid does not start with '2026'.
-- Skips if a row already exists. Rows are inserted with exclude_from_analytics = true so they
-- do not appear in admin/app analytics.

-- ========== 1. Lectures (from student_timetable) ==========

INSERT INTO attendance (
    student_id,
    subject_id,
    lecture_date,
    status,
    source_id,
    time_slot_id,
    custom_start_time,
    custom_end_time,
    is_extra_class,
    exclude_from_analytics,
    created_at,
    updated_at
)
SELECT
    st.student_id,
    st.subject_id,
    d.lecture_date,
    'CANCELLED'::attendance_status_enum,
    'STUDENT'::attendance_source_enum,
    st.slot_id,
    st.custom_start_time,
    st.custom_end_time,
    false,
    true,
    now(),
    now()
FROM (
    SELECT '2026-08-31'::date AS lecture_date, 1::smallint AS day_id
    UNION ALL SELECT '2026-09-01'::date, 2
    UNION ALL SELECT '2026-09-02'::date, 3
    UNION ALL SELECT '2026-09-03'::date, 4
    UNION ALL SELECT '2026-09-04'::date, 5
    UNION ALL SELECT '2026-09-05'::date, 6
) d
JOIN student_timetable st ON st.day_id = d.day_id
JOIN student s ON s.id = st.student_id
WHERE s.sid NOT LIKE '2026%'
  AND NOT EXISTS (
    SELECT 1
    FROM attendance a
    WHERE a.student_id = st.student_id
      AND a.subject_id = st.subject_id
      AND a.lecture_date = d.lecture_date
      AND (a.time_slot_id IS NOT DISTINCT FROM st.slot_id)
      AND (a.custom_start_time IS NOT DISTINCT FROM st.custom_start_time)
      AND (a.custom_end_time IS NOT DISTINCT FROM st.custom_end_time)
);

-- ========== 2. Labs (from student_lab_timetable) ==========
INSERT INTO attendance (
    student_id,
    subject_id,
    lecture_date,
    status,
    source_id,
    time_slot_id,
    custom_start_time,
    custom_end_time,
    is_extra_class,
    exclude_from_analytics,
    created_at,
    updated_at
)
SELECT
    lt.student_id,
    lt.subject_id,
    d.lecture_date,
    'CANCELLED'::attendance_status_enum,
    'STUDENT'::attendance_source_enum,
    lt.slot_id,
    lt.custom_start_time,
    lt.custom_end_time,
    false,
    true,
    now(),
    now()
FROM (
    SELECT '2026-08-31'::date AS lecture_date, 1::smallint AS day_id
    UNION ALL SELECT '2026-09-01'::date, 2
    UNION ALL SELECT '2026-09-02'::date, 3
    UNION ALL SELECT '2026-09-03'::date, 4
    UNION ALL SELECT '2026-09-04'::date, 5
    UNION ALL SELECT '2026-09-05'::date, 6
) d
JOIN student_lab_timetable lt ON lt.day_id = d.day_id
JOIN student s ON s.id = lt.student_id
WHERE s.sid NOT LIKE '2026%'
  AND NOT EXISTS (
    SELECT 1
    FROM attendance a
    WHERE a.student_id = lt.student_id
      AND a.subject_id = lt.subject_id
      AND a.lecture_date = d.lecture_date
      AND (a.time_slot_id IS NOT DISTINCT FROM lt.slot_id)
      AND (a.custom_start_time IS NOT DISTINCT FROM lt.custom_start_time)
      AND (a.custom_end_time IS NOT DISTINCT FROM lt.custom_end_time)
);

-- ========== 3. Tutorials (from student_tutorial_timetable) ==========
INSERT INTO attendance (
    student_id,
    subject_id,
    lecture_date,
    status,
    source_id,
    time_slot_id,
    custom_start_time,
    custom_end_time,
    is_extra_class,
    exclude_from_analytics,
    created_at,
    updated_at
)
SELECT
    tt.student_id,
    tt.subject_id,
    d.lecture_date,
    'CANCELLED'::attendance_status_enum,
    'STUDENT'::attendance_source_enum,
    tt.slot_id,
    tt.custom_start_time,
    tt.custom_end_time,
    false,
    true,
    now(),
    now()
FROM (
    SELECT '2026-08-31'::date AS lecture_date, 1::smallint AS day_id
    UNION ALL SELECT '2026-09-01'::date, 2
    UNION ALL SELECT '2026-09-02'::date, 3
    UNION ALL SELECT '2026-09-03'::date, 4
    UNION ALL SELECT '2026-09-04'::date, 5
    UNION ALL SELECT '2026-09-05'::date, 6
) d
JOIN student_tutorial_timetable tt ON tt.day_id = d.day_id
JOIN student s ON s.id = tt.student_id
WHERE s.sid NOT LIKE '2026%'
  AND NOT EXISTS (
    SELECT 1
    FROM attendance a
    WHERE a.student_id = tt.student_id
      AND a.subject_id = tt.subject_id
      AND a.lecture_date = d.lecture_date
      AND (a.time_slot_id IS NOT DISTINCT FROM tt.slot_id)
      AND (a.custom_start_time IS NOT DISTINCT FROM tt.custom_start_time)
      AND (a.custom_end_time IS NOT DISTINCT FROM tt.custom_end_time)
);

-- Optional: show how many rows were inserted (run in same session after the insert)
-- SELECT lecture_date, status, COUNT(*) FROM attendance
-- WHERE lecture_date BETWEEN '2026-08-31' AND '2026-09-05' AND status = 'CANCELLED'
-- GROUP BY lecture_date, status;
