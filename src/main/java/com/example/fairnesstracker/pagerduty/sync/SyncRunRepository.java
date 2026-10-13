package com.example.fairnesstracker.pagerduty.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long>, JpaSpecificationExecutor<SyncRun> {

    Optional<SyncRun> findFirstByKindOrderByStartedAtDesc(SyncRun.Kind kind);

    Optional<SyncRun> findFirstByKindAndStatusOrderByStartedAtDesc(SyncRun.Kind kind, SyncRun.Status status);

    /** Closes runs left RUNNING by an instance that stopped mid-sync. Call only while holding that kind's lock. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE SyncRun r SET r.status = 'FAILED', r.finishedAt = :at, r.error = :error
            WHERE r.kind = :kind AND r.status = 'RUNNING' AND r.startedAt <= :at""")
    int closeAbandoned(SyncRun.Kind kind, Instant at, String error);
}
