package com.attendanceio.api.repository.alumni

import com.attendanceio.api.model.alumni.DMAlumniCompany
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AlumniCompanyRepository : JpaRepository<DMAlumniCompany, Long> {
    fun findByNameIgnoreCase(name: String): DMAlumniCompany?

    /**
     * Companies matching [query] (blank = all). [sort] is "lpa" (best paying first, unknown salaries
     * last) or anything else for most alumni first.
     */
    @Query(
        value = """
            SELECT c.* FROM alumni_company c
            WHERE (CAST(:query AS TEXT) IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%')))
            ORDER BY
              CASE WHEN :sort = 'lpa' THEN c.avg_lpa END DESC NULLS LAST,
              CASE WHEN CAST(:query AS TEXT) IS NOT NULL AND LOWER(c.name) LIKE LOWER(CONCAT(CAST(:query AS TEXT), '%')) THEN 0 ELSE 1 END,
              c.alumni_count DESC,
              c.name ASC
        """,
        countQuery = """
            SELECT COUNT(*) FROM alumni_company c
            WHERE (CAST(:query AS TEXT) IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:query AS TEXT), '%')))
        """,
        nativeQuery = true
    )
    fun search(@Param("query") query: String?, @Param("sort") sort: String, pageable: Pageable): Page<DMAlumniCompany>
}
