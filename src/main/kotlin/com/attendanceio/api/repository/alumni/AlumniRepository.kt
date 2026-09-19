package com.attendanceio.api.repository.alumni

import com.attendanceio.api.model.alumni.DMAlumni
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AlumniRepository : JpaRepository<DMAlumni, Long> {
    fun findByCompanyIdOrderByBatchDescNameAsc(companyId: Long): List<DMAlumni>

    /**
     * Free-text search over name, company, headline, title and city, with optional exact filters.
     * Nullable params are CAST so Postgres can type them when they are null.
     * Ranking: name match (exact → prefix → contains) beats company match beats headline/city.
     */
    @Query(
        value = """
            SELECT a.* FROM alumni a
            JOIN alumni_company c ON c.id = a.company_id
            WHERE (CAST(:query AS TEXT) IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(COALESCE(a.headline, '')) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(COALESCE(a.title, '')) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(COALESCE(a.city, '')) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%')))
              AND (CAST(:companyId AS BIGINT) IS NULL OR a.company_id = CAST(:companyId AS BIGINT))
              AND (CAST(:batch AS INTEGER) IS NULL OR a.batch = CAST(:batch AS INTEGER))
              AND (CAST(:course AS TEXT) IS NULL OR a.course = CAST(:course AS TEXT))
              AND (CAST(:city AS TEXT) IS NULL OR LOWER(a.city) = LOWER(CAST(:city AS TEXT)))
              AND (:linkedinOnly = FALSE OR a.linkedin_url IS NOT NULL)
            ORDER BY
              CASE
                WHEN CAST(:query AS TEXT) IS NULL THEN 5
                WHEN LOWER(a.name) = LOWER(CAST(:query AS TEXT)) THEN 0
                WHEN LOWER(a.name) LIKE LOWER(CONCAT(CAST(:query AS TEXT), '%')) THEN 1
                WHEN LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%')) THEN 2
                WHEN LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%')) THEN 3
                ELSE 4
              END,
              c.avg_lpa DESC NULLS LAST,
              a.batch DESC NULLS LAST,
              a.name ASC
        """,
        countQuery = """
            SELECT COUNT(*) FROM alumni a
            JOIN alumni_company c ON c.id = a.company_id
            WHERE (CAST(:query AS TEXT) IS NULL
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(COALESCE(a.headline, '')) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(COALESCE(a.title, '')) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%'))
                   OR LOWER(COALESCE(a.city, '')) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%')))
              AND (CAST(:companyId AS BIGINT) IS NULL OR a.company_id = CAST(:companyId AS BIGINT))
              AND (CAST(:batch AS INTEGER) IS NULL OR a.batch = CAST(:batch AS INTEGER))
              AND (CAST(:course AS TEXT) IS NULL OR a.course = CAST(:course AS TEXT))
              AND (CAST(:city AS TEXT) IS NULL OR LOWER(a.city) = LOWER(CAST(:city AS TEXT)))
              AND (:linkedinOnly = FALSE OR a.linkedin_url IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun search(
        @Param("query") query: String?,
        @Param("companyId") companyId: Long?,
        @Param("batch") batch: Int?,
        @Param("course") course: String?,
        @Param("city") city: String?,
        @Param("linkedinOnly") linkedinOnly: Boolean,
        pageable: Pageable
    ): Page<DMAlumni>

    @Query("SELECT DISTINCT a.batch FROM DMAlumni a WHERE a.batch IS NOT NULL ORDER BY a.batch DESC")
    fun distinctBatches(): List<Int>

    @Query("SELECT a.course FROM DMAlumni a WHERE a.course IS NOT NULL GROUP BY a.course ORDER BY COUNT(a) DESC")
    fun coursesByPopularity(): List<String>

    @Query(
        value = "SELECT city FROM alumni WHERE city IS NOT NULL GROUP BY city ORDER BY COUNT(*) DESC, city ASC LIMIT :limit",
        nativeQuery = true
    )
    fun topCities(@Param("limit") limit: Int): List<String>
}
