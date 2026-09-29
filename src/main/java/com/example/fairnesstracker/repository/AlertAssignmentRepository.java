package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.AlertAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertAssignmentRepository extends JpaRepository<AlertAssignment, Long> {

    List<AlertAssignment> findByAlert_IdOrderByAssignedAtAsc(Long alertId);

    boolean existsByAlert_IdAndKindAndPagerdutyUserIdAndAssignedAt(
            Long alertId, AlertAssignment.Kind kind, String pagerdutyUserId, LocalDateTime assignedAt);

    @Modifying
    @Query("DELETE FROM AlertAssignment a WHERE a.alert.id = :alertId")
    void deleteByAlertId(@Param("alertId") Long alertId);
}
