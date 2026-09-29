package com.example.fairnesstracker.service;

import com.example.fairnesstracker.dto.pagerDuty.oncall.OncallsResponse;
import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.entity.OncallShift;
import com.example.fairnesstracker.entity.Team;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.example.fairnesstracker.repository.OncallShiftRepository;
import com.example.fairnesstracker.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the merge logic against the real repository queries (H2)
@DataJpaTest
@ActiveProfiles("test")
class OncallSyncServiceTest {

    private static final OffsetDateTime SINCE = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime UNTIL = OffsetDateTime.of(2026, 9, 30, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private EngineerRepository engineerRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private OncallShiftRepository shiftRepository;

    private OncallSyncService service;
    private Engineer asha;

    @BeforeEach
    void setUp() {
        Team sre = teamRepository.save(new Team("SRE", "Asia/Kolkata"));
        asha = new Engineer();
        asha.setName("Asha");
        asha.setEmail("asha@example.com");
        asha.setPagerDutyUserId("PASHA");
        asha.setTeam(sre);
        asha = engineerRepository.save(asha);
        service = new OncallSyncService(WebClient.create("http://unused"), engineerRepository, shiftRepository,
                TransactionOperations.withoutTransaction(), 7);
    }

    @Test
    void storesAShiftForAKnownEngineer() {
        assertThat(service.store(oncall("PASHA", "2026-09-01T00:00:00Z", "2026-09-08T00:00:00Z"), SINCE, UNTIL))
                .isTrue();

        OncallShift shift = shiftRepository.findAll().getFirst();
        assertThat(shift.getEngineer().getId()).isEqualTo(asha.getId());
        assertThat(shift.getStartsAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(shift.getEscalationLevel()).isEqualTo((short) 1);
    }

    @Test
    void skipsPagerDutyUsersWhoAreNotOurEngineers() {
        assertThat(service.store(oncall("PGHOST", "2026-09-01T00:00:00Z", "2026-09-08T00:00:00Z"), SINCE, UNTIL))
                .isFalse();
        assertThat(shiftRepository.count()).isZero();
    }

    @Test
    void mergesTheSameShiftSeenClippedInTwoSyncs() {
        // First sync sees the shift cut off at its range end, the next sync sees the rest
        service.store(oncall("PASHA", "2026-09-05T00:00:00Z", "2026-09-10T00:00:00Z"), SINCE, UNTIL);
        service.store(oncall("PASHA", "2026-09-10T00:00:00Z", "2026-09-12T00:00:00Z"), SINCE, UNTIL);
        service.store(oncall("PASHA", "2026-09-06T00:00:00Z", "2026-09-11T00:00:00Z"), SINCE, UNTIL);

        List<OncallShift> shifts = shiftRepository.findAll();
        assertThat(shifts).hasSize(1);
        assertThat(shifts.getFirst().getStartsAt()).isEqualTo(LocalDateTime.of(2026, 9, 5, 0, 0));
        assertThat(shifts.getFirst().getEndsAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 0, 0));
    }

    @Test
    void keepsSeparateShiftsAndLevelsApart() {
        service.store(oncall("PASHA", "2026-09-01T00:00:00Z", "2026-09-03T00:00:00Z"), SINCE, UNTIL);
        service.store(oncall("PASHA", "2026-09-10T00:00:00Z", "2026-09-12T00:00:00Z"), SINCE, UNTIL);
        OncallsResponse.Oncall backup = oncall("PASHA", "2026-09-01T00:00:00Z", "2026-09-03T00:00:00Z");
        backup.setEscalationLevel(2);
        service.store(backup, SINCE, UNTIL);

        assertThat(shiftRepository.count()).isEqualTo(3);
    }

    @Test
    void permanentOnCallIsCountedForTheSyncedRangeOnly() {
        service.store(oncall("PASHA", null, null), SINCE, UNTIL);

        OncallShift shift = shiftRepository.findAll().getFirst();
        assertThat(shift.getStartsAt()).isEqualTo(SINCE.toLocalDateTime());
        assertThat(shift.getEndsAt()).isEqualTo(UNTIL.toLocalDateTime());
    }

    private static OncallsResponse.Oncall oncall(String userId, String start, String end) {
        OncallsResponse.Ref user = new OncallsResponse.Ref();
        user.setId(userId);
        OncallsResponse.Ref policy = new OncallsResponse.Ref();
        policy.setId("EP1");
        OncallsResponse.Oncall oncall = new OncallsResponse.Oncall();
        oncall.setUser(user);
        oncall.setEscalationPolicy(policy);
        oncall.setEscalationLevel(1);
        oncall.setStart(start);
        oncall.setEnd(end);
        return oncall;
    }
}
