package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.OncallShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OncallShiftRepository extends JpaRepository<OncallShift, Long> {

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

    @Query("""
        SELECT s FROM OncallShift s JOIN FETCH s.engineer
        WHERE s.startsAt < :to AND s.endsAt > :from
          AND (:engineerId IS NULL OR s.engineer.id = :engineerId)
        ORDER BY s.startsAt
    """)
    List<OncallShift> findInPeriod(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   @Param("engineerId") Long engineerId);
}
