package com.example.fairnesstracker.engineer;

import com.example.fairnesstracker.engineer.Engineer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EngineerRepository extends JpaRepository<Engineer, Long> {
    Optional<Engineer>
    findByPagerDutyUserId(String pagerDutyUserId);
    Optional<Engineer> findByName(String name);

    boolean existsByTeam_Id(Long teamId);
}
