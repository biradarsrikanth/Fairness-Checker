package com.example.fairness_tracker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository  extends JpaRepository<AlertEvent,Long> {

}
