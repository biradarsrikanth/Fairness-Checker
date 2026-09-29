package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {
    Optional<MonitoredService> findByPagerdutyServiceId(String pagerdutyServiceId);
}
