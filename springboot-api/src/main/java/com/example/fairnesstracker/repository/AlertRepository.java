package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository  extends JpaRepository<AlertEvent,Long> {

    List<AlertEvent> findByEngineer_Id(Long engineerId);
    Optional<AlertEvent> findByPagerDutyIncidentId(String pagerDutyIncidentId);

    @Query("""
        SELECT a FROM AlertEvent a
        WHERE (:engineerId IS NULL OR a.engineer.id = :engineerId)
        AND (:severity IS NULL OR a.severity = :severity)
        AND (:from IS NULL OR a.triggeredAt >= :from)
        AND (:to IS NULL OR a.triggeredAt <= :to)
    """)
    List<AlertEvent> filterAlerts(
            @Param("engineerId") Long engineerId,
            @Param("severity") String severity,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("SELECT COALESCE(MAX(a.incidentNumber), 0) FROM AlertEvent a")
    Integer findMaxIncidentNumber();
}
