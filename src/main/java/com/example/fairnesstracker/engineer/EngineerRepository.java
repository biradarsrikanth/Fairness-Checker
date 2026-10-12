package com.example.fairnesstracker.engineer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EngineerRepository extends JpaRepository<Engineer, Long>, JpaSpecificationExecutor<Engineer> {

    // Loads each engineer's team in the same query instead of one query per engineer
    @Override
    @EntityGraph(attributePaths = "team")
    Page<Engineer> findAll(Specification<Engineer> spec, Pageable pageable);

    Optional<Engineer> findByPagerDutyUserId(String pagerDutyUserId);

    Optional<Engineer> findByName(String name);

    Optional<Engineer> findByEmailIgnoreCase(String email);

    boolean existsByTeam_Id(Long teamId);

    // Access checks: which team an engineer belongs to, without loading them
    @Query("SELECT e.team.id FROM Engineer e WHERE e.id = :id")
    Optional<Long> findTeamIdById(Long id);
}
