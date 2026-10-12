package com.example.fairnesstracker.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AlertRepository extends JpaRepository<AlertEvent, Long>, JpaSpecificationExecutor<AlertEvent> {

    // Loads each alert's engineer, team and service in the same query instead of one query per alert
    @Override
    @EntityGraph(attributePaths = {"engineer", "engineer.team", "service"})
    Page<AlertEvent> findAll(Specification<AlertEvent> spec, Pageable pageable);

    boolean existsByEngineer_Id(Long engineerId);

    Optional<AlertEvent> findByPagerDutyIncidentId(String pagerDutyIncidentId);

    @Query("SELECT COALESCE(MAX(a.incidentNumber), 0) FROM AlertEvent a")
    Integer findMaxIncidentNumber();
}
