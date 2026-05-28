package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository  extends JpaRepository<AlertEvent,Long> {

}
