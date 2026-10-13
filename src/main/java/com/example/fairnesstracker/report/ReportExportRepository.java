package com.example.fairnesstracker.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExportRepository extends JpaRepository<ReportExport, Long> {

    Page<ReportExport> findByUserId(Long userId, Pageable pageable);
}
