package com.example.fairnesstracker.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {
    Optional<MonitoredService> findByPagerdutyServiceId(String pagerdutyServiceId);
}
