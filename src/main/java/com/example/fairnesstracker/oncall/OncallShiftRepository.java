package com.example.fairnesstracker.oncall;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OncallShiftRepository extends JpaRepository<OncallShift, Long>, JpaSpecificationExecutor<OncallShift> {

    /** Shifts of the same engineer, policy and level that overlap or touch [start, end]. */
    @Query("""
        SELECT s FROM OncallShift s
        WHERE s.engineer.id = :engineerId
          AND s.escalationLevel = :level
          AND ((:policyId IS NULL AND s.pagerdutyEscalationPolicyId IS NULL)
               OR s.pagerdutyEscalationPolicyId = :policyId)
          AND s.startsAt <= :end AND s.endsAt >= :start
        ORDER BY s.startsAt
    """)
    List<OncallShift> findOverlapping(@Param("engineerId") Long engineerId,
                                      @Param("policyId") String policyId,
                                      @Param("level") short level,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    // Loads each shift's engineer in the same query instead of one query per shift
    @Override
    @EntityGraph(attributePaths = "engineer")
    Page<OncallShift> findAll(Specification<OncallShift> spec, Pageable pageable);
}
