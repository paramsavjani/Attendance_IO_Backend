package com.attendanceio.api.repository.college

import com.attendanceio.api.model.college.DMAcademicCalendarEntry
import com.attendanceio.api.model.college.DMCampusEvent
import com.attendanceio.api.model.college.DMClub
import com.attendanceio.api.model.college.DMFaculty
import com.attendanceio.api.model.college.DMPlacementRecruiter
import com.attendanceio.api.model.college.DMPlacementStat
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface ClubRepository : MongoRepository<DMClub, String>

interface CampusEventRepository : MongoRepository<DMCampusEvent, String> {
    fun findByEndGreaterThanEqualAndStartLessThanEqualOrderByStartAsc(from: Instant, to: Instant): List<DMCampusEvent>
}

interface FacultyRepository : MongoRepository<DMFaculty, String>

interface AcademicCalendarRepository : MongoRepository<DMAcademicCalendarEntry, String> {
    fun findByAcademicYearOrderByTermAscSrNoAsc(academicYear: String): List<DMAcademicCalendarEntry>
}

interface PlacementStatRepository : MongoRepository<DMPlacementStat, String> {
    fun findBySeasonOrderByLevelAsc(season: String): List<DMPlacementStat>
}

interface PlacementRecruiterRepository : MongoRepository<DMPlacementRecruiter, String>

interface HolidayRepository : MongoRepository<com.attendanceio.api.model.college.DMHoliday, String>

interface StaffContactRepository : MongoRepository<com.attendanceio.api.model.college.DMStaffContact, String>

interface CurriculumRepository : MongoRepository<com.attendanceio.api.model.college.DMCurriculum, String>

interface InstituteCommitteeRepository : MongoRepository<com.attendanceio.api.model.college.DMInstituteCommittee, String>

interface ScholarshipRepository : MongoRepository<com.attendanceio.api.model.college.DMScholarship, String>

interface PlacementEventRepository : MongoRepository<com.attendanceio.api.model.college.DMPlacementEvent, String>

interface CampusServiceRepository : MongoRepository<com.attendanceio.api.model.college.DMCampusService, String>

interface ProgramRepository : MongoRepository<com.attendanceio.api.model.college.DMProgram, String>
