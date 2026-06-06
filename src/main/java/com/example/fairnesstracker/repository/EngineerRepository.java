package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.Engineer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EngineerRepository extends JpaRepository<Engineer, Long> {
    Optional<Engineer>
    findByPagerDutyUserId(String pagerDutyUserId);
}
