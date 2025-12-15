-- =====================================================
-- SEMESTER & ATTENDANCE SYSTEM SCHEMA
-- =====================================================

-- =====================================================
-- CREATE ENUM TYPES
-- =====================================================
-- Drop existing types if they exist (for idempotency)
DROP TYPE IF EXISTS semester_type_enum CASCADE;
DROP TYPE IF EXISTS attendance_status_enum CASCADE;
DROP TYPE IF EXISTS attendance_source_enum CASCADE;

-- Create ENUM types
CREATE TYPE semester_type_enum AS ENUM ('SUMMER', 'WINTER');
CREATE TYPE attendance_status_enum AS ENUM ('PRESENT', 'ABSENT', 'LEAVE');
CREATE TYPE attendance_source_enum AS ENUM ('INSTITUTE', 'STUDENT');

-- Add comments to ENUM types
COMMENT ON TYPE semester_type_enum IS 'Semester type: SUMMER or WINTER';
COMMENT ON TYPE attendance_status_enum IS 'Attendance status: PRESENT, ABSENT, or LEAVE';
COMMENT ON TYPE attendance_source_enum IS 'Attendance source: INSTITUTE or STUDENT';

-- =====================================================
-- 1. SEMESTERS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS semesters (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    year INTEGER NOT NULL,
    type semester_type_enum NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(year, type) -- One semester per year and type
);

CREATE INDEX idx_semesters_year ON semesters(year);
CREATE INDEX idx_semesters_type ON semesters(type);
CREATE INDEX idx_semesters_is_active ON semesters(is_active);

-- =====================================================
-- 2. SUBJECTS TABLE
-- =====================================================
-- Note: code and semester_id together form a unique constraint
-- This allows the same subject code in different semesters
CREATE TABLE IF NOT EXISTS subjects (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    semester_id BIGINT NOT NULL,
    FOREIGN KEY (semester_id) REFERENCES semesters(id) ON DELETE CASCADE,
    UNIQUE(code, semester_id) -- Subject code is unique per semester
);

CREATE INDEX idx_subjects_code ON subjects(code);
CREATE INDEX idx_subjects_semester_id ON subjects(semester_id);
CREATE INDEX idx_subjects_code_semester ON subjects(code, semester_id);

-- =====================================================
-- 3. ATTENDANCE TABLE
-- =====================================================
-- Note: semester_id is NOT stored here because it can be derived from subject.semester_id
CREATE TABLE IF NOT EXISTS attendance (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    lecture_date DATE NOT NULL,
    status attendance_status_enum NOT NULL,
    source_id attendance_source_enum, -- Nullable
    FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    UNIQUE(student_id, subject_id, lecture_date) -- One attendance record per student per subject per date
);

CREATE INDEX idx_attendance_student_id ON attendance(student_id);
CREATE INDEX idx_attendance_subject_id ON attendance(subject_id);
CREATE INDEX idx_attendance_lecture_date ON attendance(lecture_date);
CREATE INDEX idx_attendance_student_subject_date ON attendance(student_id, subject_id, lecture_date);
CREATE INDEX idx_attendance_source_id ON attendance(source_id);
CREATE INDEX idx_attendance_status ON attendance(status);

-- =====================================================
-- HELPER VIEW: Attendance with Semester Info
-- =====================================================
-- This view joins attendance with subject and semester to get semester info
CREATE OR REPLACE VIEW attendance_with_semester AS
SELECT 
    a.id,
    a.student_id,
    a.subject_id,
    a.lecture_date,
    a.status,
    a.source_id,
    a.created_at,
    a.updated_at,
    s.code AS subject_code,
    s.name AS subject_name,
    sem.id AS semester_id,
    sem.year AS semester_year,
    sem.type AS semester_type
FROM attendance a
JOIN subjects s ON a.subject_id = s.id
JOIN semesters sem ON s.semester_id = sem.id;

-- =====================================================
-- 4. INSTITUTE ATTENDANCE TABLE
-- =====================================================
-- Tracks the last updated official attendance from institute for each student-subject
-- This stores cumulative attendance data (like from JSON imports)
CREATE TABLE IF NOT EXISTS institute_attendance (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    cutoff_date DATE NOT NULL, -- Date till which attendance is calculated
    total_classes INTEGER NOT NULL DEFAULT 0,
    present_classes INTEGER NOT NULL DEFAULT 0,
    absent_classes INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    UNIQUE(student_id, subject_id, cutoff_date) -- One record per student per subject per cutoff date
);

CREATE INDEX idx_institute_attendance_student_id ON institute_attendance(student_id);
CREATE INDEX idx_institute_attendance_subject_id ON institute_attendance(subject_id);
CREATE INDEX idx_institute_attendance_cutoff_date ON institute_attendance(cutoff_date);
CREATE INDEX idx_institute_attendance_student_subject ON institute_attendance(student_id, subject_id);
CREATE INDEX idx_institute_attendance_subject_cutoff ON institute_attendance(subject_id, cutoff_date);

-- =====================================================
-- 5. STUDENT_SUBJECT TABLE
-- =====================================================
-- Maps students to subjects they are enrolled in
CREATE TABLE IF NOT EXISTS student_subject (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    student_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    minimum_criteria INTEGER,
    FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE,
    FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    UNIQUE(student_id, subject_id) -- One enrollment per student per subject
);

CREATE INDEX idx_student_subject_student_id ON student_subject(student_id);
CREATE INDEX idx_student_subject_subject_id ON student_subject(subject_id);
CREATE INDEX idx_student_subject_student_subject ON student_subject(student_id, subject_id);

-- =====================================================
-- COMMENTS
-- =====================================================
COMMENT ON TABLE semesters IS 'Stores semesters with year and type (SUMMER/WINTER)';
COMMENT ON TABLE subjects IS 'Stores subjects/courses linked to semesters. Code is unique per semester.';
COMMENT ON TABLE attendance IS 'Stores individual attendance records. Semester can be accessed via subject.semester_id';
COMMENT ON TABLE institute_attendance IS 'Tracks official cumulative attendance from institute for each student-subject. Stores data till cutoff_date.';
COMMENT ON TABLE student_subject IS 'Maps students to subjects they are enrolled in. Represents student-subject enrollment relationship.';
COMMENT ON VIEW attendance_with_semester IS 'View that joins attendance with subject and semester information';

