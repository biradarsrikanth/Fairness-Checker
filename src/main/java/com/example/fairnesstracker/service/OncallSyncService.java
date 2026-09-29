package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.pagerDuty.SyncResult;
import com.example.fairnesstracker.dto.pagerDuty.oncall.OncallsResponse;
import com.example.fairnesstracker.dto.pagerDuty.oncall.OncallsResponse.Oncall;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.entity.OncallShift;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.example.fairnesstracker.repository.OncallShiftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Stores who was on call when, from PagerDuty /oncalls. PagerDuty may clip shifts to the requested range,
 * so a shift seen in several syncs is merged with any overlapping stored shift for the same engineer,
 * escalation policy and level instead of being stored twice.
 */
@Slf4j
@Service
public class OncallSyncService {

    // PagerDuty limits one /oncalls query to 90 days
    static final int MAX_QUERY_DAYS = 90;
    private static final int PAGE_SIZE = 100;

    private final WebClient webClient;
    private final EngineerRepository engineerRepository;
    private final OncallShiftRepository shiftRepository;
    private final TransactionOperations transactions;
    private final int defaultLookbackDays;

    public OncallSyncService(@Qualifier("pagerDutyClient") WebClient webClient,
                             EngineerRepository engineerRepository,
                             OncallShiftRepository shiftRepository,
                             TransactionOperations transactions,
                             @Value("${pagerduty.sync.oncall-lookback-days}") int defaultLookbackDays) {
        this.webClient = webClient;
        this.engineerRepository = engineerRepository;
        this.shiftRepository = shiftRepository;
        this.transactions = transactions;
        this.defaultLookbackDays = defaultLookbackDays;
    }

    public SyncResult.Oncall syncOncalls() {
        return syncOncalls(defaultLookbackDays);
    }

    /** Syncs the last {@code days} days, in chunks PagerDuty accepts. */
    public SyncResult.Oncall syncOncalls(int days) {
        OffsetDateTime until = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start = until.minusDays(days);
        int stored = 0, skipped = 0;

        for (OffsetDateTime chunkStart = start; chunkStart.isBefore(until); ) {
            OffsetDateTime chunkEnd = min(chunkStart.plusDays(MAX_QUERY_DAYS), until);
            SyncResult.Oncall chunk = syncRange(chunkStart, chunkEnd);
            stored += chunk.shiftsStored();
            skipped += chunk.skipped();
            chunkStart = chunkEnd;
        }
        SyncResult.Oncall result = new SyncResult.Oncall(stored, skipped);
        log.info("PagerDuty on-call sync finished (last {} days): {}", days, result);
        return result;
    }

    private SyncResult.Oncall syncRange(OffsetDateTime since, OffsetDateTime until) {
        int stored = 0, skipped = 0, offset = 0;
        boolean more;
        do {
            final int pageOffset = offset;
            OncallsResponse page = webClient.get()
                    .uri(b -> b.path("/oncalls")
                            .queryParam("since", since.toInstant().toString())
                            .queryParam("until", until.toInstant().toString())
                            .queryParam("time_zone", "UTC")
                            .queryParam("offset", pageOffset)
                            .queryParam("limit", PAGE_SIZE)
                            .build())
                    .retrieve()
                    .bodyToMono(OncallsResponse.class)
                    .block();
            if (page == null || page.getOncalls() == null) break;

            for (Oncall oncall : page.getOncalls()) {
                Boolean saved = transactions.execute(status -> store(oncall, since, until));
                if (Boolean.TRUE.equals(saved)) stored++; else skipped++;
            }
            more = page.isMore();
            offset += PAGE_SIZE;
        } while (more);
        return new SyncResult.Oncall(stored, skipped);
    }

    boolean store(Oncall oncall, OffsetDateTime since, OffsetDateTime until) {
        if (oncall.getUser() == null || oncall.getUser().getId() == null) return false;
        Optional<Engineer> engineer = engineerRepository.findByPagerDutyUserId(oncall.getUser().getId());
        if (engineer.isEmpty()) return false;

        // Permanent on-call entries have no start/end: count them for the synced range only
        LocalDateTime start = oncall.getStart() != null ? PagerDutyService.toUtc(oncall.getStart()) : utc(since);
        LocalDateTime end = oncall.getEnd() != null ? PagerDutyService.toUtc(oncall.getEnd()) : utc(until);
        if (!end.isAfter(start)) return false;

        String policyId = oncall.getEscalationPolicy() != null ? oncall.getEscalationPolicy().getId() : null;
        short level = (short) (oncall.getEscalationLevel() != null ? oncall.getEscalationLevel() : 1);

        List<OncallShift> overlapping = shiftRepository.findOverlapping(
                engineer.get().getId(), policyId, level, start, end);
        OncallShift shift = overlapping.isEmpty() ? new OncallShift() : overlapping.getFirst();
        for (OncallShift other : overlapping) {
            start = min(start, other.getStartsAt());
            end = max(end, other.getEndsAt());
        }
        // Merge: keep one row covering the union, delete the rest
        overlapping.stream().skip(1).forEach(shiftRepository::delete);
        shiftRepository.flush();

        shift.setEngineer(engineer.get());
        shift.setPagerdutyEscalationPolicyId(policyId);
        shift.setPagerdutyScheduleId(oncall.getSchedule() != null ? oncall.getSchedule().getId() : null);
        shift.setEscalationLevel(level);
        shift.setStartsAt(start);
        shift.setEndsAt(end);
        shiftRepository.save(shift);
        return true;
    }

    private static LocalDateTime utc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static OffsetDateTime min(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
