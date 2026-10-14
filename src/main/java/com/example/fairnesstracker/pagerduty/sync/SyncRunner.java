package com.example.fairnesstracker.pagerduty.sync;

import com.example.fairnesstracker.pagerduty.OncallSyncService;
import com.example.fairnesstracker.pagerduty.PagerDutyNotConfiguredException;
import com.example.fairnesstracker.pagerduty.PagerDutyProperties;
import com.example.fairnesstracker.pagerduty.PagerDutyService;
import com.example.fairnesstracker.pagerduty.dto.SyncResult;
import com.example.fairnesstracker.pagerduty.sync.SyncRun.Kind;
import com.example.fairnesstracker.pagerduty.sync.SyncRun.Trigger;
import com.example.fairnesstracker.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs every PagerDuty sync, scheduled or manual, under a per-kind lock shared by all gateway instances, and
 * records it as a {@link SyncRun}. A sync whose lock is taken doesn't run and leaves no row.
 *
 * <p>{@code lockAtMostFor} is how long a lock outlives an instance that died holding it. A sync still going
 * after that loses its exclusivity, so keep it well above the longest sync (a long on-call backfill).
 *
 * <p>Without a PagerDuty API token nothing runs: syncs throw {@link PagerDutyNotConfiguredException} and leave
 * no row, so a server that was never connected to PagerDuty doesn't fill up with failed runs.
 */
@Slf4j
@Service
public class SyncRunner {

    static final String ABANDONED = "Interrupted: the gateway stopped before the sync finished";

    private final PagerDutyService incidentSync;
    private final OncallSyncService oncallSync;
    private final SyncRunRepository runs;
    private final LockProvider locks;
    private final Clock clock;
    private final Duration lockAtMostFor;
    private final boolean configured;

    public SyncRunner(PagerDutyService incidentSync, OncallSyncService oncallSync, SyncRunRepository runs,
                      LockProvider locks, Clock clock, PagerDutyProperties pagerDuty,
                      @Value("${pagerduty.sync.lock-at-most-for:30m}") Duration lockAtMostFor) {
        this.incidentSync = incidentSync;
        this.oncallSync = oncallSync;
        this.runs = runs;
        this.locks = locks;
        this.clock = clock;
        this.lockAtMostFor = lockAtMostFor;
        this.configured = pagerDuty.configured();
        if (!configured) {
            log.info("No PagerDuty API token (PAGERDUTY_API_TOKEN): PagerDuty sync is off");
        }
    }

    /** Whether this server has a PagerDuty API token; without one every sync throws. */
    public boolean configured() {
        return configured;
    }

    /** Empty if an incident sync is already running somewhere. */
    public Optional<Completed<SyncResult>> syncIncidents(Trigger trigger) {
        return run(Kind.INCIDENTS, trigger, incidentSync::syncIncidents, result -> new Counts(
                result.created(), result.updated(),
                result.failed() > 0 ? result.failed() + " incident(s) could not be stored; see the gateway log" : null));
    }

    /** Empty if an on-call sync is already running somewhere. */
    public Optional<Completed<SyncResult.Oncall>> syncOncalls(Trigger trigger, int days) {
        return run(Kind.ONCALLS, trigger, () -> oncallSync.syncOncalls(days),
                result -> new Counts(result.shiftsStored(), null, null));
    }

    /** The scheduled on-call sync, over the configured lookback. */
    public Optional<Completed<SyncResult.Oncall>> syncOncalls(Trigger trigger) {
        return run(Kind.ONCALLS, trigger, oncallSync::syncOncalls,
                result -> new Counts(result.shiftsStored(), null, null));
    }

    /**
     * Whether this run is still going. A RUNNING row older than {@code lockAtMostFor} belongs to an instance
     * that died: its lock has expired too, and the next run of that kind closes it.
     */
    public boolean isRunning(SyncRun run) {
        return run.getStatus() == SyncRun.Status.RUNNING
                && run.getStartedAt().plus(lockAtMostFor).isAfter(clock.instant());
    }

    static String lockName(Kind kind) {
        return "pagerduty-sync-" + kind.name().toLowerCase();
    }

    private <T> Optional<Completed<T>> run(Kind kind, Trigger trigger, Supplier<T> work, Function<T, Counts> counts) {
        if (!configured) {
            throw new PagerDutyNotConfiguredException();
        }
        Optional<SimpleLock> lock = locks.lock(
                new LockConfiguration(clock.instant(), lockName(kind), lockAtMostFor, Duration.ZERO));
        if (lock.isEmpty()) {
            log.info("{} {} sync skipped: another one is running", trigger, kind);
            return Optional.empty();
        }
        try {
            // Holding the lock, so any RUNNING row of this kind belongs to an instance that died mid-sync
            Instant startedAt = clock.instant();
            int abandoned = runs.closeAbandoned(kind, startedAt, ABANDONED);
            if (abandoned > 0) {
                log.warn("Marked {} abandoned {} sync run(s) as failed", abandoned, kind);
            }
            SyncRun run = runs.save(new SyncRun(kind, trigger, startedAt,
                    trigger == Trigger.MANUAL ? CurrentUser.id().orElse(null) : null));

            T result;
            try {
                result = work.get();
            } catch (RuntimeException e) {
                run.failed(clock.instant(), describe(e));
                runs.save(run);
                throw e;
            }
            Counts outcome = counts.apply(result);
            run.succeeded(clock.instant(), outcome.created(), outcome.updated(), outcome.note());
            runs.save(run);
            return Optional.of(new Completed<>(run.getId(), result));
        } finally {
            lock.get().unlock();
        }
    }

    // Admins read this; PagerDuty's error messages name the request, not the token
    private static String describe(RuntimeException e) {
        return e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage()
                : e.getClass().getSimpleName();
    }

    /** A sync that ran: its {@link SyncRun} id and what it did. */
    public record Completed<T>(long runId, T result) {
    }

    private record Counts(int created, Integer updated, String note) {
    }
}
